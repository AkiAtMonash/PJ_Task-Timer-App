package com.aki.tasktimer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aki.tasktimer.ui.theme.InkVariant
import com.aki.tasktimer.ui.theme.OnInk

/** 上下スワイプを「固定する／外す」とみなす最低の移動量。これ未満はタップの指ブレとして無視する。 */
private val SWIPE_THRESHOLD = 32.dp

/**
 * 「次のタスク」画面の候補 1 個ぶん（2026-09-10、Aki の要望）。
 *
 * 名前の長さぶん伸びる札を並べると、行の切れ目がガタつき、色付きの縁が散って選べなかった。
 * 全部同じ大きさ・1 行（入りきらない名前は末尾を省略）にして 2 列に揃え、
 * タグ色は縁ではなく左端の帯だけに置く。輪郭が揃うので色が手がかりとして読める。
 *
 * 上にスワイプで固定、下にスワイプで固定を外す（Aki の発案）。長押しより「上に持っていく」感覚に合う。
 * そのぶん候補の上ではフォームの縦スクロールが効かなくなるが、キーボードを出している間しか関係しない。
 *
 * @param height 高さ。候補の数を決める計算（CandidateGridSpec.blockHeightDp）と揃えること
 */
@Composable
fun CandidateBlock(
    text: String,
    accent: Color,
    onClick: () -> Unit,
    /** 上にスワイプしたとき。null ならその向きのスワイプでは何もしない（震えもしない） */
    onSwipeUp: (() -> Unit)?,
    onSwipeDown: (() -> Unit)?,
    height: Dp,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
) {
    val haptic = LocalHapticFeedback.current
    // pointerInput は key が変わらない限り作り直されないので、最新のコールバックを参照させる。
    val latestSwipeUp by rememberUpdatedState(onSwipeUp)
    val latestSwipeDown by rememberUpdatedState(onSwipeDown)

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(4.dp))
            .background(if (selected) accent.copy(alpha = 0.16f) else InkVariant)
            .clickable(onClick = onClick)
            .pointerInput(Unit) {
                val threshold = SWIPE_THRESHOLD.toPx()
                var total = 0f
                detectVerticalDragGestures(
                    onDragStart = { total = 0f },
                    onVerticalDrag = { change, dragAmount ->
                        change.consume()
                        total += dragAmount
                    },
                    onDragEnd = {
                        // 指を離した時点の移動量で判定する。途中で戻せば取り消せる。
                        when {
                            total <= -threshold -> latestSwipeUp?.let { action ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                action()
                            }
                            total >= threshold -> latestSwipeDown?.let { action ->
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                action()
                            }
                        }
                    },
                )
            },
    ) {
        // 選択中は帯を太くする。地の色だけだと暗い色のタグで選択が分かりにくいため。
        Box(
            modifier = Modifier
                .width(if (selected) 6.dp else 3.dp)
                .fillMaxHeight()
                .background(accent),
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = text,
            color = if (selected) accent else OnInk,
            fontSize = 14.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(end = 10.dp),
        )
    }
}
