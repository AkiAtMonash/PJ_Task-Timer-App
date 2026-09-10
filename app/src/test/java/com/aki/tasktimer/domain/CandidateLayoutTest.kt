package com.aki.tasktimer.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * 候補ブロックの見積もり。1 行ぶんの高さは 上下余白 10*2 + 行の高さ 20 = 40dp、行間は 10dp。
 */
private fun spec(
    width: Float = 360f,
    height: Float,
    maxBlockWidth: Float = 175f,
) = CandidateLayoutSpec(
    containerWidthDp = width,
    availableHeightDp = height,
    maxBlockWidthDp = maxBlockWidth,
)

class CandidateLayoutTest {

    @Test
    fun `候補が無ければ0`() {
        assertEquals(0, visibleCandidateCount(emptyList(), spec(height = 200f)))
    }

    @Test
    fun `高さが無ければ1つも出さない`() {
        assertEquals(0, visibleCandidateCount(listOf("筋トレ"), spec(height = 0f)))
    }

    @Test
    fun `1行ぶんの高さなら横に並ぶぶんだけ出る`() {
        // 「筋トレ」= 全角3文字 → 42 + 左右余白24 = 66dp。66*4 + 行間10*3 = 294 で 4 個、5 個目は 370 で溢れる。
        val names = List(8) { "筋トレ" }
        assertEquals(4, visibleCandidateCount(names, spec(height = 40f)))
    }

    @Test
    fun `2行ぶんの高さなら次の行も出る`() {
        val names = List(8) { "筋トレ" }
        assertEquals(8, visibleCandidateCount(names, spec(height = 90f)))
    }

    @Test
    fun `長い名前は折り返して2行ぶんの高さを取る`() {
        // 全角13文字 → 182dp。ブロック内幅 151dp に収まらないので 2 行 = 60dp。
        val name = "英語のリスニングを毎日"
        assertEquals(0, visibleCandidateCount(listOf(name), spec(height = 59f)))
        assertEquals(1, visibleCandidateCount(listOf(name), spec(height = 60f)))
    }

    @Test
    fun `折り返しは3行で頭打ちになる`() {
        val name = "あ".repeat(60)
        // 3 行 = 上下余白 20 + 20*3 = 80dp。何文字あってもこれ以上は高くならない。
        assertEquals(0, visibleCandidateCount(listOf(name), spec(height = 79f)))
        assertEquals(1, visibleCandidateCount(listOf(name), spec(height = 80f)))
    }

    @Test
    fun `同じ行に高いブロックが来たら行の高さはそちらに合わせる`() {
        // 短い1行（40dp）と長い2行（60dp）が同じ行に並ぶ。行の高さは 60dp になるので 59dp では 1 個だけ。
        val names = listOf("筋トレ", "英語のリスニングを毎日")
        assertEquals(1, visibleCandidateCount(names, spec(height = 59f)))
        assertEquals(2, visibleCandidateCount(names, spec(height = 60f)))
    }

    @Test
    fun `入りきらない分は後ろから落ちる`() {
        // 使用頻度順に渡す前提なので、返り値は「先頭から何個か」で足りる。
        val names = listOf("朝食", "筋トレ", "皿洗い", "散歩")
        assertEquals(3, visibleCandidateCount(names, spec(width = 220f, height = 40f)))
    }
}
