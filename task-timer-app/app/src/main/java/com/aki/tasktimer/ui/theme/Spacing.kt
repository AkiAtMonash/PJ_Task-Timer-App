package com.aki.tasktimer.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.unit.dp

// mockups/tokens.css の余白（4pt スケール）と角丸をそのまま写したもの。
// 画面のなかで 13.dp のような中途半端な値を直書きしないこと。
// スケールから外れた瞬間に「なんとなく揃っている」が崩れる。

/** 余白。CSS の --space-* に 1 対 1 で対応する。 */
internal object Space {
    val xxxs = 2.dp // --space-3xs
    val xxs = 4.dp // --space-2xs
    val xs = 8.dp // --space-xs
    val sm = 12.dp // --space-sm
    val md = 16.dp // --space-md
    val lg = 24.dp // --space-lg
    val xl = 40.dp // --space-xl
    val xxl = 64.dp // --space-2xl
    val xxxl = 96.dp // --space-3xl
}

/**
 * 角丸。ほぼ角のない設計にしているのは、丸めるほど「柔らかいアプリ」に見えて、
 * 超過画面の強制力と噛み合わなくなるため。
 */
internal object Radius {
    val card = RoundedCornerShape(4.dp) // --radius-card
    val input = RoundedCornerShape(3.dp) // --radius-input
    val pill = RoundedCornerShape(999.dp) // --radius-pill
}

/** 罫線の太さ（--rule-hair）。 */
internal val RuleHair = 1.dp
