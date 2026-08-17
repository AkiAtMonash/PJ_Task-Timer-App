package com.aki.tasktimer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// isSystemInDarkTheme() で分岐させない。端末がライトテーマでも
// このアプリだけは常に暗いままにする（docs/01_SPEC.md 7 章）。
// Dynamic Color も使わない。壁紙によって超過時の警告色が沈むと困るため。
private val TaskTimerColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = AccentInk,
    secondary = Neutral,
    onSecondary = Paper,
    // error は Material のダイアログやテキスト欄が勝手に使うので、警報色を割り当てておく。
    // ただし超過表示で Alert を使うときは colorScheme 経由ではなく直接指定する
    // （「エラー」ではなく「計測データ」なので、意味の通り道を分けておきたい）。
    error = Alert,
    onError = AlertInk,
    background = Paper,
    onBackground = Ink,
    surface = Paper2,
    onSurface = Ink,
    surfaceVariant = Paper3,
    onSurfaceVariant = Muted,
    outline = Rule,
    outlineVariant = Rule2,
)

@Composable
fun TaskTimerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TaskTimerColorScheme,
        typography = TaskTimerTypography,
        content = content,
    )
}
