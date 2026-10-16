package com.parkjeongseop.wipi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 배치 편집 모델: 저장 형식, 옮기기·크기, 나누기/합치기, 그릴 배치로 변환 */
class LayoutEditModelTest {
    private val eps = 0.11f // 저장은 0.1dp 단위
    private val w = 829f
    private val h = 645f
    private val split = CustomLayout.from(computeScreenLayout(LayoutMode.SPLIT, w, h, 2.625f))
    private val stack = CustomLayout.from(computeScreenLayout(LayoutMode.STACK, 344f, 830f, 2.625f))

    private fun assertRectEquals(a: RectDp, b: RectDp) {
        assertEquals(a.left, b.left, eps); assertEquals(a.top, b.top, eps)
        assertEquals(a.width, b.width, eps); assertEquals(a.height, b.height, eps)
    }

    private fun inside(r: RectDp, w: Float, h: Float) = r.left >= -0.01f && r.top >= -0.01f && r.right <= w + 0.01f && r.bottom <= h + 0.01f

    @Test
    fun serializeRoundTrip() {
        for (l in listOf(split, stack)) {
            val back = CustomLayout.parse(l.serialize())
            assertNotNull(back)
            assertRectEquals(l.game, back!!.game)
            assertRectEquals(l.icons, back.icons)
            assertEquals(l.pads.map { it.rows }, back.pads.map { it.rows })
            l.pads.zip(back.pads).forEach { (a, b) -> assertRectEquals(a.rect, b.rect) }
        }
        assertEquals(listOf(PadRows.UPPER, PadRows.LOWER), split.pads.map { it.rows })
        assertEquals(listOf(PadRows.ALL), stack.pads.map { it.rows })
    }

    @Test
    fun parseRejectsBrokenValues() {
        assertNull(CustomLayout.parse(""))
        assertNull(CustomLayout.parse("game=1,2,3"))
        // 키가 빠짐(UPPER만)
        assertNull(CustomLayout.parse("game=0,0,240,320;icons=0,0,96,48;pad=UPPER:0,0,100,100"))
        // 키 중복(ALL + UPPER)
        assertNull(CustomLayout.parse("game=0,0,240,320;icons=0,0,96,48;pad=ALL:0,0,100,100;pad=UPPER:0,0,100,100"))
        assertNull(CustomLayout.parse("game=0,0,240,320;icons=0,0,96,48;pad=NOPE:0,0,100,100"))
    }

    @Test
    fun moveClampsAndSnaps() {
        val r = RectDp(100f, 100f, 200f, 100f)
        assertRectEquals(RectDp(0f, 0f, 200f, 100f), LayoutEdit.move(r, -500f, -500f, w, h)) // 영역 안으로
        assertRectEquals(RectDp(w - 200f, h - 100f, 200f, 100f), LayoutEdit.move(r, 5000f, 5000f, w, h))
        assertEquals(0f, LayoutEdit.move(r, -95f, 0f, w, h).left, 0f) // 가장자리 8dp 이내면 붙임
        assertEquals((w - 200f) / 2f, LayoutEdit.move(r, (w - 200f) / 2f - 100f + 5f, 0f, w, h).left, 0.01f) // 가운데
        assertEquals(150f, LayoutEdit.move(r, 50f, 0f, w, h).left, 0.01f) // 그 밖은 그대로
    }

    @Test
    fun resizeGameKeepsAspectAndLimits() {
        val g = RectDp(100f, 0f, 300f, 400f)
        val bigger = LayoutEdit.resizeGame(g, 60f, 0f, w, h)
        assertEquals(360f, bigger.width, 0.01f)
        assertEquals(480f, bigger.height, 0.01f)
        val clamped = LayoutEdit.resizeGame(g, 5000f, 5000f, w, h)
        assertTrue(inside(clamped, w, h))
        assertEquals(0.75f, clamped.width / clamped.height, 0.001f)
        val small = LayoutEdit.resizeGame(g, -5000f, 0f, w, h)
        assertEquals(LayoutEdit.MIN_GAME_W_DP, small.width, 0.01f)
    }

    @Test
    fun resizePadLimits() {
        val p = RectDp(0f, 400f, 172f, 172f)
        val tiny = LayoutEdit.resizePad(p, 3, -1000f, -1000f, w, h)
        assertEquals(padWidth(LayoutEdit.MIN_KEY_DP), tiny.width, 0.01f)
        assertEquals(padHeight(3, LayoutEdit.MIN_KEY_DP), tiny.height, 0.01f)
        val huge = LayoutEdit.resizePad(p, 3, 5000f, 5000f, w, h)
        assertTrue(inside(huge, w, h))
    }

    @Test
    fun toggleSplitKeepsAllKeysAndStaysInside() {
        val merged = LayoutEdit.toggleSplit(split, w, h)
        assertEquals(listOf(PadRows.ALL), merged.pads.map { it.rows })
        val again = LayoutEdit.toggleSplit(merged, w, h)
        assertEquals(listOf(PadRows.UPPER, PadRows.LOWER), again.pads.map { it.rows })
        // 커버에서도 나눌 수 있다(사용자 선택)
        val coverSplit = LayoutEdit.toggleSplit(stack, 344f, 830f)
        assertEquals(2, coverSplit.pads.size)
        (merged.pads + again.pads).forEach { assertTrue("${it.rect}", inside(it.rect, w, h)) }
        coverSplit.pads.forEach { assertTrue("${it.rect}", inside(it.rect, 344f, 830f)) }
        // 나눈 두 키패드는 겹치지 않는다(실수로 커버에서 화면 폭 전체 둘이 겹쳤다)
        assertTrue(!LayoutEdit.overlaps(coverSplit.pads[0].rect, coverSplit.pads[1].rect))
        assertNotNull(CustomLayout.parse(coverSplit.serialize()))
    }

    @Test
    fun toScreenLayoutClampsOrRejects() {
        val l = split.toScreenLayout(w, h, 2.625f)!!
        assertEquals(LayoutMode.CUSTOM, l.mode)
        assertEquals(KeypadModel.KEYS, l.pads.flatMap { it.rows.flatten() })
        assertEquals(1270, l.gamePx.width) // 484dp × 2.625
        // 화면이 조금 작아지면 같은 비율로 줄여 안으로 들인다(게임 3:4 유지)
        val shrunk = split.toScreenLayout(w - 10f, h - 10f, 2.625f)!!
        (listOf(shrunk.game, shrunk.icons) + shrunk.pads.map { it.rect }).forEach { assertTrue("$it", inside(it, w - 10f, h - 10f)) }
        assertEquals(0.75f, shrunk.game.width / shrunk.game.height, 0.001f)
        // 화면이 커지면 그대로(위치·크기 유지)
        assertEquals(split.game.width, split.toScreenLayout(w + 50f, h + 50f, 2.625f)!!.game.width, 0.01f)
        // 훨씬 작은 화면(커버)에는 쓰지 않는다 → 자동 배치
        assertNull(split.toScreenLayout(344f, 830f, 2.625f))
    }

    @Test
    fun keyPerScreenShape() {
        assertEquals("829x645", CustomLayout.key(829.3f, 645.4f))
        assertTrue(CustomLayout.key(829f, 645f) != CustomLayout.key(344f, 830f))
    }
}
