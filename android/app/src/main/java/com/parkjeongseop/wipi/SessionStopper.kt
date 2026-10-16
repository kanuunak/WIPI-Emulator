// 세션 정지를 UI 스레드 밖에서 실행하고, 끝날 때까지 "정지 중" 상태를 들고 있는다 (순수 Kotlin, 단위 테스트 대상).
//
// 왜: 네이티브 session::stop()은 SESSION 락을 쥔 채 에뮬 스레드를 join한다. 게임 코드가 한 tick 안에서 양보하지 않고
// 돌면(무한 루프 등) 에뮬 스레드가 정지 플래그를 영영 못 보고 join이 끝나지 않는다 → UI 스레드에서 부르면 앱 전체가 멈춘다(ANR).
// 그동안 SESSION 락을 쓰는 다른 네이티브 호출(start·키·프레임·볼륨…)도 전부 막히므로, 정지 중에는 네이티브를 부르지 않는다.

package com.parkjeongseop.wipi

/**
 * @param runInBackground block을 UI 스레드가 아닌 곳에서 한 번 실행
 * @param postToMain 메인 스레드에서 action 실행(상태는 메인 스레드에서만 바뀐다)
 * @param clock 단조 증가 시각(ms)
 * @param onStateChanged [inProgress]가 바뀔 때마다 메인 스레드에서 호출
 */
class SessionStopper(
    private val runInBackground: (block: () -> Unit) -> Unit,
    private val postToMain: (action: () -> Unit) -> Unit,
    private val clock: () -> Long,
    private val onStateChanged: () -> Unit = {},
) {
    private var startedAt: Long? = null

    /** 정지가 시작됐고 아직 안 끝났다 → 새 세션 시작·네이티브 호출 금지 */
    val inProgress: Boolean get() = startedAt != null

    /** 정지가 [STUCK_AFTER_MS] 넘게 안 끝남 = 에뮬 스레드가 tick에서 돌아오지 않는 것으로 본다(프로세스를 다시 띄워야 풀린다) */
    val stuck: Boolean get() = startedAt?.let { clock() - it >= STUCK_AFTER_MS } ?: false

    /** 정지 경과 시간(ms). 정지 중이 아니면 0. */
    val elapsedMs: Long get() = startedAt?.let { clock() - it } ?: 0L

    /** 백그라운드에서 [nativeStop]을 실행한다. 이미 정지 중이면 아무것도 하지 않고 false. */
    fun stop(nativeStop: () -> Unit): Boolean {
        if (inProgress) return false
        startedAt = clock()
        onStateChanged()
        runInBackground {
            try {
                nativeStop()
            } finally {
                postToMain {
                    startedAt = null
                    onStateChanged()
                }
            }
        }
        return true
    }

    companion object {
        const val STUCK_AFTER_MS = 3_000L
    }
}
