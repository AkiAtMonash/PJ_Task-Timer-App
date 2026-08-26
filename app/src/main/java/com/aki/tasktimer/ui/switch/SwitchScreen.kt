package com.aki.tasktimer.ui.switch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aki.tasktimer.TaskTimerApp
import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.data.model.Tag
import com.aki.tasktimer.domain.elapsedMinutes
import com.aki.tasktimer.domain.overrunRate
import com.aki.tasktimer.ui.component.Pill
import com.aki.tasktimer.ui.component.PrimaryButton
import com.aki.tasktimer.ui.component.RatingSelector
import com.aki.tasktimer.ui.component.SecondaryButton
import com.aki.tasktimer.ui.component.TagChip
import com.aki.tasktimer.ui.theme.InkVariant
import com.aki.tasktimer.ui.theme.OnInk
import com.aki.tasktimer.ui.theme.OnInkMuted
import com.aki.tasktimer.ui.theme.Warn
import com.aki.tasktimer.ui.theme.color
import com.aki.tasktimer.ui.util.formatElapsed

// 予定時間プリセット。仕様 3 章に頻度を記録するテーブルが無いため固定値
// （docs/01_SPEC.md 4.3 Step 4 とワイヤーフレームの表示に合わせる）。
private val PLANNED_MINUTE_PRESETS = listOf(15, 25, 30, 45, 60, 90, 120)

@Composable
fun SwitchScreen(
    mode: SwitchMode,
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as TaskTimerApp
    val viewModel: SwitchViewModel = viewModel {
        SwitchViewModel(app.container.sessionRepository, app.container.presetRepository, mode)
    }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(uiState.done) {
        if (uiState.done) onDone()
    }

    BackHandler(enabled = !uiState.isSaving) {
        if (!viewModel.handleBack()) onDone()
    }

    Scaffold(modifier = modifier.fillMaxSize()) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 24.dp),
        ) {
            when (uiState.step) {
                SwitchStep.RATING -> RatingStep(uiState, viewModel)
                SwitchStep.NAME -> TaskNameStep(uiState, viewModel)
                SwitchStep.GOAL -> GoalStep(uiState, viewModel)
                SwitchStep.PLANNED -> PlannedTimeStep(uiState, viewModel)
            }
        }
    }

    if (uiState.showTagPicker) {
        TagPickerDialog(
            onSelect = viewModel::selectTag,
            onDismiss = viewModel::dismissTagPicker,
        )
    }

    uiState.error?.let { error ->
        AlertDialog(
            onDismissRequest = viewModel::dismissError,
            title = { Text("エラー") },
            text = { Text(error) },
            confirmButton = { TextButton(onClick = viewModel::dismissError) { Text("閉じる") } },
        )
    }
}

