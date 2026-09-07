package com.aki.tasktimer.timer

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import androidx.core.content.ContextCompat
import com.aki.tasktimer.TaskTimerApp
import com.aki.tasktimer.block.OverdueGuardService
import com.aki.tasktimer.domain.GuardDecision
import com.aki.tasktimer.domain.guardDecision
import com.aki.tasktimer.ui.overdue.OverdueActivity
import kotlinx.coroutines.launch

/**
 * 期限到達。AlarmManager から呼ばれる。
 *
 * やること（この順）：
 * 1. DB で「本当にまだ超過中か」を確かめる（古い予約の誤発火・非常口済みを弾く）
 * 2. 全画面通知を出す（画面が消えていれば超過画面が立ち上がる）
 * 3. バイブと覆いを動かすサービスを起動する。起動できなければ 1 回だけ震わせる
 *
 * 通知を先にするのは、通知は失敗しないから。サービス起動が Android の制限で拒まれても、
 * ユーザーに見える手段が残る。
 */
class OverdueReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_DEADLINE) return
        val app = context.applicationContext as TaskTimerApp
        val container = app.container
        // onReceive はすぐ返さないと殺される。DB を読む間だけ延命する（goAsync）。
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                val session = container.sessionRepository.getRunningSession()
                val decision = guardDecision(
                    session = session,
                    now = System.currentTimeMillis(),
                    blockEnabled = container.settingsRepository.isBlockEnabled(),
                    dismissed = container.settingsRepository.getDismissedOccurrence(),
                )
                if (decision == GuardDecision.NONE) return@launch

                context.getSystemService(NotificationManager::class.java)
                    .notify(OverdueNotifications.ID_OVERDUE_FULL_SCREEN, OverdueNotifications.fullScreen(context))

                // 自アプリが前面なら全画面通知はポップアップ止まりになるので、直接開く。
                if (container.foregroundTracker.isForeground.value) {
                    runCatching {
                        context.startActivity(
                            Intent(context, OverdueActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                        )
                    }
                }

                try {
                    ContextCompat.startForegroundService(
                        context,
                        OverdueGuardService.intent(context, OverdueGuardService.ACTION_OVERDUE),
                    )
                } catch (e: Exception) {
                    // Android の背景起動制限で拒まれた場合。超過画面が開けばそこから再起動を試みる。
                    Log.w(TAG, "サービスを起動できませんでした。1 回だけ震わせます", e)
                    container.vibration.once()
                }
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        const val ACTION_DEADLINE = "com.aki.tasktimer.action.DEADLINE"
        const val EXTRA_SESSION_ID = "session_id"
        const val EXTRA_DEADLINE = "deadline"
        private const val TAG = "OverdueReceiver"
    }
}
