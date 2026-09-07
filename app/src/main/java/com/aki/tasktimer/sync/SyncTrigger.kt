package com.aki.tasktimer.sync

/**
 * 「送信待ちがあるので送ってほしい」と知らせるための口。
 *
 * Repository はこれしか知らない。実体は WorkManager（[WorkManagerSyncTrigger]）だが、
 * JVM のテストやプレビューでは [None] を差して何もしないようにできる。
 */
interface SyncTrigger {
    fun requestSync()

    object None : SyncTrigger {
        override fun requestSync() = Unit
    }
}
