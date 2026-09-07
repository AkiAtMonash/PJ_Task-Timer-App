package com.aki.tasktimer.data.model

/** Notion に対して何をするか。 */
enum class SyncOperation {
    /** 開始時：「進行中」のページを作る */
    CREATE,

    /** 終了時：ページを「完了」に書き換える（日をまたいでいれば追加ページも作る） */
    FINISH,
}

/**
 * 送信待ち行列の 1 行の状態。
 *
 * PENDING → DONE が正常系。ClientError（トークン誤りなど）や再試行の上限で FAILED に落ち、
 * 設定画面の「再試行」で PENDING に戻る。
 */
enum class SyncStatus { PENDING, DONE, FAILED }

data class SyncQueueItem(
    val id: Long,
    val sessionId: Long,
    val operation: SyncOperation,
    val status: SyncStatus,
    val retryCount: Int,
    val lastError: String?,
    val createdAt: Long,
)
