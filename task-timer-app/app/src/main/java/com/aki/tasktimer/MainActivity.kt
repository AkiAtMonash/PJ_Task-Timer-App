package com.aki.tasktimer

import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aki.tasktimer.ui.home.HomeScreen
import com.aki.tasktimer.ui.home.HomeViewModel
import com.aki.tasktimer.ui.switchflow.FlowMode
import com.aki.tasktimer.ui.switchflow.SwitchFlowActions
import com.aki.tasktimer.ui.switchflow.SwitchFlowScreen
import com.aki.tasktimer.ui.switchflow.SwitchFlowViewModel
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
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    TaskTimerRoot(modifier = Modifier.padding(innerPadding))
                }
            }
        }
    }
}

/**
 * ホームと切り替えフローの出し分け。
 *
 * Navigation Compose は入れていない。画面が一本道の 2 つしかないので、
 * 「いまフローに入っているか」を 1 つ見れば足りる（Phase 2 の決定事項 ⑧）。
 *
 * その 1 つを画面側の remember ではなく ViewModel に置いているのは、
 * 画面を回したときに下書きだけ残ってホームに戻ってしまうのを防ぐため。
 */
@Composable
private fun TaskTimerRoot(modifier: Modifier = Modifier) {
    val homeViewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory)
    val flowViewModel: SwitchFlowViewModel = viewModel(factory = SwitchFlowViewModel.Factory)

    val activeMode by flowViewModel.activeMode.collectAsStateWithLifecycle()

    if (activeMode == null) {
        val state by homeViewModel.uiState.collectAsStateWithLifecycle()

        HomeScreen(
            state = state,
            onSwitchTask = { flowViewModel.begin(FlowMode.SWITCH) },
            onStartTask = { flowViewModel.begin(FlowMode.START) },
            onStopTask = { flowViewModel.begin(FlowMode.STOP) },
            modifier = modifier,
        )
    } else {
        val state by flowViewModel.uiState.collectAsStateWithLifecycle()

        // 端末の戻るは 1 つ前の Step へ。最初の Step で戻ると下書きごと捨ててホームに戻る
        BackHandler { flowViewModel.back() }

        SwitchFlowScreen(
            state = state,
            actions = SwitchFlowActions(
                onRatingSelected = flowViewModel::selectRating,
                onRatingNoteChanged = flowViewModel::changeRatingNote,
                onRatingNoteConfirmed = flowViewModel::confirmRatingNote,
                onPresetSelected = flowViewModel::selectPreset,
                onNewTaskRequested = flowViewModel::requestNewTask,
                onNameInputChanged = flowViewModel::changeNameInput,
                onNameConfirmed = flowViewModel::confirmNewTaskName,
                onTagSelected = flowViewModel::selectTag,
                onGoalChanged = flowViewModel::changeGoal,
                onPlannedSelected = flowViewModel::selectPlannedMinutes,
                onFreeMinutesChanged = flowViewModel::changeFreeMinutes,
                onStart = flowViewModel::start,
            ),
            modifier = modifier,
        )
    }
}
