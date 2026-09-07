package com.aki.tasktimer.ui.overdue

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aki.tasktimer.data.model.ExtensionPreset
import com.aki.tasktimer.data.model.Session
import com.aki.tasktimer.data.repository.PresetRepository
import com.aki.tasktimer.data.repository.SessionRepository
import com.aki.tasktimer.domain.isOverdue
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class OverdueUiState(
    val session: Session? = null,
    val now: Long = System.currentTimeMillis(),
    val presets: List<ExtensionPreset> = emptyList(),
    /** 選択中の延長分数。押しても即実行しない（docs/01_SPEC.md 4.4-1） */
    val selectedMinutes: Int? = null,
    /** 自由入力欄の文字列。プリセットに無い分数を使いたいとき用 */
    val customInput: String = "",
    val isSaving: Boolean = false,
    /** 超過画面を閉じてよい（延長した／進行中が無い／もう超過していない） */
    val done: Boolean = false,
    val error: String? = null,
)

/**
 * 超過画面の状態。進行中セッション・1 秒ティッカー・延長プリセット・選択状態を束ねる。
 * 経過時間や超過率はここで計算せず、UI 側が純関数で導く。
 */
class OverdueViewModel(
    private val sessionRepository: SessionRepository,
    private val presetRepository: PresetRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(OverdueUiState())
    val uiState: StateFlow<OverdueUiState> = _uiState.asStateFlow()

    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    init {
        viewModelScope.launch {
            combine(
                sessionRepository.observeRunningSession(),
                presetRepository.observeExtensionPresets(),
                ticker,
            ) { session, presets, now -> Triple(session, presets, now) }
                .collect { (session, presets, now) ->
                    _uiState.update { state ->
                        state.copy(
                            session = session,
                            presets = presets,
                            now = now,
                            // 進行中が無い／超過が解けた（別の場所で延長した等）なら閉じる。
                            done = state.done || !isOverdue(session, now),
                        )
                    }
                }
        }
    }

    fun select(minutes: Int) = _uiState.update { it.copy(selectedMinutes = minutes, customInput = "") }

    /** 自由入力。数字以外は無視。空にすると未選択に戻る。 */
    fun setCustomInput(text: String) {
        val trimmed = text.trim()
        val minutes = if (trimmed.isEmpty()) null else trimmed.toIntOrNull()
        if (trimmed.isNotEmpty() && minutes == null) return
        _uiState.update {
            it.copy(customInput = trimmed, selectedMinutes = minutes?.takeIf { m -> m > 0 })
        }
    }

    fun extend() {
        val state = _uiState.value
        val minutes = state.selectedMinutes ?: return
        if (state.isSaving) return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                // Repository が新しい期限を予約し直す。延長は Notion には送らない。
                sessionRepository.addExtension(minutes, System.currentTimeMillis())
                presetRepository.recordExtensionUse(minutes)
            }.onSuccess {
                _uiState.update { it.copy(isSaving = false, done = true) }
            }.onFailure { e ->
                _uiState.update { it.copy(isSaving = false, error = e.message ?: "延長に失敗しました") }
            }
        }
    }

    fun dismissError() = _uiState.update { it.copy(error = null) }
}