@Composable
private fun RatingStep(state: SwitchUiState, viewModel: SwitchViewModel) {
    val session = state.runningSession
    if (session == null) {
        // STOP 以外でここに来ることは無いが、防御として空にしない。
        Text("評価するセッションがありません", color = OnInkMuted)
        return
    }
    // 評価はその瞬間の経過で十分。ティックは要らないので静的なスナップショットにする。
    val now = remember { System.currentTimeMillis() }
    val elapsed = elapsedMinutes(session.startedAt, now)
    val rate = overrunRate(elapsed, session.plannedMinutes)

    Column(modifier = Modifier.fillMaxSize()) {
        TagChip(tag = session.tag)
        Text(
            text = session.name,
            color = OnInk,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )

        Spacer(modifier = Modifier.height(24.dp))

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text(
                text = "経過時間",
                color = OnInkMuted,
                fontSize = 10.sp,
                letterSpacing = 2.sp,
                fontFamily = FontFamily.Monospace,
            )
            Text(
                text = formatElapsed(now - session.startedAt),
                color = OnInk,
                fontSize = 32.sp,
                fontWeight = FontWeight.SemiBold,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp),
            )
            Text(
                text = "予定 ${session.plannedMinutes}分 / ${rate}%",
                color = if (rate >= 150) Warn else OnInkMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 2.dp),
            )
        }

        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "この時間の使い方は？",
            color = OnInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        RatingSelector(
            selected = state.rating,
            onSelect = viewModel::selectRating,
        )

        if (state.rating != Rating.NORMAL) {
            Spacer(modifier = Modifier.height(16.dp))
            OutlinedTextField(
                value = state.ratingNote,
                onValueChange = viewModel::setRatingNote,
                label = { Text(if (state.rating == Rating.GOOD) "なぜ良かったか" else "なぜ悪かったか") },
                singleLine = false,
                minLines = 2,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.weight(1f))

        val canProceed = state.rating == Rating.NORMAL || state.ratingNote.isNotBlank()
        PrimaryButton(
            text = "次へ",
            onClick = viewModel::confirmRating,
            enabled = canProceed && !state.isSaving,
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskNameStep(state: SwitchUiState, viewModel: SwitchViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        Text(
            text = "次は何をする？",
            color = OnInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (state.isNewTaskInput) {
            OutlinedTextField(
                value = state.newTaskName,
                onValueChange = viewModel::setNewTaskName,
                label = { Text("新しいタスク名") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
            Spacer(modifier = Modifier.height(12.dp))
            PrimaryButton(
                text = "次へ",
                onClick = viewModel::confirmNewTaskName,
                enabled = state.newTaskName.isNotBlank(),
            )
        } else {
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                state.presets.forEach { preset ->
                    Pill(
                        text = preset.name,
                        onClick = { viewModel.selectPreset(preset) },
                    )
                }
            }
            Spacer(modifier = Modifier.height(16.dp))
            Pill(
                text = "＋ 新しいタスク…",
                onClick = viewModel::enterNewTask,
                modifier = Modifier.fillMaxWidth(),
            )
        }

        Spacer(modifier = Modifier.weight(1f))
    }
}

@Composable
private fun GoalStep(state: SwitchUiState, viewModel: SwitchViewModel) {
    Column(modifier = Modifier.fillMaxSize()) {
        state.draft.tag?.let { TagChip(tag = it) }
        Text(
            text = state.draft.name,
            color = OnInk,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "ゴールは？",
            color = OnInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))
        OutlinedTextField(
            value = state.draft.goal,
            onValueChange = viewModel::setGoal,
            placeholder = { Text("（省略可）") },
            singleLine = false,
            minLines = 2,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.weight(1f))

        Row {
            SecondaryButton(
                text = "スキップ",
                onClick = viewModel::skipGoal,
                modifier = Modifier.weight(1f),
            )
            Spacer(modifier = Modifier.width(8.dp))
            PrimaryButton(
                text = "次へ",
                onClick = viewModel::confirmGoal,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlannedTimeStep(state: SwitchUiState, viewModel: SwitchViewModel) {
    val canStart = (state.draft.plannedMinutes ?: 0) > 0 && !state.isSaving

    Column(modifier = Modifier.fillMaxSize()) {
        state.draft.tag?.let { TagChip(tag = it) }
        Text(
            text = state.draft.name,
            color = OnInk,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )
        Spacer(modifier = Modifier.height(24.dp))

        Text(
            text = "何分でやる？",
            color = OnInk,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
        Spacer(modifier = Modifier.height(12.dp))

        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            PLANNED_MINUTE_PRESETS.forEach { minutes ->
                Pill(
                    text = "$minutes",
                    selected = state.draft.plannedMinutes == minutes,
                    onClick = { viewModel.selectPlannedMinutes(minutes) },
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = state.draft.plannedMinutes?.toString() ?: "",
            onValueChange = viewModel::setPlannedMinutesInput,
            label = { Text("自由入力（分）") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.weight(1f))

        PrimaryButton(
            text = "開始する",
            onClick = viewModel::start,
            enabled = canStart,
        )
    }
}

@Composable
private fun TagPickerDialog(onSelect: (Tag) -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("タグを選ぶ") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Tag.entries.forEach { tag ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(3.dp))
                            .background(InkVariant)
                            .clickable { onSelect(tag) }
                            .padding(horizontal = 12.dp, vertical = 10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(tag.color),
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = tag.label,
                            color = tag.color,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        },
        confirmButton = {},
    )
}
