package com.aki.tasktimer.data.db.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.data.model.Session
import com.aki.tasktimer.data.model.SessionStatus
import com.aki.tasktimer.data.model.Tag

@Entity(
    tableName = "sessions",
    indices = [
        // 進行中セッションの取得はアプリ中で最も頻繁に走るクエリなので張る。
        Index(value = ["status"]),
        // 履歴画面は startedAt の降順で引く。
        Index(value = ["startedAt"]),
    ],
)
data class SessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val tag: Tag,
    val goal: String,
    val plannedMinutes: Int,
    val totalPlannedMinutes: Int,
    val startedAt: Long,
    val endedAt: Long?,
    val status: SessionStatus,
    val rating: Rating?,
    val ratingNote: String?,
)

fun SessionEntity.toDomain(): Session = Session(
    id = id,
    name = name,
    tag = tag,
    goal = goal,
    plannedMinutes = plannedMinutes,
    totalPlannedMinutes = totalPlannedMinutes,
    startedAt = startedAt,
    endedAt = endedAt,
    status = status,
    rating = rating,
    ratingNote = ratingNote,
)

fun Session.toEntity(): SessionEntity = SessionEntity(
    id = id,
    name = name,
    tag = tag,
    goal = goal,
    plannedMinutes = plannedMinutes,
    totalPlannedMinutes = totalPlannedMinutes,
    startedAt = startedAt,
    endedAt = endedAt,
    status = status,
    rating = rating,
    ratingNote = ratingNote,
)
