package com.aki.tasktimer.data.model

/**
 * 超過画面に出る延長ボタンの選択肢（docs/01_SPEC.md 3.5）。
 *
 * 初期シードは 5 / 10 / 15 / 30 分。設定画面で追加・削除・ピン留めができる。
 * 並び順は isPinned DESC, useCount DESC, minutes ASC。
 */
data class ExtensionPreset(
    val id: Long,
    val minutes: Int,
    val useCount: Int,
    val isPinned: Boolean,
)
