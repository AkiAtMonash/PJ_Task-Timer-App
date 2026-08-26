package com.aki.tasktimer.ui.theme

import androidx.compose.ui.graphics.Color

// mockups/tokens.css からの移植。値の出どころはあちらなので、色を変えるときは必ず両方直すこと。
// トークンは OKLCH だが Compose の Color は sRGB しか受け取らないので変換済みの値を置く。
// 元の oklch(...) 表記を各行に残してある。ブラウザも同じ変換とクランプをするので、
// モックアップを開いたときの見た目とこの値は一致する。
//
// ── 色の設計（tokens.css の意図） ────────────────────────────────
// タグ 6 色が Notion と同一に固定されていて（docs/01_SPEC.md 3.4）色相環が先約済み。
// アクセントと警報を「別の色相」で作ろうとすると必ずタグのどれかとぶつかる。
// そこで伝達路は **彩度** で分ける：
//
//   彩度 0.012 … 中立（地・文字・罫線）
//   彩度 0.10  … カテゴリ（タグ 6 色。TagColors.kt）
//   彩度 0.12  … 操作（色相 200。タグの色相 150 と 255 の間が最大の空きで、その真ん中）
//   彩度 0.20  … 警報（色相 32。タグの 25 と 55 に挟まれているが、
//                      画面内で唯一この彩度を使えるので混同しない）
//
// ※ 名前は Phase 0 のまま（Ink が地、OnInk が主テキスト）にしてある。
//   値だけトークンに合わせた。名前まで変えると全画面を書き換えることになるため。

internal val Ink = Color(0xFF060A0E) // oklch(14% 0.012 250) 基底面
internal val InkElevated = Color(0xFF0D1217) // oklch(18% 0.013 250) 板・カード
internal val InkVariant = Color(0xFF181E23) // oklch(23% 0.014 250) 入力欄・ピル

internal val OnInk = Color(0xFFEBEFF4) // oklch(95% 0.008 250) 主テキスト
internal val OnInkMuted = Color(0xFF9299A1) // oklch(68% 0.014 250) 補助テキスト

/** 進捗バーの消化済み部分。主テキストより一段落として、数字より前に出ないようにする。 */
internal val Neutral = Color(0xFFB8BEC5) // oklch(80% 0.012 250)

// ── 操作（唯一のアクセント・色相 200） ──────────────────────────
// Phase 0 の仮値は 0xFF7FB4FF（色相 260 付近）で、Hobby タグの 0xFF85B4F0 とほぼ同色だった。
// 予定時間ピルの「選択中」と Hobby タグのチップが同じ画面に並ぶので、見分けがつかなくなる。
// tokens.css が色相 200 を選んでいるのは、タグ 6 色から最も遠い空き色相だから。
internal val Accent = Color(0xFF3ACED6) // oklch(78% 0.12 200)
internal val OnAccent = Color(0xFF001112) // oklch(16% 0.03 200)

// ── 警報（画面内で唯一の高彩度・色相 32） ────────────────────────
// 赤ではなくオレンジ寄りなのは、超過を「失敗」ではなく「計測データ」として
// 見せたいから（docs/01_SPEC.md 1 章）。sRGB の色域外なのでクランプされた値。
internal val Warn = Color(0xFFFF6247) // oklch(70% 0.20 32)
internal val OnWarn = Color(0xFF190502) // oklch(15% 0.04 32)

internal val Outline = Color(0xFF2E3339) // oklch(32% 0.012 250) 罫線

/** 強めの境界。副ボタンの枠など、押せるものの輪郭に使う。 */
internal val OutlineStrong = Color(0xFF484E54) // oklch(42% 0.012 250)
