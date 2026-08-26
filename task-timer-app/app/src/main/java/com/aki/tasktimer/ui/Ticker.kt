package com.aki.tasktimer.ui

import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/**
 * 1 秒ごとに現在時刻を流すだけの Flow。
 *
 * **これは「経過時間」ではない。** 経過時間はこの now と session.startedAt から毎回計算する
 * （docs/02_ARCHITECTURE.md 5.2）。カウンタを持たないので、プロセスが死んで作り直されても、
 * 端末がスリープしていても、復帰した瞬間から正しい値が出る。
 *
 * 待ち時間を単純に 1000ms 固定にせず**秒の変わり目に合わせている**理由：
 * 固定にすると 1 周ごとに処理時間ぶんの遅れが積もり、やがて表示が 1 秒飛ぶ。
 * 「00:07 の次が 00:09」が数分に 1 回起きると、止まったように見えて気持ちが悪い。
 *
 * 期限の判定には絶対に使わないこと。それは AlarmManager の仕事（Phase 4）。
 * ここが止まっても鳴らなくなってはいけない。
 */
internal fun tickerFlow(intervalMillis: Long = 1_000L): Flow<Long> = flow {
    while (true) {
        val now = System.currentTimeMillis()
        emit(now)
        delay(intervalMillis - now % intervalMillis)
    }
}
