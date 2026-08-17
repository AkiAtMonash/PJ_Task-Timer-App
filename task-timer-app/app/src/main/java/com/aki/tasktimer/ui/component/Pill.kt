package com.aki.tasktimer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.FlowRowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import com.aki.tasktimer.ui.theme.Accent
import com.aki.tasktimer.ui.theme.Ink
import com.aki.tasktimer.ui.theme.Muted
import com.aki.tasktimer.ui.theme.Paper2
import com.aki.tasktimer.ui.theme.Paper3
import com.aki.tasktimer.ui.theme.Radius
import com.aki.tasktimer.ui.theme.Rule
import com.aki.tasktimer.ui.theme.RuleHair
import com.aki.tasktimer.ui.theme.Space

/**
 * 選択肢のピル（mockups/wireframe.html の .pill）。
 * タスク名プリセットにも予定時間の数字にも同じものを使う。
 *
 * 選択中はアクセント色。ここがタグ 6 色から離れた色相 200 でないと、
 * 「選ばれている」と「Hobby タグ」が同じ青に見えてしまう。
 *
 * @param mono 数字を等幅で出したいとき（予定時間のピル）
 * @param muted 「＋ 新しいタスク…」のように主役ではない選択肢
 */
@Composable
internal fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    mono: Boolean = false,
    muted: Boolean = false,
) {
    val background = if (selected) {
        // CSS は color-mix(in oklch, accent 16%, paper-2)。地がほぼ無彩色で混ぜる量も
        // 少ないので、sRGB のアルファ合成との差は目視できない
        Accent.copy(alpha = 0.16f).compositeOver(Paper2)
    } else {
        Paper3
    }
    val contentColor = when {
        selected -> Accent
        muted -> Muted
        else -> Ink
    }

    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        fontFamily = if (mono) FontFamily.Monospace else null,
        fontWeight = if (selected) FontWeight.SemiBold else null,
        color = contentColor,
        textAlign = TextAlign.Center,
        // 長いタスク名でピルが 2 行になると、8 個がスクロールなしで収まらなくなる
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
        modifier = modifier
            .clip(Radius.input)
            .background(background)
            .border(RuleHair, if (selected) Accent else Rule, Radius.input)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.sm, vertical = Space.xs),
    )
}

/**
 * ピルを並べる面（mockups/wireframe.html の .grid）。
 *
 * **スクロールさせないこと**が仕様（docs/01_SPEC.md 4.3 Step 2）。
 * 行あたりの個数は FlowRow が中身の長さから決めるので、「ES執筆」のような
 * 短い名前なら 3 個、長い名前なら 2 個で自然に折り返す。
 *
 * 中で `Modifier.weight(1f)` を付けると、その行のピルが均等幅に伸びて右端が揃う。
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun PillGrid(
    modifier: Modifier = Modifier,
    content: @Composable FlowRowScope.() -> Unit,
) {
    FlowRow(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
        verticalArrangement = Arrangement.spacedBy(Space.xs),
        content = content,
    )
}
