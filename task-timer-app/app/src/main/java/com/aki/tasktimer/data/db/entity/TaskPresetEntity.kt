package com.aki.tasktimer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aki.tasktimer.data.model.Tag
import com.aki.tasktimer.data.model.TaskPreset

@Entity(
    tableName = "task_presets",
    // 同じ名前のプリセットが 2 つできると useCount の学習が分裂して壊れる。
    // upsert の判定キーでもあるので UNIQUE を DB 側で強制する。
    indices = [Index(value = ["name"], unique = true)],
)
data class TaskPresetEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val tag: Tag,
    val lastGoal: String?,
    val lastPlannedMinutes: Int,
    val useCount: Int,
    val lastUsedAt: Long,
)

fun TaskPresetEntity.toDomain(): TaskPreset = TaskPreset(
    id = id,
    name = name,
    tag = tag,
    lastGoal = lastGoal,
    lastPlannedMinutes = lastPlannedMinutes,
    useCount = useCount,
    lastUsedAt = lastUsedAt,
)
