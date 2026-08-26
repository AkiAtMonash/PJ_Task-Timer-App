package com.aki.tasktimer.ui.switchflow

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.aki.tasktimer.R
import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.data.model.Tag
import com.aki.tasktimer.data.model.TaskPreset
import com.aki.tasktimer.ui.component.ElapsedTime
import com.aki.tasktimer.ui.component.HairRule
import com.aki.tasktimer.ui.component.InputField
import com.aki.tasktimer.ui.component.Pill
import com.aki.tasktimer.ui.component.PillGrid
import com.aki.tasktimer.ui.component.PrimaryButton
import com.aki.tasktimer.ui.component.QuestionHeading
import com.aki.tasktimer.ui.component.TagChip
import com.aki.tasktimer.ui.component.TagOption
import com.aki.tasktimer.ui.component.choiceMark
import com.aki.tasktimer.ui.theme.Alert
import com.aki.tasktimer.ui.theme.Ink
import com.aki.tasktimer.ui.theme.Muted
import com.aki.tasktimer.ui.theme.Paper3
import com.aki.tasktimer.ui.theme.Radius
import com.aki.tasktimer.ui.theme.Rule
import com.aki.tasktimer.ui.theme.RuleHair
import com.aki.tasktimer.ui.theme.Space

/** 画面から呼び返す操作をひとまとめに。引数が 12 個並ぶのを避けるためだけの入れ物。 */
internal data class SwitchFlowActions(
    val onRatingSelected: (Rating) -> Unit,
    val onRatingNoteChanged: (String) -> Unit,
    val onRatingNoteConfirmed: () -> Unit,
    val onPresetSelected: (TaskPreset) -> Unit,
    val onNewTaskRequested: () -> Unit,
    val onNameInputChanged: (String) -> Unit,
    val onNameConfirmed: () -> Unit,
    val onTagSelected: (Tag) -> Unit,
    val onGoalChanged: (String) -> Unit,
    val onPlannedSelected: (Int) -> Unit,
    val onFreeMinutesChanged: (String) -> Unit,
    val onStart: () -> Unit,
)

/**
 * タスク切り替えフロー（docs/01_SPEC.md 4.3）。
 *
 * 3 画面ぶんを 1 つの Composable で出し分ける。**最短 4 タップ**が要件なので、
 * 画面を増やしたくなったらまずタップ数を数えること。
 */
@Composable
internal fun SwitchFlowScreen(
    state: SwitchFlowUiState,
    actions: SwitchFlowActions,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = Space.lg, vertical = Space.lg),
    ) {
        // uiState は購読が始まってから作られるので、フローに入った最初の 1 フレームだけ
        // まだ下書きが反映されていない。ここで描いてしまうと、開始フローでも
        // 一瞬だけ評価画面（Step 1）が見えてしまう
        if (state.mode == null) return@Column

        when (state.step) {
            FlowStep.RATING -> RatingStep(state, actions)
            FlowStep.RATING_NOTE -> RatingNoteStep(state, actions)
            FlowStep.TASK_NAME -> TaskNameStep(state, actions)
            FlowStep.NEW_TASK_NAME -> NewTaskNameStep(state, actions)
            FlowStep.TAG_PICK -> TagPickStep(actions)
            FlowStep.GOAL_AND_TIME -> GoalAndTimeStep(state, actions)
        }
    }
}

// ---------- Step 1：評価 ----------

