package com.aki.tasktimer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import com.aki.tasktimer.data.db.entity.SyncQueueEntity
import kotlinx.coroutines.flow.Flow

/**
 * Notion 送信待ち行列の参照と状態更新。
 *
 * 行列への **投入** はここではなく [SessionDao] のトランザクション内で行う
 * （セッションの保存と不可分にするため）。ここは送信側（Worker）と設定画面が使う。
 */
@Dao
interface SyncQueueDao {

    @Query("SELECT * FROM sync_queue WHERE status = 'PENDING' ORDER BY id ASC LIMIT 1")
    suspend fun nextPending(): SyncQueueEntity?

    @Query("SELECT COUNT(*) FROM sync_queue WHERE status = :status")
    fun observeCount(status: String): Flow<Int>

    @Query("SELECT * FROM sync_queue WHERE status = 'FAILED' ORDER BY id DESC LIMIT 5")
    fun observeFailed(): Flow<List<SyncQueueEntity>>

    @Query("UPDATE sync_queue SET status = 'DONE', lastError = NULL WHERE id = :id")
    suspend fun markDone(id: Long)

    @Query("UPDATE sync_queue SET status = 'FAILED', lastError = :error WHERE id = :id")
    suspend fun markFailed(id: Long, error: String)

    @Query("UPDATE sync_queue SET retryCount = retryCount + 1, lastError = :error WHERE id = :id")
    suspend fun bumpRetry(id: Long, error: String)

    @Query("UPDATE sync_queue SET status = 'PENDING', retryCount = 0, lastError = NULL WHERE status = 'FAILED'")
    suspend fun retryAllFailed()

    @Query("UPDATE sessions SET notionPageId = :pageId WHERE id = :sessionId")
    suspend fun setNotionPageId(sessionId: Long, pageId: String)

    // デバッグ用の「日またぎテスト記録」を積むために使う。通常の投入は SessionDao 側。
    @Insert
    suspend fun insert(item: SyncQueueEntity): Long
}
