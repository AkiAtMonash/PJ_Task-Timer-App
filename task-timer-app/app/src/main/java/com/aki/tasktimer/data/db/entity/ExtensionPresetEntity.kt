package com.aki.tasktimer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aki.tasktimer.data.model.ExtensionPreset

@Entity(
    tableName = "extension_presets",
    // 同じ分数のボタンが 2 つ並ぶのは無意味なので DB 側で弾く。
    // シード投入の重複防止（INSERT OR IGNORE）もこの制約に依存している。
    indices = [Index(value = ["minutes"], unique = true)],
)
data class ExtensionPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val minutes: Int,
    val useCount: Int,
    val isPinned: Boolean,
)

fun ExtensionPresetEntity.toDomain(): ExtensionPreset = ExtensionPreset(
    id = id,
    minutes = minutes,
    useCount = useCount,
    isPinned = isPinned,
)
