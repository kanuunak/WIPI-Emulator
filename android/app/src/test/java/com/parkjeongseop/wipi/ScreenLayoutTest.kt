package com.parkjeongseop.wipi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 좌우 분할(SPLIT)과 AUTO 선택. 실측 창 크기는 HUD 기록(명세 §5.3, HANDOFF §3) */
class ScreenLayoutTest {
    private val eps = 0.01f

    private fun overlaps(a: RectDp, b: RectDp) = a.left < b.right - eps && b.left < a.right - eps && a.top < b.bottom - eps && b.top < a.bottom - eps

    private fun assertSane(l: ScreenLayout) {
        val all = listOf(l.game, l.icons) + l.pads.map { it.rect }
        all.forEach { r ->
            assertTrue("negative $r", r.width >= 0f && r.height >= 0f)
            assertTrue("outside $r in ${l.availWdp}x${l.availHdp}", r.left >= -eps && r.top >= -eps && r.right <= l.availWdp + eps && r.bottom <= l.availHdp + eps)
        }
        l.pads.forEach { assertTrue("game overlaps pad ${it.rect}", !overlaps(l.game, it.rect)) }
        if (l.iconsOutside) {
            assertTrue(!overlaps(l.icons, l.game))
            l.pads.forEach { assertTrue("icons overlap pad", !overlaps(l.icons, it.rect)) }
        }
        val ratio = l.game.width / l.game.height
        assertTrue("aspect $ratio", abs(ratio - 0.75f) / 0.75f < 0.005f)
        assertEquals(KeypadModel.KEYS, l.pads.flatMap { it.rows.flatten() }) // 모든 키가 정확히 한 번씩
    }

    @Test
    fun foldMainLandscapeSplitsAndGameGetsBig() {
        // 실측(Debug 18 HUD): 메인 가로 avail 829x645dp
        val l = computeScreenLayout(LayoutMode.AUTO, 829f, 645.3f, 2.625f)
        assertEquals(LayoutMode.SPLIT, l.mode)
        assertSane(l)
        assertEquals(645.3f, l.game.height, eps) // 높이 가득
        val stack = computeStackLayout(829f, 645.3f, 2.625f)
        assertTrue("game ${l.game.height} vs stack ${stack.game.height}", l.game.height > 2f * stack.game.height)
        assertTrue("keyH ${l.keyH}", l.keyH >= SPLIT_KEY_MIN_DP && l.keyH <= SPLIT_KEY_MAX_H_DP)
        assertEquals(2, l.pads.size)
        assertEquals(KeypadModel.UPPER_ROWS, l.pads[0].rows) // 왼쪽 = 방향키
        assertTrue(l.pads[0].rect.right <= l.game.left + eps && l.pads[1].rect.left >= l.game.right - eps)
        assertTrue(l.iconsOutside)
    }

    @Test
    fun coverScreenStaysStacked() {
        // 커버(344dp 폭): 양옆에 키 자리가 없다
        assertNull(computeSplitLayout(344f, 830f, 2.625f))
        assertEquals(LayoutMode.STACK, computeScreenLayout(LayoutMode.AUTO, 344f, 830f, 2.625f).mode)
        // SPLIT을 강제해도 들어가지 않으면 STACK
        assertEquals(LayoutMode.STACK, computeScreenLayout(LayoutMode.SPLIT, 344f, 830f, 2.625f).mode)
    }

    @Test
    fun forcedModes() {
        assertEquals(LayoutMode.STACK, computeScreenLayout(LayoutMode.STACK, 829f, 645f, 2.625f).mode)
        val split = computeScreenLayout(LayoutMode.SPLIT, 829f, 645f, 2.625f)
        assertEquals(LayoutMode.SPLIT, split.mode)
        assertSane(split)
    }

    @Test
    fun narrowWideWindowShrinksGameToFitPads() {
        // 높이에 비해 폭이 모자라면 게임을 줄여 양옆 최소 키패드 폭을 확보
        val l = computeSplitLayout(600f, 645f, 2.625f)
        assertNotNull(l)
        assertSane(l!!)
        assertEquals(600f - 2 * padWidth(SPLIT_KEY_MIN_DP), l.game.width, eps)
        assertTrue(l.keyH >= SPLIT_KEY_MIN_DP - eps)
    }

    @Test
    fun keysCappedOnVeryWideWindow() {
        val l = computeSplitLayout(2000f, 600f, 2f)!!
        assertSane(l)
        assertTrue(l.keyH <= SPLIT_KEY_MAX_H_DP + eps)
        assertTrue(l.pads.all { it.rect.width <= padWidth(SPLIT_KEY_MAX_W_DP) + eps })
    }

    @Test
    fun shortWindowFitsKeysByHeight() {
        // 낮은 창: 숫자 키패드(4행)가 높이에 들어가도록 키 높이를 줄인다(하한 미만이면 SPLIT 안 씀)
        val l = computeSplitLayout(900f, 240f, 2f)
        if (l != null) {
            assertSane(l)
            assertTrue(l.pads.all { it.rect.height <= 240f + eps })
        }
        assertNull(computeSplitLayout(900f, 150f, 2f))
    }

    @Test
    fun stackConversionKeepsAllKeysInOnePad() {
        val l = computeScreenLayout(LayoutMode.STACK, 344f, 830f, 2.625f)
        assertEquals(1, l.pads.size)
        assertEquals(KeypadModel.ROWS, l.pads.single().rows)
        assertTrue(l.summary.startsWith("STACK"))
    }

    @Test
    fun keypadLayoutForSubsetRows() {
        val d = 2f
        val upper = KeypadModel.layout(padWidth(50f) * d, padHeight(3, 50f) * d, KeypadMetrics(d), KeypadModel.UPPER_ROWS)
        assertEquals(9, upper.keys.size)
        assertEquals(50f * d, upper.keys.first().bottom - upper.keys.first().top, 0.01f)
        assertTrue(upper.dpadBlockBottom > 0f)
        val lower = KeypadModel.layout(padWidth(50f) * d, padHeight(4, 50f) * d, KeypadMetrics(d), KeypadModel.LOWER_ROWS)
        assertEquals(12, lower.keys.size)
        assertEquals(0f, lower.dpadBlockBottom, 0f) // 숫자만 → 방향키 블록 없음
    }
}
