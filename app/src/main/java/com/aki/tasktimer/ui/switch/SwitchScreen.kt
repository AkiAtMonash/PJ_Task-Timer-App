package com.aki.tasktimer.ui.switch

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowOverflow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
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
import com.aki.tasktimer.ui.component.TagChip
import com.aki.tasktimer.ui.theme.InkVariant
import com.aki.tasktimer.ui.theme.OnInk
import com.aki.tasktimer.ui.theme.OnInkMuted
import com.aki.tasktimer.ui.theme.Warn
import com.aki.tasktimer.ui.theme.color
import com.aki.tasktimer.ui.util.formatElapsed

/**
 * 名前のプリセット 1 行ぶんの高さ（ピルの高さ ＋ 行間）。
 * 「余白が埋まるまで候補を出し、それ以上は使用頻度の低いものから消す」ための計算に使う。
 */
private val PRESET_ROW_HEIGHT = 44.dp

@Composable
fun SwitchScreen(
    onDone: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as TaskTimerApp
    val viewModel: SwitchViewModel = viewModel {
        SwitchViewModel(app.container.sessionRepository, app.container.presetRepository)
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
                SwitchStep.FORM -> TaskFormStep(uiState, viewModel)
            }
        }
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
        // 進行中が無いときは FORM から始まるのでここには来ないが、防御として空にしない。
        Text("評価するセッションがありません", color = OnInkMuted)
        return
    }
    val keyboard = LocalSoftwareKeyboardController.current
    // 評価はその瞬間の経過で十分。ティックは要らないので静的なスナップショットにする。
    val now = remember { System.currentTimeMillis() }
    val elapsed = elapsedMinutes(session.startedAt, now)
    val rate = overrunRate(elapsed, session.plannedMinutes)

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
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
            // 1 行固定。キーボードの右下ボタン（完了）で入力を終えてキーボードを閉じる。
            OutlinedTextField(
                value = state.ratingNote,
                onValueChange = viewModel::setRatingNote,
                label = { Text(if (state.rating == Rating.GOOD) "なぜ良かったか" else "なぜ悪かったか") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { keyboard?.hide() }),
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

/**
 * 次のタスクの入力を 1 画面にまとめたフォーム（2026-09-07、Aki の要望）。
 * 上から：名前のプリセット → 名前 → タグ（3×2）→ ゴール → 予定時間 → 開始。
 * キーボードの右下ボタンで 名前 → ゴール → 予定時間 と進み、最後は完了でキーボードが閉じる。
 * キーボードが出ている間だけスクロールできる（出ていなければ 1 画面に収まる）。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun TaskFormStep(state: SwitchUiState, viewModel: SwitchViewModel) {
    val focus = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current
    val density = LocalDensity.current
    val draft = state.draft

    // 候補（プリセット）に使える行数を、画面の余白から決める。
    // キーボードを出していない状態の画面の高さから、候補以外の部分（入力欄・タグ・ボタン等）の高さを引き、
    // 残りに何行入るかを数える。候補は使用頻度順なので、入りきらない分＝頻度の低いものが自動的に落ちる。
    var otherContentHeightPx by remember { mutableIntStateOf(0) }
    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val screenHeightPx = with(density) { maxHeight.toPx() }
        val rowHeightPx = with(density) { PRESET_ROW_HEIGHT.toPx() }
        val presetMaxLines = if (otherContentHeightPx == 0) {
            2
        } else {
            ((screenHeightPx - otherContentHeightPx) / rowHeightPx).toInt().coerceIn(1, 8)
        }

    Column(modifier = Modifier.fillMaxSize().imePadding()) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
        ) {
            Text(
                text = "次は何をする？",
                color = OnInk,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
            )
            Spacer(modifier = Modifier.height(10.dp))

            if (state.presets.isNotEmpty()) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    maxLines = presetMaxLines,
                    overflow = FlowRowOverflow.Clip,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    state.presets.forEach { preset ->
                        Pill(
                            text = preset.name,
                            selected = preset.name == draft.name,
                            onClick = {
                                viewModel.selectPreset(preset)
                                focus.clearFocus()
                                keyboard?.hide()
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
            }

            Column(
                modifier = Modifier.onSizeChanged { size ->
                    // 候補以外の高さ。見出し・候補との間隔・開始ボタンぶんも足しておく。
                    val extra = with(density) { (14.sp.toPx() + 10.dp.toPx() + 12.dp.toPx() + 48.dp.toPx() + 8.dp.toPx()).toInt() }
                    otherContentHeightPx = size.height + extra
                },
            ) {
            OutlinedTextField(
                value = draft.name,
                onValueChange = viewModel::setName,
                label = { Text("タスク名") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(14.dp))
            SectionLabel("タグ")
            TagGrid(selected = draft.tag, onSelect = viewModel::selectTag)

            Spacer(modifier = Modifier.height(14.dp))
            OutlinedTextField(
                value = draft.goal,
                onValueChange = viewModel::setGoal,
                label = { Text("ゴール（省略可）") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                keyboardActions = KeyboardActions(onNext = { focus.moveFocus(FocusDirection.Down) }),
                modifier = Modifier.fillMaxWidth(),
            )

            Spacer(modifier = Modifier.height(14.dp))
            SectionLabel("何分でやる？")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                SwitchViewModel.PLANNED_MINUTE_PRESETS.forEach { minutes ->
                    Pill(
                        text = "$minutes",
                        selected = draft.plannedMinutes == minutes && draft.plannedInput.isEmpty(),
                        onClick = {
                            viewModel.selectPlannedMinutes(minutes)
                            focus.clearFocus()
                            keyboard?.hide()
                        },
                    )
                }
                // 一番右：自由入力。ここが最後の項目なので、右下ボタンは完了。
                OutlinedTextField(
                    value = draft.plannedInput,
                    onValueChange = viewModel::setPlannedMinutesInput,
                    placeholder = { Text("自由", fontSize = 13.sp) },
                    suffix = { Text("分", fontSize = 13.sp, color = OnInkMuted) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focus.clearFocus(); keyboard?.hide() }),
                    textStyle = LocalTextStyle.current.copy(fontSize = 14.sp),
                    modifier = Modifier
                        .width(104.dp)
                        .height(52.dp),
                )
            }
            Spacer(modifier = Modifier.height(12.dp))
            }
        }

        PrimaryButton(
            text = "開始する",
            onClick = viewModel::start,
            enabled = state.canStart,
        )
    }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = OnInkMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold,
        modifier = Modifier.padding(bottom = 8.dp),
    )
}

/** タグを 3 列 × 2 行で並べる。ダイアログを挟まず、その場で選べる。 */
@Composable
private fun TagGrid(selected: Tag?, onSelect: (Tag) -> Unit) {
    val tags = Tag.entries
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        tags.chunked(3).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                row.forEach { tag ->
                    val isSelected = tag == selected
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(3.dp))
                            .background(if (isSelected) tag.color.copy(alpha = 0.18f) else InkVariant)
                            .clickable { onSelect(tag) }
                            .padding(horizontal = 10.dp, vertical = 10.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(tag.color),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = tag.label,
                            color = if (isSelected) tag.color else OnInk,
                            fontSize = 12.sp,
                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                            maxLines = 1,
                        )
                    }
                }
            }
        }
    }
}
