package com.parkjeongseop.wipi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.File

class CpuUsageTest {
    /** 실제 /proc/<pid>/stat 형식(52필드). utime=14번째 1200, stime=15번째 300 */
    private val stat = "12345 (ePARK.foldretro) S 678 678 0 0 -1 1077952832 52000 0 300 0 1200 300 0 0 10 -10 40 0 " +
        "123456 15000000000 60000 18446744073709551615 1 1 0 0 0 0 4612 1 1073775864 0 0 0 17 5 0 0 0 0 0 0 0 0 0 0 0 0 0"

    @Test
    fun parsesUtimePlusStime() {
        assertEquals(1500L, CpuUsage.parseTicks(stat))
    }

    @Test
    fun commWithSpacesAndParens() {
        // comm은 공백·괄호를 담을 수 있다 → 마지막 ')' 뒤부터 센다
        val tricky = stat.replace("(ePARK.foldretro)", "(wie emu (x) )")
        assertEquals(1500L, CpuUsage.parseTicks(tricky))
    }

    @Test
    fun malformedIsNull() {
        assertNull(CpuUsage.parseTicks(""))
        assertNull(CpuUsage.parseTicks("12345 (x) S 1 2 3"))
        assertNull(CpuUsage.parseTicks(stat.replace(" 1200 300 ", " abc 300 ")))
    }

    @Test
    fun parsesThisJvmProcStat() {
        // 호스트(Linux)에서 단위 테스트가 돌면 실제 파일 형식으로도 확인
        val self = File("/proc/self/stat")
        if (self.exists()) assertNotNull(CpuUsage.parseTicks(self.readText()))
    }

    @Test
    fun percentIsPerCore() {
        // 100틱/초에서 1초 동안 12틱 = 코어 하나의 12%
        assertEquals(12f, CpuUsage.percent(1000, 1012, 5_000, 6_000, 100)!!, 0.001f)
        // 두 코어를 꽉 쓰면 200%
        assertEquals(200f, CpuUsage.percent(0, 200, 0, 1_000, 100)!!, 0.001f)
        // 2초 동안 50틱 = 25%
        assertEquals(25f, CpuUsage.percent(0, 50, 0, 2_000, 100)!!, 0.001f)
    }

    @Test
    fun percentRejectsBadSamples() {
        assertNull(CpuUsage.percent(0, 10, 1_000, 1_000, 100)) // 시간이 안 흐름
        assertNull(CpuUsage.percent(10, 5, 0, 1_000, 100)) // 틱 감소
        assertNull(CpuUsage.percent(0, 10, 0, 1_000, 0))
    }
}
