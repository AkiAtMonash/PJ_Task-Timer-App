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
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.ui.theme.Accent
import com.aki.tasktimer.ui.theme.InkVariant
import com.aki.tasktimer.ui.theme.OnInk
import com.aki.tasktimer.ui.theme.OnInkMuted
import com.aki.tasktimer.ui.theme.Outline

/** ◯/△/✕ の記号。履歴やホームの「最後の記録」でも使う。 */
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
 * Step 1 の ◯/△/✕ 3 択（docs/01_SPEC.md 4.3 Step 1）。
 * デフォルトは △（普通）。選択中は操作色の罫線が付く。
 */
@Composable
fun RatingSelector(
    selected: Rating,
    onSelect: (Rating) -> Unit,
    modifier: Modifier = Modifier,
    /**
     * 横に 3 つ並べて 1 行に収める。横画面用。
     * 縦に積むと 3 択だけで 150dp 使い、横画面では「次へ」が画面の外に出てしまう。
     */
    horizontal: Boolean = false,
) {
    if (horizontal) {
        Row(
            modifier = modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RATING_OPTIONS.forEach { option ->
                RatingTile(
                    option = option,
                    isSelected = selected == option.rating,
                    onClick = { onSelect(option.rating) },
                    modifier = Modifier.weight(1f),
                )
            }
        }
        return
    }
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

/**
 * 横並び用の 1 つ分。記号の下にラベルを置く。
 * 「理由を入力」の添え書きは入れない。すぐ下の入力欄のラベルが同じことを言っているため。
 */
@Composable
private fun RatingTile(
    option: RatingOption,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(3.dp)
    val border = if (isSelected) BorderStroke(1.dp, Accent) else BorderStroke(1.dp, Outline)
    Column(
        modifier = modifier
            .clip(shape)
            .background(InkVariant)
            .border(border, shape)
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = ratingSymbol(option.rating),
            color = OnInk,
            fontSize = 18.sp,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = ratingLabel(option.rating),
            color = OnInk,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 2.dp),
        )
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
            text = ratingSymbol(option.rating),
            color = OnInk,
            fontSize = 16.sp,
            fontFamily = FontFamily.Monospace,
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
