package com.aki.tasktimer.data.model

/**
 * タスク名のクイック選択用プリセット（docs/01_SPEC.md 3.3）。
 *
 * ユーザーが手で管理するものではなく、セッション開始のたびに自動で学習される。
 * 並び順は useCount DESC, lastUsedAt DESC。
 */
data class TaskPreset(
    val id: Long,
    val name: String,
    /** 前回このタスクで使ったタグ */
    val tag: Tag,
    /** 前回のゴール。次回のプレースホルダとして提示する */
    val lastGoal: String?,
    /** 前回の予定時間。次回の初期値として提示する */
    val lastPlannedMinutes: Int,
    val useCount: Int,
    val lastUsedAt: Long,
)
