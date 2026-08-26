package com.aki.tasktimer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.aki.tasktimer.data.model.Tag
import com.aki.tasktimer.ui.theme.Paper2
import com.aki.tasktimer.ui.theme.Radius
import com.aki.tasktimer.ui.theme.RuleHair
import com.aki.tasktimer.ui.theme.Space
import com.aki.tasktimer.ui.theme.color

/**
 * タグのチップ（mockups/wireframe.html の .chip）。
 *
 * 面をタグ色で塗りつぶさず、地に 14% だけ溶かして文字と点で色を伝える。
 * べた塗りにすると彩度 0.10 の面が画面のなかで一番強い要素になってしまい、
 * 主役であるはずの経過時間より目立つ。
 */
@Composable
internal fun TagChip(tag: Tag, modifier: Modifier = Modifier) {
    val tagColor = tag.color

    Row(
        modifier = modifier
            .clip(Radius.pill)
            // CSS 側は color-mix(in oklch, ...) だが、地がほぼ無彩色で混ぜる量も 14% なので
            // sRGB のアルファ合成との差は目視できない。Compose の標準的な書き方を採る。
            .background(tagColor.copy(alpha = 0.14f).compositeOver(Paper2))
            .padding(horizontal = Space.xs, vertical = Space.xxxs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xxs),
    ) {
        // 点を添えるのは、文字色だけだと 12px では色の判別がつきにくいため
        Box(
            Modifier
                .size(6.dp)
                .clip(Radius.pill)
                .background(tagColor),
        )
        Text(
            text = tag.label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.02.em,
            color = tagColor,
        )
    }
}

/**
 * タグを選ばせるときの選択肢（Step 2 の新規タスク用）。
 *
 * 見た目をチップと同じにしているのは、ここで選んだ色がそのままホームの
 * チップとして出るのを、選ぶ前に分からせるため。
 * 押せるものなので、チップより余白を厚くして指で押せる高さを確保する。
 */
@Composable
internal fun TagOption(
    tag: Tag,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val tagColor = tag.color

    Row(
        modifier = modifier
            .clip(Radius.pill)
            .background(tagColor.copy(alpha = 0.14f).compositeOver(Paper2))
            .border(RuleHair, tagColor.copy(alpha = 0.5f), Radius.pill)
            .clickable(onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(Space.xs),
    ) {
        Box(
            Modifier
                .size(8.dp)
                .clip(Radius.pill)
                .background(tagColor),
        )
        Text(
            text = tag.label,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            color = tagColor,
        )
    }
}
