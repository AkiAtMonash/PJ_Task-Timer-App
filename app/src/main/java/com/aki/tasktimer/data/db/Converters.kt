package com.aki.tasktimer.data.db

import androidx.room.TypeConverter
import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.data.model.SessionStatus
import com.aki.tasktimer.data.model.Tag

/**
 * enum ↔ DB 列の変換。
 *
 * Tag は enum 名（JOB_HUNTING）ではなく **label（"Job Hunting"）** を保存する。
 * v2 の Notion 同期と Phase 7 のエクスポートで、そのまま使える文字列にしておきたいため。
 *
 * SessionStatus / Rating は name() をそのまま使う。DAO のクエリで
 * `WHERE status = 'RUNNING'` と直に書けるようにするため、この対応は変えないこと。
 */
class Converters {

    @TypeConverter
    fun tagToString(tag: Tag): String = tag.label

    @TypeConverter
    fun stringToTag(label: String): Tag =
        // v1 にタグ編集 UI は無いので、ここに来る値は必ず enum のどれか。
        // 到達したら DB を手で書き換えた等のプログラミングエラーなので、黙って握り潰さない。
        Tag.fromLabel(label) ?: throw IllegalArgumentException("未知のタグ: $label")

    @TypeConverter
    fun statusToString(status: SessionStatus): String = status.name

    @TypeConverter
    fun stringToStatus(value: String): SessionStatus = SessionStatus.valueOf(value)

    @TypeConverter
    fun ratingToString(rating: Rating?): String? = rating?.name

    @TypeConverter
    fun stringToRating(value: String?): Rating? = value?.let { Rating.valueOf(it) }
}
