package com.aki.tasktimer.domain

import com.aki.tasktimer.data.model.Rating
import com.aki.tasktimer.data.model.Session
import com.aki.tasktimer.data.model.SessionStatus
import com.aki.tasktimer.data.model.Tag
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private const val MIN = 60_000L
private const val START = 1_784_000_000_000L

private fun running(planned: Int = 45, total: Int = planned, id: Long = 1L): Session = Session(
    id = id,
    name = "ES執筆",
    tag = Tag.JOB_HUNTING,
    goal = "",
    plannedMinutes = planned,
    totalPlannedMinutes = total,
    startedAt = START,
    endedAt = null,
    status = SessionStatus.RUNNING,
    rating = null,
    ratingNote = null,
)

private fun done(): Session = running().copy(
    endedAt = START + 50 * MIN,
    status = SessionStatus.DONE,
    rating = Rating.NORMAL,
)

class IsOverdueTest {

    @Test
    fun `期限ちょうどは超過`() {
        assertTrue(isOverdue(running(45), START + 45 * MIN))
    }

    @Test
    fun `期限の 1 ミリ秒前はまだ超過ではない`() {
        assertFalse(isOverdue(running(45), START + 45 * MIN - 1))
    }

    @Test
    fun `延長後は新しい期限まで超過ではない`() {
        val extended = running(planned = 45, total = 55)
        assertFalse(isOverdue(extended, START + 47 * MIN))
        assertTrue(isOverdue(extended, START + 55 * MIN))
    }

    @Test
    fun `終わったセッションは超過扱いしない`() {
        assertFalse(isOverdue(done(), START + 100 * MIN))
    }

    @Test
    fun `進行中が無ければ超過ではない`() {
        assertFalse(isOverdue(null, START + 100 * MIN))
    }
}

class GuardDecisionTest {

    private val overdueNow = START + 47 * MIN

    @Test
    fun `超過していてブロック ON なら ALERT`() {
        assertEquals(GuardDecision.ALERT, guardDecision(running(), overdueNow, blockEnabled = true, dismissed = null))
    }

    @Test
    fun `ブロック OFF なら覆い無しの ALERT_NO_BLOCK`() {
        assertEquals(
            GuardDecision.ALERT_NO_BLOCK,
            guardDecision(running(), overdueNow, blockEnabled = false, dismissed = null),
        )
    }

    @Test
    fun `まだ期限前なら NONE`() {
        assertEquals(GuardDecision.NONE, guardDecision(running(), START + 10 * MIN, true, null))
    }

    @Test
    fun `非常口で閉じた同じ超過は NONE`() {
        val dismissed = DismissedOccurrence(sessionId = 1L, deadlineMillis = START + 45 * MIN)
        assertEquals(GuardDecision.NONE, guardDecision(running(), overdueNow, true, dismissed))
    }

    @Test
    fun `非常口の後に延長して期限が変わればまた ALERT`() {
        val dismissed = DismissedOccurrence(sessionId = 1L, deadlineMillis = START + 45 * MIN)
        val extended = running(planned = 45, total = 55)
        assertEquals(GuardDecision.ALERT, guardDecision(extended, START + 56 * MIN, true, dismissed))
    }

    @Test
    fun `非常口の記憶が別セッションのものなら効かない`() {
        val dismissed = DismissedOccurrence(sessionId = 99L, deadlineMillis = START + 45 * MIN)
        assertEquals(GuardDecision.ALERT, guardDecision(running(id = 1L), overdueNow, true, dismissed))
    }

    @Test
    fun `進行中が無ければ NONE`() {
        assertEquals(GuardDecision.NONE, guardDecision(null, overdueNow, true, null))
    }
}