@Composable
private fun ColumnScope.RatingStep(
    state: SwitchFlowUiState,
    actions: SwitchFlowActions,
) {
    val previous = state.previous

    if (previous != null) {
        TagChip(previous.tag)
        Text(
            text = previous.name,
            style = MaterialTheme.typography.titleLarge,
            color = Ink,
            modifier = Modifier.padding(top = Space.sm),
        )
        HairRule(Modifier.padding(vertical = Space.md))

        // 必ず経過時間を見せてから評価させる（docs/01_SPEC.md 4.3 Step 1）。
        // 「何分使ったか」を見ないまま良し悪しを付けても、記録として意味がない
        Text(
            text = stringResource(R.string.switch_elapsed_label),
            style = MaterialTheme.typography.labelSmall,
            textAlign = TextAlign.Center,
            color = Muted,
            modifier = Modifier.fillMaxWidth(),
        )
        ElapsedTime(
            text = previous.elapsedText,
            isOverrun = previous.isOverrun,
            modifier = Modifier.padding(top = Space.xxs),
        )
        Text(
            text = stringResource(
                R.string.switch_planned_and_rate,
                previous.plannedMinutes,
                previous.overrunRate,
            ),
            style = MaterialTheme.typography.labelMedium,
            textAlign = TextAlign.Center,
            color = if (previous.isOverrun) Alert else Muted,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = Space.xxs),
        )
    }

    Spacer(Modifier.weight(1f))

    QuestionHeading(
        text = stringResource(R.string.switch_rating_question),
        modifier = Modifier.padding(bottom = Space.sm),
    )

    RatingButton(
        rating = Rating.GOOD,
        label = stringResource(R.string.switch_rating_good),
        hint = stringResource(R.string.switch_rating_needs_note),
        onClick = { actions.onRatingSelected(Rating.GOOD) },
    )
    RatingButton(
        rating = Rating.NORMAL,
        label = stringResource(R.string.switch_rating_normal),
        hint = stringResource(R.string.switch_rating_is_default),
        onClick = { actions.onRatingSelected(Rating.NORMAL) },
        modifier = Modifier.padding(top = Space.xs),
    )
    RatingButton(
        rating = Rating.BAD,
        label = stringResource(R.string.switch_rating_bad),
        hint = stringResource(R.string.switch_rating_needs_note),
        onClick = { actions.onRatingSelected(Rating.BAD) },
        modifier = Modifier.padding(top = Space.xs),
    )
}

@Composable
private fun RatingButton(
    rating: Rating,
    label: String,
    hint: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.input)
            .background(Paper3)
            .border(RuleHair, Rule, Radius.input)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.sm),
    ) {
        Text(text = rating.choiceMark, style = MaterialTheme.typography.bodyLarge)
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = Ink,
        )
        Spacer(Modifier.weight(1f))
        Text(
            text = hint,
            style = MaterialTheme.typography.labelSmall,
            color = Muted,
        )
    }
}

// ---------- Step 1 の続き：理由の入力 ----------

@Composable
private fun ColumnScope.RatingNoteStep(
    state: SwitchFlowUiState,
    actions: SwitchFlowActions,
) {
    QuestionHeading(
        text = if (state.rating == Rating.GOOD) {
            stringResource(R.string.switch_note_title_good)
        } else {
            stringResource(R.string.switch_note_title_bad)
        },
        modifier = Modifier.padding(bottom = Space.sm),
    )

    InputField(
        value = state.ratingNote,
        onValueChange = actions.onRatingNoteChanged,
        placeholder = stringResource(R.string.switch_note_placeholder),
        singleLine = false,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.weight(1f))

    PrimaryButton(
        text = if (state.mode == FlowMode.STOP) {
            stringResource(R.string.switch_stop_confirm)
        } else {
            stringResource(R.string.switch_note_next)
        },
        onClick = actions.onRatingNoteConfirmed,
        // 理由は必須。空のまま進めると ✅ / ❌ を付けた意味がなくなる
        enabled = state.ratingNote.isNotBlank(),
    )
}

// ---------- Step 2：次のタスク ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.TaskNameStep(
    state: SwitchFlowUiState,
    actions: SwitchFlowActions,
) {
    QuestionHeading(
        text = stringResource(R.string.switch_task_question),
        modifier = Modifier.padding(bottom = Space.sm),
    )

    PillGrid {
        // スクロールなしで収める上限。並び順は SQL 側で確定済みなので触らない
        state.presets.take(MAX_VISIBLE_PRESETS).forEach { preset ->
            Pill(
                text = preset.name,
                onClick = { actions.onPresetSelected(preset) },
                modifier = Modifier.weight(1f),
            )
        }
    }

    HairRule(Modifier.padding(vertical = Space.md))

    Pill(
        text = stringResource(R.string.switch_task_new),
        onClick = actions.onNewTaskRequested,
        muted = true,
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.weight(1f))
}

