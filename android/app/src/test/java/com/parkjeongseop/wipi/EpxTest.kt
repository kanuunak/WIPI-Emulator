package com.parkjeongseop.wipi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

/** 업스케일(EPX/Scale2x) 규칙 — 셰이더(Epx.AGSL)와 같은 규칙의 Kotlin 구현 */
class EpxTest {
    private val o = 0xFF000000.toInt() // 검정
    private val x = 0xFFFFFFFF.toInt() // 흰색

    @Test
    fun flatAreaUnchanged() {
        val src = IntArray(9) { x }
        assertArrayEquals(IntArray(36) { x }, Epx.render(src, 3, 3, 6, 6))
    }

    @Test
    fun matchesScale2xAtExactlyTwice() {
        // 대각선 계단: 가운데 픽셀의 왼쪽 위 칸이 이웃(위·왼쪽) 색으로 채워진다
        val src = intArrayOf(
            o, x, x,
            x, o, x,
            x, x, o,
        )
        val out = Epx.render(src, 3, 3, 6, 6)
        // 가운데 원본 픽셀(1,1)=검정 → 2x에서 (2..3, 2..3). 위(1,0)=흰, 왼(0,1)=흰, 오른(2,1)=흰, 아래(1,2)=흰
        // 네 이웃이 모두 같으면 규칙상 "반대쪽과 달라야" 하므로 바뀌지 않는다(점 하나는 그대로)
        assertEquals(o, out[2 * 6 + 2])
        // 모서리 픽셀(0,0)=검정: 오른쪽(1,0)=흰, 아래(0,1)=흰, 위·왼쪽은 clamp로 자기 자신(검정)
        assertEquals(o, out[0])
    }

    @Test
    fun smoothsStaircaseCorner() {
        // ┐ 모양 계단: P=흰(1,1), 위 A=검정, 오른쪽 B=검정, 왼쪽 C=흰, 아래 D=흰
        //   x o o
        //   x x o
        //   x x x
        val src = intArrayOf(
            x, o, o,
            x, x, o,
            x, x, x,
        )
        val out = Epx.render(src, 3, 3, 6, 6)
        // P의 오른쪽 위 칸: A==B(검정), A!=C(흰), B!=D(흰) → 검정으로 채워 계단이 매끈해진다
        assertEquals(o, out[2 * 6 + 3])
        // 나머지 세 칸은 그대로 흰색
        assertEquals(x, out[2 * 6 + 2])
        assertEquals(x, out[3 * 6 + 2])
        assertEquals(x, out[3 * 6 + 3])
    }

    @Test
    fun straightEdgesStaySharp() {
        // 세로선: 위·아래 이웃이 같으므로 바뀌지 않는다 — 글자·테두리가 번지지 않는다
        val src = intArrayOf(
            x, o, x,
            x, o, x,
            x, o, x,
        )
        val out = Epx.render(src, 3, 3, 6, 6)
        for (row in 0 until 6) {
            assertEquals(x, out[row * 6 + 1])
            assertEquals(o, out[row * 6 + 2])
            assertEquals(o, out[row * 6 + 3])
            assertEquals(x, out[row * 6 + 4])
        }
    }

    @Test
    fun nonIntegerScaleStaysInBounds() {
        val src = IntArray(240 * 320) { if ((it % 240 + it / 240) % 7 == 0) o else x }
        val out = Epx.render(src, 240, 320, 585, 780) // 커버 화면 배율 2.44
        assertEquals(585 * 780, out.size)
    }
}
