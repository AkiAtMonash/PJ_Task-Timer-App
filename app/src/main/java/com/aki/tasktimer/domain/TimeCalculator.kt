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

/**
 * 期限までの残り分数。**負なら超過分**（docs/01_SPEC.md 4.1 の「残り 21分」「2分 超過」）。
 *
 * **truncate するのは経過分ではなく残り時間のほう。** 経過を先に分へ丸めてから引くと 1 分ずれる：
 * 45 分予定・23 分 41 秒経過なら、残りは 21 分 19 秒なので「21 分」。
 * `45 - 23 = 22` としてはいけない（mockups/wireframe.html の表示は 21 分）。
 *
 * 端数は 0 方向に切り捨てる。残り 21 分 19 秒 → 21、超過 2 分 12 秒 → -2。
 */
fun remainingMinutes(startedAt: Long, now: Long, totalPlannedMinutes: Int): Int {
    val diff = deadlineMillis(startedAt, totalPlannedMinutes) - now
    return if (diff >= 0L) {
        (diff / MILLIS_PER_MINUTE).toInt()
    } else {
        -((-diff) / MILLIS_PER_MINUTE).toInt()
    }
}

/**
 * 期限を過ぎているか。
 *
 * **[remainingMinutes] の符号で判定しないこと。** 45 分予定で 45 分 30 秒経過だと
 * 端数が切り捨てられて 0 が返り、「残り 0 分」と「0 分超過」が区別できない。
 * 符号で見ると、超過してから丸 1 分ぶん警報色にならない時間帯ができる。
 */
fun isOverrun(startedAt: Long, now: Long, totalPlannedMinutes: Int): Boolean =
    now >= deadlineMillis(startedAt, totalPlannedMinutes)

/**
 * 進捗バーの塗り分け（mockups/wireframe.html の「進行中」2 状態）。
 * [base] が中立色、[over] が警報色。**足すと必ず 1.0 以下**になる。
 */
data class ProgressSplit(val base: Float, val over: Float)

/**
 * 進捗バーの長さ。
 *
 * 未超過：バー全体が予定時間を表す。23:41 / 予定 45 分 → base 0.53。
 * 超過後：**バー全体が経過時間に取り直され**、予定の位置で色が変わる。
 *         47:12 / 予定 45 分 → base 0.95（= 45/47.2）、over 0.05。
 *
 * 超過後にバーを伸ばし続けず全体を経過時間に取り直すのは、バーが画面からはみ出さないため。
 * 「予定に対してどれだけ食い込んだか」の比率は保たれる。
 *
 * 分を丸めてから比を取ると、47 分 12 秒が 47 分になって 95/5 が 96/4 にずれる。
 * ミリ秒のまま計算すること。
 */
fun progressSplit(startedAt: Long, now: Long, totalPlannedMinutes: Int): ProgressSplit {
    if (totalPlannedMinutes <= 0 || now <= startedAt) return ProgressSplit(0f, 0f)

    val elapsed = (now - startedAt).toDouble()
    val planned = totalPlannedMinutes * MILLIS_PER_MINUTE.toDouble()

    if (elapsed <= planned) return ProgressSplit((elapsed / planned).toFloat(), 0f)

    val base = (planned / elapsed).toFloat()
    return ProgressSplit(base, 1f - base)
}

/**
 * 分数を人が読む形に（docs/01_SPEC.md 4.2 の「最後の記録」用）。
 *
 * 45 → `45分`、72 → `1時間12分`、480 → `8時間`。
 * 「480分」のままだと 8 時間なのか一瞬で分からない。
 */
fun formatDurationMinutes(minutes: Int): String {
    if (minutes <= 0) return "0分"

    val hours = minutes / 60
    val rest = minutes % 60
    return when {
        hours == 0 -> "${rest}分"
        rest == 0 -> "${hours}時間"
        else -> "${hours}時間${rest}分"
    }
}
