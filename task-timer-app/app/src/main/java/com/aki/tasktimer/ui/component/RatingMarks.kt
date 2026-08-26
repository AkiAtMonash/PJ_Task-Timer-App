package com.aki.tasktimer.ui.component

import com.aki.tasktimer.data.model.Rating

/**
 * 評価の記号。**選ばせるときと振り返るときで別の記号を使う。**
 *
 * 選択ボタンは絵文字。色と形があるぶん、押す前に「どれを押すか」が一瞬で決まる。
 * 一覧はモノクロの記号。履歴を上から眺めるときに絵文字が並ぶと視線が奪われて、
 * 肝心の時間の数字が読めなくなる。
 *
 * 記号の使い分けは docs/01_SPEC.md 4.2 / 4.3 と mockups/wireframe.html で
 * ばらついていたので、ここで 1 つに決めている。
 */

/** 選択ボタン用（Step 1）。 */
internal val Rating.choiceMark: String
    get() = when (this) {
        Rating.GOOD -> "✅"
        Rating.NORMAL -> "🫳"
        Rating.BAD -> "❌"
    }

/** 一覧・記録の振り返り用（ホームの「最後の記録」、Phase 6 の履歴）。 */
internal val Rating.listMark: String
    get() = when (this) {
        Rating.GOOD -> "◯"
        Rating.NORMAL -> "△"
        Rating.BAD -> "✕"
    }
