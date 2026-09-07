package com.aki.tasktimer.timer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.aki.tasktimer.TaskTimerApp
import kotlinx.coroutines.launch

/**
 * 再起動後の予約し直し（docs/01_SPEC.md 5.2）。
 *
 * AlarmManager の予約は再起動で全部消える。進行中セッションが DB に残っていれば
 * その期限を登録し直す。期限がすでに過ぎていれば AlarmManager が即座に鳴らしてくれるので、
 * 「過ぎていたらすぐ超過画面」はこれだけで実現できる。
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        val container = (context.applicationContext as TaskTimerApp).container
        val pending = goAsync()
        container.applicationScope.launch {
            try {
                container.sessionRepository.rescheduleDeadline()
            } finally {
                pending.finish()
            }
        }
    }
}
