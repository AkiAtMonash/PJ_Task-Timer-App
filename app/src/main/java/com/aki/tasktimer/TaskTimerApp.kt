package com.aki.tasktimer

import android.app.Application
import com.aki.tasktimer.di.AppContainer

/**
 * AppContainer を保持するだけの Application。
 *
 * Activity / Service / BroadcastReceiver はどれもここから Repository を取る。
 * プロセスが作り直されても必ず先に onCreate が走るので、
 * サービスやレシーバから見ても container は常に初期化済みになる。
 */
class TaskTimerApp : Application() {

    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
