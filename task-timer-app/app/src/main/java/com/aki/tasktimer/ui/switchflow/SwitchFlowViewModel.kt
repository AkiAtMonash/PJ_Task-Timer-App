// docs/02_ARCHITECTURE.md 3 章のパッケージ構成では ui/switch/ だが、switch は Java の予約語で、
// JVM のパッケージ名として使うと Java 側から参照できない名前になる。
// Kotlin だけならコンパイルは通るものの踏む必要のない地雷なので switchflow にしている。
package com.aki.tasktimer.ui.switchflow

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.data.model.Tag
import com.aki.tasktimer.data.model.TaskPreset
import com.aki.tasktimer.data.repository.PresetRepository
import com.aki.tasktimer.data.repository.SessionRepository
import com.aki.tasktimer.domain.elapsedMinutes
import com.aki.tasktimer.domain.formatElapsed
import com.aki.tasktimer.domain.isOverrun
import com.aki.tasktimer.domain.overrunRate
import com.aki.tasktimer.ui.container
import com.aki.tasktimer.ui.tickerFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** このフローに入ってきた理由。押したボタンで決まり、途中で変わらない。 */
internal enum class FlowMode {
    /** 「タスクを切り替える」。前を終えて次を始める */
    SWITCH,

    /** 未計測から「タスクを開始する」。評価する相手がいないので Step 1 を飛ばす */
    START,

    /** 「中断（記録して停止）」。評価だけ聞いて終わる */
    STOP,
}

internal enum class FlowStep {
    /** Step 1：前タスクの評価 */
    RATING,

    /** Step 1 の続き：✅ / ❌ を選んだときの理由入力（必須） */
    RATING_NOTE,

    /** Step 2：次のタスク名をプリセットから選ぶ */
    TASK_NAME,

    /** Step 2 の続き：新しいタスク名を打つ */
    NEW_TASK_NAME,

    /** Step 2 の続き：新規タスクのタグを選ぶ */
    TAG_PICK,

    /** Step 3：ゴールと予定時間（1 画面にまとめている） */
    GOAL_AND_TIME,
}

/** Step 1 で見せる「いま終えようとしているタスク」。 */
internal data class PreviousTask(
    val name: String,
    val tag: Tag,
    val elapsedText: String,
    val plannedMinutes: Int,
    val overrunRate: Int,
    val isOverrun: Boolean,
)

internal data class SwitchFlowUiState(
    /** null ならフローに入っていない */
    val mode: FlowMode?,
    val step: FlowStep,
    /** Step 1 のときだけ入る。他の Step では毎秒の再描画を避けるため null にしている */
    val previous: PreviousTask? = null,
    val presets: List<TaskPreset> = emptyList(),
    val rating: Rating? = null,
    val ratingNote: String = "",
    val name: String = "",
    val nameInput: String = "",
    val tag: Tag? = null,
    val goal: String = "",
    /** 前回のゴール。薄字で出して入力を促すだけで、初期値としては入れない */
    val goalPlaceholder: String = "",
    val plannedMinutes: Int? = null,
    val freeMinutes: String = "",
)

/**
 * 切り替えフローの下書きを預かる（docs/01_SPEC.md 4.3）。
 *
 * **確定するまで DB に触らない。** 途中でやめたら何も残らないのが正しい。
 * 最後の [start] / [confirmStop] で 1 回だけ書き込む。
 *
 * フローに入っているかどうかもこの ViewModel が持つ（[activeMode]）。
 * 画面側の remember で持つと、画面を回した瞬間に下書きだけ残って画面がホームに戻る。
 */
