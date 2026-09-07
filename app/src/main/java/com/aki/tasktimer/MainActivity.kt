package com.aki.tasktimer

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import com.aki.tasktimer.ui.navigation.TaskTimerNavHost
import com.aki.tasktimer.ui.theme.TaskTimerTheme

class MainActivity : ComponentActivity() {

    /**
     * 「切り替えフローを開いて」という依頼の回数。超過画面の「中断して別のタスクへ」や
     * 覆いのタップから届く。値が増えるたびに NavHost が切り替え画面へ進む。
     */
    private var openSwitchRequest by mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 引数なしの enableEdgeToEdge() は端末のライト/ダーク設定に追従してしまう。
        // このアプリは常にダークなので、ステータスバーのアイコンも明示的に明色側へ固定する。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        handleIntent(intent)

        setContent {
            TaskTimerTheme {
                TaskTimerNavHost(
                    openSwitchRequest = openSwitchRequest,
                    onSwitchFlowFinished = {
                        // 次のタスクを開始したら、ロック画面の上に出る権利を返す。
                        // ロック中だった場合はここでロック画面に戻る。
                        setShowWhenLocked(false)
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent)
    }

    private fun handleIntent(intent: Intent?) {
        if (intent?.getBooleanExtra(EXTRA_OPEN_SWITCH, false) == true) {
            // 同じ Intent が再配達されても二重に反応しないよう、読んだら消す。
            intent.removeExtra(EXTRA_OPEN_SWITCH)
            openSwitchRequest++
            // 超過画面はロック画面の上に出る。そこから「中断して別のタスクへ」で来たときは、
            // 評価〜開始までもロックの上で続けられるようにする（Aki の要望：途中でロック画面に戻らない）。
            setShowWhenLocked(true)
            setTurnScreenOn(true)
            // ロック画面の上では画面がすぐ消える設定になっていることが多い。入力の途中で消えないようにする。
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    companion object {
        const val EXTRA_OPEN_SWITCH = "open_switch"
    }
}
