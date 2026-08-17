package com.aki.tasktimer.domain

/**
 * 時間まわりの計算。**副作用ゼロの純関数だけを置く。**
 * 現在時刻をここで取らないこと（now は必ず引数で受ける）。テストできなくなる。
 */

private const val MILLIS_PER_MINUTE = 60_000L

/**
 * 経過分数。カウンタで積算せず、毎回 now - startedAt で求める（docs/02_ARCHITECTURE.md 5.2）。
 * プロセスが死んで作り直されても正しい値になる。
 *
 * 端数は切り捨て。59 秒はまだ 0 分。
 * now が startedAt より前（端末の時刻変更など）なら 0 を返す。UI に負の値を出さないため。
 */
fun elapsedMinutes(startedAt: Long, now: Long): Int {
    if (now <= startedAt) return 0
    return ((now - startedAt) / MILLIS_PER_MINUTE).toInt()
}

/**
 * 超過率（%）。
 *
 * **分母は当初の予定（plannedMinutes）であって totalPlannedMinutes ではない**
 * （docs/01_SPEC.md 4.4-5）。延長で分母が膨らむと、見積もりの精度という
 * 測りたかったものが測れなくなる。
 *
 * plannedMinutes が 0 以下なら 0 を返す。UI 上は 1 分以上しか入力できないが、
 * ゼロ除算でアプリが落ちるほうが害が大きい。
 */
fun overrunRate(elapsedMinutes: Int, plannedMinutes: Int): Int {
    if (plannedMinutes <= 0) return 0
    return (elapsedMinutes.toLong() * 100 / plannedMinutes).toInt()
}

/**
 * 合計所要時間（docs/01_SPEC.md 4.4-2）。
 * 「予定 + 延長」ではなく「**経過** + 選択中の延長」。
 * 45 分予定で 47 分経過して +10 を選んだら 57 分。実際に費やすことになる総量を見せる。
 */
fun totalRequiredMinutes(elapsedMinutes: Int, selectedExtension: Int): Int =
    elapsedMinutes + selectedExtension

/**
 * 期限の絶対時刻。AlarmManager に渡す値。
 * 分母は延長を含んだ totalPlannedMinutes（超過率とは逆なので注意）。
 */
fun deadlineMillis(startedAt: Long, totalPlannedMinutes: Int): Long =
    startedAt + totalPlannedMinutes * MILLIS_PER_MINUTE
