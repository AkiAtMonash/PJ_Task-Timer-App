package com.aki.tasktimer.sync

import com.aki.tasktimer.data.db.dao.SessionDao
import com.aki.tasktimer.data.db.dao.SyncQueueDao
import com.aki.tasktimer.data.db.entity.SessionEntity
import com.aki.tasktimer.data.db.entity.SyncQueueEntity
import com.aki.tasktimer.data.db.entity.toDomain
import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.data.model.SessionStatus
import com.aki.tasktimer.data.model.SyncOperation
import com.aki.tasktimer.data.model.SyncQueueItem
import com.aki.tasktimer.data.model.SyncStatus
import com.aki.tasktimer.data.model.Tag
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * 送信待ち行列の状態を設定画面に見せる／操作するための入口。
 * 行列への通常の投入は SessionRepository → SessionDao で行う。ここは覗く側。
 */
class SyncRepository(
    private val syncQueueDao: SyncQueueDao,
    private val sessionDao: SessionDao,
    private val syncTrigger: SyncTrigger,
) {

    fun observePendingCount(): Flow<Int> = syncQueueDao.observeCount(SyncStatus.PENDING.name)

    fun observeFailedCount(): Flow<Int> = syncQueueDao.observeCount(SyncStatus.FAILED.name)

    fun observeFailed(): Flow<List<SyncQueueItem>> =
        syncQueueDao.observeFailed().map { list -> list.map { it.toDomain() } }

    /** 失敗した行を全部 PENDING に戻して送り直す。 */
    suspend fun retryAllFailed() {
        syncQueueDao.retryAllFailed()
        syncTrigger.requestSync()
    }

    /** 溜まっている分を今すぐ送る。 */
    fun requestSync() = syncTrigger.requestSync()

    /**
     * デバッグ用：「昨日 23:50 〜 今日 0:20」の完了済み記録を作って行列に積む。
     * 深夜を待たずに日またぎの分割（2 ページになる）を実機で確かめるためのもの。
     * 履歴にも残るので、本番運用に入ったら使わないこと。
     */
    suspend fun insertCrossMidnightTestSession(now: Long, zone: ZoneId) {
        val todayStart = ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(now), zone)
            .toLocalDate()
            .atStartOfDay(zone)
        val startedAt = todayStart.minusMinutes(10).toInstant().toEpochMilli()
        val endedAt = todayStart.plusMinutes(20).toInstant().toEpochMilli()

        val id = sessionDao.insert(
            SessionEntity(
                name = "日またぎテスト",
                tag = Tag.FOR_MYSELF,
                goal = "0時で2ページに分かれることを確認する",
                plannedMinutes = 30,
                totalPlannedMinutes = 30,
                startedAt = startedAt,
                endedAt = endedAt,
                status = SessionStatus.DONE,
                rating = Rating.NORMAL,
                ratingNote = "特に無し",
            ),
        )
        syncQueueDao.insert(SyncQueueEntity(sessionId = id, operation = SyncOperation.CREATE, createdAt = now))
        syncQueueDao.insert(SyncQueueEntity(sessionId = id, operation = SyncOperation.FINISH, createdAt = now))
        syncTrigger.requestSync()
    }
}
