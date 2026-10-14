// 에뮬 화면 세로 스택 레이아웃 계산 (명세 §5.3). 순수 Kotlin — android.* 의존 없음, 단위 테스트 대상.
// 입력은 인셋을 뺀 가용 영역(dp). 출력 좌표는 그 가용 영역의 왼쪽 위가 원점이다.

package com.parkjeongseop.wipi

import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** dp 사각형 (가용 영역 기준 좌표) */
data class RectDp(val left: Float, val top: Float, val width: Float, val height: Float) {
    val right: Float get() = left + width
    val bottom: Float get() = top + height
}

/** px 사각형 (가용 영역 기준 좌표) */
data class RectPx(val left: Int, val top: Int, val width: Int, val height: Int)

/** 레이아웃 조정값. 기본값이 명세 §5.3 값이다. (P1 keypadSize·integerScale은 여기에 추가) */
data class LayoutPrefs(
    val maxKeypadWidthDp: Float = 420f,
    val targetKeyHDp: Float = 48f,
    val minKeyHDp: Float = 40f,
    val maxKeyHDp: Float = 56f,
    /** 게임 높이가 가용 높이의 이 비율 미만이면 키를 줄인다 */
    val minGameHeightRatio: Float = 0.45f,
)

data class StackLayout(
    val availWdp: Float,
    val availHdp: Float,
    val density: Float,
    val keyH: Float,
    val game: RectDp,
    val gamePx: RectPx,
    /** 게임 px 폭 / 원본 240 */
    val gameScale: Float,
    /** 바깥 여백(8dp) 포함 키패드 영역 */
    val keypad: RectDp,
    /** ⏸·⚙ 아이콘 영역 (게임 우상단, 48dp 터치 2개) */
    val icons: RectDp,
    /** 게임과 키패드 사이 남는 세로 여백 */
    val gap: Float,
    /** 최소 키 높이로도 키패드가 가용 높이에 안 들어가면 false (게임 0, 키패드는 위로 넘침) */
    val fits: Boolean,
) {
    /** 로그·HUD용 한 줄 요약 */
    fun summary(): String =
        "avail=${fmt(availWdp)}x${fmt(availHdp)}dp d=${fmt(density, 3)} keyH=${fmt(keyH)} " +
            "game=${fmt(game.width)}x${fmt(game.height)}dp@${fmt(game.left)},${fmt(game.top)} " +
            "px=${gamePx.width}x${gamePx.height} scale=${fmt(gameScale, 2)} " +
            "keypad=${fmt(keypad.width)}x${fmt(keypad.height)}dp@${fmt(keypad.left)},${fmt(keypad.top)} " +
            "gap=${fmt(gap)}" + if (fits) "" else " OVERFLOW"
}

const val KEYPAD_ROW_COUNT = 7
const val KEYPAD_COL_COUNT = 3
const val KEYPAD_SPACING_DP = 4f
const val KEYPAD_MARGIN_DP = 8f
const val ICON_TOUCH_DP = 48f

/** 키 높이 [keyH]일 때 키패드 전체 높이(바깥 여백 포함) */
fun keypadHeight(keyH: Float): Float =
    KEYPAD_ROW_COUNT * keyH + (KEYPAD_ROW_COUNT - 1) * KEYPAD_SPACING_DP + 2 * KEYPAD_MARGIN_DP

/**
 * 명세 §5.3 알고리즘 1~6.
 * 1. 키패드 폭 = min(가용 폭, 420dp), keyH 목표 48dp
 * 2. 키패드 높이 = 7·keyH + 6·4 + 2·8
 * 3. 게임 = (가용 높이 − 키패드 높이) 안에 3:4 Fit
 * 4. 게임 높이 < 가용 높이 45%면 keyH를 40dp까지 1dp씩 줄인다
 * 5. 게임이 폭에 막혀 세로가 남으면 그만큼 keyH를 키운다(최대 56dp), 나머지는 게임–키패드 사이 여백
 * 6. 게임은 위·가로 가운데, 키패드는 아래 정렬
 */
fun computeStackLayout(
    availWdp: Float,
    availHdp: Float,
    density: Float,
    prefs: LayoutPrefs = LayoutPrefs(),
): StackLayout {
    val w = max(0f, availWdp)
    val h = max(0f, availHdp)
    val aspect = SCREEN_WIDTH.toFloat() / SCREEN_HEIGHT // 3:4

    // (폭, 높이, 폭에 막혔는지)
    fun fitGame(keyH: Float): Triple<Float, Float, Boolean> {
        val maxH = max(0f, h - keypadHeight(keyH))
        return if (w / aspect <= maxH) Triple(w, w / aspect, true) else Triple(maxH * aspect, maxH, false)
    }

    // 1~4
    var keyH = prefs.targetKeyHDp
    var fit = fitGame(keyH)
    while (!fit.third && fit.second < h * prefs.minGameHeightRatio && keyH - 1f >= prefs.minKeyHDp) {
        keyH -= 1f
        fit = fitGame(keyH)
    }

    // 5. 폭에 막혀 남는 세로 공간을 키 높이로 (1dp 단위). 게임 크기는 폭이 정하므로 변하지 않는다
    val (gameW, gameH, widthLimited) = fit
    if (widthLimited) {
        val spare = h - keypadHeight(keyH) - gameH
        val grow = min(floor(spare / KEYPAD_ROW_COUNT), prefs.maxKeyHDp - keyH)
        if (grow > 0f) keyH += grow
    }

    val keypadH = keypadHeight(keyH)
    val fits = keypadH <= h
    val gap = max(0f, h - keypadH - gameH)

    // 6. 배치
    val game = RectDp(left = (w - gameW) / 2f, top = 0f, width = gameW, height = gameH)
    val keypadW = min(w, prefs.maxKeypadWidthDp)
    val keypad = RectDp(left = (w - keypadW) / 2f, top = h - keypadH, width = keypadW, height = keypadH)
    val iconsW = 2 * ICON_TOUCH_DP
    val icons = RectDp(left = max(0f, game.right - iconsW), top = 0f, width = min(iconsW, w), height = ICON_TOUCH_DP)

    val gamePx = RectPx(
        left = (game.left * density).roundToInt(),
        top = (game.top * density).roundToInt(),
        width = (game.width * density).roundToInt(),
        height = (game.height * density).roundToInt(),
    )

    return StackLayout(
        availWdp = w,
        availHdp = h,
        density = density,
        keyH = keyH,
        game = game,
        gamePx = gamePx,
        gameScale = gamePx.width.toFloat() / SCREEN_WIDTH,
        keypad = keypad,
        icons = icons,
        gap = gap,
        fits = fits,
    )
}

private fun fmt(v: Float, decimals: Int = 1): String {
    // String.format은 로케일(소수점 쉼표)을 타므로 직접 만든다
    var scale = 1
    repeat(decimals) { scale *= 10 }
    val n = (v * scale).roundToInt()
    val sign = if (n < 0) "-" else ""
    val a = kotlin.math.abs(n)
    return if (decimals == 0) "$sign$a" else "$sign${a / scale}.${(a % scale).toString().padStart(decimals, '0')}"
}
