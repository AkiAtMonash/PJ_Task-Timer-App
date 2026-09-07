package com.aki.tasktimer.domain

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

private val JST: ZoneId = ZoneId.of("Asia/Tokyo")

private fun jst(y: Int, mo: Int, d: Int, h: Int, mi: Int, s: Int = 0, ms: Int = 0): Long =
    LocalDateTime.of(y, mo, d, h, mi, s, ms * 1_000_000).atZone(JST).toInstant().toEpochMilli()

private fun day(y: Int, mo: Int, d: Int): LocalDate = LocalDate.of(y, mo, d)

private const val ONE_DAY_MS = 24L * 60 * 60 * 1000

class SplitSegmentsByDayTest {

    @Test
    fun `同じ日で完結するなら 1 区間で境界は入力そのもの`() {
        val start = jst(2026, 7, 29, 10, 0)
        val end = jst(2026, 7, 29, 11, 30)

        val result = splitSegmentsByDay(start, end, JST)

        assertEquals(listOf(DaySegment(day(2026, 7, 29), start, end)), result)
    }

    @Test
    fun `23 時から翌 7 時は 2 区間で境界が翌日 0 時ちょうど`() {
        val start = jst(2026, 7, 29, 23, 0)
        val end = jst(2026, 7, 30, 7, 0)
        val midnight = jst(2026, 7, 30, 0, 0)

        val result = splitSegmentsByDay(start, end, JST)

        assertEquals(
            listOf(
                DaySegment(day(2026, 7, 29), start, midnight),
                DaySegment(day(2026, 7, 30), midnight, end),
            ),
            result,
        )
    }

    @Test
    fun `0 時ちょうどに始まると当日 1 区間`() {
        val start = jst(2026, 7, 29, 0, 0)
        val end = jst(2026, 7, 29, 8, 0)

        val result = splitSegmentsByDay(start, end, JST)

        assertEquals(1, result.size)
        assertEquals(day(2026, 7, 29), result[0].date)
    }

    @Test
    fun `0 時ちょうどに終わると翌日に長さ 0 の区間を作らない`() {
        val result = splitSegmentsByDay(jst(2026, 7, 29, 22, 0), jst(2026, 7, 30, 0, 0), JST)

        assertEquals(1, result.size)
        assertEquals(day(2026, 7, 29), result[0].date)
        assertEquals(jst(2026, 7, 30, 0, 0), result[0].endMillis)
    }

    @Test
    fun `3 日以上またぐと中日は丸 1 日ぶんの区間`() {
        val result = splitSegmentsByDay(jst(2026, 7, 29, 23, 0), jst(2026, 8, 1, 1, 0), JST)

        assertEquals(4, result.size)
        assertEquals(ONE_DAY_MS, result[1].endMillis - result[1].startMillis)
        assertEquals(ONE_DAY_MS, result[2].endMillis - result[2].startMillis)
        assertEquals(
            listOf(day(2026, 7, 29), day(2026, 7, 30), day(2026, 7, 31), day(2026, 8, 1)),
            result.map { it.date },
        )
    }

    @Test
    fun `区間の長さの合計は元のセッション長と一致する`() {
        val start = jst(2026, 7, 29, 22, 17, 33)
        val end = jst(2026, 8, 2, 4, 5, 12)

        val total = splitSegmentsByDay(start, end, JST).sumOf { it.endMillis - it.startMillis }

        assertEquals(end - start, total)
    }

    @Test
    fun `隣り合う区間は隙間も重なりも無い`() {
        val result = splitSegmentsByDay(jst(2026, 7, 29, 22, 0), jst(2026, 8, 1, 3, 0), JST)

        result.zipWithNext().forEach { (a, b) ->
            assertEquals(a.endMillis, b.startMillis)
        }
    }

    @Test
    fun `終了が開始以前なら空`() {
        val t = jst(2026, 7, 29, 10, 0)

        assertTrue(splitSegmentsByDay(t, t, JST).isEmpty())
        assertTrue(splitSegmentsByDay(t, t - 1, JST).isEmpty())
    }

    @Test
    fun `ミリ秒の端数があっても境界は 0 時 0 分 0 秒 000`() {
        val start = jst(2026, 7, 29, 23, 59, 59, 500)
        val end = jst(2026, 7, 30, 0, 0, 0, 500)

        val result = splitSegmentsByDay(start, end, JST)

        assertEquals(2, result.size)
        assertEquals(jst(2026, 7, 30, 0, 0), result[0].endMillis)
        assertEquals(jst(2026, 7, 30, 0, 0), result[1].startMillis)
    }

    @Test
    fun `splitByDay と同じ長さになる`() {
        val start = jst(2026, 7, 29, 22, 17, 33)
        val end = jst(2026, 8, 2, 4, 5, 12)

        val segments = splitSegmentsByDay(start, end, JST)
        val byDay = splitByDay(start, end, JST)

        assertEquals(byDay.keys.toList(), segments.map { it.date })
        segments.forEach { seg ->
            assertEquals(byDay[seg.date], Duration.ofMillis(seg.endMillis - seg.startMillis))
        }
    }
}

class PlanFinishTest {

    @Test
    fun `同日なら PATCH だけで追加ページは無い`() {
        val start = jst(2026, 7, 29, 10, 0)
        val end = jst(2026, 7, 29, 11, 0)

        val plan = planFinish(start, end, JST)!!

        assertEquals(DaySegment(day(2026, 7, 29), start, end), plan.patchSegment)
        assertTrue(plan.extraSegments.isEmpty())
    }

    @Test
    fun `日をまたぐと先頭が PATCH で残りが追加ページ`() {
        val start = jst(2026, 7, 29, 23, 0)
        val end = jst(2026, 7, 31, 7, 0)

        val plan = planFinish(start, end, JST)!!

        assertEquals(day(2026, 7, 29), plan.patchSegment.date)
        assertEquals(listOf(day(2026, 7, 30), day(2026, 7, 31)), plan.extraSegments.map { it.date })
        assertEquals(3, plan.allSegments.size)
    }

    @Test
    fun `区間が作れなければ null`() {
        val t = jst(2026, 7, 29, 10, 0)

        assertNull(planFinish(t, t, JST))
    }
}
