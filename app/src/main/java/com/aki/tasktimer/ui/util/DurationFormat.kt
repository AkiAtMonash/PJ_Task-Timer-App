package com.aki.tasktimer.ui.util

import java.util.Locale

/**
 * 経過時間の表示書式。表示の関心事なので domain ではなく ui に置くが、
 * 端末なしで境界ケースを検証できるよう副作用ゼロの純関数にしている。
 *
 * 1 時間未満は `MM:SS`（例 23:41）、1 時間以上は `H:MM:SS`（例 1:12:00）。
 * ワイヤーフレームの 23:41 / 1:12:00 に合わせている。
 *
 * **分を 2 桁に 0 埋めする理由**：この数字は 48sp で中央に置かれ毎秒書き換わる。
 * `9:59` → `10:00` で桁が増えると、中央揃えなので表示全体が横にずれて目立つ。
 * 等幅フォント（tokens.css の tabular figures）は 1 桁ぶんの幅は揃えるが、
 * 桁数そのものは揃えてくれない。
 * ※ `0:59` のほうが自然だと思うなら、下の `%02d` を `%d` に戻せば元に戻る。
 */
fun formatElapsed(millis: Long): String {
    val clamped = millis.coerceAtLeast(0L)
    val totalSeconds = clamped / 1_000
    val hours = totalSeconds / 3_600
    val minutes = (totalSeconds % 3_600) / 60
    val seconds = totalSeconds % 60
    return if (hours > 0) {
        String.format(Locale.ROOT, "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.ROOT, "%02d:%02d", minutes, seconds)
    }
}
