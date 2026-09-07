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

/**
 * 切り替えフローの 2 ステップ。
 * 元は「評価 → 名前 → ゴール → 予定時間」の 4 画面だったが、次へを押し続けるのが面倒という
 * Aki の要望で、名前・タグ・ゴール・予定時間を 1 画面（FORM）にまとめた（2026-09-07）。
 */
enum class SwitchStep { RATING, FORM }

/** 次のセッションの下書き。FORM で埋まる。 */
data class TaskDraft(
    val name: String = "",
    val tag: Tag? = null,
    val goal: String = "",
    val plannedMinutes: Int? = null,
    /** 予定時間の自由入力欄の文字列 */
    val plannedInput: String = "",
)

data class SwitchUiState(
    val step: SwitchStep,
    val runningSession: Session?,
    val presets: List<TaskPreset>,
    val rating: Rating = Rating.NORMAL,
    val ratingNote: String = "",
    val draft: TaskDraft = TaskDraft(),
    val isSaving: Boolean = false,
    val done: Boolean = false,
    val error: String? = null,
) {
    val canStart: Boolean
        get() = draft.name.isNotBlank() && draft.tag != null && (draft.plannedMinutes ?: 0) > 0 && !isSaving
}

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
        // 進行中セッションがあれば評価から、無ければ（初回起動）入力フォームから。
        viewModelScope.launch {
            val running = sessionRepository.getRunningSession()
            val initialStep = if (running != null) SwitchStep.RATING else SwitchStep.FORM
            _uiState.update { it.copy(runningSession = running, step = initialStep) }
        }
        // プリセットは並び順を DAO のまま保つ。学習で増えるのに追従させる。
        viewModelScope.launch {
            presetRepository.observeTaskPresets().collect { presets ->
                _uiState.update { it.copy(presets = presets) }
            }
        }
    }

    // ---- Step 1（評価） ----

    fun selectRating(rating: Rating) = _uiState.update { it.copy(rating = rating) }

    fun setRatingNote(note: String) = _uiState.update { it.copy(ratingNote = note.replace("\n", " ")) }

    fun confirmRating() {
        val state = _uiState.value
        // ◯/✕ は理由が必須。空なら進ませない（UI 側でもボタンを無効化するが二重に守る）。
        if (state.rating != Rating.NORMAL && state.ratingNote.isBlank()) return
        _uiState.update { it.copy(step = SwitchStep.FORM) }
    }

    // ---- Step 2（次のタスク：名前・タグ・ゴール・予定時間を 1 画面で） ----

    /** プリセットをタップすると前回のタグ・ゴール・予定時間が全部埋まる（docs/01_SPEC.md 3.3）。 */
    fun selectPreset(preset: TaskPreset) {
        _uiState.update {
            it.copy(
                draft = TaskDraft(
                    name = preset.name,
                    tag = preset.tag,
                    goal = preset.lastGoal ?: "",
                    plannedMinutes = preset.lastPlannedMinutes,
                    plannedInput = if (preset.lastPlannedMinutes in PLANNED_MINUTE_PRESETS) "" else preset.lastPlannedMinutes.toString(),
                ),
            )
        }
    }

    fun setName(name: String) =
        _uiState.update { it.copy(draft = it.draft.copy(name = name.replace("\n", ""))) }

    fun selectTag(tag: Tag) = _uiState.update { it.copy(draft = it.draft.copy(tag = tag)) }

    fun setGoal(goal: String) =
        _uiState.update { it.copy(draft = it.draft.copy(goal = goal.replace("\n", " "))) }

    fun selectPlannedMinutes(minutes: Int) =
        _uiState.update { it.copy(draft = it.draft.copy(plannedMinutes = minutes, plannedInput = "")) }

    fun setPlannedMinutesInput(text: String) {
        val trimmed = text.trim()
        val minutes = if (trimmed.isEmpty()) null else trimmed.toIntOrNull()
        // 数値以外は受け付けない（無視）。空入力は「未選択」に戻す。
        if (trimmed.isNotEmpty() && minutes == null) return
        _uiState.update {
            it.copy(draft = it.draft.copy(plannedMinutes = minutes?.takeIf { m -> m > 0 }, plannedInput = trimmed))
        }
    }

    fun start() {
        val state = _uiState.value
        if (!state.canStart) return
        val draft = state.draft
        val tag = draft.tag ?: return
        val planned = draft.plannedMinutes ?: return
        val name = draft.name.trim()

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
        return when (state.step) {
            SwitchStep.RATING -> false
            SwitchStep.FORM -> {
                if (state.runningSession != null) {
                    _uiState.update { it.copy(step = SwitchStep.RATING) }
                    true
                } else {
                    false
                }
            }
        }
    }

    /** △（普通）は理由を自動で「特に無し」にする（docs/01_SPEC.md 4.3 Step 1）。 */
    private fun resolvedNote(state: SwitchUiState): String =
        if (state.rating == Rating.NORMAL) "特に無し" else state.ratingNote.trim()

    companion object {
        // 予定時間プリセット。仕様 3 章に頻度を記録するテーブルが無いため固定値
        // （docs/01_SPEC.md 4.3 とワイヤーフレームの表示に合わせる）。
        val PLANNED_MINUTE_PRESETS = listOf(15, 25, 30, 45, 60, 90, 120)
    }
}
