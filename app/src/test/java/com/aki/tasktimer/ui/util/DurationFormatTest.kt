package com.aki.tasktimer.ui.util

import org.junit.Assert.assertEquals
import org.junit.Test

class DurationFormatTest {

    @Test
    fun `0 ミリ秒は 0 分 0 秒`() {
        assertEquals("0:00", formatElapsed(0L))
    }

    @Test
    fun `59 秒は 0 分 59 秒`() {
        assertEquals("0:59", formatElapsed(59_000L))
    }

    @Test
    fun `60 秒は 1 分 0 秒`() {
        assertEquals("1:00", formatElapsed(60_000L))
    }

    @Test
    fun `59 分 59 秒は 1 時間未満の表記のまま`() {
        assertEquals("59:59", formatElapsed(3_599_000L))
    }

    @Test
    fun `60 分で 1 時間表記に切り替わる`() {
        assertEquals("1:00:00", formatElapsed(3_600_000L))
    }

    @Test
    fun `1 時間 12 分は H_MM_SS`() {
        assertEquals("1:12:00", formatElapsed(4_320_000L))
    }

    @Test
    fun `負値は 0 分 0 秒に丸める`() {
        assertEquals("0:00", formatElapsed(-1L))
    }
}
