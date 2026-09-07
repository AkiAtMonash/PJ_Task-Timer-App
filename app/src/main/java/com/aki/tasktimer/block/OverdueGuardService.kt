package com.aki.tasktimer.block

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.provider.Settings
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import com.aki.tasktimer.MainActivity
import com.aki.tasktimer.TaskTimerApp
import com.aki.tasktimer.data.model.Session
import com.aki.tasktimer.domain.DismissedOccurrence
import com.aki.tasktimer.domain.deadlineMillis
import com.aki.tasktimer.domain.guardDecision
import com.aki.tasktimer.domain.GuardDecision
import com.aki.tasktimer.timer.OverdueNotifications
import com.aki.tasktimer.ui.overdue.OverdueActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

/**
 * 超過している間だけ生きるサービス。バイブ・覆い・常駐通知の生殺与奪をここに集約する。
 *
 * 状態は DB から導く（docs/02_ARCHITECTURE.md 5.1）：
 * - 「進行中セッションが期限を過ぎている」間だけ動く
 * - 次のタスクが始まる（進行中の id が変わる）か、延長で期限が未来になれば、自分で止まる
 * - 「中断して別のタスクへ」を押したか（acknowledged）だけはメモリ内で持つ。
 *   押したらバイブは止めるが、覆いは次のタスクが始まるまで続ける
 *
 * 覆いは自アプリの画面が前面のときは外し、他アプリやホームに行ったら被せる。
 */
class OverdueGuardService : Service() {

    private val container by lazy { (application as TaskTimerApp).container }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var overlay: OverlayView? = null
    private var watchJob: Job? = null
    private var acknowledged = false
    private var vibrating = false
    private var lastNotifiedAcknowledged: Boolean? = null
    private var broughtBackOnce = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        // 覆いの準備に失敗しても（機種差など）サービス自体は落とさない。バイブと通知だけで続行する。
        overlay = runCatching {
            OverlayView(
                context = this,
                onTap = ::bringBack,
                onEmergencyExit = ::emergencyStop,
            )
        }.onFailure { Log.w(TAG, "覆いを準備できませんでした", it) }.getOrNull()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // 起動から 5 秒以内に startForeground を呼ばないと ANR で殺される（docs/05 2.3）。何より先に呼ぶ。
        ServiceCompat.startForeground(
            this,
            OverdueNotifications.ID_GUARD,
            OverdueNotifications.guard(this, acknowledged),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE,
        )
        lastNotifiedAcknowledged = acknowledged

        when (intent?.action) {
            ACTION_OVERDUE, ACTION_RECHECK -> startWatching()
            ACTION_ACKNOWLEDGE -> {
                acknowledged = true
                startWatching()
            }
            ACTION_BRING_BACK -> {
                startWatching()
                bringBack()
            }
            ACTION_EMERGENCY_STOP -> emergencyStop()
            else -> startWatching()
        }
        return START_NOT_STICKY
    }

    private fun startWatching() {
        if (watchJob != null) return
        val settings = container.settingsRepository
        watchJob = scope.launch {
            combine(
                container.sessionRepository.observeRunningSession(),
                settings.observeBlockEnabled(),
                settings.observeDismissedOccurrence(),
                container.foregroundTracker.isForeground,
                ticker(),
            ) { session, blockEnabled, dismissed, foreground, now ->
                Snapshot(session, blockEnabled, dismissed, foreground, now)
            }.collect { apply(it) }
        }
    }

    private fun apply(s: Snapshot) {
        val decision = guardDecision(s.session, s.now, s.blockEnabled, s.dismissed)
        if (decision == GuardDecision.NONE) {
            // 次のタスクが始まった／延長した／非常口で閉じた。役目終了。
            stopAll()
            return
        }

        // バイブ：応答（中断して別のタスクへ／延長）まで鳴らし続ける。
        if (!acknowledged && !vibrating) {
            container.vibration.startRepeating()
            vibrating = true
        } else if (acknowledged && vibrating) {
            container.vibration.stop()
            vibrating = false
        }

        // 覆い：強制力 ON で、権限があり、自アプリが前面でないときだけ。
        val shouldCover = decision == GuardDecision.ALERT &&
            Settings.canDrawOverlays(this) &&
            !s.foreground
        val ov = overlay ?: return
        if (shouldCover && !ov.isShowing) {
            ov.show()
            // 最初に覆いを出したとき、超過画面を前に持ってくることを 1 回だけ試す。
            // 画面が点いていて他アプリを使っている最中は、全画面通知がポップアップ止まりになるため。
            if (!broughtBackOnce && !acknowledged) {
                broughtBackOnce = true
                bringBack()
            }
        } else if (!shouldCover && ov.isShowing) {
            ov.hide()
        }

        if (lastNotifiedAcknowledged != acknowledged) {
            lastNotifiedAcknowledged = acknowledged
            NotificationManagerCompat.from(this)
                .notify(OverdueNotifications.ID_GUARD, OverdueNotifications.guard(this, acknowledged))
        }
    }

    /** 覆いのタップ／通知のタップ：まだ答えていなければ超過画面、答えた後なら切り替えフローへ。 */
    private fun bringBack() {
        val intent = if (!acknowledged) {
            Intent(this, OverdueActivity::class.java)
        } else {
            Intent(this, MainActivity::class.java).putExtra(MainActivity.EXTRA_OPEN_SWITCH, true)
        }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            startActivity(intent)
        } catch (e: Exception) {
            Log.w(TAG, "画面を前に出せませんでした", e)
        }
    }

    /** 非常口。この超過の 1 回分を記憶して、次に自分で切り替えるまで何もしない。 */
    private fun emergencyStop() {
        scope.launch {
            runCatching {
                val session: Session? = container.sessionRepository.getRunningSession()
                if (session != null) {
                    container.settingsRepository.setDismissedOccurrence(
                        DismissedOccurrence(
                            sessionId = session.id,
                            deadlineMillis = deadlineMillis(session.startedAt, session.totalPlannedMinutes),
                        ),
                    )
                }
            }
            stopAll()
        }
    }

    private fun stopAll() {
        watchJob?.cancel()
        watchJob = null
        container.vibration.stop()
        vibrating = false
        overlay?.hide()
        OverdueNotifications.cancelFullScreen(this)
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        watchJob?.cancel()
        container.vibration.stop()
        overlay?.hide()
        scope.cancel()
        super.onDestroy()
    }

    private fun ticker() = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    private data class Snapshot(
        val session: Session?,
        val blockEnabled: Boolean,
        val dismissed: DismissedOccurrence?,
        val foreground: Boolean,
        val now: Long,
    )

    companion object {
        const val ACTION_OVERDUE = "com.aki.tasktimer.action.OVERDUE"
        const val ACTION_ACKNOWLEDGE = "com.aki.tasktimer.action.ACKNOWLEDGE"
        const val ACTION_RECHECK = "com.aki.tasktimer.action.RECHECK"
        const val ACTION_BRING_BACK = "com.aki.tasktimer.action.BRING_BACK"
        const val ACTION_EMERGENCY_STOP = "com.aki.tasktimer.action.EMERGENCY_STOP"
        private const val TAG = "OverdueGuardService"

        fun intent(context: Context, action: String): Intent =
            Intent(context, OverdueGuardService::class.java).setAction(action)
    }
}
