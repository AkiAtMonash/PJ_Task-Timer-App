package com.aki.tasktimer.domain

import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.data.model.Session
import com.aki.tasktimer.data.model.SessionStatus
import com.aki.tasktimer.data.model.Tag
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

private val JST: ZoneId = ZoneId.of("Asia/Tokyo")
private val NY: ZoneId = ZoneId.of("America/New_York")

private fun jst(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0): Long =
    LocalDateTime.of(y, mo, d, h, mi, s).atZone(JST).toInstant().toEpochMilli()

private fun ny(y: Int, mo: Int, d: Int, h: Int, mi: Int): Long =
    LocalDateTime.of(y, mo, d, h, mi).atZone(NY).toInstant().toEpochMilli()

private fun day(y: Int, mo: Int, d: Int): LocalDate = LocalDate.of(y, mo, d)

private fun mins(n: Long): Duration = Duration.ofMinutes(n)

private fun session(
    startedAt: Long,
    endedAt: Long?,
    tag: Tag = Tag.HOBBY,
    id: Long = 1L,
): Session = Session(
    id = id,
    name = "テスト",
    tag = tag,
    goal = "",
    plannedMinutes = 60,
    totalPlannedMinutes = 60,
    startedAt = startedAt,
    endedAt = endedAt,
    status = if (endedAt == null) SessionStatus.RUNNING else SessionStatus.DONE,
    rating = if (endedAt == null) null else Rating.NORMAL,
    ratingNote = null,
)

class SplitByDayTest {

    @Test
    fun `同じ日で完結するセッションは 1 日ぶんだけ`() {
        val result = splitByDay(jst(2026, 7, 29, 10, 0), jst(2026, 7, 29, 11, 30), JST)

        assertEquals(mapOf(day(2026, 7, 29) to mins(90)), result)
    }

    @Test
    fun `仕様の例 23 時から翌 7 時の Sleep は 60 分と 420 分に割れる`() {
        // docs/01_SPEC.md 6.1 の本文は「当日 59 分」だが、それだと合計が
        // 元の 8 時間に届かない。日境界は 0 時ちょうどなので 60 分が正しい。
        val result = splitByDay(jst(2026, 7, 29, 23, 0), jst(2026, 7, 30, 7, 0), JST)

        assertEquals(
            mapOf(
                day(2026, 7, 29) to mins(60),
                day(2026, 7, 30) to mins(420),
            ),
            result,
        )
    }

    @Test
    fun `0 時ちょうどに始まるセッション`() {
        val result = splitByDay(jst(2026, 7, 29, 0, 0), jst(2026, 7, 29, 8, 0), JST)

        assertEquals(mapOf(day(2026, 7, 29) to mins(480)), result)
    }

    @Test
    fun `0 時ちょうどに終わるセッションは翌日に 0 分のエントリを作らない`() {
        val result = splitByDay(jst(2026, 7, 29, 22, 0), jst(2026, 7, 30, 0, 0), JST)

        assertEquals(1, result.size)
        assertEquals(mapOf(day(2026, 7, 29) to mins(120)), result)
    }

    @Test
    fun `23 時 59 分 59 秒に終わるセッションは当日だけ`() {
        val result = splitByDay(jst(2026, 7, 29, 23, 0), jst(2026, 7, 29, 23, 59, 59), JST)

        assertEquals(1, result.size)
        assertEquals(mins(59).plusSeconds(59), result[day(2026, 7, 29)])
    }

    @Test
    fun `3 日以上またぐと中日は丸ごと 1440 分`() {
        val result = splitByDay(jst(2026, 7, 29, 22, 0), jst(2026, 8, 1, 3, 0), JST)

        assertEquals(
            mapOf(
                day(2026, 7, 29) to mins(120),
                day(2026, 7, 30) to mins(1440),
                day(2026, 7, 31) to mins(1440),
                day(2026, 8, 1) to mins(180),
            ),
            result,
        )
    }

    @Test
    fun `月をまたいでも日付が正しく進む`() {
        val result = splitByDay(jst(2026, 7, 31, 23, 30), jst(2026, 8, 1, 0, 30), JST)

        assertEquals(
            mapOf(
                day(2026, 7, 31) to mins(30),
                day(2026, 8, 1) to mins(30),
            ),
            result,
        )
    }

    @Test
    fun `開始と終了が同じなら空`() {
        val t = jst(2026, 7, 29, 10, 0)

        assertTrue(splitByDay(t, t, JST).isEmpty())
    }

    @Test
    fun `終了が開始より前なら空`() {
        assertTrue(splitByDay(jst(2026, 7, 29, 10, 0), jst(2026, 7, 29, 9, 0), JST).isEmpty())
    }

