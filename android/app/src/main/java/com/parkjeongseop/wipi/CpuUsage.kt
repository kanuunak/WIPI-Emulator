// 앱 프로세스 CPU 사용률 — /proc/self/stat의 utime+stime(클록 틱) 차이를 벽시계 시간으로 나눈다.
// 레이아웃 HUD가 켜져 있을 때만 1초마다 측정한다(발열 수정 효과를 실기기에서 숫자로 확인하는 용도).
// 파싱·계산은 순수 Kotlin이라 JVM 단위 테스트 대상. 실제 파일 읽기와 CLK_TCK 조회만 Android 의존.

package com.parkjeongseop.wipi

import android.os.SystemClock
import android.system.Os
import android.system.OsConstants
import java.io.File

object CpuUsage {
    /**
     * /proc/<pid>/stat 한 줄에서 utime+stime(클록 틱)을 꺼낸다. 형식이 다르면 null.
     * 2번째 필드(comm)는 괄호로 감싸져 있고 공백·괄호를 담을 수 있으므로 마지막 ')' 뒤부터 센다.
     * ')' 뒤 첫 필드가 3번(state) → utime(14번)은 그로부터 11번째, stime(15번)은 12번째.
     */
    fun parseTicks(stat: String): Long? {
        val close = stat.lastIndexOf(')')
        if (close < 0) return null
        val fields = stat.substring(close + 1).trim().split(' ').filter { it.isNotEmpty() }
        if (fields.size < 13) return null
        val utime = fields[11].toLongOrNull() ?: return null
        val stime = fields[12].toLongOrNull() ?: return null
        return utime + stime
    }

    /**
     * 두 표본 사이 CPU 사용률(%). 코어 하나를 꽉 쓰면 100(여러 코어를 쓰면 100을 넘을 수 있다 — top·ps와 같은 기준).
     * 시간이 흐르지 않았거나 틱이 줄었으면(재시작 등) null.
     */
    fun percent(prevTicks: Long, ticks: Long, prevWallMs: Long, wallMs: Long, ticksPerSecond: Long): Float? {
        val dWall = wallMs - prevWallMs
        val dTicks = ticks - prevTicks
        if (dWall <= 0 || dTicks < 0 || ticksPerSecond <= 0) return null
        return dTicks * 1000f * 100f / (ticksPerSecond * dWall)
    }

    /** 1초 간격 측정기. [sample]은 직전 표본 대비 %를 돌려준다(첫 호출은 null). 백그라운드 스레드에서 불러도 된다. */
    class Sampler {
        private val ticksPerSecond: Long = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }.getOrDefault(100L)
        private var prevTicks = -1L
        private var prevWall = 0L

        fun sample(): Float? {
            val ticks = runCatching { parseTicks(File("/proc/self/stat").readText()) }.getOrNull() ?: return null
            val wall = SystemClock.elapsedRealtime()
            val result = if (prevTicks >= 0) percent(prevTicks, ticks, prevWall, wall, ticksPerSecond) else null
            prevTicks = ticks
            prevWall = wall
            return result
        }
    }
}
