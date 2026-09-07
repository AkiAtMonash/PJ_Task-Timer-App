package com.aki.tasktimer.domain

import com.aki.tasktimer.data.model.Session
import com.aki.tasktimer.data.model.SessionStatus

/**
 * 「今、超過しているか」「超過画面・バイブ・ブロックを出すべきか」の判断。**純関数。**
 *
 * 期限の検知そのものは AlarmManager が行う（docs/05 2.1）。ここはアラームが鳴った後や
 * 再起動後に「本当にまだ超過中か」を DB の値から判断するためのもの。
 * 古いアラームの誤発火や、非常口で閉じた後の再発火をここで弾く。
 */

/** 進行中で、かつ期限を過ぎている。期限ちょうどは超過とみなす。 */
fun isOverdue(session: Session?, now: Long): Boolean {
    if (session == null || session.status != SessionStatus.RUNNING) return false
    return now >= deadlineMillis(session.startedAt, session.totalPlannedMinutes)
}

/**
 * 非常口で閉じた「超過の 1 回分」。セッション id と期限の組で識別する。
 * 同じセッションでも延長すれば期限が変わるので、次の超過はまた出る。
 */
data class DismissedOccurrence(
    val sessionId: Long,
    val deadlineMillis: Long,
)

fun isDismissed(session: Session, dismissed: DismissedOccurrence?): Boolean =
    dismissed != null &&
        dismissed.sessionId == session.id &&
        dismissed.deadlineMillis == deadlineMillis(session.startedAt, session.totalPlannedMinutes)

enum class GuardDecision {
    /** 何もしない（超過していない／非常口で閉じた分） */
    NONE,

    /** 超過画面・バイブ・ブロックを出す */
    ALERT,

    /** 超過画面・バイブだけ。ブロック（覆い）は出さない（強制力 OFF） */
    ALERT_NO_BLOCK,
}

fun guardDecision(
    session: Session?,
    now: Long,
    blockEnabled: Boolean,
    dismissed: DismissedOccurrence?,
): GuardDecision {
    if (session == null || !isOverdue(session, now)) return GuardDecision.NONE
    if (isDismissed(session, dismissed)) return GuardDecision.NONE
    return if (blockEnabled) GuardDecision.ALERT else GuardDecision.ALERT_NO_BLOCK
}
