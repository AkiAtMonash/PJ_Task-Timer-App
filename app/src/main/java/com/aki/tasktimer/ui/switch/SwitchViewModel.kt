package com.aki.tasktimer.ui.switch

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.data.model.Session
import com.aki.tasktimer.data.model.Tag
import com.aki.tasktimer.data.model.TaskPreset
import com.aki.tasktimer.data.repository.PresetRepository
import com.aki.tasktimer.data.repository.SessionRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 切り替えフローの 4 ステップ（docs/01_SPEC.md 4.3）。 */
enum class SwitchStep { RATING, NAME, GOAL, PLANNED }

/** 次のセッションの下書き。Step 2〜4 で徐々に埋まる。 */
data class TaskDraft(
    val name: String = "",
    val tag: Tag? = null,
    val goal: String = "",
    val plannedMinutes: Int? = null,
)

data class SwitchUiState(
    val step: SwitchStep,
    val runningSession: Session?,
    val presets: List<TaskPreset>,
    val rating: Rating = Rating.NORMAL,
    val ratingNote: String = "",
    val draft: TaskDraft = TaskDraft(),
    val isNewTaskInput: Boolean = false,
    val newTaskName: String = "",
    val showTagPicker: Boolean = false,
    val isSaving: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
)

/**
 * 切り替えフロー。「前のタスクを評価して終える → 次のタスクを始める」を 1 本の流れで行う。
 *
 * 「評価だけして止める（中断）」という出口は用意しない。記録が止まっている瞬間を作らないのが
 * このアプリの前提なので、終えるときは必ず次を始める（docs/01_SPEC.md 1 章・4.1）。
 */
