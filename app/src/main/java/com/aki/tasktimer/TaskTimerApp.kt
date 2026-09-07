package com.aki.tasktimer

import android.app.Application
import com.aki.tasktimer.di.AppContainer
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

        // 送り損ねた分の拾い上げ。前回の送信が途中で止まっていても、次にアプリを開いたときに再開する。
        // 同期 OFF なら何もしない（Worker 側でも二重に確認する）。
        container.applicationScope.launch {
            runCatching {
                if (container.settingsRepository.isSyncEnabled()) container.syncTrigger.requestSync()
            }
        }
    }
}
