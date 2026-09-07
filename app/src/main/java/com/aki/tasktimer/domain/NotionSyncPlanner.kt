package com.aki.tasktimer.domain

import java.time.ZoneId

/**
 * 終了したセッションを Notion のページにどう割り付けるかの計画（純関数）。
 *
 * 開始時にすでに「進行中」のページが 1 枚できているので、
 * - 先頭の区間 → そのページを「完了」に書き換える（PATCH）
 * - 2 日目以降の区間 → 新しいページを「完了」で作る（POST）
 * という割り付けになる。日をまたがなければ extra は空。
 *
 * 送信処理（sync/）にこの判断を持たせないために切り出してある。
 */
data class FinishPlan(
    val patchSegment: DaySegment,
    val extraSegments: List<DaySegment>,
) {
    val allSegments: List<DaySegment> get() = listOf(patchSegment) + extraSegments
}

/**
 * 終了が開始以前（区間が 1 つも作れない）なら null。
 * Session の不変条件では起きないが、DB を手で触った場合などに備えて例外にはしない。
 */
fun planFinish(startedAt: Long, endedAt: Long, zone: ZoneId): FinishPlan? {
    val segments = splitSegmentsByDay(startedAt, endedAt, zone)
    if (segments.isEmpty()) return null
    return FinishPlan(
        patchSegment = segments.first(),
        extraSegments = segments.drop(1),
    )
}
