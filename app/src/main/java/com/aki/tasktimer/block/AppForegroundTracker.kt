package com.aki.tasktimer.block

import android.app.Activity
import android.app.Application
import android.os.Bundle
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * 自アプリの画面が前面にあるかどうか。
 *
 * 覆い（オーバーレイ）は「自アプリの画面が見えているときは隠す」必要がある
 * （超過画面や切り替えフローを覆ってしまっては操作できない）。
 * 画面の数を数えるだけの単純な仕組みで、追加ライブラリは使わない。
 * 画面 A → B の遷移では B の開始が A の停止より先に来るので、数が 0 に落ちてチラつくことはない。
 */
class AppForegroundTracker : Application.ActivityLifecycleCallbacks {

    private var startedCount = 0
    private val _isForeground = MutableStateFlow(false)
    val isForeground: StateFlow<Boolean> = _isForeground

    override fun onActivityStarted(activity: Activity) {
        startedCount++
        _isForeground.value = startedCount > 0
    }

    override fun onActivityStopped(activity: Activity) {
        startedCount = (startedCount - 1).coerceAtLeast(0)
        _isForeground.value = startedCount > 0
    }

    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityResumed(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
