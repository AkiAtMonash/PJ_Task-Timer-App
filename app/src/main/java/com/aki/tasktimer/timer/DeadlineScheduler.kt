package com.aki.tasktimer.timer

/**
 * 「この時刻になったら起こして」を OS に頼むための口。
 *
 * Repository はこれしか知らない。実体は AlarmManager（[AlarmScheduler]）だが、
 * JVM のテストでは [None] を差して何もしないようにできる（SyncTrigger と同じ形）。
 */
interface DeadlineScheduler {
    /** 期限を（再）登録する。同じセッションの再登録は上書き。 */
    fun schedule(sessionId: Long, deadlineMillis: Long)

    /** 進行中セッションが無くなったときに取り消す。 */
    fun cancel()

    object None : DeadlineScheduler {
        override fun schedule(sessionId: Long, deadlineMillis: Long) = Unit
        override fun cancel() = Unit
    }
}
