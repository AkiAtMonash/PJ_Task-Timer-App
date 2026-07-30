package com.aki.tasktimer.data.db.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import com.aki.tasktimer.data.db.entity.ExtensionPresetEntity
import com.aki.tasktimer.data.db.entity.TaskPresetEntity
import com.aki.tasktimer.data.model.Tag
import kotlinx.coroutines.flow.Flow

/**
 * タスク名プリセットと延長プリセットの DAO。
 * 並び順は docs/01_SPEC.md 3.3 / 3.5 の指定どおり SQL 側で確定させる。
 * UI 側でソートし直さないこと（画面ごとに順番が変わると学習が体感できなくなる）。
 */
@Dao
abstract class PresetDao {

    // ---- タスク名プリセット ----

    @Query("SELECT * FROM task_presets ORDER BY useCount DESC, lastUsedAt DESC")
    abstract fun observeTaskPresets(): Flow<List<TaskPresetEntity>>

    @Query("SELECT * FROM task_presets WHERE name = :name LIMIT 1")
    abstract suspend fun getTaskPresetByName(name: String): TaskPresetEntity?

    @Insert
    abstract suspend fun insertTaskPreset(preset: TaskPresetEntity): Long

    @Update
    abstract suspend fun updateTaskPreset(preset: TaskPresetEntity)

    @Query("DELETE FROM task_presets WHERE id = :id")
    abstract suspend fun deleteTaskPreset(id: Long)

    /**
     * セッション開始時の自動学習（docs/01_SPEC.md 3.3）。
     *
     * 名前をキーにした upsert。Room の @Upsert は主キー一致でしか動かず、
     * ここで揃えたいのは name（UNIQUE 制約つき）なので手で書いている。
     * tag / lastGoal / lastPlannedMinutes は「前回値」なので毎回上書きする。
     */
    @Transaction
    open suspend fun recordTaskUse(
        name: String,
        tag: Tag,
        goal: String?,
        plannedMinutes: Int,
        usedAt: Long,
    ) {
        val existing = getTaskPresetByName(name)
        if (existing == null) {
            insertTaskPreset(
                TaskPresetEntity(
                    name = name,
                    tag = tag,
                    lastGoal = goal,
                    lastPlannedMinutes = plannedMinutes,
                    useCount = 1,
                    lastUsedAt = usedAt,
                ),
            )
        } else {
            updateTaskPreset(
                existing.copy(
                    tag = tag,
                    lastGoal = goal,
                    lastPlannedMinutes = plannedMinutes,
                    useCount = existing.useCount + 1,
                    lastUsedAt = usedAt,
                ),
            )
        }
    }

    // ---- 延長プリセット ----

    @Query("SELECT * FROM extension_presets ORDER BY isPinned DESC, useCount DESC, minutes ASC")
    abstract fun observeExtensionPresets(): Flow<List<ExtensionPresetEntity>>

    @Insert
    abstract suspend fun insertExtensionPreset(preset: ExtensionPresetEntity): Long

    @Query("DELETE FROM extension_presets WHERE id = :id")
    abstract suspend fun deleteExtensionPreset(id: Long)

    @Query("UPDATE extension_presets SET isPinned = :pinned WHERE id = :id")
    abstract suspend fun setExtensionPresetPinned(id: Long, pinned: Boolean)

    @Query("UPDATE extension_presets SET useCount = useCount + 1 WHERE minutes = :minutes")
    abstract suspend fun incrementExtensionPresetUse(minutes: Int)
}
