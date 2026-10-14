// 터치 키패드 모델 (명세 §5.2.2). 순수 Kotlin — android.* 의존 없음, 단위 테스트 대상.
// 그리기와 히트테스트가 같은 키 사각형(px, 키패드 왼쪽 위 원점)을 쓴다.

package com.parkjeongseop.wipi

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.sqrt

/**
 * 슬라이드 규칙의 단위. NUM은 같은 클러스터 안에서만 슬라이드, FUNC는 뗄 때까지 고정.
 * DPAD의 방향키는 조이스틱처럼 동작하고(OK 중심 기준), OK는 직접 눌렀을 때만 눌리며 고정이다.
 */
enum class KeyCluster { DPAD, NUM, FUNC }

/** [code]는 코어 키 문자열(§3.3) */
data class KeyDef(val code: String, val label: String, val cluster: KeyCluster)

/** 키 사각형(px, 키패드 기준 좌표) */
data class KeyRect(val key: KeyDef, val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val centerX: Float get() = (left + right) / 2f
    val centerY: Float get() = (top + bottom) / 2f

    fun contains(x: Float, y: Float): Boolean = x >= left && x <= right && y >= top && y <= bottom

    /** 사각형 안쪽으로 [inset]만큼 들어와 있는지 (히스테리시스) */
    fun containsInset(x: Float, y: Float, inset: Float): Boolean =
        x >= left + inset && x <= right - inset && y >= top + inset && y <= bottom - inset

    /** 점에서 사각형까지 거리(안이면 0) */
    fun distanceTo(x: Float, y: Float): Float {
        val dx = max(0f, max(left - x, x - right))
        val dy = max(0f, max(top - y, y - bottom))
        return sqrt(dx * dx + dy * dy)
    }
}

/** dp 기준 치수. density = px/dp. */
data class KeypadMetrics(
    val density: Float,
    val spacingDp: Float = KEYPAD_SPACING_DP,
    val marginDp: Float = KEYPAD_MARGIN_DP,
    /** 처음 누를 때 키 사이 간격이면 이 거리 이내의 가장 가까운 키 */
    val snapDp: Float = 6f,
    /** 슬라이드 때 새 키 사각형 안쪽으로 이만큼 들어와야 바뀐다 */
    val hysteresisDp: Float = 6f,
    /** 방향키 조이스틱: OK 중심에서 이 거리 안에서는 현재 방향 유지. ◀↔▶ 전환 = 중심선 너머 이 거리 */
    val dpadDeadZoneDp: Float = 12f,
    /** 키패드 영역 밖으로 이보다 멀리 나가면 release */
    val leaveDp: Float = 32f,
)

