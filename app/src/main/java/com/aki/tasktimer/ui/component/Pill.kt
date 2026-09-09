package com.aki.tasktimer.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aki.tasktimer.ui.theme.Accent
import com.aki.tasktimer.ui.theme.InkVariant
import com.aki.tasktimer.ui.theme.OnInk
import com.aki.tasktimer.ui.theme.OnInkMuted
import com.aki.tasktimer.ui.theme.Outline

/**
 * 選択式のピル（ワイヤーフレームの .pill）。
 * タスク名プリセットと予定時間プリセットで共用する。
 * 選択中は操作色（Accent）の地と罫線になる。
 *
 * [accent] を渡すと、その色で縁を塗る。タスク名の候補にタグ色を渡して、
 * 一覧のどれがどのタグのタスクか一目で分かるようにするため（2026-09-07 Aki の要望）。
 * 予定時間のようにタグを持たないものは渡さない。
 */
@Composable
fun Pill(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    accent: Color? = null,
    subText: String? = null,
) {
    val shape = RoundedCornerShape(3.dp)
    val accentColor = accent ?: Accent
    val background = if (selected) accentColor.copy(alpha = 0.16f) else InkVariant
    // 縁：タグ色があれば常にその色。無ければ従来どおり（選択中だけ操作色）。
    // 縁が全部色付きになると選択中が分かりにくいので、選択中はさらに太くして区別する。
    val border = when {
        selected -> BorderStroke(2.dp, accentColor)
        accent != null -> BorderStroke(1.5.dp, accent)
        else -> BorderStroke(1.dp, Outline)
    }
    val contentColor = if (selected) accentColor else OnInk

    Box(
        modifier = modifier
            .clip(shape)
            .background(background)
            .border(border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = text,
                color = contentColor,
                fontSize = 14.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            )
            // 押した結果どうなるかを小さく添える（超過画面の「計 57分」）。
            if (subText != null) {
                Text(
                    text = subText,
                    color = OnInkMuted,
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
