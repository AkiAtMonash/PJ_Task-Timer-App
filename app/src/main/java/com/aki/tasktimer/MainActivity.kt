package com.aki.tasktimer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.aki.tasktimer.ui.navigation.TaskTimerNavHost
import com.aki.tasktimer.ui.theme.TaskTimerTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 引数なしの enableEdgeToEdge() は端末のライト/ダーク設定に追従してしまう。
        // このアプリは常にダークなので、ステータスバーのアイコンも明示的に明色側へ固定する。
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )

        setContent {
            TaskTimerTheme {
                TaskTimerNavHost()
            }
        }
    }
}
