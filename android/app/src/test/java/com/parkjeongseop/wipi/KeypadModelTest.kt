package com.parkjeongseop.wipi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 명세 §7.2 KeypadModel 케이스. Fold5 커버 실측(폭 344dp, keyH 48dp, 배율 2.625) 기준. */
class KeypadModelTest {
    private val density = 2.625f
    private val keyH = 48f
    private val layout = KeypadModel.layout(344f * density, keypadHeight(keyH) * density, KeypadMetrics(density))
    private val eps = 0.01f

    private fun dp(v: Float) = v * density
    private fun rect(code: String) = layout.rectOf(code)!!
    private fun key(code: String) = KeypadModel.KEYS.first { it.code == code }

    // ── 키 정의·배치 ──

    @Test
    fun keyDefinitionsMatchOriginalArrangement() {
        assertEquals(
            listOf("SOFT_L", "UP", "SOFT_R", "LEFT", "OK", "RIGHT", "CALL", "DOWN", "CLR", "1", "2", "3", "4", "5", "6", "7", "8", "9", "*", "0", "#"),
            KeypadModel.KEYS.map { it.code },
        )
        assertFalse(KeypadModel.KEYS.any { it.code == "HANGUP" })
        assertEquals(setOf("UP", "DOWN", "LEFT", "RIGHT", "OK"), KeypadModel.KEYS.filter { it.cluster == KeyCluster.DPAD }.map { it.code }.toSet())
        assertEquals(setOf("SOFT_L", "SOFT_R", "CALL", "CLR"), KeypadModel.KEYS.filter { it.cluster == KeyCluster.FUNC }.map { it.code }.toSet())
        assertEquals(12, KeypadModel.KEYS.count { it.cluster == KeyCluster.NUM })
    }

    @Test
    fun layoutUsesKeyHeightSpacingAndMargin() {
        assertEquals(21, layout.keys.size)
        layout.keys.forEach { r ->
            assertEquals("row height ${r.key.code}", dp(keyH), r.bottom - r.top, eps)
            assertTrue("inside ${r.key.code}", r.left >= dp(KEYPAD_MARGIN_DP) - eps && r.right <= layout.widthPx - dp(KEYPAD_MARGIN_DP) + eps)
            assertTrue("inside ${r.key.code}", r.top >= dp(KEYPAD_MARGIN_DP) - eps && r.bottom <= layout.heightPx - dp(KEYPAD_MARGIN_DP) + eps)
        }
        // 이웃 키 사이 간격 = 4dp
        assertEquals(dp(KEYPAD_SPACING_DP), rect("2").left - rect("1").right, eps)
        assertEquals(dp(KEYPAD_SPACING_DP), rect("4").top - rect("1").bottom, eps)
        // 사각형끼리 겹치지 않음
        for (a in layout.keys) for (b in layout.keys) if (a !== b) {
            val overlap = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
            assertFalse("${a.key.code} overlaps ${b.key.code}", overlap)
        }
        // 방향키 블록 = 위 여백 + 3행 + 2간격 = 160dp (제스처 제외 한도 200dp 이내)
        assertEquals(dp(160f), layout.dpadBlockBottom, eps)
    }

    // ── 처음 누를 때 ──

    @Test
    fun keyCenterHitsThatKey() {
        layout.keys.forEach { r -> assertEquals(r.key, layout.hitTestInitial(r.centerX, r.centerY)) }
    }

    @Test
    fun gapPointSnapsToNearestKey() {
        // 1과 2 사이 간격(4dp): 1의 오른쪽 경계에서 1dp → 1, 2의 왼쪽 경계에서 1dp → 2
        val y = rect("1").centerY
        assertEquals(key("1"), layout.hitTestInitial(rect("1").right + dp(1f), y))
        assertEquals(key("2"), layout.hitTestInitial(rect("2").left - dp(1f), y))
        // 4와 7 사이 세로 간격
        val x = rect("4").centerX
        assertEquals(key("4"), layout.hitTestInitial(x, rect("4").bottom + dp(1.5f)))
        assertEquals(key("7"), layout.hitTestInitial(x, rect("7").top - dp(1.5f)))
    }

