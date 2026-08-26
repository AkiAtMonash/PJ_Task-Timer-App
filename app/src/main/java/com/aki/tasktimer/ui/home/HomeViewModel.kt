package com.aki.tasktimer.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.aki.tasktimer.data.model.Session
import com.aki.tasktimer.data.repository.SessionRepository
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.stateIn

/**
 * ホーム画面の状態。1 秒ごとの now と進行中／最後の記録を束ねる。
 *
 * 経過時間はここでは計算しない。now を供給するだけで、UI 側が
 * elapsedMinutes(startedAt, now) で導く（docs/02_ARCHITECTURE.md 5.2）。
 * 積算カウンタを持たないので、プロセス再生成でも値が狂わない。
 */
data class HomeUiState(
    val runningSession: Session?,
    val lastFinishedSession: Session?,
    val now: Long,
)

class HomeViewModel(
    private val sessionRepository: SessionRepository,
) : ViewModel() {

    // 1 秒ごとに現在時刻を流すだけ。while(true) の無限ループは
    // WhileSubscribed(5_000) により画面が見えなくなると止まる。
    private val ticker = flow {
        while (true) {
            emit(System.currentTimeMillis())
            delay(1_000)
        }
    }

    val uiState: StateFlow<HomeUiState> = combine(
        sessionRepository.observeRunningSession(),
        sessionRepository.observeLastFinishedSession(),
        ticker,
    ) { running, lastFinished, now ->
        HomeUiState(
            runningSession = running,
            lastFinishedSession = lastFinished,
            now = now,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(null, null, System.currentTimeMillis()),
    )
}
