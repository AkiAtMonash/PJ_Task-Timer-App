package com.aki.tasktimer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable

// isSystemInDarkTheme() で分岐させない。端末がライトテーマでも
// このアプリだけは常に暗いままにする（docs/01_SPEC.md 7 章）。
// Dynamic Color も使わない。壁紙によって超過時の警告色が沈むと困るため。
private val TaskTimerColorScheme = darkColorScheme(
    primary = Accent,
    onPrimary = OnAccent,
    secondary = OnInkMuted,
    onSecondary = Ink,
    error = Warn,
    onError = OnWarn,
    background = Ink,
    onBackground = OnInk,
    surface = InkElevated,
    onSurface = OnInk,
    surfaceVariant = InkVariant,
    onSurfaceVariant = OnInkMuted,
    outline = Outline,
    outlineVariant = OutlineStrong,
)

@Composable
fun TaskTimerTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = TaskTimerColorScheme,
        content = content,
    )
}