    @Test
    fun snapWithinSixDpOnlyOtherwiseNothing() {
        val y = rect("4").centerY
        // 바깥 여백(8dp) 안: 키에서 5dp → 그 키, 7dp → 없음
        assertEquals(key("4"), layout.hitTestInitial(rect("4").left - dp(5f), y))
        assertEquals(key("6"), layout.hitTestInitial(rect("6").right + dp(5.9f), y))
        assertNull(layout.hitTestInitial(rect("4").left - dp(7f), y))
        assertNull(layout.hitTestInitial(rect("6").right + dp(6.5f), y))
        // 모서리: 가로·세로 각 5dp → 대각 거리 7.07dp → 없음
        assertNull(layout.hitTestInitial(rect("SOFT_L").left - dp(5f), rect("SOFT_L").top - dp(5f)))
        // 키패드 밖
        assertNull(layout.hitTestInitial(-dp(20f), y))
    }

    // ── 누른 채 움직일 때 ──

    @Test
    fun slideWithinDpadChangesKeyOnlyPastHysteresis() {
        val up = key("UP")
        val ok = rect("OK")
        // OK 사각형 위쪽 경계에서 3dp 안 → 아직 UP
        assertEquals(up, layout.hitTestMove(up, ok.centerX, ok.top + dp(3f)))
        // 간격 → UP 유지
        assertEquals(up, layout.hitTestMove(up, ok.centerX, ok.top - dp(2f)))
        // 6dp 이상 안쪽 → OK
        assertEquals(key("OK"), layout.hitTestMove(up, ok.centerX, ok.top + dp(6.5f)))
        // ▲에서 ▶로 (명세 §5.2.5)
        assertEquals(key("RIGHT"), layout.hitTestMove(up, rect("RIGHT").centerX, rect("RIGHT").centerY))
    }

    @Test
    fun jitterAroundBoundaryIsIgnored() {
        // ◀ / OK 경계 근처에서 ±3dp 떨림: 어느 쪽에서 시작해도 바뀌지 않는다
        val boundary = (rect("LEFT").right + rect("OK").left) / 2f
        val y = rect("OK").centerY
        var current = key("LEFT")
        listOf(-3f, 3f, -2f, 4f, -4f, 5f, 0f).forEach { d ->
            current = layout.hitTestMove(current, boundary + dp(d), y)
            assertEquals(key("LEFT"), current)
        }
        current = key("OK")
        listOf(3f, -3f, 2f, -4f, 4f, -5f, 0f).forEach { d ->
            current = layout.hitTestMove(current, boundary + dp(d), y)
            assertEquals(key("OK"), current)
        }
    }

    @Test
    fun slideWithinNumCluster() {
        assertEquals(key("5"), layout.hitTestMove(key("4"), rect("5").centerX, rect("5").centerY))
        assertEquals(key("#"), layout.hitTestMove(key("9"), rect("#").centerX, rect("#").centerY))
    }

    @Test
    fun noSlideAcrossClusters() {
        // DPAD → NUM, NUM → DPAD 금지
        assertEquals(key("DOWN"), layout.hitTestMove(key("DOWN"), rect("2").centerX, rect("2").centerY))
        assertEquals(key("2"), layout.hitTestMove(key("2"), rect("DOWN").centerX, rect("DOWN").centerY))
        // DPAD → FUNC 금지 (▲에서 ▷ 위로 미끄러져도 ▲ 유지)
        assertEquals(key("UP"), layout.hitTestMove(key("UP"), rect("SOFT_R").centerX, rect("SOFT_R").centerY))
        assertEquals(key("1"), layout.hitTestMove(key("1"), rect("CALL").centerX, rect("CALL").centerY))
    }

    @Test
    fun funcKeysNeverSlide() {
        listOf("SOFT_L", "SOFT_R", "CALL", "CLR").forEach { code ->
            val k = key(code)
            layout.keys.forEach { r -> assertEquals(k, layout.hitTestMove(k, r.centerX, r.centerY)) }
        }
    }

    // ── 영역 이탈 ──

    @Test
    fun leavingBeyond32DpReleases() {
        val w = layout.widthPx
        val h = layout.heightPx
        val cy = h / 2f
        val cx = w / 2f
        assertFalse(layout.isBeyondLeave(cx, cy))
        assertFalse(layout.isBeyondLeave(-dp(31f), cy))
        assertTrue(layout.isBeyondLeave(-dp(33f), cy))
        assertFalse(layout.isBeyondLeave(w + dp(31f), cy))
        assertTrue(layout.isBeyondLeave(w + dp(33f), cy))
        assertFalse(layout.isBeyondLeave(cx, -dp(31f)))
        assertTrue(layout.isBeyondLeave(cx, -dp(33f)))
        assertFalse(layout.isBeyondLeave(cx, h + dp(31f)))
        assertTrue(layout.isBeyondLeave(cx, h + dp(33f)))
    }
}
