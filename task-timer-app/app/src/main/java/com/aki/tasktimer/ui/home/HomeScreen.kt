package com.aki.tasktimer.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import com.aki.tasktimer.R
import com.aki.tasktimer.ui.component.ElapsedTime
import com.aki.tasktimer.ui.component.PrimaryButton
import com.aki.tasktimer.ui.component.ProgressBar
import com.aki.tasktimer.ui.component.SecondaryButton
import com.aki.tasktimer.ui.component.TagChip
import com.aki.tasktimer.ui.component.listMark
import com.aki.tasktimer.ui.theme.Alert
import com.aki.tasktimer.ui.theme.Ink
import com.aki.tasktimer.ui.theme.Muted
import com.aki.tasktimer.ui.theme.Neutral
import com.aki.tasktimer.ui.theme.Rule
import com.aki.tasktimer.ui.theme.RuleHair
import com.aki.tasktimer.ui.theme.Space
import kotlin.math.absoluteValue

/**
 * ホーム画面（docs/01_SPEC.md 4.1 / 4.2）。
 *
 * 状態を持たない。ViewModel から受け取って描くだけなので、
 * プレビューでも実機でも同じものが出る。
 */
@Composable
internal fun HomeScreen(
    state: HomeUiState,
    onSwitchTask: () -> Unit,
    onStartTask: () -> Unit,
    onStopTask: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Space.lg, vertical = Space.lg),
    ) {
        when (state) {
            // 読み込み中は何も描かない。一瞬「計測していません」が出るほうが害が大きい
            HomeUiState.Loading -> Unit

            is HomeUiState.Idle -> IdleContent(state = state, onStartTask = onStartTask)

            is HomeUiState.Running -> RunningContent(
                state = state,
                onSwitchTask = onSwitchTask,
                onStopTask = onStopTask,
            )
        }
    }
}

@Composable
private fun ColumnScope.RunningContent(
    state: HomeUiState.Running,
    onSwitchTask: () -> Unit,
    onStopTask: () -> Unit,
) {
    TagChip(state.tag)

    Text(
        text = state.name,
        style = MaterialTheme.typography.titleLarge,
        color = Ink,
        modifier = Modifier.padding(top = Space.sm),
    )

    if (state.goal.isNotBlank()) {
        Row(
            modifier = Modifier.padding(top = Space.sm),
            horizontalArrangement = Arrangement.spacedBy(Space.xs),
        ) {
            // モックアップは手描きの SVG マーク。絵文字（🎯）を置くとここだけフルカラーになり、
            // 主役であるはずの経過時間より目立ってしまう。単色の記号なら他の文字と同じ強さに収まる
            Text(text = "◎", style = MaterialTheme.typography.bodyMedium, color = Muted)
            Text(
                text = state.goal,
                style = MaterialTheme.typography.bodyMedium,
                color = Neutral,
            )
        }
    }

    ElapsedTime(
        text = state.elapsedText,
        isOverrun = state.isOverrun,
        modifier = Modifier.padding(top = Space.lg, bottom = Space.xxs),
    )

    // stringResource は Composable なので buildString のような普通のラムダの中では呼べない。
    // 先に文字列を作ってから結合する
    val plannedText = stringResource(R.string.home_planned, state.plannedMinutes)
    val statusText = if (state.isOverrun) {
        stringResource(R.string.home_overrun, state.remainingMinutes.absoluteValue)
    } else {
        stringResource(R.string.home_remaining, state.remainingMinutes)
    }

    Text(
        text = "$plannedText / $statusText",
        style = MaterialTheme.typography.labelMedium,
        textAlign = TextAlign.Center,
        color = if (state.isOverrun) Alert else Muted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Space.md),
    )

    ProgressBar(state.progress)

    // ボタンは常に画面下。経過時間の桁が増えても位置が動かないようにする
    Spacer(Modifier.weight(1f))

    PrimaryButton(text = stringResource(R.string.home_switch), onClick = onSwitchTask)
    SecondaryButton(
        text = stringResource(R.string.home_stop),
        onClick = onStopTask,
        modifier = Modifier.padding(top = Space.xs),
    )
}

@Composable
private fun ColumnScope.IdleContent(
    state: HomeUiState.Idle,
    onStartTask: () -> Unit,
) {
    Spacer(Modifier.weight(1f))

    Text(
        text = stringResource(R.string.home_idle),
        style = MaterialTheme.typography.titleLarge,
        textAlign = TextAlign.Center,
        color = Muted,
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = Space.lg),
    )

    PrimaryButton(text = stringResource(R.string.home_start), onClick = onStartTask)

    Spacer(Modifier.weight(1f))

    state.lastRecord?.let { record ->
        Spacer(
            Modifier
                .fillMaxWidth()
                .height(RuleHair)
                .background(Rule),
        )
        Text(
            text = stringResource(R.string.home_last_record),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = Muted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.md, bottom = Space.xxs),
        )
        Text(
            text = listOfNotNull(record.name, record.durationText, record.rating?.listMark)
                .joinToString(" / "),
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center,
            color = Neutral,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}
