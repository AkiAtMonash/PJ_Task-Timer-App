package com.aki.tasktimer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

private const val MIN = 60_000L

class TimeCalculatorTest {

    // ---------- elapsedMinutes ----------

    @Test
    fun `開始直後は 0 分`() {
        assertEquals(0, elapsedMinutes(startedAt = 1_000L, now = 1_000L))
    }

    @Test
    fun `59 秒はまだ 0 分（切り捨て）`() {
        assertEquals(0, elapsedMinutes(startedAt = 0L, now = 59_999L))
    }

    @Test
    fun `ちょうど 1 分で 1 分`() {
        assertEquals(1, elapsedMinutes(startedAt = 0L, now = MIN))
    }

    @Test
    fun `1 分 59 秒はまだ 1 分`() {
        assertEquals(1, elapsedMinutes(startedAt = 0L, now = 2 * MIN - 1))
    }

    @Test
    fun `端末の時刻が巻き戻っても負の値を返さない`() {
        assertEquals(0, elapsedMinutes(startedAt = 10 * MIN, now = 5 * MIN))
    }

    @Test
    fun `8 時間の睡眠は 480 分`() {
        assertEquals(480, elapsedMinutes(startedAt = 0L, now = 8 * 60 * MIN))
    }

    // ---------- overrunRate ----------

    @Test
    fun `仕様の例 47 分経過 予定 45 分は 104 パーセント`() {
        assertEquals(104, overrunRate(elapsedMinutes = 47, plannedMinutes = 45))
    }

    @Test
    fun `仕様の例 1 時間 12 分経過 予定 45 分は 160 パーセント`() {
        assertEquals(160, overrunRate(elapsedMinutes = 72, plannedMinutes = 45))
    }

    @Test
    fun `予定ちょうどなら 100 パーセント`() {
        assertEquals(100, overrunRate(elapsedMinutes = 45, plannedMinutes = 45))
    }

    @Test
    fun `予定未満は 100 未満（切り捨て）`() {
        // 22 * 100 / 45 = 48.88... -> 48
        assertEquals(48, overrunRate(elapsedMinutes = 22, plannedMinutes = 45))
    }

    @Test
    fun `経過 0 分なら 0 パーセント`() {
        assertEquals(0, overrunRate(elapsedMinutes = 0, plannedMinutes = 45))
    }

    @Test
    fun `予定 0 分でもゼロ除算で落ちない`() {
        assertEquals(0, overrunRate(elapsedMinutes = 30, plannedMinutes = 0))
    }

    @Test
    fun `予定が負でも落ちない`() {
        assertEquals(0, overrunRate(elapsedMinutes = 30, plannedMinutes = -5))
    }

    @Test
    fun `Int で掛けると溢れる大きさでも正しい（Long で計算していることの確認）`() {
        // 25_000_000 * 100 は Int の上限を超える。Int 演算だと負の値になる。
        // 現実にはあり得ない長さだが、計算方法を固定するための番人として置いている。
        assertEquals(25_000_000, overrunRate(elapsedMinutes = 25_000_000, plannedMinutes = 100))
    }

    // ---------- totalRequiredMinutes ----------

    @Test
    fun `仕様の例 47 分経過に 10 分足すと 57 分`() {
        assertEquals(57, totalRequiredMinutes(elapsedMinutes = 47, selectedExtension = 10))
    }

    @Test
    fun `延長を選んでいなければ経過時間そのもの`() {
        assertEquals(47, totalRequiredMinutes(elapsedMinutes = 47, selectedExtension = 0))
    }

    @Test
    fun `合計所要時間の分母は予定ではなく経過（予定 45 分でも 47 分経過なら 77 分）`() {
        // 「+30 を選んだら計 77 分か…」という判断ができることが仕様の狙い
        assertEquals(77, totalRequiredMinutes(elapsedMinutes = 47, selectedExtension = 30))
    }

    // ---------- deadlineMillis ----------

    @Test
    fun `期限は開始時刻に合計予定を足したもの`() {
        assertEquals(45 * MIN, deadlineMillis(startedAt = 0L, totalPlannedMinutes = 45))
    }

    @Test
    fun `延長後は合計予定のぶんだけ伸びる`() {
        val startedAt = 1_784_000_000_000L
        assertEquals(startedAt + 55 * MIN, deadlineMillis(startedAt, totalPlannedMinutes = 55))
    }

    @Test
    fun `24 時間の予定でも Long で溢れない`() {
        val startedAt = 1_784_000_000_000L
        assertEquals(startedAt + 1440 * MIN, deadlineMillis(startedAt, totalPlannedMinutes = 1440))
    }
}
