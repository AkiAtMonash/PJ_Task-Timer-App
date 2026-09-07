package com.aki.tasktimer.timer

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent

/**
 * 期限の予約（docs/05 2.1）。
 *
 * - `setExactAndAllowWhileIdle`：Doze（画面を消して放置した省電力状態）でも時刻どおりに起きる。
 *   `setExact` は Doze 中に発火しないので使わない
 * - 権限は `USE_EXACT_ALARM`（インストール時に自動付与・剥奪されない）。`SCHEDULE_EXACT_ALARM` は宣言しない
 * - PendingIntent は 1 本だけ（requestCode 固定 ＋ UPDATE_CURRENT）。同時に走るタイマーは 1 個なので、
 *   新しい期限を登録すれば古い予約は自動的に置き換わる
 *
 * 受信側（[OverdueReceiver]）は extras を信用せず、必ず DB で「まだ超過中か」を確かめる。
 */
class AlarmScheduler(context: Context) : DeadlineScheduler {

    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)

    override fun schedule(sessionId: Long, deadlineMillis: Long) {
        alarmManager.setExactAndAllowWhileIdle(
            AlarmManager.RTC_WAKEUP,
            deadlineMillis,
            pendingIntent(sessionId, deadlineMillis),
        )
    }

    override fun cancel() {
        alarmManager.cancel(pendingIntent(sessionId = 0L, deadlineMillis = 0L))
    }

    private fun pendingIntent(sessionId: Long, deadlineMillis: Long): PendingIntent =
        PendingIntent.getBroadcast(
            appContext,
            REQUEST_CODE,
            Intent(appContext, OverdueReceiver::class.java)
                .setAction(OverdueReceiver.ACTION_DEADLINE)
                .putExtra(OverdueReceiver.EXTRA_SESSION_ID, sessionId)
                .putExtra(OverdueReceiver.EXTRA_DEADLINE, deadlineMillis),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

    private companion object {
        const val REQUEST_CODE = 1001
    }
}
