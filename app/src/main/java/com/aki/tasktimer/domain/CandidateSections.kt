package com.aki.tasktimer.domain

import com.aki.tasktimer.data.model.TaskPreset
import kotlin.math.ceil

/**
 * 「次のタスク」画面の候補を 3 つの欄に分けたもの。画面には上から この順で出す。
 *
 * 20 個を平らに並べると選べなかった（2026-09-10、Aki の実機スクショ）。原因は数の多さと、
 * 一度しか使わない名前が「よく使う順」の上位に混ざること。欄を分けて数を絞る。
 */
data class CandidateSections(
    /** 自分で固定したもの。数や余白に関係なく必ず全部出す */
    val pinned: List<TaskPreset> = emptyList(),
    /** 2 回以上使ったもの。よく使う順 */
    val frequent: List<TaskPreset> = emptyList(),
    /** 直近に使ったもののうち、上の 2 欄に出ていないもの。一度きりの名前はここでだけ拾える */
    val recent: List<TaskPreset> = emptyList(),
) {
    val isEmpty: Boolean get() = pinned.isEmpty() && frequent.isEmpty() && recent.isEmpty()

    /** 固定がまだ 1 つも無いときだけ、固定のしかたを案内する（固定すると消える）。 */
    val showPinHint: Boolean get() = pinned.isEmpty() && (frequent.isNotEmpty() || recent.isNotEmpty())
}

/** 「よくやる」に入るのに必要な使用回数。1 回きりの名前はここで弾かれ「最近」でだけ拾える */
const val FREQUENT_MIN_USES = 2

/** 「最近」欄の最大数 */
const val RECENT_CANDIDATE_COUNT = 2

/** 「よくやる」と「最近」を合わせた最大数。固定は数えない */
const val UNPINNED_CANDIDATE_LIMIT = 8

/**
 * 候補を 3 つの欄に振り分ける。並び順は SQL で確定済みのものを受け取り、ここでは並べ替えない。
 *
 * @param presets 全候補。固定が先頭、あとは使用回数順
 * @param recent 固定していない候補を直近に使った順に [RECENT_CANDIDATE_COUNT] 件
 */
fun buildCandidateSections(
    presets: List<TaskPreset>,
    recent: List<TaskPreset>,
    limit: Int = UNPINNED_CANDIDATE_LIMIT,
): CandidateSections {
    val pinned = presets.filter { it.isPinned }
    val frequentAll = presets.filter { !it.isPinned && it.useCount >= FREQUENT_MIN_USES }
    val recentUnpinned = recent.filter { !it.isPinned }

    // 「最近」の一度きりの名前が「よくやる」に押し出されて消えないよう、その分の枠を先に空けておく。
    val oneOffRecentCount = recentUnpinned.count { r -> frequentAll.none { it.id == r.id } }
    val frequent = frequentAll.take((limit - oneOffRecentCount).coerceAtLeast(0))
    val recentShown = recentUnpinned
        .filter { r -> frequent.none { it.id == r.id } }
        .take((limit - frequent.size).coerceAtLeast(0))

    return CandidateSections(pinned = pinned, frequent = frequent, recent = recentShown)
}

/**
 * 候補欄の寸法。候補は全部同じ大きさなので、行数から高さが決まる。
 */
data class CandidateGridSpec(
    val columns: Int = 2,
    val blockHeightDp: Float = 44f,
    /** 候補どうしの隙間（縦・横とも） */
    val gapDp: Float = 8f,
    /** 欄の見出し 1 行ぶん（文字 ＋ 下の余白） */
    val labelHeightDp: Float = 22f,
    /** 欄と欄のあいだ */
    val sectionGapDp: Float = 12f,
)

/** 候補欄全体の高さ。空の欄は見出しごと出さない。固定の案内は見出し 1 行ぶんとして数える。 */
fun candidateSectionsHeightDp(sections: CandidateSections, spec: CandidateGridSpec = CandidateGridSpec()): Float {
    val parts = buildList {
        if (sections.showPinHint) add(spec.labelHeightDp)
        listOf(sections.pinned, sections.frequent, sections.recent).forEach { items ->
            if (items.isNotEmpty()) {
                val rows = ceil(items.size / spec.columns.toFloat()).toInt()
                add(spec.labelHeightDp + rows * spec.blockHeightDp + (rows - 1) * spec.gapDp)
            }
        }
    }
    if (parts.isEmpty()) return 0f
    return parts.sum() + (parts.size - 1) * spec.sectionGapDp
}

/**
 * 候補欄が [availableHeightDp] に収まるまで、画面の下にあるものから削る（最近 → よくやる の順）。
 * 固定は約束どおり削らない。固定だけで溢れる場合は溢れたまま返す（入力欄側がスクロールできる）。
 */
fun fitCandidateSections(
    sections: CandidateSections,
    availableHeightDp: Float,
    spec: CandidateGridSpec = CandidateGridSpec(),
): CandidateSections {
    var fitted = sections
    while (candidateSectionsHeightDp(fitted, spec) > availableHeightDp) {
        fitted = when {
            fitted.recent.isNotEmpty() -> fitted.copy(recent = fitted.recent.dropLast(1))
            fitted.frequent.isNotEmpty() -> fitted.copy(frequent = fitted.frequent.dropLast(1))
            else -> return fitted
        }
    }
    return fitted
}