internal class SwitchFlowViewModel(
    private val sessionRepository: SessionRepository,
    private val presetRepository: PresetRepository,
) : ViewModel() {

    private data class Draft(
        val mode: FlowMode? = null,
        val step: FlowStep = FlowStep.RATING,
        val rating: Rating? = null,
        val ratingNote: String = "",
        val name: String = "",
        val nameInput: String = "",
        val tag: Tag? = null,
        val goal: String = "",
        val goalPlaceholder: String = "",
        val plannedMinutes: Int? = null,
        val freeMinutes: String = "",
    )

    private val draft = MutableStateFlow(Draft())

    /**
     * いまフローに入っているか。ここだけは時計と無関係に見たいので uiState とは別に出す。
     * uiState を購読すると 1 秒ごとの時計まで一緒に回ってしまう。
     */
    val activeMode: StateFlow<FlowMode?> = draft
        .map { it.mode }
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val uiState: StateFlow<SwitchFlowUiState> = combine(
        draft,
        sessionRepository.observeRunningSession(),
        presetRepository.observeTaskPresets(),
        tickerFlow(),
    ) { d, running, presets, now ->
        // Step 1 以外で previous を作り直すと、画面全体が 1 秒ごとに再描画されてしまう。
        // 経過時間を見せている間だけ時計を反映させる
        val showsElapsed = d.step == FlowStep.RATING || d.step == FlowStep.RATING_NOTE
        val previous = if (showsElapsed && running != null) {
            PreviousTask(
                name = running.name,
                tag = running.tag,
                elapsedText = formatElapsed(running.startedAt, now),
                plannedMinutes = running.plannedMinutes,
                // 超過率の分母は当初見積もり。延長を足すと「見積もりの精度」が測れなくなる
                // （docs/01_SPEC.md 4.4-5）
                overrunRate = overrunRate(
                    elapsedMinutes(running.startedAt, now),
                    running.plannedMinutes,
                ),
                isOverrun = isOverrun(running.startedAt, now, running.totalPlannedMinutes),
            )
        } else {
            null
        }

        SwitchFlowUiState(
            mode = d.mode,
            step = d.step,
            previous = previous,
            presets = presets,
            rating = d.rating,
            ratingNote = d.ratingNote,
            name = d.name,
            nameInput = d.nameInput,
            tag = d.tag,
            goal = d.goal,
            goalPlaceholder = d.goalPlaceholder,
            plannedMinutes = d.plannedMinutes,
            freeMinutes = d.freeMinutes,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = SwitchFlowUiState(mode = null, step = FlowStep.RATING),
    )

    /**
     * フローを開く。**必ず下書きを作り直す。**
     * 前回の入力が残っていると、次に開いたときに他人のゴールが入っている状態になる。
     */
    fun begin(mode: FlowMode) {
        draft.value = Draft(
            mode = mode,
            // 未計測から始めるときは評価する相手がいない
            step = if (mode == FlowMode.START) FlowStep.TASK_NAME else FlowStep.RATING,
        )
    }

    /** フローを閉じる。書き込みはしない。 */
    private fun close() {
        draft.value = Draft()
    }

    // ---- Step 1：評価 ----

    fun selectRating(rating: Rating) {
        if (rating == Rating.NORMAL) {
            // 🫳 は理由を聞かない。空のまま保存すると、あとで見返したときに
            // 「書き忘れた」のか「特に無かった」のか区別がつかなくなる
            draft.update { it.copy(rating = rating, ratingNote = NORMAL_NOTE) }
            advanceFromRating()
        } else {
            draft.update {
                it.copy(rating = rating, ratingNote = "", step = FlowStep.RATING_NOTE)
            }
        }
    }

    fun changeRatingNote(text: String) = draft.update { it.copy(ratingNote = text) }

    /** 理由を書き終えた。空では進ませない（docs/01_SPEC.md 4.3 Step 1：理由は必須）。 */
    fun confirmRatingNote() {
        if (draft.value.ratingNote.isBlank()) return
        advanceFromRating()
    }

    /**
     * 評価が済んだあとの行き先。
     *
     * 中断はここで終わり。**次のタスク名を聞かない**のが切り替えとの違いで、
     * 聞いてしまうと中断と切り替えが同じ操作になる。
     */
    private fun advanceFromRating() {
        if (draft.value.mode == FlowMode.STOP) {
            confirmStop()
        } else {
            draft.update { it.copy(step = FlowStep.TASK_NAME) }
        }
    }

    // ---- Step 2：次のタスク ----

    fun selectPreset(preset: TaskPreset) {
        draft.update {
            it.copy(
                name = preset.name,
                tag = preset.tag,
                // 前回のゴールは薄字のプレースホルダとして出すだけ。
                // 本文に入れてしまうと、違うことをやるときに毎回消す手間が増える
                goal = "",
                goalPlaceholder = preset.lastGoal.orEmpty(),
                plannedMinutes = preset.lastPlannedMinutes,
                freeMinutes = "",
                step = FlowStep.GOAL_AND_TIME,
            )
        }
    }

    fun requestNewTask() = draft.update { it.copy(nameInput = "", step = FlowStep.NEW_TASK_NAME) }

    fun changeNameInput(text: String) = draft.update { it.copy(nameInput = text) }

    fun confirmNewTaskName() {
        draft.update { d ->
            val name = d.nameInput.trim()
            if (name.isEmpty()) d else d.copy(name = name, step = FlowStep.TAG_PICK)
        }
    }

    /** 新規タスクのタグ。既存タスクのタグは v1 では変えられない（docs/01_SPEC.md 4.3 Step 2）。 */
    fun selectTag(tag: Tag) {
        draft.update {
            it.copy(
                tag = tag,
                goal = "",
                goalPlaceholder = "",
                plannedMinutes = null,
                freeMinutes = "",
                step = FlowStep.GOAL_AND_TIME,
            )
        }
    }

    // ---- Step 3：ゴールと予定時間 ----

    fun changeGoal(text: String) = draft.update { it.copy(goal = text) }

    fun selectPlannedMinutes(minutes: Int) =
        draft.update { it.copy(plannedMinutes = minutes, freeMinutes = "") }

    fun changeFreeMinutes(text: String) {
        // 数字以外は弾く。キーボードを数字に絞っていても貼り付けは通ってしまう。
        // 4 桁（最大 9999 分 ≒ 6.9 日）あれば実用上足りる
        val digits = text.filter { it.isDigit() }.take(4)
        draft.update { d ->
            d.copy(
                freeMinutes = digits,
                plannedMinutes = digits.toIntOrNull()?.takeIf { it > 0 },
            )
        }
    }

    // ---- 確定 ----

    /** 次のタスクを開始する。前タスクがあれば同じ時刻で終了させる（1 トランザクション）。 */
    fun start() {
        val d = draft.value
        val tag = d.tag ?: return
        val planned = d.plannedMinutes ?: return
        if (d.name.isBlank() || planned <= 0) return

        viewModelScope.launch {
            val now = System.currentTimeMillis()
            // 進行中が無ければ単に開始するだけなので、START のときもこれで足りる。
            // startSession は進行中があると例外を投げるので、あえて使い分けない
            sessionRepository.switchSession(
                rating = d.rating,
                ratingNote = d.ratingNote.ifBlank { null },
                name = d.name,
                tag = tag,
                goal = d.goal,
                plannedMinutes = planned,
                at = now,
            )
            // 自動学習（docs/01_SPEC.md 3.3）。次回この名前を選んだときの初期値になる
            presetRepository.recordTaskUse(
                name = d.name,
                tag = tag,
                goal = d.goal,
                plannedMinutes = planned,
                usedAt = now,
            )
            close()
        }
    }

    /** 中断（記録して停止）。次のタスクは始めない。 */
    fun confirmStop() {
        val rating = draft.value.rating ?: return
        val note = draft.value.ratingNote

        viewModelScope.launch {
            sessionRepository.finishSession(
                rating = rating,
                ratingNote = note.ifBlank { null },
                endedAt = System.currentTimeMillis(),
            )
            close()
        }
    }

    /**
     * 戻る操作。最初の Step で戻ったら下書きごと捨ててフローを閉じる。
     * 閉じたことは [activeMode] が null になることで画面に伝わる。
     */
    fun back() {
        val d = draft.value
        val previousStep = when (d.step) {
            FlowStep.RATING -> null
            FlowStep.RATING_NOTE -> FlowStep.RATING
            FlowStep.TASK_NAME -> if (d.mode == FlowMode.START) null else FlowStep.RATING
            FlowStep.NEW_TASK_NAME -> FlowStep.TASK_NAME
            FlowStep.TAG_PICK -> FlowStep.NEW_TASK_NAME
            // プリセット経由でも新規入力経由でもタスク名の選び直しに戻す。
            // 経路ごとに戻り先を変えると「戻ったのに違う画面」になって迷う
            FlowStep.GOAL_AND_TIME -> FlowStep.TASK_NAME
        }

        if (previousStep == null) close() else draft.update { it.copy(step = previousStep) }
    }

    companion object {
        /** 🫳 を選んだときに自動で入る理由（docs/01_SPEC.md 4.3 Step 1）。 */
        const val NORMAL_NOTE = "特に無し"

        /**
         * 予定時間の候補（mockups/wireframe.html の Step 3・4）。
         * 使用頻度を覚える置き場が無いので固定にしている（Phase 2 の決定事項 ②）。
         */
        val PLANNED_PRESETS = listOf(15, 25, 30, 45, 60, 90, 120)

        val Factory = viewModelFactory {
            initializer {
                SwitchFlowViewModel(
                    sessionRepository = container.sessionRepository,
                    presetRepository = container.presetRepository,
                )
            }
        }
    }
}
