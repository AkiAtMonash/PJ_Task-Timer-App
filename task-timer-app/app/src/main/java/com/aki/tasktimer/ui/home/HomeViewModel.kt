package com.aki.tasktimer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.data.model.Session
import com.aki.tasktimer.data.model.Tag
import com.aki.tasktimer.data.repository.SessionRepository
import com.aki.tasktimer.domain.ProgressSplit
import com.aki.tasktimer.domain.elapsedMinutes
import com.aki.tasktimer.domain.formatDurationMinutes
import com.aki.tasktimer.domain.formatElapsed
import com.aki.tasktimer.domain.isOverrun
import com.aki.tasktimer.domain.progressSplit
import com.aki.tasktimer.domain.remainingMinutes
import com.aki.tasktimer.ui.container
import com.aki.tasktimer.ui.tickerFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

/** ホーム画面の状態。進行中かどうかで別物なので sealed で分けている。 */
internal sealed interface HomeUiState {

    /** DB を読む前の一瞬。ここで「未計測」を描くと、起動のたびに一瞬ちらつく。 */
    data object Loading : HomeUiState

    /**
     * 未計測。中断直後と初回起動でしか出ない画面
     * （運用上は次のタスクをすぐ入れるので、ほとんど目にしない）。
     */
    data class Idle(val lastRecord: LastRecord?) : HomeUiState

    data class Running(
        val name: String,
        val tag: Tag,
        val goal: String,
        val elapsedText: String,
        val plannedMinutes: Int,
        /** 正なら残り、負なら超過。表示の出し分けは [isOverrun] で決めること */
        val remainingMinutes: Int,
        val isOverrun: Boolean,
        val progress: ProgressSplit,
    ) : HomeUiState
}

/** 「ES執筆 / 45分 / ◯」の 1 行（docs/01_SPEC.md 4.2）。 */
internal data class LastRecord(
    val name: String,
    val durationText: String,
    val rating: Rating?,
)

/**
 * ホーム画面の状態を組み立てる。
 *
 * 進行中セッションの正は DB（docs/02_ARCHITECTURE.md 5.1）。ここでは一切保持せず、
 * 購読した結果と現在時刻から毎回作り直す。
 */
internal class HomeViewModel(sessionRepository: SessionRepository) : ViewModel() {

    val uiState: StateFlow<HomeUiState> = combine(
        sessionRepository.observeRunningSession(),
        sessionRepository.observeLastFinishedSession(),
        tickerFlow(),
    ) { running, lastFinished, now ->
        // 未計測のときも ticker は 1 秒ごとに流れてくるが、出来上がる Idle は毎回等しいので
        // StateFlow が同値を潰す。無駄な再描画にはならない
        if (running == null) HomeUiState.Idle(lastFinished?.toLastRecord()) else running.toRunning(now)
    }.stateIn(
        scope = viewModelScope,
        // 画面が背面に回ったら購読ごと止めて ticker を寝かせる。
        // 5 秒の猶予は画面回転や一瞬のアプリ切り替えで作り直さないため
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState.Loading,
    )

    companion object {
        val Factory = viewModelFactory {
            initializer { HomeViewModel(container.sessionRepository) }
        }
    }
}

private fun Session.toRunning(now: Long): HomeUiState.Running = HomeUiState.Running(
    name = name,
    tag = tag,
    goal = goal,
    elapsedText = formatElapsed(startedAt, now),
    plannedMinutes = plannedMinutes,
    remainingMinutes = remainingMinutes(startedAt, now, totalPlannedMinutes),
    isOverrun = isOverrun(startedAt, now, totalPlannedMinutes),
    progress = progressSplit(startedAt, now, totalPlannedMinutes),
)

private fun Session.toLastRecord(): LastRecord = LastRecord(
    name = name,
    // 終わったセッションなので endedAt は必ず入っている。
    // 万一 null でも落とさず 0 分として見せる（記録の表示でクラッシュさせない）
    durationText = formatDurationMinutes(elapsedMinutes(startedAt, endedAt ?: startedAt)),
    rating = rating,
)
