package com.aki.tasktimer.ui.theme

import androidx.compose.ui.graphics.Color

// ダークモード専用のパレット（docs/01_SPEC.md 7 章：テーマ切り替えは作らない）。
// 地を真っ黒にしていないのは、進捗バーの「未消化部分」を地より暗く置けるようにするため。

internal val Ink = Color(0xFF101114)
internal val InkElevated = Color(0xFF1A1C20)
internal val InkVariant = Color(0xFF24272D)

internal val OnInk = Color(0xFFE6E8EC)
internal val OnInkMuted = Color(0xFF9AA0AA)

internal val Accent = Color(0xFF7FB4FF)
internal val OnAccent = Color(0xFF08213F)

// 超過表示（経過時間・超過率）に使う警告色。赤よりオレンジ寄りにしているのは、
// 「失敗」ではなく「計測データ」として見せたいから（docs/01_SPEC.md 1 章）。
internal val Warn = Color(0xFFFF8A6B)
internal val OnWarn = Color(0xFF3A1206)

internal val Outline = Color(0xFF3A3E46)
