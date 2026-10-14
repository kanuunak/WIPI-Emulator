package com.parkjeongseop.wipi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/** 명세 §7.2 EmulatorLayout 케이스 + §5.3 Fold5 커버 추정치 표 */
class EmulatorLayoutTest {
    private val eps = 0.01f

    /** 창 dp에서 인셋(dp)을 빼고 계산 */
    private fun layout(winW: Float, winH: Float, density: Float, top: Float = 0f, bottom: Float = 0f, left: Float = 0f, right: Float = 0f) =
        computeStackLayout(winW - left - right, winH - top - bottom, density)

    /** 명세 §7.2 단정: 음수 없음, 비율 오차 < 0.5%, keyH ≥ 40, 사각형이 가용 영역 안, 키패드 하단이 인셋 위 */
    private fun assertInvariants(l: StackLayout) {
        listOf(l.game, l.keypad, l.icons).forEach { r ->
            assertTrue("negative size $r", r.width >= 0f && r.height >= 0f)
            assertTrue("outside avail $r in ${l.availWdp}x${l.availHdp}", r.left >= -eps && r.top >= -eps && r.right <= l.availWdp + eps && r.bottom <= l.availHdp + eps)
        }
        assertTrue("keyH ${l.keyH}", l.keyH >= 40f && l.keyH <= 56f)
        assertTrue(l.fits)
        assertTrue("keypad bottom ${l.keypad.bottom}", l.keypad.bottom <= l.availHdp + eps)
        assertTrue("game overlaps keypad", l.game.bottom <= l.keypad.top + eps)
        assertTrue(l.gap >= 0f)
        if (l.game.height > 0f) {
            val ratio = l.game.width / l.game.height
            assertTrue("aspect $ratio", abs(ratio - 0.75f) / 0.75f < 0.005f)
            val pxRatio = l.gamePx.width.toFloat() / l.gamePx.height
            assertTrue("px aspect $pxRatio", abs(pxRatio - 0.75f) / 0.75f < 0.005f)
        }
    }

    private fun assertClose(expected: Float, actual: Float, tol: Float = 1f) =
        assertTrue("expected $expected ±$tol, got $actual", abs(expected - actual) <= tol)

    // ── §5.3 Fold5 커버 추정치 표 (배율 2.625, 344×882dp) ──

    @Test
    fun fold5CoverGestureNav() {
        val l = layout(344f, 882f, 2.625f, top = 28f, bottom = 24f)
        assertInvariants(l)
        assertEquals(830f, l.availHdp, eps)
        assertEquals(48f, l.keyH, eps)
        assertClose(340f, l.game.width)
        assertClose(454f, l.game.height)
        assertEquals(0.99f, l.game.width / l.availWdp, 0.01f) // 가로 채움 약 99%
    }

    @Test
    fun fold5CoverThreeButtonNav() {
        val l = layout(344f, 882f, 2.625f, top = 28f, bottom = 48f)
        assertInvariants(l)
        assertEquals(806f, l.availHdp, eps)
        assertEquals(48f, l.keyH, eps)
        assertClose(322f, l.game.width)
        assertClose(430f, l.game.height)
        assertEquals(0.94f, l.game.width / l.availWdp, 0.01f)
    }

    @Test
    fun fold5CoverDisplayZoom() {
        // 화면 확대(배율 3.0): 904×2316px → 301×772dp
        val l = layout(904f / 3f, 2316f / 3f, 3f, top = 28f, bottom = 24f)
        assertInvariants(l)
        assertTrue(l.keyH >= 40f)
        // 화면 축소(배율 2.25): 402×1029dp
        assertInvariants(layout(904f / 2.25f, 2316f / 2.25f, 2.25f, top = 28f, bottom = 24f))
    }

    // ── Fold5 메인 ──

    @Test
    fun fold5MainPortrait() {
        val l = layout(690f, 829f, 2.625f, top = 28f, bottom = 24f)
        assertInvariants(l)
        assertEquals(420f, l.keypad.width, eps) // 키패드 폭 상한, 가운데
        assertEquals((690f - 420f) / 2f, l.keypad.left, eps)
        assertClose(301f, l.game.width)
        assertClose(401f, l.game.height)
        assertEquals((690f - l.game.width) / 2f, l.game.left, eps) // 게임 가로 가운데
    }

    @Test
    fun fold5MainLandscapeShrinksKeys() {
        val l = layout(829f, 690f, 2.625f, top = 28f, bottom = 24f)
        assertInvariants(l)
        assertTrue("keys shrink to protect game: ${l.keyH}", l.keyH < 48f)
        // 45% 이상을 확보했거나, 이미 최소 키 높이
        assertTrue(l.game.height >= l.availHdp * 0.45f || l.keyH == 40f)
        // 1dp 더 키우면 45% 미만이 된다 (필요한 만큼만 줄였는지)
        val bigger = l.availHdp - keypadHeight(l.keyH + 1f)
        assertTrue(bigger < l.availHdp * 0.45f)
    }

    // ── 기타 기기 ──

    @Test
    fun ordinaryPhone() {
        assertInvariants(layout(411f, 914f, 2.625f, top = 24f, bottom = 48f))
        assertInvariants(layout(411f, 914f, 2.625f, top = 24f, bottom = 24f))
    }

    @Test
    fun wideCover() {
        // 10:16 와이드 커버 (약 454×717dp)
        val l = layout(454f, 717f, 2.625f, top = 28f, bottom = 24f)
        assertInvariants(l)
        assertTrue(l.game.height >= l.availHdp * 0.45f || l.keyH == 40f)
    }

    // ── 알고리즘 단계 ──

    @Test
    fun widthLimitedGrowsKeysThenGap() {
        // 폭 300dp → 게임 300×400, 키패드 376 → 남는 224dp: keyH +8(=56 상한), 나머지 168dp는 여백
        val l = computeStackLayout(300f, 1000f, 3f)
        assertInvariants(l)
        assertEquals(300f, l.game.width, eps)
        assertEquals(400f, l.game.height, eps)
        assertEquals(56f, l.keyH, eps)
        assertEquals(1000f - keypadHeight(56f) - 400f, l.gap, eps)
        assertEquals(1000f, l.keypad.bottom, eps) // 키패드는 아래 정렬
        assertEquals(0f, l.game.top, eps) // 게임은 위
    }

    @Test
    fun widthLimitedGrowsOnlyBySpare() {
        // 남는 공간이 21dp → keyH +3
        val h = 300f / 0.75f + keypadHeight(48f) + 21f
        val l = computeStackLayout(300f, h, 3f)
        assertInvariants(l)
        assertEquals(51f, l.keyH, eps)
        assertEquals(0f, l.gap, eps)
    }

    @Test
    fun tinyWindowHasNoNegativeSizes() {
        val l = computeStackLayout(200f, 300f, 2f)
        assertFalse(l.fits)
        assertEquals(40f, l.keyH, eps)
        assertTrue(l.game.width >= 0f && l.game.height >= 0f && l.gap >= 0f)
        assertTrue(computeStackLayout(-5f, -5f, 2f).game.height >= 0f)
    }

    @Test
    fun iconsInsideGameTopRight() {
        val l = layout(344f, 882f, 2.625f, top = 28f, bottom = 24f)
        assertEquals(l.game.right, l.icons.right, eps)
        assertEquals(l.game.top, l.icons.top, eps)
        assertEquals(48f, l.icons.height, eps)
    }

    @Test
    fun summaryIsLocaleIndependent() {
        val s = computeStackLayout(344f, 830f, 2.625f).summary()
        assertTrue(s, s.contains("keyH=48.0") && s.contains("d=2.625"))
    }
}
