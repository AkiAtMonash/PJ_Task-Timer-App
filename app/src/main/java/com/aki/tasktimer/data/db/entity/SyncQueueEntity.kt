package com.aki.tasktimer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aki.tasktimer.data.model.SyncOperation
import com.aki.tasktimer.data.model.SyncQueueItem
import com.aki.tasktimer.data.model.SyncStatus

/**
 * Notion への送信待ち行列（docs/06 5.2）。
 *
 * セッションの開始・終了と同じトランザクションで 1 行ずつ積む。
 * 送信はこの表を id の昇順に消化するだけなので、「前タスクの完了 → 次タスクの進行中」の順序が
 * ネットワークの都合で入れ替わることがない。
 *
 * sessions への外部キーは張らない。v1 にセッション削除は無く、
 * 張るとマイグレーションの SQL が長くなるだけで得るものがない。
 */
@Entity(
    tableName = "sync_queue",
    indices = [
        Index(value = ["status"]),
        Index(value = ["sessionId"]),
    ],
)
data class SyncQueueEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val operation: SyncOperation,
    val status: SyncStatus = SyncStatus.PENDING,
    val retryCount: Int = 0,
    val lastError: String? = null,
    val createdAt: Long,
)

fun SyncQueueEntity.toDomain(): SyncQueueItem = SyncQueueItem(
    id = id,
    sessionId = sessionId,
    operation = operation,
    status = status,
    retryCount = retryCount,
    lastError = lastError,
    createdAt = createdAt,
)
