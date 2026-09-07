package com.aki.tasktimer.sync

import android.content.Context
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

/**
 * WorkManager に [NotionSyncWorker] を 1 回ぶん積む。
 *
 * - ネットが繋がっているときだけ動く（機内モードなら復帰まで待つ）
 * - 失敗したら 30 秒 → 60 秒 → 120 秒… と間を空けて自動で再実行する
 * - 同名の作業として積むので、連打しても同時に 2 つは走らない
 *
 * APPEND_OR_REPLACE にしている理由：KEEP だと「今走っている Worker が行列を空だと確認した直後に
 * 新しい行が入った」ケースで新しい依頼が捨てられ、その行が次の機会まで送られない。
 * APPEND なら今の実行の後ろに連結され、必ずもう 1 周する。
 */
class WorkManagerSyncTrigger(context: Context) : SyncTrigger {

    private val appContext = context.applicationContext

    override fun requestSync() {
        val request = OneTimeWorkRequestBuilder<NotionSyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build(),
            )
            .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
            .build()
        WorkManager.getInstance(appContext)
            .enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.APPEND_OR_REPLACE, request)
    }

    private companion object {
        const val WORK_NAME = "notion_sync"
    }
}
