package com.aki.tasktimer.ui.theme

import androidx.compose.ui.graphics.Color
import com.aki.tasktimer.data.model.Tag

/**
 * タグの色。docs/01_SPEC.md 3.4 で色相が Notion と同一に固定されているため、
 * 色相は変えず、ワイヤーフレーム（mockups/wireframe.html）が揃えた chroma 0.10 の
 * OKLCH を sRGB に変換してハードコードする。
 *
 * Tag enum のコメント（「色は ui/theme 側で対応づける（Phase 2）」）の回収。
 * チップの背景は同色を低透明度で混ぜた面を TagChip 側で作る。
 */
val Tag.color: Color
    get() = when (this) {
        Tag.SLEEP -> Color(0xFFC0A1E4)        // 紫
        Tag.UNI_STUDY -> Color(0xFFDFCA7D)    // 黄
        Tag.JOB_HUNTING -> Color(0xFFE3928B)  // 赤
        Tag.HOBBY -> Color(0xFF85B4F0)        // 青
        Tag.CHORE -> Color(0xFFE9A679)        // オレンジ
        Tag.FOR_MYSELF -> Color(0xFF88CA95)   // 緑
    }
