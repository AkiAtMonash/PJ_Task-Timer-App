package com.aki.tasktimer.timer

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.aki.tasktimer.R
import com.aki.tasktimer.block.OverdueGuardService
import com.aki.tasktimer.ui.overdue.OverdueActivity

/**
 * 超過まわりの通知（docs/05 2.2）。
 *
 * Android では「他のアプリの上に全画面で出す」は通知の一種（full-screen intent）なので、
 * 超過画面を出すには通知が要る。チャンネルの重要度を下げると全画面表示が無視される。
 */
object OverdueNotifications {

    /** 超過の全画面通知。IMPORTANCE_HIGH でないと full-screen intent が効かない。 */
    const val CHANNEL_OVERDUE = "overdue"

    /** 超過中に常駐する通知（バイブと覆いを動かし続けるサービス用）。控えめでよい。 */
    const val CHANNEL_GUARD = "overdue_guard"

    const val ID_OVERDUE_FULL_SCREEN = 2001
    const val ID_GUARD = 2002

    fun createChannels(context: Context) {
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_OVERDUE, "予定時間の超過", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "予定時間が来たときに全画面で知らせる"
                // 音は鳴らさない（docs/01_SPEC.md 4.4-6）。バイブはサービス側で連続して鳴らす。
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_GUARD, "超過中", NotificationManager.IMPORTANCE_LOW).apply {
                description = "予定時間を超過している間だけ表示される"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    /** 予定時間が来た瞬間に出す通知。画面が消えていれば超過画面が全画面で立ち上がる。 */
    fun fullScreen(context: Context): Notification {
        val openOverdue = PendingIntent.getActivity(
            context,
            0,
            Intent(context, OverdueActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_OVERDUE)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle("予定時間を超過")
            .setContentText("延長するか、次のタスクへ")
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setFullScreenIntent(openOverdue, true)
            .setContentIntent(openOverdue)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    /** 超過中に常駐する通知。タップすると超過画面（または切り替えフロー）に戻る。 */
    fun guard(context: Context, acknowledged: Boolean): Notification {
        val bringBack = PendingIntent.getForegroundService(
            context,
            1,
            OverdueGuardService.intent(context, OverdueGuardService.ACTION_BRING_BACK),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(context, CHANNEL_GUARD)
            .setSmallIcon(R.drawable.ic_stat_timer)
            .setContentTitle(if (acknowledged) "次のタスクを始めるまで戻れません" else "予定時間を超過中")
            .setContentText("タップして戻る")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_STATUS)
            .setContentIntent(bringBack)
            .setOngoing(true)
            .setSilent(true)
            .build()
    }

    fun cancelFullScreen(context: Context) {
        context.getSystemService(NotificationManager::class.java).cancel(ID_OVERDUE_FULL_SCREEN)
    }
}
