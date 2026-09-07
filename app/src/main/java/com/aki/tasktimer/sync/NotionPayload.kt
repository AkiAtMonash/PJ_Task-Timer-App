package com.aki.tasktimer.sync

import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.data.model.Session
import com.aki.tasktimer.domain.DaySegment
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject

/**
 * Notion API に送る JSON の組み立て（純関数。ネットワークには触らない）。
 *
 * プロパティ名は Notion 側の「⏱️ DB_タイムログ」に合わせてある（docs/06 2 章）。
 * 送るのは 名前 / status / time / タグ / 評価 / メモ / ゴール / 予定時間（分） だけ。
 * 「日付」「時間」は Notion 側の数式、「重複検知用」は別用途なので触らない。
 */
object NotionPayload {

    const val PROP_TITLE = "名前"
    const val PROP_STATUS = "status"
    const val PROP_TIME = "time"
    const val PROP_TAG = "タグ"
    const val PROP_RATING = "評価"
    const val PROP_MEMO = "メモ"
    const val PROP_GOAL = "ゴール"
    const val PROP_PLANNED = "予定時間（分）"

    const val STATUS_RUNNING = "進行中"
    const val STATUS_DONE = "完了"

    /** 既存システムと同じ。空文字を送るとエラーになりうるので必ず何か入れる（docs/06 2 章）。 */
    const val EMPTY_MEMO = "特に無し"

    /** Notion の「評価」select の選択肢名。 */
    fun ratingName(rating: Rating): String = when (rating) {
        Rating.GOOD -> "◯（良い）"
        Rating.NORMAL -> "△（普通）"
        Rating.BAD -> "✕（悪い）"
    }

    /**
     * epoch millis → "2026-07-29T14:00:00+09:00"。
     * Notion の date は分単位で表示するので秒未満は落とす（小数秒を付けると見た目が乱れるだけ）。
     */
    fun toIso(millis: Long, zone: ZoneId): String =
        Instant.ofEpochMilli(millis)
            .atZone(zone)
            .truncatedTo(ChronoUnit.SECONDS)
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)

    /** 開始時：「進行中」のページを作る POST /v1/pages の本文。 */
    fun createRunningPage(databaseId: String, session: Session, zone: ZoneId): JSONObject =
        JSONObject()
            .put("parent", JSONObject().put("database_id", databaseId))
            .put(
                "properties",
                baseProperties(session)
                    .put(PROP_STATUS, status(STATUS_RUNNING))
                    .put(PROP_TIME, dateRange(toIso(session.startedAt, zone), end = null)),
            )

    /**
     * 終了時：既存ページを「完了」にする PATCH /v1/pages/{id} の本文。
     * [segment] は日またぎで切った先頭区間（またがなければセッション全体）。
     *
     * time は start も一緒に送る。Notion の date は丸ごと置き換わるので、
     * end だけ送ると start が消える。
     */
    fun finishPage(session: Session, segment: DaySegment, zone: ZoneId): JSONObject =
        JSONObject().put("properties", completedProperties(session, segment, zone))

    /** 日またぎの 2 日目以降：「完了」のページを新しく作る POST /v1/pages の本文。 */
    fun createCompletedPage(
        databaseId: String,
        session: Session,
        segment: DaySegment,
        zone: ZoneId,
    ): JSONObject =
        JSONObject()
            .put("parent", JSONObject().put("database_id", databaseId))
            .put("properties", baseProperties(session).merge(completedProperties(session, segment, zone)))

    // ---- 部品 ----

    /** 名前・タグ・ゴール・予定時間。開始時にも終了時にも同じ値を送る。 */
    private fun baseProperties(session: Session): JSONObject {
        val props = JSONObject()
            .put(PROP_TITLE, JSONObject().put("title", richText(session.name)))
            .put(PROP_TAG, JSONObject().put("multi_select", JSONArray().put(JSONObject().put("name", session.tag.label))))
            .put(PROP_PLANNED, JSONObject().put("number", session.plannedMinutes))
        // ゴールが空ならキーごと省く。空の rich_text を送るより安全。
        if (session.goal.isNotBlank()) {
            props.put(PROP_GOAL, JSONObject().put("rich_text", richText(session.goal)))
        }
        return props
    }

    /** 完了・時間範囲・評価・メモ。 */
    private fun completedProperties(session: Session, segment: DaySegment, zone: ZoneId): JSONObject {
        val props = JSONObject()
            .put(PROP_STATUS, status(STATUS_DONE))
            .put(PROP_TIME, dateRange(toIso(segment.startMillis, zone), toIso(segment.endMillis, zone)))
            .put(PROP_MEMO, JSONObject().put("rich_text", richText(session.ratingNote?.takeIf { it.isNotBlank() } ?: EMPTY_MEMO)))
        session.rating?.let { rating ->
            props.put(PROP_RATING, JSONObject().put("select", JSONObject().put("name", ratingName(rating))))
        }
        return props
    }

    private fun status(name: String): JSONObject =
        JSONObject().put("status", JSONObject().put("name", name))

    private fun dateRange(start: String, end: String?): JSONObject {
        val date = JSONObject().put("start", start)
        if (end != null) date.put("end", end)
        return JSONObject().put("date", date)
    }

    private fun richText(text: String): JSONArray =
        JSONArray().put(JSONObject().put("text", JSONObject().put("content", text)))

    private fun JSONObject.merge(other: JSONObject): JSONObject {
        other.keys().forEach { key -> put(key, other.get(key)) }
        return this
    }
}
