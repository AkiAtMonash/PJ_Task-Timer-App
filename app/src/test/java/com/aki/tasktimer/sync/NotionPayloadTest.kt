package com.aki.tasktimer.sync

import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.data.model.Session
import com.aki.tasktimer.data.model.SessionStatus
import com.aki.tasktimer.data.model.Tag
import com.aki.tasktimer.domain.DaySegment
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private val JST: ZoneId = ZoneId.of("Asia/Tokyo")
private const val DB = "18a0bc4f73378145ae19d00b3921f39b"

private fun jst(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0, ms: Int = 0): Long =
    LocalDateTime.of(y, mo, d, h, mi, s, ms * 1_000_000).atZone(JST).toInstant().toEpochMilli()

private fun session(
    goal: String = "A社のガクチカを書き切る",
    rating: Rating? = Rating.GOOD,
    ratingNote: String? = "集中できた",
    endedAt: Long? = jst(2026, 7, 29, 14, 47),
    notionPageId: String? = null,
): Session = Session(
    id = 1,
    name = "ES執筆",
    tag = Tag.JOB_HUNTING,
    goal = goal,
    plannedMinutes = 45,
    totalPlannedMinutes = 55,
    startedAt = jst(2026, 7, 29, 14, 0),
    endedAt = endedAt,
    status = if (endedAt == null) SessionStatus.RUNNING else SessionStatus.DONE,
    rating = rating,
    ratingNote = ratingNote,
    notionPageId = notionPageId,
)

private fun segment(start: Long, end: Long) =
    DaySegment(LocalDate.of(2026, 7, 29), start, end)

class ToIsoTest {

    @Test
    fun `日本時間のオフセット付きで秒まで`() {
        assertEquals("2026-07-29T14:00:00+09:00", NotionPayload.toIso(jst(2026, 7, 29, 14, 0), JST))
    }

    @Test
    fun `ミリ秒は落とす`() {
        assertEquals(
            "2026-07-29T14:00:05+09:00",
            NotionPayload.toIso(jst(2026, 7, 29, 14, 0, 5, 987), JST),
        )
    }
}

class RatingNameTest {

    @Test
    fun `Notion の選択肢名に対応する`() {
        assertEquals("◯（良い）", NotionPayload.ratingName(Rating.GOOD))
        assertEquals("△（普通）", NotionPayload.ratingName(Rating.NORMAL))
        assertEquals("✕（悪い）", NotionPayload.ratingName(Rating.BAD))
    }
}

class CreateRunningPageTest {

    private val body = NotionPayload.createRunningPage(DB, session(endedAt = null, rating = null, ratingNote = null), JST)
    private val props = body.getJSONObject("properties")

    @Test
    fun `親はデータベース`() {
        assertEquals(DB, body.getJSONObject("parent").getString("database_id"))
    }

    @Test
    fun `status は進行中`() {
        assertEquals("進行中", props.getJSONObject("status").getJSONObject("status").getString("name"))
    }

    @Test
    fun `time は開始だけで終了が無い`() {
        val date = props.getJSONObject("time").getJSONObject("date")
        assertEquals("2026-07-29T14:00:00+09:00", date.getString("start"))
        assertFalse(date.has("end"))
    }

    @Test
    fun `名前 タグ ゴール 予定時間が入る`() {
        assertEquals(
            "ES執筆",
            props.getJSONObject("名前").getJSONArray("title").getJSONObject(0).getJSONObject("text").getString("content"),
        )
        assertEquals(
            "Job Hunting",
            props.getJSONObject("タグ").getJSONArray("multi_select").getJSONObject(0).getString("name"),
        )
        assertEquals(
            "A社のガクチカを書き切る",
            props.getJSONObject("ゴール").getJSONArray("rich_text").getJSONObject(0).getJSONObject("text").getString("content"),
        )
        // 予定時間は当初の見積もり。延長込みの 55 ではない。
        assertEquals(45, props.getJSONObject("予定時間（分）").getInt("number"))
    }

    @Test
    fun `評価とメモはまだ送らない`() {
        assertFalse(props.has("評価"))
        assertFalse(props.has("メモ"))
    }

    @Test
    fun `ゴールが空ならキーごと省く`() {
        val body = NotionPayload.createRunningPage(DB, session(goal = "", endedAt = null, rating = null), JST)
        assertFalse(body.getJSONObject("properties").has("ゴール"))
    }

    @Test
    fun `Notion 側の数式や重複検知用には触らない`() {
        assertFalse(props.has("日付"))
        assertFalse(props.has("時間"))
        assertFalse(props.has("重複検知用"))
    }
}

class FinishPageTest {

    private val start = jst(2026, 7, 29, 14, 0)
    private val end = jst(2026, 7, 29, 14, 47)
    private val body = NotionPayload.finishPage(session(), segment(start, end), JST)
    private val props = body.getJSONObject("properties")

    @Test
    fun `status は完了`() {
        assertEquals("完了", props.getJSONObject("status").getJSONObject("status").getString("name"))
    }

