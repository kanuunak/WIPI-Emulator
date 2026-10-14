package com.parkjeongseop.wipi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 정지를 백그라운드에서 실행하고 끝날 때까지 새 시작을 막는다 (HANDOFF_Phase3 §5) */
class SessionStopperTest {
    private var now = 0L
    private val background = mutableListOf<() -> Unit>()
    private val main = mutableListOf<() -> Unit>()
    private var stateChanges = 0
    private val stopper = SessionStopper(
        runInBackground = { background += it },
        postToMain = { main += it },
        clock = { now },
        onStateChanged = { stateChanges++ },
    )

    private fun runBackground() = background.removeAt(0).invoke()
    private fun runMain() = main.removeAt(0).invoke()

    @Test
    fun stopRunsOffCallerAndFinishesOnMain() {
        var stopped = false
        assertTrue(stopper.stop { stopped = true })
        // 호출한 스레드(UI)에서는 네이티브 정지가 실행되지 않는다
        assertFalse(stopped)
        assertTrue(stopper.inProgress)
        assertEquals(1, stateChanges)

        runBackground()
        assertTrue(stopped)
        assertTrue(stopper.inProgress) // 메인 스레드에 완료가 전달되기 전까지는 정지 중

        runMain()
        assertFalse(stopper.inProgress)
        assertEquals(2, stateChanges)
    }

    @Test
    fun secondStopWhileStoppingIsIgnored() {
        var calls = 0
        assertTrue(stopper.stop { calls++ })
        assertFalse(stopper.stop { calls++ })
        assertEquals(1, background.size)
        runBackground()
        runMain()
        assertEquals(1, calls)
        // 끝난 뒤에는 다시 정지할 수 있다
        assertTrue(stopper.stop { calls++ })
    }

    @Test
    fun hungStopBecomesStuck() {
        stopper.stop { /* 에뮬 스레드가 tick에서 안 돌아와 join이 끝나지 않는 경우: 백그라운드 작업을 실행하지 않는다 */ }
        now += SessionStopper.STUCK_AFTER_MS - 1
        assertFalse(stopper.stuck)
        now += 1
        assertTrue(stopper.stuck)
        assertTrue(stopper.inProgress)
        assertEquals(SessionStopper.STUCK_AFTER_MS, stopper.elapsedMs)
    }

    @Test
    fun throwingStopStillFinishes() {
        stopper.stop { throw IllegalStateException("boom") }
        try {
            runBackground()
        } catch (_: IllegalStateException) {
        }
        runMain()
        assertFalse(stopper.inProgress)
        assertFalse(stopper.stuck)
    }
}
