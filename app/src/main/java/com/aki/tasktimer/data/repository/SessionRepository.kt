package com.aki.tasktimer.data.repository

import com.aki.tasktimer.data.db.dao.SessionDao
import com.aki.tasktimer.data.db.entity.SessionEntity
import com.aki.tasktimer.data.db.entity.toDomain
import com.aki.tasktimer.data.model.Extension
import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.data.model.Session
import com.aki.tasktimer.data.model.SessionStatus
import com.aki.tasktimer.data.model.Tag
import com.aki.tasktimer.sync.SyncTrigger
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 進行中セッションの唯一の入口（docs/02_ARCHITECTURE.md 5.1）。
 *
 * サービス・ViewModel・オーバーレイはそれぞれ状態を持たず、全員が
 * [observeRunningSession] を購読する。DB の status = RUNNING のレコードが唯一の正。
 *
 * Notion 同期との関係：
 * - 書き込みのたびに [isSyncEnabled] を見て、ON なら DAO に「送信待ち行列にも積め」と伝える
 *   （積む処理そのものは DAO のトランザクション内）
 * - コミット後に [syncTrigger] で送信を起動する。起動に失敗しても記録は残っているので、
 *   ここで例外を外に出さない
 *
 * DataStore や WorkManager に直接依存しないのは、この Repository を JVM だけで
 * 組み立てられるようにしておくため。
 */
class SessionRepository(
    private val sessionDao: SessionDao,
    private val isSyncEnabled: suspend () -> Boolean = { false },
    private val syncTrigger: SyncTrigger = SyncTrigger.None,
) {

    // ---- 購読 ----

    fun observeRunningSession(): Flow<Session?> =
        sessionDao.observeRunning().map { it?.toDomain() }

    fun observeHistory(): Flow<List<Session>> =
        sessionDao.observeFinished().map { entities -> entities.map { it.toDomain() } }

    fun observeLastFinishedSession(): Flow<Session?> =
        sessionDao.observeLastFinished().map { it?.toDomain() }

    fun observeExtensions(sessionId: Long): Flow<List<Extension>> =
        sessionDao.observeExtensions(sessionId).map { entities -> entities.map { it.toDomain() } }

    // ---- 単発の取得 ----

    suspend fun getRunningSession(): Session? = sessionDao.getRunning()?.toDomain()

    suspend fun getSession(id: Long): Session? = sessionDao.getById(id)?.toDomain()

    suspend fun getExtensions(sessionId: Long): List<Extension> =
        sessionDao.getExtensions(sessionId).map { it.toDomain() }

    // ---- 更新 ----

    /**
     * 未計測の状態から開始する。進行中セッションがある場合は例外。
     * 切り替えたいときは [switchSession] を使う。
     *
     * @return 開始したセッションの id
     */
    suspend fun startSession(
        name: String,
        tag: Tag,
        goal: String,
        plannedMinutes: Int,
        startedAt: Long,
    ): Long {
        val sync = isSyncEnabled()
        val id = sessionDao.startExclusively(
            newRunningSession(name, tag, goal, plannedMinutes, startedAt),
            enqueueSync = sync,
        )
        if (sync) requestSyncQuietly()
        return id
    }

    /**
     * 進行中セッションを終える。次を開始しない。
     * 通常の画面からは呼ばない（記録が止まる瞬間を作らないため）。
     *
     * @return 終了したセッションの id
     */
    suspend fun finishSession(rating: Rating, ratingNote: String?, endedAt: Long): Long {
        val sync = isSyncEnabled()
        val id = sessionDao.finishRunning(
            endedAt = endedAt,
            rating = rating,
            ratingNote = ratingNote,
            enqueueSync = sync,
        )
        if (sync) requestSyncQuietly()
        return id
    }

    /**
     * 前のタスクを終えて、同時に次のタスクを開始する（docs/01_SPEC.md 4.3）。
     *
     * 終了時刻＝次の開始時刻。「時間は途切れない」という前提を守るため、
     * 2 つの操作に分けずここで 1 トランザクションにしている。
     * 進行中が無ければ単に開始するだけなので、その場合 rating は null でよい。
     *
     * @return 新しく開始したセッションの id
     */
    suspend fun switchSession(
        rating: Rating?,
        ratingNote: String?,
        name: String,
        tag: Tag,
        goal: String,
        plannedMinutes: Int,
        at: Long,
    ): Long {
        val sync = isSyncEnabled()
        val result = sessionDao.switchRunning(
            at = at,
            rating = rating,
            ratingNote = ratingNote,
            next = newRunningSession(name, tag, goal, plannedMinutes, at),
            enqueueSync = sync,
        )
        if (sync) requestSyncQuietly()
        return result.startedId
    }

    /**
     * 進行中セッションを延長する（docs/01_SPEC.md 4.4-3）。
     * Notion には送らない（タスクはまだ終わっていない）。
     *
     * @return 更新後のセッション。呼び出し側はこの totalPlannedMinutes で
     *         アラームを再登録する（Phase 4）
     */
    suspend fun addExtension(minutes: Int, now: Long): Session =
        sessionDao.extendRunning(minutes = minutes, now = now).toDomain()

    private fun requestSyncQuietly() {
        // 送信の起動に失敗しても記録は DB に残っている。次の書き込みかアプリ起動時に拾われる。
        runCatching { syncTrigger.requestSync() }
    }

    private fun newRunningSession(
        name: String,
        tag: Tag,
        goal: String,
        plannedMinutes: Int,
        startedAt: Long,
    ): SessionEntity {
        require(name.isNotBlank()) { "タスク名が空です" }
        require(plannedMinutes > 0) { "予定時間は 1 分以上でなければなりません: $plannedMinutes" }
        return SessionEntity(
            name = name.trim(),
            tag = tag,
            goal = goal.trim(),
            plannedMinutes = plannedMinutes,
            // 開始時点では延長ゼロなので当初予定と一致する。以後 plannedMinutes は動かさない。
            totalPlannedMinutes = plannedMinutes,
            startedAt = startedAt,
            endedAt = null,
            status = SessionStatus.RUNNING,
            rating = null,
            ratingNote = null,
        )
    }
}
