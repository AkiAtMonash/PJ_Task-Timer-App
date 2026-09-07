package com.aki.tasktimer.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aki.tasktimer.ui.theme.OnInkMuted

/**
 * ゴールの標識。ワイヤーフレームは SVG の的（円＋十字）を使っているので、
 * 絵文字にせず Canvas で同じ形を描く（絵文字はワイヤーフレーム gate 30 で禁じている）。
 * ホームと超過画面で共用する。
 */
@Composable
fun GoalRow(
    goal: String,
    modifier: Modifier = Modifier,
    textColor: Color = OnInkMuted,
    fontSize: androidx.compose.ui.unit.TextUnit = 14.sp,
) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        GoalMarker(modifier = Modifier.size(12.dp), color = textColor)
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = goal,
            color = textColor,
            fontSize = fontSize,
        )
    }
}

@Composable
fun GoalMarker(modifier: Modifier = Modifier, color: Color = OnInkMuted) {
    Canvas(modifier = modifier) {
        val stroke = 1.2.dp.toPx()
        val c = Offset(size.width / 2f, size.height / 2f)
        val outer = minOf(size.width, size.height) / 2f - stroke
        val inner = outer * 0.45f
        drawCircle(color = color, radius = outer, center = c, style = Stroke(stroke))
        drawLine(color, Offset(c.x, c.y - outer), Offset(c.x, c.y - inner), strokeWidth = stroke)
        drawLine(color, Offset(c.x, c.y + inner), Offset(c.x, c.y + outer), strokeWidth = stroke)
        drawLine(color, Offset(c.x - outer, c.y), Offset(c.x - inner, c.y), strokeWidth = stroke)
        drawLine(color, Offset(c.x + inner, c.y), Offset(c.x + outer, c.y), strokeWidth = stroke)
    }
}
