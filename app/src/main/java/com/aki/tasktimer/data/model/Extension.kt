package com.aki.tasktimer.data.model

/**
 * 延長 1 回分の記録（docs/01_SPEC.md 3.2）。
 *
 * 合算せず 1 回ずつ残すのは、あとで「見積もりの精度」を分析するため。
 * 「30 分延長した」と「10 分を 3 回延長した」は意味がまったく違う。
 */
data class Extension(
    val id: Long,
    val sessionId: Long,
    val minutes: Int,
    val extendedAt: Long,
    /** 延長を選んだ時点の経過分数 */
    val elapsedAtExtension: Int,
)
