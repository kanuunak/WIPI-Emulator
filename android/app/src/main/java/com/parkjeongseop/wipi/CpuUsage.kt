// 앱 프로세스 CPU 사용률 — /proc/self/stat의 utime+stime(클록 틱) 차이를 벽시계 시간으로 나눈다.
// 스레드별(/proc/self/task/<tid>/stat)로도 재서 상위 몇 개를 이름과 함께 보인다(에뮬·렌더·오디오 중 어디가 쓰는지).
// 레이아웃 HUD가 켜져 있을 때만 1초마다 측정한다(발열 수정 효과를 실기기에서 숫자로 확인하는 용도).
// 파싱·계산은 순수 Kotlin이라 JVM 단위 테스트 대상. 실제 파일 읽기와 CLK_TCK 조회만 Android 의존.

package com.parkjeongseop.wipi

import android.os.Process
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

    /** stat 줄의 2번째 필드(comm, 스레드 이름 최대 15자). 형식이 다르면 null. */
    fun parseName(stat: String): String? {
        val open = stat.indexOf('(')
        val close = stat.lastIndexOf(')')
        return if (open in 0 until close) stat.substring(open + 1, close) else null
    }

    /** 스레드 표본: tid → (이름, utime+stime 틱) */
    data class ThreadTicks(val name: String, val ticks: Long)

    /**
     * 두 스레드 표본 사이 상위 [n]개 (이름 to %). 같은 이름은 합친다(오디오 스레드 여러 개 등).
     * 이전 표본에 없던 스레드(새로 생김)는 이번 틱 전체를 이 구간 사용량으로 보지 않고 건너뛴다.
     */
    fun topThreads(
        prev: Map<Int, ThreadTicks>,
        cur: Map<Int, ThreadTicks>,
        dWallMs: Long,
        ticksPerSecond: Long,
        n: Int,
    ): List<Pair<String, Float>> {
        if (dWallMs <= 0 || ticksPerSecond <= 0) return emptyList()
        val byName = HashMap<String, Long>()
        for ((tid, now) in cur) {
            val before = prev[tid] ?: continue
            val d = now.ticks - before.ticks
            if (d > 0) byName[now.name] = (byName[now.name] ?: 0L) + d
        }
        return byName.entries
            .sortedByDescending { it.value }
            .take(n)
            .map { it.key to it.value * 1000f * 100f / (ticksPerSecond * dWallMs) }
    }

    /** 표본 결과: 프로세스 전체 %와 상위 스레드 */
    data class Sample(val total: Float, val threads: List<Pair<String, Float>>)

    /** 1초 간격 측정기. [sample]은 직전 표본 대비 값을 돌려준다(첫 호출은 null). 백그라운드 스레드에서 부른다. */
    class Sampler(private val topN: Int = 4) {
        private val ticksPerSecond: Long = runCatching { Os.sysconf(OsConstants._SC_CLK_TCK) }.getOrDefault(100L)
        private var prevTicks = -1L
        private var prevWall = 0L
        private var prevThreads: Map<Int, ThreadTicks> = emptyMap()
        private val mainTid = Process.myPid()

        fun sample(): Sample? {
            val ticks = runCatching { parseTicks(File("/proc/self/stat").readText()) }.getOrNull() ?: return null
            val threads = readThreads()
            val wall = SystemClock.elapsedRealtime()
            val result = if (prevTicks >= 0) {
                percent(prevTicks, ticks, prevWall, wall, ticksPerSecond)?.let { total ->
                    Sample(total, topThreads(prevThreads, threads, wall - prevWall, ticksPerSecond, topN))
                }
            } else {
                null
            }
            prevTicks = ticks
            prevWall = wall
            prevThreads = threads
            return result
        }

        private fun readThreads(): Map<Int, ThreadTicks> {
            val out = HashMap<Int, ThreadTicks>()
            File("/proc/self/task").listFiles()?.forEach { dir ->
                val tid = dir.name.toIntOrNull() ?: return@forEach
                val stat = runCatching { File(dir, "stat").readText() }.getOrNull() ?: return@forEach // 스레드가 방금 끝남
                // 메인(UI) 스레드 이름은 패키지명 끝 15자라 알아보기 어렵다 → "main"
                val name = if (tid == mainTid) "main" else parseName(stat) ?: return@forEach
                val t = parseTicks(stat) ?: return@forEach
                out[tid] = ThreadTicks(name, t)
            }
            return out
        }
    }
}
