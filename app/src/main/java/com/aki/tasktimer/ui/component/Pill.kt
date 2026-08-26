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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aki.tasktimer.ui.theme.Accent
import com.aki.tasktimer.ui.theme.InkVariant
import com.aki.tasktimer.ui.theme.OnInk
import com.aki.tasktimer.ui.theme.Outline

/**
 * 選択式のピル（ワイヤーフレームの .pill）。
 * タスク名プリセット（Step 2）と予定時間プリセット（Step 4）で共用する。
 * 選択中は操作色（Accent）の地と罫線になる。
 */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val shape = RoundedCornerShape(3.dp)
    val background = if (selected) Accent.copy(alpha = 0.16f) else InkVariant
    val border = if (selected) BorderStroke(1.dp, Accent) else BorderStroke(1.dp, Outline)
    val contentColor = if (selected) Accent else OnInk

    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            color = contentColor,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
