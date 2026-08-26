package com.aki.tasktimer.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aki.tasktimer.ui.theme.Ink
import com.aki.tasktimer.ui.theme.InkVariant
import com.aki.tasktimer.ui.theme.OnInk
import com.aki.tasktimer.ui.theme.OnInkMuted
import com.aki.tasktimer.ui.theme.Outline

/**
 * 主ボタン（ワイヤーフレームの .btn.pri）。明るい地に暗い文字。
 * Material3 の primary（暗い地に明るい文字）とは逆なので自前で持つ。
 */
@Composable
fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TaskTimerButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        background = if (enabled) OnInk else InkVariant,
        contentColor = if (enabled) Ink else OnInkMuted,
        border = null,
    )
}

/**
 * 副ボタン（ワイヤーフレームの .btn.sec）。透明地に罫線。
 * 中断など、主操作と同列にしない操作に使う。
 */
@Composable
fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    TaskTimerButton(
        text = text,
        onClick = onClick,
        modifier = modifier,
        enabled = enabled,
        background = Color.Transparent,
        contentColor = if (enabled) OnInk else OnInkMuted,
        border = BorderStroke(1.dp, Outline),
    )
}

@Composable
private fun TaskTimerButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier,
    enabled: Boolean,
    background: Color,
    contentColor: Color,
    border: BorderStroke?,
) {
    val shape = RoundedCornerShape(3.dp)
    Box(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(background)
            .then(if (border != null) Modifier.border(border, shape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}
