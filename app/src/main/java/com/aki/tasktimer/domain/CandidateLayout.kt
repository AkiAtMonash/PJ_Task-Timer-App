package com.aki.tasktimer.domain

import kotlin.math.ceil
import kotlin.math.min

/**
 * 「次のタスク」画面の候補ブロックを、余白に入るぶんだけ出すための見積もり。
 *
 * 候補は使用頻度順に並んでいるので、「入るところまで採る」＝「頻度の低いものが落ちる」になる。
 * 描いてから測ると 1 フレーム分ちらつくので、描く前に文字数から幅と行数を見積もる
 * （2026-09-10、候補を 2〜3 行の塊にした際の要件）。
 *
 * 見積もりなので実レイアウトと 1 行ずれることはある。ずれた場合も入力欄側がスクロールできるため、
 * 画面が壊れることはない。
 */
data class CandidateLayoutSpec(
    /** 候補を並べられる横幅 */
    val containerWidthDp: Float,
    /** 候補に使える縦幅 */
    val availableHeightDp: Float,
    /** ブロック 1 個の最大幅。これを超える名前は折り返す */
    val maxBlockWidthDp: Float,
    val fontSizeDp: Float = 14f,
    val lineHeightDp: Float = 20f,
    val horizontalPaddingDp: Float = 12f,
    val verticalPaddingDp: Float = 10f,
    val gapDp: Float = 10f,
    /** 折り返しの上限行数。これを超える名前は末尾を省略する */
    val maxLines: Int = 3,
)

/**
 * [names] を上から順に詰めていき、[spec] の縦幅に収まる個数を返す。
 */
fun visibleCandidateCount(names: List<String>, spec: CandidateLayoutSpec): Int {
    if (names.isEmpty() || spec.availableHeightDp <= 0f || spec.containerWidthDp <= 0f) return 0

    var count = 0
    // 確定済みの行の高さ合計（行間を含む）。現在組み立て中の行は含めない。
    var committedHeight = 0f
    var rowWidth = 0f
    var rowHeight = 0f

    for (name in names) {
        val width = min(blockWidthDp(name, spec), spec.containerWidthDp)
        val height = blockHeightDp(name, spec)
        val fitsInCurrentRow = count > 0 && rowWidth + spec.gapDp + width <= spec.containerWidthDp

        if (fitsInCurrentRow) {
            // 同じ行に別の高さのブロックが来たら、行の高さは高いほうに合わせられる。
            val grownRowHeight = maxOf(rowHeight, height)
            if (committedHeight + grownRowHeight > spec.availableHeightDp) return count
            rowWidth += spec.gapDp + width
            rowHeight = grownRowHeight
        } else {
            val nextCommitted = if (count == 0) 0f else committedHeight + rowHeight + spec.gapDp
            if (nextCommitted + height > spec.availableHeightDp) return count
            committedHeight = nextCommitted
            rowWidth = width
            rowHeight = height
        }
        count++
    }
    return count
}

/** 折り返さなければ何 dp の幅になるか。上限に達したら上限（＝折り返す）。 */
private fun blockWidthDp(name: String, spec: CandidateLayoutSpec): Float =
    min(textWidthDp(name, spec.fontSizeDp) + spec.horizontalPaddingDp * 2, spec.maxBlockWidthDp)

private fun blockHeightDp(name: String, spec: CandidateLayoutSpec): Float =
    spec.verticalPaddingDp * 2 + lineCount(name, spec) * spec.lineHeightDp

private fun lineCount(name: String, spec: CandidateLayoutSpec): Int {
    val innerWidth = spec.maxBlockWidthDp - spec.horizontalPaddingDp * 2
    if (innerWidth <= 0f) return 1
    val lines = ceil(textWidthDp(name, spec.fontSizeDp) / innerWidth).toInt()
    return lines.coerceIn(1, spec.maxLines)
}

/**
 * 文字数から幅を見積もる。全角は 1 文字ぶん、半角は約 0.55 文字ぶんとして数える。
 * 実測ではないが、1 行に何個入るかを決めるにはこの粒度で足りる。
 */
private fun textWidthDp(text: String, fontSizeDp: Float): Float {
    var ratio = 0f
    for (c in text) {
        ratio += when {
            c.code < 0x2000 -> 0.55f          // ラテン文字・数字・記号
            c.code in 0xFF61..0xFF9F -> 0.55f // 半角カナ
            else -> 1.0f                       // 漢字・かな・全角記号
        }
    }
    return ratio * fontSizeDp
}
