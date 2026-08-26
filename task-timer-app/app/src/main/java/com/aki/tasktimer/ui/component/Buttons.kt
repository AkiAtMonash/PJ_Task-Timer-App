package com.aki.tasktimer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import com.aki.tasktimer.ui.theme.Ink
import com.aki.tasktimer.ui.theme.Muted
import com.aki.tasktimer.ui.theme.Neutral
import com.aki.tasktimer.ui.theme.Paper
import com.aki.tasktimer.ui.theme.Paper3
import com.aki.tasktimer.ui.theme.Radius
import com.aki.tasktimer.ui.theme.Rule
import com.aki.tasktimer.ui.theme.Rule2
import com.aki.tasktimer.ui.theme.RuleHair
import com.aki.tasktimer.ui.theme.Space

/**
 * 主ボタン（mockups/wireframe.html の .btn.pri）。
 *
 * **アクセント色ではなく明るい中立色**で塗る。アクセントは「いま選ばれている」を
 * 表す色なので、常に置かれている主ボタンに使うと選択状態の合図が薄まる。
 * 暗い画面で一番明るい面＝押すところ、という単純な規則にしている。
 *
 * 無効時に透明度を下げないのは、暗地では文字が読めなくなるため。
 * 面と文字をそれぞれ別の色に落とす。
 */
@Composable
internal fun PrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        color = if (enabled) Paper else Muted,
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.input)
            .background(if (enabled) Ink else Paper3)
            .then(if (enabled) Modifier else Modifier.border(RuleHair, Rule, Radius.input))
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
    )
}

/** 副ボタン（.btn.sec）。枠だけ。主ボタンと並べても取り違えない強さにする。 */
@Composable
internal fun SecondaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        textAlign = TextAlign.Center,
        color = if (enabled) Neutral else Muted,
        modifier = modifier
            .fillMaxWidth()
            .clip(Radius.input)
            .background(Color.Transparent)
            .border(RuleHair, if (enabled) Rule2 else Rule, Radius.input)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = Space.md, vertical = Space.sm),
    )
}
