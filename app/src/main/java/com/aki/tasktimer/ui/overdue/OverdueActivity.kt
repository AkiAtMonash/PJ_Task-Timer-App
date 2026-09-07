package com.aki.tasktimer.ui.overdue

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.addCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aki.tasktimer.MainActivity
import com.aki.tasktimer.TaskTimerApp
import com.aki.tasktimer.block.OverdueGuardService
import com.aki.tasktimer.timer.OverdueNotifications
import com.aki.tasktimer.ui.theme.TaskTimerTheme

/**
 * 超過画面専用の Activity（docs/02_ARCHITECTURE.md 4 章）。
 * 全画面通知（full-screen intent）から起動され、ロック画面の上にも出る。
 *
 * - 戻るボタンは握りつぶす（docs/01_SPEC.md 4.4-7）
 * - 起動時にバイブ・覆いのサービスも起動する（前面からの起動は常に許可されるので、
 *   アラーム受信側で起動に失敗していてもここで復帰できる）
 */
class OverdueActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        setShowWhenLocked(true)
        setTurnScreenOn(true)

        // 逃げ道の封鎖。予測型バックでも効くよう Dispatcher に登録する。
        onBackPressedDispatcher.addCallback(this) { /* 何もしない */ }

        startGuardService(OverdueGuardService.ACTION_OVERDUE)

        setContent {
            TaskTimerTheme {
                OverdueRoute(
                    onExtended = { finishQuietly() },
                    onSwitchTask = { goToSwitchFlow() },
                    onEmergencyExit = {
                        startGuardService(OverdueGuardService.ACTION_EMERGENCY_STOP)
                        finishQuietly()
                    },
                )
            }
        }
    }

    /** 「中断して別のタスクへ」：バイブを止め、切り替えフローへ。覆いは次のタスクが始まるまで続く。 */
    private fun goToSwitchFlow() {
        startGuardService(OverdueGuardService.ACTION_ACKNOWLEDGE)
        OverdueNotifications.cancelFullScreen(this)
        startActivity(
            Intent(this, MainActivity::class.java)
                .putExtra(MainActivity.EXTRA_OPEN_SWITCH, true)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
        )
        finish()
    }

    private fun finishQuietly() {
        OverdueNotifications.cancelFullScreen(this)
        // 延長直後は DB が更新済みなので、サービスに「確かめて止まれ」と伝える。
        startGuardService(OverdueGuardService.ACTION_RECHECK)
        finish()
    }

    private fun startGuardService(action: String) {
        try {
            ContextCompat.startForegroundService(this, OverdueGuardService.intent(this, action))
        } catch (e: Exception) {
            Log.w("OverdueActivity", "サービスを起動できませんでした: $action", e)
        }
    }
}

@androidx.compose.runtime.Composable
private fun OverdueRoute(
    onExtended: () -> Unit,
    onSwitchTask: () -> Unit,
    onEmergencyExit: () -> Unit,
) {
    val app = LocalContext.current.applicationContext as TaskTimerApp
    val viewModel: OverdueViewModel = viewModel {
        OverdueViewModel(app.container.sessionRepository, app.container.presetRepository)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.done) {
        if (uiState.done) onExtended()
    }

    OverdueScreen(
        state = uiState,
        onSelect = viewModel::select,
        onExtend = viewModel::extend,
        onSwitchTask = onSwitchTask,
        onEmergencyExit = onEmergencyExit,
        onDismissError = viewModel::dismissError,
    )
}
