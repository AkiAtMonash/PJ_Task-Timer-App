package com.aki.tasktimer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import com.aki.tasktimer.domain.ProgressSplit
import com.aki.tasktimer.ui.theme.Alert
import com.aki.tasktimer.ui.theme.Ink
import com.aki.tasktimer.ui.theme.Neutral
import com.aki.tasktimer.ui.theme.Paper3
import com.aki.tasktimer.ui.theme.Radius

/**
 * 経過時間の巨大表示（mockups/wireframe.html の .elapsed）。このアプリの主役。
 *
 * 超過すると警報色になる。赤ではなくオレンジ寄りなのは、超過を「失敗」ではなく
 * 「計測データ」として見せたいから（docs/01_SPEC.md 1 章）。
 *
 * 字間を詰めているのは、49sp の等幅数字が既定の字間だと間延びして見えるため。
 */
@Composable
internal fun ElapsedTime(
    text: String,
    isOverrun: Boolean,
    modifier: Modifier = Modifier,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.displayLarge,
        letterSpacing = (-0.03).em,
        textAlign = TextAlign.Center,
        color = if (isOverrun) Alert else Ink,
        modifier = modifier.fillMaxWidth(),
    )
}

/**
 * 進捗バー（mockups/wireframe.html の .bar）。
 *
 * 予定内は中立色、超過分だけ警報色。**操作色（アクセント）は使わない。**
 * バーは「押せるもの」ではないので、押せるものと同じ色にしてはいけない。
 *
 * 超過後は [ProgressSplit] 側でバー全体が経過時間に取り直されるので、
 * ここは受け取った比率をそのまま並べるだけでよい。
 */
@Composable
internal fun ProgressBar(
    split: ProgressSplit,
    modifier: Modifier = Modifier,
) {
    val remainder = 1f - split.base - split.over

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(4.dp)
            .clip(Radius.pill)
            .background(Paper3),
    ) {
        // weight は 0 を受け付けないので、それぞれ出番があるときだけ置く
        if (split.base > 0f) {
            Box(
                Modifier
                    .weight(split.base)
                    .fillMaxHeight()
                    .background(Neutral),
            )
        }
        if (split.over > 0f) {
            Box(
                Modifier
                    .weight(split.over)
                    .fillMaxHeight()
                    .background(Alert),
            )
        }
        // 残りは地の色（Paper3）のまま見せる
        if (remainder > 0f) {
            Spacer(Modifier.weight(remainder))
        }
    }
}