    @Test
    fun `按分した合計は必ず元のセッション長と一致する`() {
        val startedAt = jst(2026, 7, 29, 22, 17, 33)
        val endedAt = jst(2026, 8, 2, 4, 5, 12)

        val total = splitByDay(startedAt, endedAt, JST).values
            .fold(Duration.ZERO) { acc, d -> acc.plus(d) }

        assertEquals(Duration.ofMillis(endedAt - startedAt), total)
    }

    @Test
    fun `日付は昇順で返る`() {
        val result = splitByDay(jst(2026, 7, 29, 22, 0), jst(2026, 8, 1, 3, 0), JST)

        assertEquals(
            listOf(day(2026, 7, 29), day(2026, 7, 30), day(2026, 7, 31), day(2026, 8, 1)),
            result.keys.toList(),
        )
    }

    @Test
    fun `夏時間が始まる日でも実時間で按分される`() {
        // 2026-03-08 はアメリカ東部の夏時間開始日。この日は 23 時間しかない。
        // 単純に 24 時間で割る実装だとここで壊れる。
        val result = splitByDay(ny(2026, 3, 7, 23, 0), ny(2026, 3, 8, 7, 0), NY)

        assertEquals(
            mapOf(
                day(2026, 3, 7) to mins(60),
                // 0 時から 7 時まででも、2 時が飛ぶので実時間は 6 時間
                day(2026, 3, 8) to mins(360),
            ),
            result,
        )
    }
}

class DailyTotalsTest {

    @Test
    fun `同じ日の同じタグは合算される`() {
        val sessions = listOf(
            session(jst(2026, 7, 29, 9, 0), jst(2026, 7, 29, 10, 0), Tag.UNI_STUDY, id = 1),
            session(jst(2026, 7, 29, 14, 0), jst(2026, 7, 29, 14, 30), Tag.UNI_STUDY, id = 2),
        )

        val result = dailyTotals(sessions, JST)

        assertEquals(mapOf(Tag.UNI_STUDY to mins(90)), result[day(2026, 7, 29)])
    }

    @Test
    fun `同じ日でもタグが違えば分かれる`() {
        val sessions = listOf(
            session(jst(2026, 7, 29, 9, 0), jst(2026, 7, 29, 10, 0), Tag.UNI_STUDY, id = 1),
            session(jst(2026, 7, 29, 10, 0), jst(2026, 7, 29, 10, 45), Tag.JOB_HUNTING, id = 2),
        )

        val result = dailyTotals(sessions, JST)

        assertEquals(
            mapOf(Tag.UNI_STUDY to mins(60), Tag.JOB_HUNTING to mins(45)),
            result[day(2026, 7, 29)],
        )
    }

    @Test
    fun `日をまたぐセッションは両方の日にタグ別で計上される`() {
        val sessions = listOf(
            session(jst(2026, 7, 29, 23, 0), jst(2026, 7, 30, 7, 0), Tag.SLEEP, id = 1),
        )

        val result = dailyTotals(sessions, JST)

        assertEquals(mapOf(Tag.SLEEP to mins(60)), result[day(2026, 7, 29)])
        assertEquals(mapOf(Tag.SLEEP to mins(420)), result[day(2026, 7, 30)])
    }

    @Test
    fun `進行中のセッションは集計に含めない`() {
        val sessions = listOf(
            session(jst(2026, 7, 29, 9, 0), jst(2026, 7, 29, 10, 0), Tag.UNI_STUDY, id = 1),
            session(jst(2026, 7, 29, 10, 0), endedAt = null, tag = Tag.HOBBY, id = 2),
        )

        val result = dailyTotals(sessions, JST)

        assertEquals(mapOf(Tag.UNI_STUDY to mins(60)), result[day(2026, 7, 29)])
    }

    @Test
    fun `日付は昇順で返る`() {
        val sessions = listOf(
            session(jst(2026, 7, 31, 9, 0), jst(2026, 7, 31, 10, 0), id = 1),
            session(jst(2026, 7, 29, 9, 0), jst(2026, 7, 29, 10, 0), id = 2),
            session(jst(2026, 7, 30, 9, 0), jst(2026, 7, 30, 10, 0), id = 3),
        )

        val result = dailyTotals(sessions, JST)

        assertEquals(
            listOf(day(2026, 7, 29), day(2026, 7, 30), day(2026, 7, 31)),
            result.keys.toList(),
        )
    }

    @Test
    fun `セッションが無ければ空`() {
        assertTrue(dailyTotals(emptyList(), JST).isEmpty())
    }
}
