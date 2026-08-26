package com.aki.tasktimer.ui.component

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.ui.theme.Accent
import com.aki.tasktimer.ui.theme.InkVariant
import com.aki.tasktimer.ui.theme.OnInk
import com.aki.tasktimer.ui.theme.OnInkMuted
import com.aki.tasktimer.ui.theme.Outline

/**
 * 評価の記号。**選ばせるときと振り返るときで別の記号を使う**（Aki の決定）。
 *
 * 選択ボタンは絵文字。色と形があるぶん、押す前に「どれを押すか」が一瞬で決まる。
 * 一覧はモノクロの記号。履歴を上から眺めるときに絵文字が並ぶと視線が奪われて、
 * 肝心の時間の数字が読めなくなる。
 */

/** 選択ボタン用（Step 1）。 */
fun ratingChoiceSymbol(rating: Rating): String = when (rating) {
    Rating.GOOD -> "✅"
    Rating.NORMAL -> "🫳"
    Rating.BAD -> "❌"
}

/** 一覧・振り返り用（ホームの「最後の記録」、Phase 6 の履歴）。 */
fun ratingSymbol(rating: Rating): String = when (rating) {
    Rating.GOOD -> "◯"
    Rating.NORMAL -> "△"
    Rating.BAD -> "✕"
}

/** 評価の日本語ラベル。 */
fun ratingLabel(rating: Rating): String = when (rating) {
    Rating.GOOD -> "良かった"
    Rating.NORMAL -> "普通"
    Rating.BAD -> "悪かった"
}

private data class RatingOption(val rating: Rating, val note: String)

private val RATING_OPTIONS = listOf(
    RatingOption(Rating.GOOD, "理由を入力"),
    RatingOption(Rating.NORMAL, "デフォルト"),
    RatingOption(Rating.BAD, "理由を入力"),
)

/**
 * Step 1 の ✅/🫳/❌ 3 択（docs/01_SPEC.md 4.3 Step 1）。
 * デフォルトは △（普通）。選択中は操作色の罫線が付く。
 */
@Composable
fun RatingSelector(
    selected: Rating,
    onSelect: (Rating) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        RATING_OPTIONS.forEach { option ->
            RatingRow(
                option = option,
                isSelected = selected == option.rating,
                onClick = { onSelect(option.rating) },
            )
        }
    }
}

@Composable
private fun RatingRow(
    option: RatingOption,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(3.dp)
    val border = if (isSelected) BorderStroke(1.dp, Accent) else BorderStroke(1.dp, Outline)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(InkVariant)
            .border(border, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            // 一覧の ◯△✕ ではなく絵文字。ここは「選ぶ」場面なので形と色があるほうが速い
            text = ratingChoiceSymbol(option.rating),
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.width(12.dp))
        Text(
            text = ratingLabel(option.rating),
            color = OnInk,
            fontSize = 14.sp,
        )
        Spacer(modifier = Modifier.weight(1f))
        Text(
            text = option.note,
            color = OnInkMuted,
            fontSize = 12.sp,
        )
    }
}
