package com.aki.tasktimer.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import com.aki.tasktimer.ui.theme.Accent
import com.aki.tasktimer.ui.theme.Ink
import com.aki.tasktimer.ui.theme.Muted
import com.aki.tasktimer.ui.theme.Paper
import com.aki.tasktimer.ui.theme.Radius
import com.aki.tasktimer.ui.theme.Rule
import com.aki.tasktimer.ui.theme.RuleHair

/**
 * 文字入力欄（mockups/wireframe.html の .field）。
 *
 * Material の既定色はライトテーマ前提で、暗地だと枠が浮いて見える。
 * トークンの色に差し替えたものをここに 1 つだけ用意して、画面ごとに書かない。
 */
@Composable
internal fun InputField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    singleLine: Boolean = true,
    numeric: Boolean = false,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        // ここで fillMaxWidth を足さない。予定時間の自由入力だけは幅を絞りたいので、
        // 幅の決定は呼ぶ側に任せる
        modifier = modifier,
        textStyle = MaterialTheme.typography.bodyMedium,
        placeholder = if (placeholder.isEmpty()) {
            null
        } else {
            {
                Text(
                    text = placeholder,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Muted,
                )
            }
        },
        singleLine = singleLine,
        keyboardOptions = KeyboardOptions(
            keyboardType = if (numeric) KeyboardType.Number else KeyboardType.Text,
        ),
        shape = Radius.input,
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Ink,
            unfocusedTextColor = Ink,
            focusedContainerColor = Paper,
            unfocusedContainerColor = Paper,
            cursorColor = Accent,
            focusedBorderColor = Accent,
            unfocusedBorderColor = Rule,
            focusedPlaceholderColor = Muted,
            unfocusedPlaceholderColor = Muted,
        ),
    )
}

/** 質問の見出し（.q）。1 画面に 1 つ、何を聞かれているかを短く置く。 */
@Composable
internal fun QuestionHeading(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = Ink,
        modifier = modifier,
    )
}

/** 面を区切る罫線（.rule）。 */
@Composable
internal fun HairRule(modifier: Modifier = Modifier) {
    Spacer(
        modifier
            .fillMaxWidth()
            .height(RuleHair)
            .background(Rule),
    )
}