class SwitchViewModel(
    private val sessionRepository: SessionRepository,
    private val presetRepository: PresetRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        SwitchUiState(step = SwitchStep.RATING, runningSession = null, presets = emptyList()),
    )
    val uiState: StateFlow<SwitchUiState> = _uiState.asStateFlow()

    init {
        // 進行中セッションがあれば Step 1（評価）から、無ければ（初回起動）Step 2（名前）から。
        viewModelScope.launch {
            val running = sessionRepository.getRunningSession()
            val initialStep = if (running != null) SwitchStep.RATING else SwitchStep.NAME
            _uiState.update { it.copy(runningSession = running, step = initialStep) }
        }
        // プリセットは Step 2 の並び順を DAO のまま保つ。学習で増えるのに追従させる。
        viewModelScope.launch {
            presetRepository.observeTaskPresets().collect { presets ->
                _uiState.update { it.copy(presets = presets) }
            }
        }
    }

    // ---- Step 1（評価） ----

    fun selectRating(rating: Rating) = _uiState.update { it.copy(rating = rating) }

    fun setRatingNote(note: String) = _uiState.update { it.copy(ratingNote = note) }

    fun confirmRating() {
        val state = _uiState.value
        // ◯/✕ は理由が必須。空なら進ませない（UI 側でもボタンを無効化するが二重に守る）。
        if (state.rating != Rating.NORMAL && state.ratingNote.isBlank()) return
        _uiState.update { it.copy(step = SwitchStep.NAME) }
    }

    // ---- Step 2（次のタスク名） ----

    fun selectPreset(preset: TaskPreset) {
        _uiState.update {
            it.copy(
                draft = TaskDraft(
                    name = preset.name,
                    tag = preset.tag,
                    goal = preset.lastGoal ?: "",
                    plannedMinutes = preset.lastPlannedMinutes,
                ),
                step = SwitchStep.GOAL,
            )
        }
    }

    fun enterNewTask() = _uiState.update { it.copy(isNewTaskInput = true) }

    fun setNewTaskName(name: String) = _uiState.update { it.copy(newTaskName = name) }

    fun confirmNewTaskName() {
        val name = _uiState.value.newTaskName.trim()
        if (name.isBlank()) return
        // 新規タスクはタグを選んでいないので、タグ選択を挟む（docs/01_SPEC.md 4.3 Step 2）。
        _uiState.update {
            it.copy(
                draft = it.draft.copy(name = name),
                showTagPicker = true,
            )
        }
    }

    fun selectTag(tag: Tag) {
        _uiState.update {
            it.copy(
                draft = it.draft.copy(tag = tag),
                showTagPicker = false,
                step = SwitchStep.GOAL,
            )
        }
    }

    fun dismissTagPicker() = _uiState.update { it.copy(showTagPicker = false) }

    // ---- Step 3（ゴール） ----

    fun setGoal(goal: String) = _uiState.update { it.copy(draft = it.draft.copy(goal = goal)) }

    fun confirmGoal() {
        _uiState.update {
            it.copy(draft = it.draft.copy(goal = it.draft.goal.trim()), step = SwitchStep.PLANNED)
        }
    }

    fun skipGoal() {
        _uiState.update {
            it.copy(draft = it.draft.copy(goal = ""), step = SwitchStep.PLANNED)
        }
    }

    // ---- Step 4（予定時間） ----

    fun selectPlannedMinutes(minutes: Int) =
        _uiState.update { it.copy(draft = it.draft.copy(plannedMinutes = minutes)) }

    fun setPlannedMinutesInput(text: String) {
        val trimmed = text.trim()
        val minutes = if (trimmed.isEmpty()) null else trimmed.toIntOrNull()
        // 数値以外は受け付けない（無視）。空入力は「未選択」に戻す。
        if (trimmed.isNotEmpty() && minutes == null) return
        _uiState.update { it.copy(draft = it.draft.copy(plannedMinutes = minutes)) }
    }

    fun start() {
        val state = _uiState.value
        val draft = state.draft
        val tag = draft.tag ?: return
        val planned = draft.plannedMinutes ?: return
        val name = draft.name.trim()
        if (name.isBlank() || planned <= 0) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                val at = System.currentTimeMillis()
                // 進行中があれば終了＋開始、無ければ単に開始。DAO が rating=null を吸収する。
                sessionRepository.switchSession(
                    rating = state.rating,
                    ratingNote = resolvedNote(state),
                    name = name,
                    tag = tag,
                    goal = draft.goal.trim(),
                    plannedMinutes = planned,
                    at = at,
                )
                // プリセット自動学習（docs/01_SPEC.md 3.3）。開始に成功したときだけ増やす。
                presetRepository.recordTaskUse(
                    name = name,
                    tag = tag,
                    goal = draft.goal.trim().ifBlank { null },
                    plannedMinutes = planned,
                    usedAt = at,
                )
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, done = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "開始に失敗しました") }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }

    // ---- 戻る ----

    /**
     * 戻るを処理する。ステップを 1 つ戻せたら true、最初のステップで戻れなければ false
     * （false のときは呼び出し側が画面ごと閉じる）。
     */
    fun handleBack(): Boolean {
        val state = _uiState.value
        if (state.showTagPicker) {
            _uiState.update { it.copy(showTagPicker = false) }
            return true
        }
        if (state.isNewTaskInput) {
            _uiState.update { it.copy(isNewTaskInput = false, newTaskName = "") }
            return true
        }
        return when (state.step) {
            SwitchStep.RATING -> false
            SwitchStep.NAME -> {
                if (state.runningSession != null) {
                    _uiState.update { it.copy(step = SwitchStep.RATING) }
                    true
                } else {
                    false
                }
            }
            SwitchStep.GOAL -> {
                _uiState.update { it.copy(step = SwitchStep.NAME) }
                true
            }
            SwitchStep.PLANNED -> {
                _uiState.update { it.copy(step = SwitchStep.GOAL) }
                true
            }
        }
    }

    /** △（普通）は理由を自動で「特に無し」にする（docs/01_SPEC.md 4.3 Step 1）。 */
    private fun resolvedNote(state: SwitchUiState): String =
        if (state.rating == Rating.NORMAL) "特に無し" else state.ratingNote.trim()
}
