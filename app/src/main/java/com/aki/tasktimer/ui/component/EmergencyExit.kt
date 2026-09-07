package com.aki.tasktimer.ui.component

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.aki.tasktimer.ui.theme.Ink
import com.aki.tasktimer.ui.theme.OnInk
import com.aki.tasktimer.ui.theme.OnInkMuted
import com.aki.tasktimer.ui.theme.Warn

/** 非常口の長押し時間。バグで端末が使えなくなったとき専用なので、うっかり押せない長さにする。 */
const val EMERGENCY_HOLD_MILLIS = 30_000L

/**
 * 非常口（docs/01_SPEC.md 4.4-7）。画面右上の角を 30 秒押し続けると [onTriggered]。
 *
 * 押している間は小さな輪がじわじわ埋まっていく。指を離すとリセット。
 * 超過画面と覆いの両方に置く。**必ず先に作り、ブロックを有効にする前にテストする**（CLAUDE.md Phase 5）。
 */
@Composable
fun EmergencyExitHotspot(
    onTriggered: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var pressed by remember { mutableStateOf(false) }
    var progress by remember { mutableFloatStateOf(0f) }

    LaunchedEffect(pressed) {
        if (!pressed) {
            progress = 0f
            return@LaunchedEffect
        }
        val startedAt = withFrameMillis { it }
        while (pressed) {
            val elapsed = withFrameMillis { it } - startedAt
            progress = (elapsed.toFloat() / EMERGENCY_HOLD_MILLIS).coerceIn(0f, 1f)
            if (elapsed >= EMERGENCY_HOLD_MILLIS) {
                pressed = false
                onTriggered()
                break
            }
        }
    }

    Box(
        modifier = modifier
            .size(72.dp)
            .pointerInput(Unit) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        try {
                            awaitRelease()
                        } finally {
                            pressed = false
                        }
                    },
                )
            },
        contentAlignment = Alignment.Center,
    ) {
        if (pressed) {
            Canvas(modifier = Modifier.size(28.dp)) {
                val stroke = Stroke(width = 3.dp.toPx())
                drawArc(
                    color = OnInkMuted.copy(alpha = 0.3f),
                    startAngle = -90f,
                    sweepAngle = 360f,
                    useCenter = false,
                    size = Size(size.width, size.height),
                    style = stroke,
                )
                drawArc(
                    color = Warn,
                    startAngle = -90f,
                    sweepAngle = 360f * progress,
                    useCenter = false,
                    size = Size(size.width, size.height),
                    style = stroke,
                )
            }
        }
    }
}

/**
 * 非常口の確認。Dialog を使わず同じ画面の上に描く。
 * 覆いはサービスの窓なので、Activity が要る Dialog は開けない。
 */
@Composable
fun EmergencyExitConfirm(
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(Ink.copy(alpha = 0.96f))
            .clickable(enabled = false) {},
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("強制的に閉じますか？", color = OnInk, fontSize = 18.sp, fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "バグで操作できなくなったとき用の非常口です。\nタスクは進行中のまま残り、次に「切り替える」を押すまでアプリは何もしません。",
                color = OnInkMuted,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(24.dp))
            PrimaryButton(text = "閉じる", onClick = onConfirm)
            Spacer(modifier = Modifier.height(8.dp))
            SecondaryButton(text = "やめる", onClick = onCancel)
        }
    }
}
