package com.aki.tasktimer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aki.tasktimer.data.db.entity.ExtensionEntity
import com.aki.tasktimer.data.db.entity.SessionEntity
import com.aki.tasktimer.data.db.entity.SyncQueueEntity
import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.data.model.SessionStatus
import com.aki.tasktimer.data.model.SyncOperation
import com.aki.tasktimer.domain.elapsedMinutes
import kotlinx.coroutines.flow.Flow

/** [SessionDao.switchRunning] の結果。終えたセッションが無ければ finishedId は null。 */
data class SwitchResult(
    val finishedId: Long?,
    val startedId: Long,
)

/**
 * セッションと延長の DAO。
 *
 * 状態を変える操作をすべて `@Transaction` の中に置いているのは、
 * 「進行中セッションを読む → 判断する → 書く」を不可分にするため。
 * 読みと書きを呼び出し側で分けると、その隙間で RUNNING が 2 件になりうる。
 *
 * Notion の送信待ち行列（sync_queue）への投入も同じトランザクションで行う。
 * セッションは保存できたのに行列に積めなかった、という食い違いを作らないため。
 */
@Dao
abstract class SessionDao {

    // ---- 参照 ----

    @Query("SELECT * FROM sessions WHERE status = 'RUNNING' LIMIT 1")
    abstract fun observeRunning(): Flow<SessionEntity?>

    @Query("SELECT * FROM sessions WHERE status = 'RUNNING' LIMIT 1")
    abstract suspend fun getRunning(): SessionEntity?

    @Query("SELECT * FROM sessions WHERE id = :id")
    abstract suspend fun getById(id: Long): SessionEntity?

    @Query("SELECT * FROM sessions WHERE status = 'DONE' ORDER BY startedAt DESC")
    abstract fun observeFinished(): Flow<List<SessionEntity>>

    @Query("SELECT * FROM sessions WHERE status = 'DONE' ORDER BY endedAt DESC LIMIT 1")
    abstract fun observeLastFinished(): Flow<SessionEntity?>

    @Query("SELECT * FROM extensions WHERE sessionId = :sessionId ORDER BY extendedAt ASC")
    abstract fun observeExtensions(sessionId: Long): Flow<List<ExtensionEntity>>

    @Query("SELECT * FROM extensions WHERE sessionId = :sessionId ORDER BY extendedAt ASC")
    abstract suspend fun getExtensions(sessionId: Long): List<ExtensionEntity>

    // ---- 素の書き込み ----
    // Repository からは呼ばない。必ず下のトランザクション経由で使うこと。
    // （protected にすると Room の生成コードとの相性が読めないので public のままにしてある。
    //   DAO に触れるのは data/repository だけ、という約束で担保する）

    @Insert
    abstract suspend fun insert(session: SessionEntity): Long

    @Update
    abstract suspend fun update(session: SessionEntity)

    @Insert
    abstract suspend fun insertExtension(extension: ExtensionEntity): Long

    @Insert
    abstract suspend fun insertSyncQueue(item: SyncQueueEntity): Long

    @Query("SELECT COUNT(*) FROM sync_queue WHERE sessionId = :sessionId AND operation = 'CREATE'")
    abstract suspend fun countCreateQueued(sessionId: Long): Int

    // ---- トランザクション ----

    /**
     * 進行中セッションが無いことを確認したうえで開始する。
     * すでにある場合は例外。黙って 2 件目を作ると、以降どちらが正なのか誰にも分からなくなる。
     *
     * @param enqueueSync true なら Notion の送信待ち行列に CREATE を積む（同期 ON のとき）
     */
    @Transaction
    open suspend fun startExclusively(session: SessionEntity, enqueueSync: Boolean): Long {
        val running = getRunning()
        check(running == null) {
            "進行中のセッション（id=${running?.id}, name=${running?.name}）があります。" +
                "先に switch を使ってください"
        }
        val id = insert(session)
        if (enqueueSync) enqueueCreate(id, session.startedAt)
        return id
    }