class KeypadLayout(
    val widthPx: Float,
    val heightPx: Float,
    val metrics: KeypadMetrics,
    val keys: List<KeyRect>,
) {
    private val snapPx = metrics.snapDp * metrics.density
    private val hysteresisPx = metrics.hysteresisDp * metrics.density
    private val leavePx = metrics.leaveDp * metrics.density
    private val deadZonePx = metrics.dpadDeadZoneDp * metrics.density
    private val okRect = rectOf("OK")

    fun rectOf(code: String): KeyRect? = keys.firstOrNull { it.key.code == code }

    /** 처음 누를 때: 키 사각형 안이면 그 키, 아니면 [KeypadMetrics.snapDp] 이내의 가장 가까운 키, 없으면 null */
    fun hitTestInitial(x: Float, y: Float): KeyDef? {
        keys.firstOrNull { it.contains(x, y) }?.let { return it.key }
        val nearest = keys.minByOrNull { it.distanceTo(x, y) } ?: return null
        return if (nearest.distanceTo(x, y) <= snapPx) nearest.key else null
    }

    /**
     * 누른 채 움직일 때.
     * - FUNC·OK: 그대로(뗄 때까지 고정).
     * - NUM: 같은 클러스터의 다른 키 사각형 안쪽으로 [KeypadMetrics.hysteresisDp] 이상 들어왔을 때만 그 키.
     *   간격·다른 클러스터·경계 근처면 현재 키 유지.
     * - 방향키: [dpadMove] (조이스틱식).
     */
    fun hitTestMove(current: KeyDef, x: Float, y: Float): KeyDef = when {
        current.cluster == KeyCluster.FUNC || current.code == "OK" -> current
        current.cluster == KeyCluster.DPAD -> dpadMove(current, x, y)
        else -> {
            val target = keys.firstOrNull { it.containsInset(x, y, hysteresisPx) }
            if (target != null && target.key.cluster == current.cluster) target.key else current
        }
    }

    /**
     * 방향키 조이스틱 (사용자 결정 2026-10-14, 명세 §5.2.2 개정). OK를 거치지 않고 방향끼리만 바뀐다.
     * 1. 현재 키 사각형 안이면 유지.
     * 2. 다른 방향키 사각형 안쪽으로 [KeypadMetrics.hysteresisDp] 이상 들어오면 그 키(그림과 일치).
     * 3. 그 밖에서는 OK 중심 기준 위치로 판단:
     *    - 같은 축 반전(◀↔▶, ▲↔▼): 중심선 너머 [KeypadMetrics.dpadDeadZoneDp] 이상이면 반대 방향.
     *    - 축 전환(좌우↔상하): OK 칸의 위아래(좌우) 경계를 히스테리시스만큼 넘고, 그 축이 우세할 때만.
     *      → 좌우로 흔드는 중 손가락이 위아래로 조금 흔들려도 ▲▼가 눌리지 않는다.
     *    - 둘 다 아니면(중심 근처 데드존) 현재 방향 유지.
     */
    private fun dpadMove(current: KeyDef, x: Float, y: Float): KeyDef {
        val ok = okRect ?: return current
        if (rectOf(current.code)?.contains(x, y) == true) return current
        keys.firstOrNull { it.key.cluster == KeyCluster.DPAD && it.key.code != "OK" && it.containsInset(x, y, hysteresisPx) }
            ?.let { return it.key }

        val dx = x - ok.centerX
        val dy = y - ok.centerY
        val ax = abs(dx)
        val ay = abs(dy)
        val horizontal = current.code == "LEFT" || current.code == "RIGHT"
        val code = if (horizontal) {
            when {
                ay >= (ok.bottom - ok.top) / 2f + hysteresisPx && ay >= ax + hysteresisPx -> if (dy < 0) "UP" else "DOWN"
                ax >= deadZonePx -> if (dx < 0) "LEFT" else "RIGHT"
                else -> return current
            }
        } else {
            when {
                ax >= (ok.right - ok.left) / 2f + hysteresisPx && ax >= ay + hysteresisPx -> if (dx < 0) "LEFT" else "RIGHT"
                ay >= deadZonePx -> if (dy < 0) "UP" else "DOWN"
                else -> return current
            }
        }
        return rectOf(code)?.key ?: current
    }

    /** 키패드 영역(0,0 ~ width,height) 밖으로 [KeypadMetrics.leaveDp]보다 멀리 나갔는지 */
    fun isBeyondLeave(x: Float, y: Float): Boolean =
        x < -leavePx || y < -leavePx || x > widthPx + leavePx || y > heightPx + leavePx

    /** 방향키 블록(1~3행) 아래쪽 경계(px). 시스템 제스처 제외 영역에 쓴다. */
    val dpadBlockBottom: Float
        get() = keys.filter { it.key.cluster != KeyCluster.NUM }.maxOfOrNull { it.bottom } ?: 0f
}

object KeypadModel {
    /** 배열은 원본 앱과 같다(§3.4). HANGUP은 두지 않는다(게임이 종료로 처리할 수 있음 — 하드웨어 F2만). */
    val ROWS: List<List<KeyDef>> = listOf(
        listOf(func("SOFT_L", "◁"), dpad("UP", "▲"), func("SOFT_R", "▷")),
        listOf(dpad("LEFT", "◀"), dpad("OK", "OK"), dpad("RIGHT", "▶")),
        listOf(func("CALL", "📞"), dpad("DOWN", "▼"), func("CLR", "CLR")),
        listOf(num("1"), num("2"), num("3")),
        listOf(num("4"), num("5"), num("6")),
        listOf(num("7"), num("8"), num("9")),
        listOf(num("*"), num("0"), num("#")),
    )

    val KEYS: List<KeyDef> = ROWS.flatten()

    /**
     * 키패드 영역(바깥 여백 포함, [widthPx]×[heightPx])을 7행×3열로 나눈다.
     * 행 높이 = (높이 − 2·여백 − 6·간격) / 7 → 높이가 [keypadHeight](keyH)·density면 행 높이 = keyH.
     */
    fun layout(widthPx: Float, heightPx: Float, metrics: KeypadMetrics): KeypadLayout {
        val margin = metrics.marginDp * metrics.density
        val spacing = metrics.spacingDp * metrics.density
        val rows = ROWS.size
        val cols = ROWS.first().size
        val keyW = max(0f, (widthPx - 2 * margin - (cols - 1) * spacing) / cols)
        val keyH = max(0f, (heightPx - 2 * margin - (rows - 1) * spacing) / rows)

        val rects = ROWS.flatMapIndexed { r, row ->
            row.mapIndexed { c, key ->
                val left = margin + c * (keyW + spacing)
                val top = margin + r * (keyH + spacing)
                KeyRect(key, left, top, left + keyW, top + keyH)
            }
        }
        return KeypadLayout(widthPx, heightPx, metrics, rects)
    }

    private fun dpad(code: String, label: String) = KeyDef(code, label, KeyCluster.DPAD)
    private fun func(code: String, label: String) = KeyDef(code, label, KeyCluster.FUNC)
    private fun num(code: String) = KeyDef(code, code, KeyCluster.NUM)
}
