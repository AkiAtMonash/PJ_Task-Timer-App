package com.aki.tasktimer.ui.theme

import androidx.compose.ui.graphics.Color
import com.aki.tasktimer.data.model.Tag

// mockups/tokens.css からの移植。値の出どころはあちらなので、色を変えるときは必ず両方直すこと
// （docs/01_SPEC.md 4 章：画面イメージはモックアップを見て確認する、と決めてある）。
//
// トークンは OKLCH で書かれているが Compose の Color は sRGB しか受け取らないので変換済みの値を置く。
// 元の oklch(...) 表記を各行に残してある。ブラウザも同じ変換と色域クランプをするので、
// モックアップを開いたときの見た目とこの値は一致する。
//
// ── 色の設計（tokens.css の意図をここに引き写す） ──────────────────
// タグ 6 色が Notion と同一に固定されていて（docs/01_SPEC.md 3.4）色相環が先約済み。
// アクセントと警報を「別の色相」で作ろうとすると必ずタグのどれかとぶつかる。
// そこで伝達路は **彩度** で分ける：
//
//   彩度 0.012 … 中立（地・文字・罫線）
//   彩度 0.10  … カテゴリ（タグ 6 色。色相は仕様固定・彩度だけ揃える）
//   彩度 0.12  … 操作（色相 200。タグの色相 150 と 255 の間が最大の空きで、その真ん中）
//   彩度 0.20  … 警報（色相 32。タグの 25 と 55 に挟まれているが、
//                      画面内で唯一この彩度を使えるので混同しない）
//
// ダークモードのみ。ライトテーマは作らない（CLAUDE.md / docs/01_SPEC.md 7 章）。

// ── 中立（色相 250） ──────────────────────────────────────────
internal val Paper = Color(0xFF060A0E) // oklch(14% 0.012 250) 基底面
internal val Paper2 = Color(0xFF0D1217) // oklch(18% 0.013 250) 板・カード
internal val Paper3 = Color(0xFF181E23) // oklch(23% 0.014 250) 入力欄・ピル
internal val Rule = Color(0xFF2E3339) // oklch(32% 0.012 250) 罫線
internal val Rule2 = Color(0xFF484E54) // oklch(42% 0.012 250) 強めの罫線・境界
internal val Muted = Color(0xFF9299A1) // oklch(68% 0.014 250) 補助テキスト
internal val Neutral = Color(0xFFB8BEC5) // oklch(80% 0.012 250) 副次テキスト・進捗バー
internal val Ink = Color(0xFFEBEFF4) // oklch(95% 0.008 250) 主テキスト・主ボタン地

// ── 操作（唯一のアクセント・色相 200） ────────────────────────────
internal val Accent = Color(0xFF3ACED6) // oklch(78% 0.12 200) 選択中
internal val AccentInk = Color(0xFF001112) // oklch(16% 0.03 200) アクセント面上の文字
internal val Focus = Color(0xFF24E9F2) // oklch(85% 0.14 200) フォーカスリング

// ── 警報（画面内で唯一の高彩度・色相 32） ──────────────────────────
// 赤ではなくオレンジ寄りなのは、超過を「失敗」ではなく「計測データ」として見せたいから
// （docs/01_SPEC.md 1 章）。sRGB の色域からはみ出すのでクランプされた値になっている。
internal val Alert = Color(0xFFFF6247) // oklch(70% 0.20 32) 超過・非常口
internal val AlertInk = Color(0xFF190502) // oklch(15% 0.04 32) 警報面の地

// ── カテゴリ：色相は docs/01_SPEC.md 3.4 で固定。変更禁止 ──────────
// v2 の Notion 同期で既存 DB のタグと突き合わせるため、名前だけでなく色相も動かさない。
// 彩度は 0.10 に揃えてあるので、黒地の上でどれも同じ強さに見える。
private val TagSleepColor = Color(0xFFC0A1E4) // oklch(76% 0.10 305) 紫
private val TagUniColor = Color(0xFFDFCA7D) // oklch(84% 0.10  95) 黄
private val TagJobColor = Color(0xFFE3928B) // oklch(74% 0.10  25) 赤
private val TagHobbyColor = Color(0xFF85B4F0) // oklch(76% 0.10 255) 青
private val TagChoreColor = Color(0xFFE9A679) // oklch(78% 0.10  55) オレンジ
private val TagSelfColor = Color(0xFF88CA95) // oklch(78% 0.10 150) 緑

/**
 * タグ → 色の対応。**この 1 か所以外でタグの色を決めないこと。**
 *
 * Tag.kt が「色は UI の関心なので ui/theme 側で対応づける（Phase 2）」と予告していた場所がここ。
 * enum なので when は網羅チェックされる。タグが増えればコンパイルエラーで気づける。
 */
internal val Tag.color: Color
    get() = when (this) {
        Tag.SLEEP -> TagSleepColor
        Tag.UNI_STUDY -> TagUniColor
        Tag.JOB_HUNTING -> TagJobColor
        Tag.HOBBY -> TagHobbyColor
        Tag.CHORE -> TagChoreColor
        Tag.FOR_MYSELF -> TagSelfColor
    }
