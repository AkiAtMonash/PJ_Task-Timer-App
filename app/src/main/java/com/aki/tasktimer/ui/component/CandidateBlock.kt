package com.aki.tasktimer.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aki.tasktimer.ui.theme.InkVariant
import com.aki.tasktimer.ui.theme.OnInk

/**
 * 「次のタスク」画面の候補 1 個ぶんのブロック（2026-09-10、Aki の要望）。
 *
 * 元は 1 行のピルだったが、名前の長さぶんだけ横に伸びるせいで行の切れ目がガタガタになり、
 * 隣の色付きの縁どうしがくっついて読みにくかった。幅に上限を設けて長い名前は折り返し、
 * 余白を広げて「塊」に見えるようにしている。
 *
 * [accent] は前回このタスクで使ったタグの色。どれがどのタグか一目で分かるように縁を塗る。
 */
@Composable
fun CandidateBlock(
    text: String,
    accent: Color,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(6.dp)
    // 縁は常にタグ色なので、選択中は地を塗って太くすることで区別する。
    val border = if (selected) BorderStroke(2.dp, accent) else BorderStroke(1.5.dp, accent)

    Box(
        modifier = modifier
            .clip(shape)
            .background(if (selected) accent.copy(alpha = 0.16f) else InkVariant)
            .border(border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
    ) {
        Text(
            text = text,
            color = if (selected) accent else OnInk,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            // 3 行を超える名前は打ち切る。見積もり（CandidateLayoutSpec.maxLines）と揃えること。
            maxLines = 3,
            overflow = TextOverflow.Ellipsis,
        )
    }
}
