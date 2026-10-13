package com.parkjeongseop.wipi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.util.Random

/** 명세 §7.2 KeyInputHub 필수 케이스 1~8 */
class KeyInputHubTest {
    /** sink 호출을 "down:KEY@시각" 형태로 순서대로 기록 */
    private val events = mutableListOf<String>()
    private var now = 1_000L
    private val scheduled = mutableListOf<Pair<Long, () -> Unit>>()
    private lateinit var hub: KeyInputHub

    private val sink = object : KeySink {
        override fun down(key: String) { events += "down:$key@$now" }
        override fun up(key: String) { events += "up:$key@$now" }
    }

    @Before
    fun setUp() {
        hub = KeyInputHub(sink, clock = { now }, schedule = { delay, action -> scheduled += (now + delay) to action })
    }

    /** 가짜 시계를 진행하며 예약된 action을 기한 순서대로 실행 */
    private fun advance(ms: Long) {
        val target = now + ms
        while (true) {
            val next = scheduled.filter { it.first <= target }.minByOrNull { it.first } ?: break
            scheduled.remove(next)
            now = next.first
            next.second()
        }
        now = target
    }

    @Test
    fun `1 single press and release sends down then up`() {
        hub.press("touch:0", "OK")
        advance(100)
        hub.release("touch:0")

        assertEquals(listOf("down:OK@1000", "up:OK@1100"), events)
        assertTrue(scheduled.isEmpty())
    }

    @Test
    fun `2 two sources on same key send one down and up only after both release`() {
        hub.press("touch:0", "5")
        advance(50)
        hub.press("touch:1", "5")
        advance(50)
        hub.release("touch:0")
        assertEquals(listOf("down:5@1000"), events)

        advance(50)
        hub.release("touch:1")
        assertEquals(listOf("down:5@1000", "up:5@1150"), events)
    }

    @Test
    fun `3 move sends up of old key then down of new key without delay`() {
        hub.press("touch:0", "UP")
        advance(10) // 최소 누름 시간 안이어도 슬라이드는 지연 없음
        hub.move("touch:0", "RIGHT")

        assertEquals(listOf("down:UP@1000", "up:UP@1010", "down:RIGHT@1010"), events)
        assertTrue(scheduled.isEmpty())

        advance(100)
        hub.release("touch:0")
        assertEquals("up:RIGHT@1110", events.last())
    }

    @Test
    fun `4 tap shorter than min hold delays up to down plus min hold`() {
        hub.press("touch:0", "2")
        advance(10)
        hub.release("touch:0")
        assertEquals(listOf("down:2@1000"), events)

        advance(29) // t=1039: 아직
        assertEquals(listOf("down:2@1000"), events)

        advance(1) // t=1040 = down + 40ms
        assertEquals(listOf("down:2@1000", "up:2@1040"), events)
    }

    @Test
    fun `5 re-press during pending up flushes up immediately then down`() {
        hub.press("touch:0", "5")
        advance(10)
        hub.release("touch:0") // up은 1040으로 미뤄짐
        advance(10)
        hub.press("touch:1", "5") // t=1020

        assertEquals(listOf("down:5@1000", "up:5@1020", "down:5@1020"), events)

        // 예전 예약이 실행돼도 새 누름을 떼지 않는다
        advance(100)
        assertEquals(3, events.size)

        hub.release("touch:1")
        assertEquals("up:5@1120", events.last())
    }

    @Test
    fun `6 releaseAll flushes pending and held keys and ignores later unknown releases`() {
        hub.press("touch:0", "4")
        advance(5)
        hub.release("touch:0") // 대기 중 up
        hub.press("hw:31", "6")
        hub.press("stick:LEFT", "LEFT")
        events.clear()

        hub.releaseAll()
        assertEquals(setOf("up:4@1005", "up:6@1005", "up:LEFT@1005"), events.toSet())
        assertEquals(3, events.size)
        assertFalse(hub.holds("hw:31"))

        hub.release("hw:31")
        hub.release("stick:LEFT")
        hub.move("touch:0", "1")
        advance(100) // 예전 대기 up 예약도 무동작이어야 함
        assertEquals(3, events.size)
    }

    @Test
    fun `7 hw and touch on same key are reference counted`() {
        hub.press("hw:19", "UP")
        hub.press("touch:3", "UP")
        advance(100)
        hub.release("hw:19")
        assertEquals(listOf("down:UP@1000"), events)
        assertTrue(hub.holds("touch:3"))

        hub.release("touch:3")
        assertEquals(listOf("down:UP@1000", "up:UP@1100"), events)

        // 반대 순서도 동일
        events.clear()
        hub.press("touch:3", "OK")
        hub.press("hw:66", "OK")
        advance(100)
        hub.release("touch:3")
        assertEquals(listOf("down:OK@1100"), events)
        hub.release("hw:66")
        assertEquals(listOf("down:OK@1100", "up:OK@1200"), events)
    }

    @Test
    fun `8 down and up always alternate per key under random input`() {
        val keys = listOf("UP", "DOWN", "OK", "5", "CLR")
        val sources = listOf("touch:0", "touch:1", "touch:2", "hw:19", "hw:23", "stick:UP")
        val rnd = Random(42)

        repeat(5_000) {
            val source = sources[rnd.nextInt(sources.size)]
            val key = keys[rnd.nextInt(keys.size)]
            when (rnd.nextInt(10)) {
                0, 1, 2 -> hub.press(source, key)
                3, 4, 5 -> hub.release(source)
                6, 7 -> hub.move(source, key)
                8 -> advance(rnd.nextInt(60).toLong())
                else -> if (rnd.nextInt(20) == 0) hub.releaseAll()
            }
        }
        sources.forEach(hub::release)
        advance(1_000)

        val down = HashMap<String, Boolean>()
        for (e in events) {
            val (type, key) = e.substringBefore('@').split(':', limit = 2)
            val isDown = down[key] ?: false
            if (type == "down") assertFalse("down 연속: $key", isDown) else assertTrue("up 연속: $key", isDown)
            down[key] = type == "down"
        }
        // 모든 source를 뗀 뒤엔 눌린 키가 하나도 없어야 한다
        assertTrue("눌린 채 남은 키: $down", down.values.none { it })
        assertTrue(events.size > 100)
    }
}
