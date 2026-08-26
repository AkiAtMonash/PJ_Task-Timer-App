package com.aki.tasktimer.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// mockups/tokens.css の型階層（比 1.25・16px 基準）をそのまま写したもの。
//
// フォントは端末標準のまま。モックアップは IBM Plex を指定しているが、
// 日本語ウェイトを同梱すると 1 つで 5MB 近くあり、個人アプリの APK には重すぎる。
// 数字だけ等幅にすれば目的（毎秒書き換わっても桁がガタつかない）は達成できる。

/**
 * 数字が横に揺れないようにするための共通設定。
 *
 * 経過時間は毎秒書き換わるので、字幅が可変だと 1 秒ごとに表示全体が動いて
 * 非常に読みづらくなる。等幅フォント＋ tabular figures の二重掛けにしている。
 */
private val TabularMono = TextStyle(
    fontFamily = FontFamily.Monospace,
    fontFeatureSettings = "tnum",
)

/** 本文側でも数字を含む行（「予定 45分 / 残り 21分」など）はここを使う。 */
private val TabularBody = TextStyle(fontFeatureSettings = "tnum")

internal val TaskTimerTypography = Typography(
    // 49px — 経過時間。画面の主役なのでここだけ突出させる
    displayLarge = TabularMono.copy(
        fontSize = 49.sp,
        lineHeight = 54.sp,
        fontWeight = FontWeight.Medium,
    ),
    // 39px — 超過画面の合計所要時間（Phase 4 で使う）
    displayMedium = TabularMono.copy(
        fontSize = 39.sp,
        lineHeight = 44.sp,
        fontWeight = FontWeight.Medium,
    ),
    // 31px / 25px — Phase 2 では出番がない。階層の抜けを埋めるためだけに置いている
    headlineLarge = TextStyle(
        fontSize = 31.sp,
        lineHeight = 38.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    headlineMedium = TextStyle(fontSize = 25.sp, lineHeight = 32.sp),
    // 20px — タスク名。経過時間（49px）との差を大きく取って、主役がどちらか迷わせない
    titleLarge = TextStyle(
        fontSize = 20.sp,
        lineHeight = 28.sp,
        fontWeight = FontWeight.SemiBold,
    ),
    // 16px — 本文
    bodyLarge = TabularBody.copy(fontSize = 16.sp, lineHeight = 24.sp),
    // 14px — 本文の下限
    bodyMedium = TabularBody.copy(fontSize = 14.sp, lineHeight = 20.sp),
    // 14px — ボタンラベル
    labelLarge = TextStyle(
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontWeight = FontWeight.Medium,
    ),
    // 12px
    labelMedium = TabularBody.copy(fontSize = 12.sp, lineHeight = 16.sp),
    // 10px — ラベル専用。本文には使わない
    labelSmall = TextStyle(fontSize = 10.sp, lineHeight = 14.sp),
)