@Composable
private fun ColumnScope.NewTaskNameStep(
    state: SwitchFlowUiState,
    actions: SwitchFlowActions,
) {
    QuestionHeading(
        text = stringResource(R.string.switch_task_question),
        modifier = Modifier.padding(bottom = Space.sm),
    )

    InputField(
        value = state.nameInput,
        onValueChange = actions.onNameInputChanged,
        placeholder = stringResource(R.string.switch_task_name_placeholder),
        modifier = Modifier.fillMaxWidth(),
    )

    Spacer(Modifier.weight(1f))

    PrimaryButton(
        text = stringResource(R.string.switch_task_name_next),
        onClick = actions.onNameConfirmed,
        enabled = state.nameInput.isNotBlank(),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.TagPickStep(actions: SwitchFlowActions) {
    QuestionHeading(
        text = stringResource(R.string.switch_tag_question),
        modifier = Modifier.padding(bottom = Space.sm),
    )

    PillGrid {
        // v1 ではタグは固定 6 種。増減しないので enum をそのまま並べる
        Tag.entries.forEach { tag ->
            TagOption(tag = tag, onClick = { actions.onTagSelected(tag) })
        }
    }

    Spacer(Modifier.weight(1f))
}

// ---------- Step 3：ゴールと予定時間 ----------

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun ColumnScope.GoalAndTimeStep(
    state: SwitchFlowUiState,
    actions: SwitchFlowActions,
) {
    state.tag?.let { TagChip(it) }

    Text(
        text = state.name,
        style = MaterialTheme.typography.titleLarge,
        color = Ink,
        modifier = Modifier.padding(top = Space.sm),
    )

    HairRule(Modifier.padding(vertical = Space.md))

    QuestionHeading(
        text = stringResource(R.string.switch_goal_question),
        modifier = Modifier.padding(bottom = Space.sm),
    )
    InputField(
        value = state.goal,
        onValueChange = actions.onGoalChanged,
        // 前回のゴールを薄字で出して入力を促す。空のままでも進める
        placeholder = state.goalPlaceholder.ifEmpty {
            stringResource(R.string.switch_goal_placeholder)
        },
        modifier = Modifier.fillMaxWidth(),
    )

    HairRule(Modifier.padding(vertical = Space.md))

    QuestionHeading(
        text = stringResource(R.string.switch_time_question),
        modifier = Modifier.padding(bottom = Space.sm),
    )
    PillGrid {
        SwitchFlowViewModel.PLANNED_PRESETS.forEach { minutes ->
            Pill(
                text = minutes.toString(),
                onClick = { actions.onPlannedSelected(minutes) },
                selected = state.plannedMinutes == minutes,
                mono = true,
                modifier = Modifier.weight(1f),
            )
        }
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = Space.xs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Text(
            text = stringResource(R.string.switch_time_free),
            style = MaterialTheme.typography.labelMedium,
            color = Muted,
        )
        InputField(
            value = state.freeMinutes,
            onValueChange = actions.onFreeMinutesChanged,
            numeric = true,
            modifier = Modifier.width(96.dp),
        )
        Text(
            text = stringResource(R.string.switch_time_unit),
            style = MaterialTheme.typography.labelMedium,
            color = Muted,
        )
    }

    Spacer(Modifier.weight(1f))

    PrimaryButton(
        text = stringResource(R.string.switch_start),
        onClick = actions.onStart,
        // 予定時間が決まっていないと期限が計算できない（Phase 4 のアラームがここに依存する）
        enabled = state.plannedMinutes != null && state.name.isNotBlank(),
    )
}

/** スクロールなしで一目に入る数（docs/01_SPEC.md 4.3 Step 2：6〜8 個）。 */
private const val MAX_VISIBLE_PRESETS = 8
