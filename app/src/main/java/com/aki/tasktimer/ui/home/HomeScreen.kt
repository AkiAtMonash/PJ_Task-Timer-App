package com.aki.tasktimer.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.aki.tasktimer.TaskTimerApp
import com.aki.tasktimer.data.model.Session
import com.aki.tasktimer.domain.elapsedMinutes
import com.aki.tasktimer.ui.component.PrimaryButton
import com.aki.tasktimer.ui.component.TagChip
import com.aki.tasktimer.ui.component.ratingSymbol
import com.aki.tasktimer.ui.theme.InkVariant
import com.aki.tasktimer.ui.theme.OnInk
import com.aki.tasktimer.ui.theme.OnInkMuted
import com.aki.tasktimer.ui.theme.Warn
import com.aki.tasktimer.ui.util.formatElapsed

/**
 * ホーム画面。進行中／未計測の 2 状態（docs/01_SPEC.md 4.1 / 4.2）。
 * ViewModel は画面スコープで生成し、バックスタックに残る限り生きる。
 */
@Composable
fun HomeScreen(
    onStart: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val app = LocalContext.current.applicationContext as TaskTimerApp
    val viewModel: HomeViewModel = viewModel { HomeViewModel(app.container.sessionRepository) }
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            // 設定（Notion 連携）への入口。タイマー表示の邪魔にならないよう右上に小さく置く。
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.End,
            ) {
                IconButton(onClick = onOpenSettings) {
                    Icon(
                        imageVector = Icons.Outlined.Settings,
                        contentDescription = "設定",
                        tint = OnInkMuted,
                    )
                }
            }
        },
    ) { innerPadding ->
        val session = uiState.runningSession
        if (session != null) {
            RunningHome(
                session = session,
                now = uiState.now,
                onSwitch = onStart,
                modifier = Modifier.padding(innerPadding),
            )
        } else {
            IdleHome(
                lastFinished = uiState.lastFinishedSession,
                onStart = onStart,
                modifier = Modifier.padding(innerPadding),
            )
        }
    }
}

@Composable
private fun RunningHome(
    session: Session,
    now: Long,
    onSwitch: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val elapsed = elapsedMinutes(session.startedAt, now)
    val planned = session.totalPlannedMinutes
    val remaining = planned - elapsed
    val isOver = remaining < 0

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
    ) {
        TagChip(tag = session.tag)

        Text(
            text = session.name,
            color = OnInk,
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp),
        )

        if (session.goal.isNotBlank()) {
            GoalRow(goal = session.goal, modifier = Modifier.padding(top = 8.dp))
        }

        Text(
            text = formatElapsed(now - session.startedAt),
            color = if (isOver) Warn else OnInk,
            fontFamily = FontFamily.Monospace,
            fontSize = 48.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 24.dp),
        )

        Text(
            text = if (isOver) {
                "予定 ${planned}分 / ${-remaining}分 超過"
            } else {
                "予定 ${planned}分 / 残り ${remaining}分"
            },
            color = if (isOver) Warn else OnInkMuted,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .padding(top = 2.dp, bottom = 16.dp),
        )

        ProgressBar(
            elapsed = elapsed,
            planned = planned,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.weight(1f))

        // 「中断（記録して停止）」は置かない。記録が止まっている瞬間を作らないのがこのアプリの前提で、
        // 「今のを終えて別のことを始める」は切り替えそのもの（docs/01_SPEC.md 4.1）。
        PrimaryButton(text = "タスクを切り替える", onClick = onSwitch)
    }
}

/**
 * ゴールの標識。ワイヤーフレームは SVG の的（円＋十字）を使っているので、
 * 絵文字にせず Canvas で同じ形を描く（絵文字はワイヤーフレーム gate 30 で禁じている）。
 */
@Composable
private fun GoalRow(goal: String, modifier: Modifier = Modifier) {
    Row(modifier = modifier, verticalAlignment = Alignment.CenterVertically) {
        GoalMarker(modifier = Modifier.size(12.dp))
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = goal,
            color = OnInkMuted,
            fontSize = 14.sp,
        )
    }
}

@Composable
private fun GoalMarker(modifier: Modifier = Modifier, color: Color = OnInkMuted) {
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

/**
 * 進捗バー。ワイヤーフレームの 53%（予定内）／ 95-5（超過）を再現する。
 * total = max(elapsed, planned) とし、中立部分と超過部分を合計 1.0 になるよう按分する。
 */
@Composable
private fun ProgressBar(elapsed: Int, planned: Int, modifier: Modifier = Modifier) {
    val total = maxOf(elapsed, planned)
    val neutral = if (total == 0) 0f else minOf(elapsed, planned).toFloat() / total
    val over = if (total == 0) 0f else maxOf(0, elapsed - planned).toFloat() / total

    Box(
        modifier = modifier
            .height(4.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(InkVariant),
    ) {
        Row(modifier = Modifier.fillMaxHeight()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(neutral)
                    .fillMaxHeight()
                    .background(OnInk),
            )
            if (over > 0f) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth(over)
                        .fillMaxHeight()
                        .background(Warn),
                )
            }
        }
    }
}

@Composable
private fun IdleHome(
    lastFinished: Session?,
    onStart: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.weight(1f))

        Text(
            text = "計測していません",
            color = OnInkMuted,
            fontSize = 16.sp,
        )
        Spacer(modifier = Modifier.height(24.dp))
        PrimaryButton(
            text = "タスクを開始する",
            onClick = onStart,
            modifier = Modifier.fillMaxWidth(),
        )

        Spacer(modifier = Modifier.weight(1f))

        if (lastFinished != null) {
            LastRecordCard(lastFinished = lastFinished)
        }
    }
}

@Composable
private fun LastRecordCard(lastFinished: Session, modifier: Modifier = Modifier) {
    // observeLastFinishedSession は DONE のみ返すので endedAt/rating は必ず非 null。
    val durationMinutes = lastFinished.endedAt?.let { elapsedMinutes(lastFinished.startedAt, it) } ?: 0
    val symbol = lastFinished.rating?.let { ratingSymbol(it) } ?: ""

    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = "最後の記録",
            color = OnInkMuted,
            fontSize = 12.sp,
        )
        Spacer(modifier = Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = lastFinished.name,
                color = OnInk,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "${durationMinutes}分",
                color = OnInkMuted,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = symbol,
                color = OnInk,
                fontSize = 14.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}