    @Test
    fun `time は開始と終了の両方を送る`() {
        val date = props.getJSONObject("time").getJSONObject("date")
        assertEquals("2026-07-29T14:00:00+09:00", date.getString("start"))
        assertEquals("2026-07-29T14:47:00+09:00", date.getString("end"))
    }

    @Test
    fun `評価とメモが入る`() {
        assertEquals("◯（良い）", props.getJSONObject("評価").getJSONObject("select").getString("name"))
        assertEquals(
            "集中できた",
            props.getJSONObject("メモ").getJSONArray("rich_text").getJSONObject(0).getJSONObject("text").getString("content"),
        )
    }

    @Test
    fun `メモが空なら「特に無し」にする`() {
        val body = NotionPayload.finishPage(session(rating = Rating.NORMAL, ratingNote = ""), segment(start, end), JST)
        val memo = body.getJSONObject("properties").getJSONObject("メモ")
            .getJSONArray("rich_text").getJSONObject(0).getJSONObject("text").getString("content")
        assertEquals("特に無し", memo)
    }

    @Test
    fun `PATCH の本文に parent は無い`() {
        assertFalse(body.has("parent"))
    }
}

class CreateCompletedPageTest {

    @Test
    fun `2 日目のページは区間の時刻で完了として作られ 名前やタグも持つ`() {
        val start = jst(2026, 7, 30, 0, 0)
        val end = jst(2026, 7, 30, 7, 0)
        val body = NotionPayload.createCompletedPage(
            DB,
            session(endedAt = end),
            DaySegment(LocalDate.of(2026, 7, 30), start, end),
            JST,
        )
        val props = body.getJSONObject("properties")

        assertEquals(DB, body.getJSONObject("parent").getString("database_id"))
        assertEquals("完了", props.getJSONObject("status").getJSONObject("status").getString("name"))
        val date = props.getJSONObject("time").getJSONObject("date")
        assertEquals("2026-07-30T00:00:00+09:00", date.getString("start"))
        assertEquals("2026-07-30T07:00:00+09:00", date.getString("end"))
        assertTrue(props.has("名前"))
        assertTrue(props.has("タグ"))
        assertTrue(props.has("評価"))
        assertTrue(props.has("メモ"))
        assertEquals(45, props.getJSONObject("予定時間（分）").getInt("number"))
    }
}

/**
 * 取り残しページを作らないための照会まわり（ADR 0002）。
 * ここが壊れると「進行中」が二重に増えるので、応答の読み取りと時刻の突き合わせを固定しておく。
 */
class NotionPayloadQueryTest {

    @Test
    fun `照会は進行中だけを引く`() {
        val filter = NotionPayload.runningPagesQuery().getJSONObject("filter")
        assertEquals("status", filter.getString("property"))
        assertEquals("進行中", filter.getJSONObject("status").getString("equals"))
    }

    @Test
    fun `応答からページ id と開始時刻を取り出す`() {
        val body = """
            {"results":[
              {"id":"page-a","properties":{"time":{"date":{"start":"2026-07-29T14:00:00.000+09:00","end":null}}}},
              {"id":"page-b","properties":{"time":{"date":{"start":"2026-07-29T15:30:00+09:00"}}}}
            ]}
        """.trimIndent()
        val refs = NotionPayload.parsePageRefs(body)
        assertEquals(listOf("page-a", "page-b"), refs.map { it.id })
        assertEquals(jst(2026, 7, 29, 14, 0), refs[0].startMillis)
        assertEquals(jst(2026, 7, 29, 15, 30), refs[1].startMillis)
    }

    @Test
    fun `時刻が読めない行は落とす`() {
        val body = """
            {"results":[
              {"id":"no-time","properties":{}},
              {"id":"empty-date","properties":{"time":{"date":null}}},
              {"id":"broken","properties":{"time":{"date":{"start":"きのう"}}}},
              {"id":"ok","properties":{"time":{"date":{"start":"2026-07-29T14:00:00+09:00"}}}}
            ]}
        """.trimIndent()
        assertEquals(listOf("ok"), NotionPayload.parsePageRefs(body).map { it.id })
    }

    @Test
    fun `応答が壊れていても落ちない`() {
        assertEquals(emptyList<NotionPayload.PageRef>(), NotionPayload.parsePageRefs("なにこれ"))
        assertEquals(emptyList<NotionPayload.PageRef>(), NotionPayload.parsePageRefs("{}"))
    }

    @Test
    fun `突き合わせは秒まで　ミリ秒の差は同じ扱い`() {
        val startedAt = jst(2026, 7, 29, 14, 0, s = 3, ms = 480)
        // 送るときに秒未満を落としているので、Notion 側は 14:00:03 ちょうどで返ってくる。
        assertTrue(NotionPayload.sameStart(jst(2026, 7, 29, 14, 0, s = 3), startedAt))
        assertFalse(NotionPayload.sameStart(jst(2026, 7, 29, 14, 0, s = 4), startedAt))
    }
}
