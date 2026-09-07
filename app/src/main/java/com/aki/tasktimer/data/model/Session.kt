package com.aki.tasktimer.data.model

/**
 * セッション＝タスク 1 回の記録（docs/01_SPEC.md 3.1）。
 *
 * 不変条件（SessionRepository が守る）：
 * - status = RUNNING のセッションは DB 全体で最大 1 件
 * - endedAt が非 null なら status = DONE、rating も非 null
 * - endedAt > startedAt
 */
data class Session(
    val id: Long,
    val name: String,
    val tag: Tag,
    val goal: String,
    /** 当初の見積もり。延長しても変わらない。超過率の分母はこちら */
    val plannedMinutes: Int,
    /** 予定時間 ＋ 全延長の合計。延長のたびに増える。期限計算はこちら */
    val totalPlannedMinutes: Int,
    val startedAt: Long,
    val endedAt: Long?,
    val status: SessionStatus,
    val rating: Rating?,
    val ratingNote: String?,
    /**
     * Notion 側に作ったページの id。開始時の送信が成功すると入る。
     * 終了時はこのページを「完了」に書き換える。null なら Notion にまだページが無い
     * （同期 OFF で始めた、または送信が失敗した）。
     */
    val notionPageId: String? = null,
)

enum class SessionStatus { RUNNING, DONE }

/** ◯ / △ / ✕ の 3 択（docs/01_SPEC.md 4.3 Step 1） */
enum class Rating { GOOD, NORMAL, BAD }