    /**
     * 進行中セッションを終える。次を開始しない。
     * 通常の操作では使わない（記録が止まる瞬間を作らないため）。デバッグ用の導線などから使う。
     *
     * @return 終了したセッションの id
     */
    @Transaction
    open suspend fun finishRunning(
        endedAt: Long,
        rating: Rating,
        ratingNote: String?,
        enqueueSync: Boolean,
    ): Long {
        val running = checkNotNull(getRunning()) { "進行中のセッションがありません" }
        require(endedAt > running.startedAt) {
            "endedAt($endedAt) は startedAt(${running.startedAt}) より後でなければなりません"
        }
        update(
            running.copy(
                endedAt = endedAt,
                status = SessionStatus.DONE,
                rating = rating,
                ratingNote = ratingNote,
            ),
        )
        if (enqueueSync) enqueueFinishIfTracked(running.id, endedAt)
        return running.id
    }

    /**
     * 前のセッションを終了し、同じ時刻で次を開始する（docs/01_SPEC.md 4.3「切り替え」）。
     *
     * 終了時刻と開始時刻に **同じ [at]** を使うのが肝。別々に now を取ると、
     * その差が「どのタスクでもない時間」として記録から抜け落ちる。
     * 進行中が無い場合は単に開始するだけなので、rating は不要。
     *
     * 送信待ち行列には FINISH（前）→ CREATE（次）の順で積む。Notion 側で
     * 「前が完了してから次が進行中になる」順序を id の昇順で保証するため。
     */
    @Transaction
    open suspend fun switchRunning(
        at: Long,
        rating: Rating?,
        ratingNote: String?,
        next: SessionEntity,
        enqueueSync: Boolean,
    ): SwitchResult {
        val running = getRunning()
        if (running != null) {
            checkNotNull(rating) { "進行中セッションを終えるには評価が必要です" }
            require(at > running.startedAt) {
                "切り替え時刻($at) は startedAt(${running.startedAt}) より後でなければなりません"
            }
            update(
                running.copy(
                    endedAt = at,
                    status = SessionStatus.DONE,
                    rating = rating,
                    ratingNote = ratingNote,
                ),
            )
            if (enqueueSync) enqueueFinishIfTracked(running.id, at)
        }
        val startedId = insert(next.copy(startedAt = at, status = SessionStatus.RUNNING, endedAt = null))
        if (enqueueSync) enqueueCreate(startedId, at)
        return SwitchResult(finishedId = running?.id, startedId = startedId)
    }

    /**
     * 進行中セッションを延長する。
     * 延長履歴の追加と totalPlannedMinutes の更新は必ずセットで行う。
     *
     * elapsedAtExtension をここで計算しているのは、トランザクション内で読んだ
     * startedAt と必ず同じ行から導出するため。呼び出し側で計算すると値がずれうる。
     *
     * Notion には何も送らない。延長してもタスクは終わっていないので、ページは「進行中」のまま。
     *
     * @return 更新後のセッション（新しい期限を組み立てるのに使う）
     */
    @Transaction
    open suspend fun extendRunning(minutes: Int, now: Long): SessionEntity {
        require(minutes > 0) { "延長は 1 分以上でなければなりません: $minutes" }
        val running = checkNotNull(getRunning()) { "進行中のセッションがありません" }
        insertExtension(
            ExtensionEntity(
                sessionId = running.id,
                minutes = minutes,
                extendedAt = now,
                elapsedAtExtension = elapsedMinutes(running.startedAt, now),
            ),
        )
        val updated = running.copy(totalPlannedMinutes = running.totalPlannedMinutes + minutes)
        update(updated)
        return updated
    }

    // ---- 送信待ち行列（トランザクション内から呼ぶ） ----

    private suspend fun enqueueCreate(sessionId: Long, now: Long) {
        insertSyncQueue(
            SyncQueueEntity(sessionId = sessionId, operation = SyncOperation.CREATE, createdAt = now),
        )
    }

    /**
     * CREATE が積まれたことのあるセッションだけ FINISH を積む。
     * 同期 OFF のときに始めたセッションは Notion にページが無いので、終了だけ送っても意味がない
     * （「連携 ON 後に始めた分だけ送る」という決め事をここで実現している）。
     */
    private suspend fun enqueueFinishIfTracked(sessionId: Long, now: Long) {
        if (countCreateQueued(sessionId) == 0) return
        insertSyncQueue(
            SyncQueueEntity(sessionId = sessionId, operation = SyncOperation.FINISH, createdAt = now),
        )
    }
}
