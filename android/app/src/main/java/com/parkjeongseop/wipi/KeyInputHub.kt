// 키 입력 허브 — 터치·하드웨어 키·스틱 입력을 모아 코어로 보내는 순수 Kotlin 로직 (명세 §5.2.1).
// android.* 의존 없음: 시계·지연 실행을 주입받아 JVM 단위 테스트에서 가짜 시계로 검증한다.

package com.parkjeongseop.wipi

/** 허브가 키 이벤트를 내보내는 곳. 실제 구현은 WipiNative.nativeKeyDown/Up. */
interface KeySink {
    fun down(key: String)
    fun up(key: String)
}

/**
 * 여러 입력 source(`touch:<pointerId>`, `hw:<keyCode>`, `stick:<방향>` …)를 키별 참조 카운트로 합친다.
 *
 * - 키의 holder가 0→1일 때만 [KeySink.down], 1→0일 때만 [KeySink.up]. 코어는 이미 눌린 키의 Down과
 *   안 눌린 키의 Up을 무시하므로, 같은 키를 두 source가 누르면 첫 source를 뗄 때 키가 떨어지는 문제를 막는다.
 * - 최소 누름 시간: down 후 [MIN_HOLD_MS] 미만에 떼면 up을 down + [MIN_HOLD_MS]로 미룬다(같은 tick의
 *   down/up은 게임이 못 잡음). 미뤄진 up이 있는 키가 다시 눌리면 그 up을 즉시 보낸 뒤 down.
 * - 키 반복은 만들지 않는다(코어가 눌린 키마다 100ms 간격으로 만든다).
 * - 메인 스레드 전용(Compose 포인터 처리와 onKeyDown 모두 메인 스레드).
 *
 * @param clock 단조 증가 시각(ms)
 * @param schedule [delayMs] 뒤에 action을 한 번 실행. 취소는 필요 없다(실행 시점에 상태를 다시 확인함).
 */
class KeyInputHub(
    private val sink: KeySink,
    private val clock: () -> Long,
    private val schedule: (delayMs: Long, action: () -> Unit) -> Unit,
) {
    /** source → 그 source가 누르고 있는 키 */
    private val sourceKey = HashMap<String, String>()

    /** 키 → 그 키를 누르고 있는 source 집합 (비어 있으면 항목 제거) */
    private val holders = HashMap<String, MutableSet<String>>()

    /** 키 → sink.down을 보낸 시각 (sink 기준으로 눌린 키만) */
    private val downAt = HashMap<String, Long>()

    /** 키 → 미뤄진 up의 토큰. 예약된 action은 토큰이 그대로일 때만 up을 보낸다. */
    private val pendingUp = HashMap<String, Long>()
    private var nextToken = 0L

    /** 이 source가 지금 키를 누르고 있는지 */
    fun holds(source: String): Boolean = source in sourceKey

    /** source가 key를 누름. 이미 다른 키를 누르고 있던 source면 [move]와 같다. */
    fun press(source: String, key: String) {
        val current = sourceKey[source]
        if (current != null) {
            if (current != key) move(source, key)
            return
        }
        acquire(source, key)
    }

    /** 슬라이드: source의 키를 newKey로 바꾼다. 지연 없이 up(old) → down(new). 모르는 source는 무시. */
    fun move(source: String, newKey: String) {
        val old = sourceKey[source] ?: return
        if (old == newKey) return
        sourceKey.remove(source)
        if (dropHolder(old, source)) sendUp(old)
        acquire(source, newKey)
    }

    /** source가 키를 뗌. 최소 누름 시간이 안 지났으면 up을 미룬다. 모르는 source는 무시. */
    fun release(source: String) {
        val key = sourceKey.remove(source) ?: return
        if (!dropHolder(key, source)) return

        val wait = (downAt[key] ?: Long.MIN_VALUE / 2) + MIN_HOLD_MS - clock()
        if (wait <= 0) {
            sendUp(key)
            return
        }
        val token = nextToken++
        pendingUp[key] = token
        schedule(wait) {
            if (pendingUp[key] == token) {
                pendingUp.remove(key)
                sendUp(key)
            }
        }
    }

    /** 대기 중 up을 즉시 보내고 눌린 키를 전부 up, 상태 초기화. 이후 모르는 source의 release는 무시된다. */
    fun releaseAll() {
        val pending = pendingUp.keys.toList()
        pendingUp.clear()
        pending.forEach(::sendUp)

        val held = holders.keys.toList()
        holders.clear()
        sourceKey.clear()
        held.forEach(::sendUp)
    }

    private fun acquire(source: String, key: String) {
        sourceKey[source] = key
        val set = holders.getOrPut(key) { HashSet() }
        set += source
        if (set.size == 1) {
            // 미뤄진 up이 남아 있으면 먼저 보내서 down/up이 항상 교대하게 한다
            if (pendingUp.remove(key) != null) sendUp(key)
            downAt[key] = clock()
            sink.down(key)
        }
    }

    /** holder에서 source를 빼고, 그 키의 마지막 holder였으면 true */
    private fun dropHolder(key: String, source: String): Boolean {
        val set = holders[key] ?: return false
        set -= source
        if (set.isNotEmpty()) return false
        holders.remove(key)
        return true
    }

    private fun sendUp(key: String) {
        downAt.remove(key)
        sink.up(key)
    }

    companion object {
        /** 최소 누름 시간(ms) — KeyTest로 튜닝 대상 (명세 §12) */
        const val MIN_HOLD_MS = 40L
    }
}
