package com.aki.tasktimer.data.db.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import com.aki.tasktimer.data.model.Extension

@Entity(
    tableName = "extensions",
    foreignKeys = [
        ForeignKey(
            entity = SessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            // セッションが消えたら延長履歴も一緒に消す。孤児レコードを残さない。
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index(value = ["sessionId"])],
)
data class ExtensionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val minutes: Int,
    val extendedAt: Long,
    val elapsedAtExtension: Int,
)

fun ExtensionEntity.toDomain(): Extension = Extension(
    id = id,
    sessionId = sessionId,
    minutes = minutes,
    extendedAt = extendedAt,
    elapsedAtExtension = elapsedAtExtension,
)
