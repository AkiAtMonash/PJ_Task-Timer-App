package com.aki.tasktimer.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
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

    // ---------- remainingMinutes ----------

    @Test
    fun `モックアップの例 23 分 41 秒経過 予定 45 分は残り 21 分`() {
        // 経過を先に分へ丸めて 45 - 23 = 22 としてはいけない。
        // 実際の残りは 21 分 19 秒なので 21 が正しい。この 1 分ずれの番人。
        assertEquals(21, remainingMinutes(startedAt = 0L, now = sec(23, 41), totalPlannedMinutes = 45))
    }

    @Test
    fun `モックアップの例 47 分 12 秒経過 予定 45 分は 2 分超過`() {
        assertEquals(-2, remainingMinutes(startedAt = 0L, now = sec(47, 12), totalPlannedMinutes = 45))
    }

    @Test
    fun `開始直後は予定がまるごと残っている`() {
        assertEquals(45, remainingMinutes(startedAt = 0L, now = 0L, totalPlannedMinutes = 45))
    }

    @Test
    fun `超過の端数も 0 方向に切り捨てる`() {
        assertEquals(-3, remainingMinutes(startedAt = 0L, now = sec(48, 59), totalPlannedMinutes = 45))
    }

    @Test
    fun `延長したぶんだけ残りが増える（分母は合計予定）`() {
        assertEquals(31, remainingMinutes(startedAt = 0L, now = sec(23, 41), totalPlannedMinutes = 55))
    }

    // ---------- isOverrun ----------

    @Test
    fun `予定ちょうどの瞬間から超過扱い`() {
        assertTrue(isOverrun(startedAt = 0L, now = 45 * MIN, totalPlannedMinutes = 45))
    }

    @Test
    fun `1 ミリ秒前はまだ超過ではない`() {
        assertFalse(isOverrun(startedAt = 0L, now = 45 * MIN - 1L, totalPlannedMinutes = 45))
    }

    @Test
    fun `残り 0 分でも超過とは限らない（符号で判定してはいけない）`() {
        // 44 分 30 秒。remainingMinutes は 0 を返すが、まだ超過していない
        val now = sec(44, 30)
        assertEquals(0, remainingMinutes(startedAt = 0L, now = now, totalPlannedMinutes = 45))
        assertFalse(isOverrun(startedAt = 0L, now = now, totalPlannedMinutes = 45))
    }

    @Test
    fun `超過 30 秒では remainingMinutes が 0 でも超過している`() {
        val now = sec(45, 30)
        assertEquals(0, remainingMinutes(startedAt = 0L, now = now, totalPlannedMinutes = 45))
        assertTrue(isOverrun(startedAt = 0L, now = now, totalPlannedMinutes = 45))
    }

    // ---------- progressSplit ----------

    @Test
    fun `モックアップの例 23 分 41 秒 予定 45 分は 53 パーセント`() {
        val split = progressSplit(startedAt = 0L, now = sec(23, 41), totalPlannedMinutes = 45)
        assertEquals(0.526f, split.base, 0.001f)
        assertEquals(0f, split.over, 0.001f)
    }

    @Test
    fun `モックアップの例 47 分 12 秒 予定 45 分は 95 と 5`() {
        // 分に丸めてから比を取ると 96/4 になってしまう。ミリ秒のまま計算していることの確認
        val split = progressSplit(startedAt = 0L, now = sec(47, 12), totalPlannedMinutes = 45)
        assertEquals(0.953f, split.base, 0.001f)
        assertEquals(0.047f, split.over, 0.001f)
    }

    @Test
    fun `予定ちょうどは全部が中立色`() {
        val split = progressSplit(startedAt = 0L, now = 45 * MIN, totalPlannedMinutes = 45)
        assertEquals(1f, split.base, 0.001f)
        assertEquals(0f, split.over, 0.001f)
    }

    @Test
    fun `予定の 2 倍かかったら半分ずつ`() {
        val split = progressSplit(startedAt = 0L, now = 90 * MIN, totalPlannedMinutes = 45)
        assertEquals(0.5f, split.base, 0.001f)
        assertEquals(0.5f, split.over, 0.001f)
    }

    @Test
    fun `どれだけ超過してもバーは 1 を超えない`() {
        val split = progressSplit(startedAt = 0L, now = 600 * MIN, totalPlannedMinutes = 1)
        assertTrue("base + over が 1 を超えた", split.base + split.over <= 1f)
    }

    @Test
    fun `進捗バーは予定 0 分でもゼロ除算で落ちない`() {
        val split = progressSplit(startedAt = 0L, now = 10 * MIN, totalPlannedMinutes = 0)
        assertEquals(0f, split.base, 0.001f)
        assertEquals(0f, split.over, 0.001f)
    }

    // ---------- formatDurationMinutes ----------

    @Test
    fun `1 時間未満は分だけ`() {
        assertEquals("45分", formatDurationMinutes(45))
    }

    @Test
    fun `1 時間以上は時間と分`() {
        assertEquals("1時間12分", formatDurationMinutes(72))
    }

    @Test
    fun `ちょうど何時間なら分を省く（480分ではなく8時間）`() {
        assertEquals("8時間", formatDurationMinutes(480))
    }

    @Test
    fun `0 分でも負でも壊れない`() {
        assertEquals("0分", formatDurationMinutes(0))
        assertEquals("0分", formatDurationMinutes(-5))
    }
}

/** 分秒をミリ秒に。テストの意図（「23 分 41 秒」）をそのまま書けるようにするため。 */
private fun sec(minutes: Int, seconds: Int): Long = minutes * MIN + seconds * 1_000L
