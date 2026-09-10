package com.aki.tasktimer.domain

import com.aki.tasktimer.data.model.Tag
import com.aki.tasktimer.data.model.TaskPreset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

private fun preset(id: Long, uses: Int = 1, pinned: Boolean = false, usedAt: Long = id) = TaskPreset(
    id = id,
    name = "task$id",
    tag = Tag.CHORE,
    lastGoal = null,
    lastPlannedMinutes = 30,
    useCount = uses,
    lastUsedAt = usedAt,
    isPinned = pinned,
)

private fun ids(list: List<TaskPreset>) = list.map { it.id }

class CandidateSectionsTest {

    // ---- 振り分け ----

    @Test
    fun `固定したものは使用回数に関係なく固定欄に全部出る`() {
        val presets = listOf(preset(1, uses = 1, pinned = true), preset(2, uses = 9, pinned = true), preset(3, uses = 5))
        val sections = buildCandidateSections(presets, recent = emptyList())
        assertEquals(listOf(1L, 2L), ids(sections.pinned))
        assertEquals(listOf(3L), ids(sections.frequent))
    }

    @Test
    fun `1回しか使っていない名前はよくやるに入らない`() {
        val presets = listOf(preset(1, uses = 3), preset(2, uses = 1))
        val sections = buildCandidateSections(presets, recent = emptyList())
        assertEquals(listOf(1L), ids(sections.frequent))
        assertTrue(sections.recent.isEmpty())
    }

    @Test
    fun `一度きりの名前は最近欄でだけ拾える`() {
        val oneOff = preset(2, uses = 1)
        val presets = listOf(preset(1, uses = 3), oneOff)
        val sections = buildCandidateSections(presets, recent = listOf(oneOff))
        assertEquals(listOf(1L), ids(sections.frequent))
        assertEquals(listOf(2L), ids(sections.recent))
    }

    @Test
    fun `よくやるに出ているものは最近に重ねて出さない`() {
        val frequent = preset(1, uses = 3)
        val oneOff = preset(2, uses = 1)
        val sections = buildCandidateSections(listOf(frequent, oneOff), recent = listOf(frequent, oneOff))
        assertEquals(listOf(1L), ids(sections.frequent))
        assertEquals(listOf(2L), ids(sections.recent))
    }

    @Test
    fun `固定したものは最近にも出さない`() {
        val pinned = preset(1, uses = 1, pinned = true)
        val sections = buildCandidateSections(listOf(pinned), recent = listOf(pinned))
        assertEquals(listOf(1L), ids(sections.pinned))
        assertTrue(sections.recent.isEmpty())
    }

    @Test
    fun `よくやると最近は合わせて上限まで。最近の一度きりの枠は先に確保される`() {
        val frequent = (1L..10L).map { preset(it, uses = 5) }
        val recent = listOf(preset(20, uses = 1), preset(21, uses = 1))
        val sections = buildCandidateSections(frequent + recent, recent = recent, limit = 8)
        assertEquals(6, sections.frequent.size)
        assertEquals(listOf(20L, 21L), ids(sections.recent))
    }

    @Test
    fun `固定は上限に数えない`() {
        val pinned = (1L..5L).map { preset(it, pinned = true) }
        val frequent = (6L..15L).map { preset(it, uses = 5) }
        val sections = buildCandidateSections(pinned + frequent, recent = emptyList(), limit = 8)
        assertEquals(5, sections.pinned.size)
        assertEquals(8, sections.frequent.size)
    }

    @Test
    fun `固定が無いときだけ固定の案内を出す`() {
        assertTrue(buildCandidateSections(listOf(preset(1, uses = 3)), emptyList()).showPinHint)
        assertFalse(buildCandidateSections(listOf(preset(1, pinned = true), preset(2, uses = 3)), emptyList()).showPinHint)
        // 候補が 1 つも無ければ案内も出さない
        assertFalse(buildCandidateSections(emptyList(), emptyList()).showPinHint)
    }

    // ---- 高さ（候補 44dp・隙間 8dp・見出し 22dp・欄の間 12dp） ----

    @Test
    fun `候補が無ければ高さ0`() {
        assertEquals(0f, candidateSectionsHeightDp(CandidateSections()), 0.01f)
    }

    @Test
    fun `欄の高さは見出しと行数で決まる`() {
        // 固定 3 個 = 2 行：22 + 44*2 + 8 = 118
        val sections = CandidateSections(pinned = (1L..3L).map { preset(it, pinned = true) })
        assertEquals(118f, candidateSectionsHeightDp(sections), 0.01f)
    }

    @Test
    fun `固定が無いときは案内の1行ぶんも数える`() {
        // 案内 22 ＋ 欄の間 12 ＋ よくやる 1 行（22 + 44）= 100
        val sections = CandidateSections(frequent = listOf(preset(1, uses = 3)))
        assertEquals(100f, candidateSectionsHeightDp(sections), 0.01f)
    }

    // ---- 余白に収める ----

    @Test
    fun `収まっていれば削らない`() {
        val sections = CandidateSections(pinned = listOf(preset(1, pinned = true)), frequent = listOf(preset(2, uses = 3)))
        assertEquals(sections, fitCandidateSections(sections, availableHeightDp = 1000f))
    }

    @Test
    fun `溢れたら画面の下の最近から削り、次によくやるを削る`() {
        val sections = CandidateSections(
            pinned = listOf(preset(1, pinned = true)),
            frequent = (2L..5L).map { preset(it, uses = 3) },
            recent = listOf(preset(6), preset(7)),
        )
        // 固定 1 行 66 ＋ 12 ＋ よくやる 1 行 66 = 144 まで入る
        val fitted = fitCandidateSections(sections, availableHeightDp = 144f)
        assertEquals(1, fitted.pinned.size)
        assertEquals(listOf(2L, 3L), ids(fitted.frequent))
        assertTrue(fitted.recent.isEmpty())
    }

    @Test
    fun `固定だけで溢れても固定は削らない`() {
        val sections = CandidateSections(
            pinned = (1L..6L).map { preset(it, pinned = true) },
            frequent = listOf(preset(7, uses = 3)),
        )
        val fitted = fitCandidateSections(sections, availableHeightDp = 50f)
        assertEquals(6, fitted.pinned.size)
        assertTrue(fitted.frequent.isEmpty())
    }
}
