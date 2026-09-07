package com.aki.tasktimer

import android.app.Application
import com.aki.tasktimer.di.AppContainer
import com.aki.tasktimer.timer.OverdueNotifications
import kotlinx.coroutines.launch

/**
 * AppContainer を保持する Application。
 *
 * Activity / Service / BroadcastReceiver / Worker はどれもここから Repository を取る。
 * プロセスが作り直されても必ず先に onCreate が走るので、
 * サービスやレシーバから見ても container は常に初期化済みになる。
 */
class TaskTimerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        registerActivityLifecycleCallbacks(container.foregroundTracker)
        // チャンネルは何度作っても同じ設定なら無害。起動のたびに作っておく。
        OverdueNotifications.createChannels(this)

        container.applicationScope.launch {
            // 期限の予約を入れ直す。強制終了や更新で予約が消えていても、次に起動したときに復旧する。
            // 期限がすでに過ぎていれば OS が即座に鳴らす（非常口で閉じた分は受信側で弾く）。
            runCatching { container.sessionRepository.rescheduleDeadline() }

            // 送り損ねた Notion 送信の拾い上げ。同期 OFF なら何もしない。
            runCatching {
                if (container.settingsRepository.isSyncEnabled()) container.syncTrigger.requestSync()
            }
        }
    }
}
