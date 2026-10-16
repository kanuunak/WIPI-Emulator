package com.parkjeongseop.wipi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.floor

/** 스캔라인·LCD 선 위치: 실기기(Debug 26) "규칙 없는 줄/두꺼운 바둑판" — 비정수 배율에서 두께가 들쭉날쭉했다 */
class ScreenLinesTest {
    /** nearest 확대에서 화면 px y가 그리는 원본 행 */
    private fun srcRow(y: Int, length: Int, cells: Int) = floor((y + 0.5f) * cells / length).toInt()

    @Test
    fun uniformThicknessOnNonIntegerScale() {
        // 커버 실측: 게임 903x1204px(배율 3.76)
        for ((len, cells) in listOf(1204 to 320, 903 to 240, 1694 to 320, 1270 to 240, 780 to 320)) {
            val lines = ScreenLines.lines(len, cells, 0.34f)
            assertEquals(cells, lines.size)
            val thickness = lines.map { it.second - it.first }.toSet()
            assertEquals("두께가 모두 같아야 한다 ($len/$cells): $thickness", 1, thickness.size)
            assertEquals(len, lines.last().second)
        }
    }

    @Test
    fun linesSitAtTheBottomOfEachSourceRow() {
        val len = 1204
        val cells = 320
        val lines = ScreenLines.lines(len, cells, 0.34f)
        lines.forEachIndexed { r, (a, b) ->
            // 선의 모든 px는 그 원본 행(r)을 그리는 px, 선 바로 다음 px는 다음 행
            for (y in a until b) assertEquals("row $r y $y", r, srcRow(y, len, cells))
            if (b < len) assertEquals(r + 1, srcRow(b, len, cells))
            // 선 위에 그 행의 밝은 px가 최소 1px 남는다
            assertTrue(a - 1 >= 0 && srcRow(a - 1, len, cells) == r)
        }
    }

    @Test
    fun lcdIsOnePixel() {
        ScreenLines.lines(903, 240, 0f).forEach { (a, b) -> assertEquals(1, b - a) }
    }

    @Test
    fun tooSmallScaleDrawsNothing() {
        assertTrue(ScreenLines.lines(320, 320, 0.34f).isEmpty()) // 배율 1
        assertTrue(ScreenLines.lines(400, 320, 0.34f).isEmpty()) // 배율 1.25
        assertTrue(ScreenLines.lines(0, 320, 0.34f).isEmpty())
    }
}
