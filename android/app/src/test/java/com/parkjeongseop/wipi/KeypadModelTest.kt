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

    // ── 방향키 조이스틱 (명세 §5.2.2 개정, 사용자 결정 2026-10-14) ──

    private val okCx get() = rect("OK").centerX
    private val okCy get() = rect("OK").centerY

    /** 같은 손가락으로 OK 중심 기준 점(dp)들을 차례로 지나며 hitTestMove를 적용, 매 점의 키 코드를 돌려준다 */
    private fun drag(start: String, vararg pointsDp: Pair<Float, Float>): List<String> {
        var current = key(start)
        return pointsDp.map { (dx, dy) ->
            current = layout.hitTestMove(current, okCx + dp(dx), okCy + dp(dy))
            current.code
        }
    }

    @Test
    fun dpadSlideIntoAnotherDirectionKey() {
        // ▲에서 ▶로 (명세 §5.2.5)
        assertEquals(key("RIGHT"), layout.hitTestMove(key("UP"), rect("RIGHT").centerX, rect("RIGHT").centerY))
        assertEquals(key("DOWN"), layout.hitTestMove(key("LEFT"), rect("DOWN").centerX, rect("DOWN").centerY))
        // 현재 키 사각형 안이면(모서리라도) 유지 — ▲ 왼쪽 아래 모서리
        assertEquals(key("UP"), layout.hitTestMove(key("UP"), rect("UP").left + dp(1f), rect("UP").bottom - dp(1f)))
    }

    @Test
    fun rockLeftRightAcrossCenterWithoutOk() {
        // ◀에서 시작해 중심 근처를 좌우로 흔든다: 중심선 너머 12dp마다 ◀↔▶, OK는 한 번도 안 눌림
        val path = drag(
            "LEFT",
            -40f to 0f, -11f to 0f, 11f to 0f, 13f to 0f, 30f to 0f, -11f to 0f, -13f to 0f, -30f to 0f, 13f to 0f,
        )
        assertEquals(listOf("LEFT", "LEFT", "LEFT", "RIGHT", "RIGHT", "RIGHT", "LEFT", "LEFT", "RIGHT"), path)
    }

    @Test
    fun verticalWobbleWhileRockingDoesNotPressUpDown() {
        // 좌우로 흔드는 손가락이 위아래로 ±20dp 남짓 흔들려도(OK 칸 높이 안) ▲▼로 안 바뀐다
        val path = drag("LEFT", 0f to 20f, 20f to -20f, 0f to -22f, -20f to 22f, 5f to 26f)
        assertEquals(listOf("LEFT", "RIGHT", "RIGHT", "LEFT", "LEFT"), path)
    }

    @Test
    fun axisSwitchNeedsLeavingOkBand() {
        // 좌우 → 상하: OK 칸 위/아래 경계(24dp) + 6dp를 넘고 그 축이 우세할 때
        assertEquals(listOf("LEFT", "DOWN"), drag("LEFT", 5f to 29f, 5f to 31f))
        assertEquals(listOf("RIGHT", "UP"), drag("RIGHT", 10f to -29f, 10f to -40f))
        // 상하 → 좌우: OK 칸 좌우 경계(약 53dp) + 6dp
        assertEquals(listOf("UP", "UP", "RIGHT"), drag("UP", 40f to -5f, 58f to -5f, 61f to -5f))
        // ▲↔▼ 반전도 중심선 너머 12dp
        assertEquals(listOf("UP", "DOWN"), drag("UP", 0f to 11f, 0f to 13f))
    }

    @Test
    fun okIsNeverReachedBySlideAndStaysFixed() {
        val directions = listOf("UP", "DOWN", "LEFT", "RIGHT")
        var y = 0f
        while (y <= layout.heightPx) {
            var x = 0f
            while (x <= layout.widthPx) {
                directions.forEach { d ->
                    val next = layout.hitTestMove(key(d), x, y)
                    assertTrue("$d -> ${next.code} at $x,$y", next.code in directions)
                }
                assertEquals(key("OK"), layout.hitTestMove(key("OK"), x, y))
                x += dp(4f)
            }
            y += dp(4f)
        }
    }

    @Test
    fun jitterAroundCenterIsIgnored() {
        // 데드존(±12dp) 안 떨림은 현재 방향 유지
        assertEquals(List(7) { "LEFT" }, drag("LEFT", -3f to 0f, 3f to 2f, -2f to -4f, 4f to 3f, -4f to 0f, 11f to 0f, 0f to 0f))
        assertEquals(List(7) { "RIGHT" }, drag("RIGHT", 3f to 0f, -3f to 2f, 2f to -4f, -4f to 3f, 4f to 0f, -11f to 0f, 0f to 0f))
    }

    @Test
    fun slideWithinNumCluster() {
        assertEquals(key("5"), layout.hitTestMove(key("4"), rect("5").centerX, rect("5").centerY))
        assertEquals(key("#"), layout.hitTestMove(key("9"), rect("#").centerX, rect("#").centerY))
    }

    @Test
    fun noSlideAcrossClusters() {
        // 방향키 → 숫자·기능키 금지: 방향키는 방향으로만 바뀐다 (▼에서 2 위 = ▼, ▲에서 ▷ 위 = 오른쪽 위 대각 → ▶)
        assertEquals(key("DOWN"), layout.hitTestMove(key("DOWN"), rect("2").centerX, rect("2").centerY))
        assertEquals(key("RIGHT"), layout.hitTestMove(key("UP"), rect("SOFT_R").centerX, rect("SOFT_R").centerY))
        // NUM → DPAD·FUNC 금지
        assertEquals(key("2"), layout.hitTestMove(key("2"), rect("DOWN").centerX, rect("DOWN").centerY))
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
