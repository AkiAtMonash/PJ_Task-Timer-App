package com.aki.tasktimer.data.repository

import com.aki.tasktimer.data.db.dao.PresetDao
import com.aki.tasktimer.data.db.entity.ExtensionPresetEntity
import com.aki.tasktimer.data.db.entity.toDomain
import com.aki.tasktimer.data.model.ExtensionPreset
import com.aki.tasktimer.data.model.Tag
import com.aki.tasktimer.data.model.TaskPreset
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * タスク名プリセットと延長プリセット。
 * 並び順は DAO（SQL）側で確定済みなので、ここでも UI でも並べ替えないこと。
 */
class PresetRepository(private val presetDao: PresetDao) {

    // ---- タスク名プリセット ----

    fun observeTaskPresets(): Flow<List<TaskPreset>> =
        presetDao.observeTaskPresets().map { entities -> entities.map { it.toDomain() } }

    /**
     * セッション開始のたびに呼ぶ自動学習（docs/01_SPEC.md 3.3）。
     * 既存なら useCount++ ＋ 前回値の更新、無ければ useCount = 1 で作成。
     */
    suspend fun recordTaskUse(
        name: String,
        tag: Tag,
        goal: String?,
        plannedMinutes: Int,
        usedAt: Long,
    ) = presetDao.recordTaskUse(
        name = name.trim(),
        tag = tag,
        goal = goal?.trim()?.ifBlank { null },
        plannedMinutes = plannedMinutes,
        usedAt = usedAt,
    )

    suspend fun deleteTaskPreset(id: Long) = presetDao.deleteTaskPreset(id)

    // ---- 延長プリセット ----

    fun observeExtensionPresets(): Flow<List<ExtensionPreset>> =
        presetDao.observeExtensionPresets().map { entities -> entities.map { it.toDomain() } }

    suspend fun addExtensionPreset(minutes: Int) {
        require(minutes > 0) { "延長プリセットは 1 分以上でなければなりません: $minutes" }
        presetDao.insertExtensionPreset(
            ExtensionPresetEntity(minutes = minutes, useCount = 0, isPinned = false),
        )
    }

    suspend fun deleteExtensionPreset(id: Long) = presetDao.deleteExtensionPreset(id)

    suspend fun setExtensionPresetPinned(id: Long, pinned: Boolean) =
        presetDao.setExtensionPresetPinned(id, pinned)

    /** 延長が確定したときに呼ぶ。並び順（useCount DESC）に効く。 */
    suspend fun recordExtensionUse(minutes: Int) = presetDao.incrementExtensionPresetUse(minutes)
}
