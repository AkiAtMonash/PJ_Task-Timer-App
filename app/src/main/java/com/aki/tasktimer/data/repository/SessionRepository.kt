package com.aki.tasktimer.data.repository

import com.aki.tasktimer.data.db.dao.SessionDao
import com.aki.tasktimer.data.db.entity.SessionEntity
import com.aki.tasktimer.data.db.entity.toDomain
import com.aki.tasktimer.data.model.Extension
import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.data.model.Session
import com.aki.tasktimer.data.model.SessionStatus
import com.aki.tasktimer.data.model.Tag
import com.aki.tasktimer.domain.deadlineMillis
import com.aki.tasktimer.sync.SyncTrigger
import com.aki.tasktimer.timer.DeadlineScheduler
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 進行中セッションの唯一の入口（docs/02_ARCHITECTURE.md 5.1）。
 *
 * サービス・ViewModel・オーバーレイはそれぞれ状態を持たず、全員が
 * [observeRunningSession] を購読する。DB の status = RUNNING のレコードが唯一の正。
 *
 * 書き込みのあとに 2 つの「外の仕組み」を起動する：
 * - Notion 同期（[syncTrigger]）：ON なら送信待ち行列に積み、送信を起動
 * - 期限の予約（[deadlineScheduler]）：開始・切り替え・延長のたびに新しい期限を OS に登録
 * どちらも失敗しても記録は残っているので、ここで例外を外に出さない。
 *
 * DataStore / WorkManager / AlarmManager に直接依存しないのは、
 * この Repository を JVM だけで組み立てられるようにしておくため。
 */
class SessionRepository(
    private val sessionDao: SessionDao,
    private val isSyncEnabled: suspend () -> Boolean = { false },
    private val syncTrigger: SyncTrigger = SyncTrigger.None,
    private val deadlineScheduler: DeadlineScheduler = DeadlineScheduler.None,
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
        scheduleQuietly(id, deadlineMillis(startedAt, plannedMinutes))
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
        runCatching { deadlineScheduler.cancel() }
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
        // 前の予約は同じ PendingIntent なので、新しい期限の登録で自動的に置き換わる。
        scheduleQuietly(result.startedId, deadlineMillis(at, plannedMinutes))
        if (sync) requestSyncQuietly()
        return result.startedId
    }

    /**
     * 進行中セッションを延長する（docs/01_SPEC.md 4.4-3）。
     * 新しい期限（開始 ＋ 延長込みの合計予定）を予約し直す。Notion には送らない。
     *
     * @return 更新後のセッション
     */
    suspend fun addExtension(minutes: Int, now: Long): Session {
        val session = sessionDao.extendRunning(minutes = minutes, now = now).toDomain()
        scheduleQuietly(session.id, deadlineMillis(session.startedAt, session.totalPlannedMinutes))
        return session
    }

    /**
     * 期限の予約を入れ直す。再起動後（予約は再起動で全部消える）と、アプリ起動時に呼ぶ。
     * 期限がすでに過ぎていれば OS が即座に鳴らす。進行中が無ければ予約を消す。
     */
    suspend fun rescheduleDeadline() {
        val running = getRunningSession()
        if (running == null) {
            runCatching { deadlineScheduler.cancel() }
        } else {
            scheduleQuietly(running.id, deadlineMillis(running.startedAt, running.totalPlannedMinutes))
        }
    }

    private fun scheduleQuietly(sessionId: Long, deadline: Long) {
        runCatching { deadlineScheduler.schedule(sessionId, deadline) }
    }

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
