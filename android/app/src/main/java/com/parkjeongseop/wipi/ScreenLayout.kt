// 에뮬 화면 배치: 세로 스택(STACK, 명세 §5.3 — EmulatorLayout.kt)과 좌우 분할(SPLIT, 명세 §4.2 P1).
// SPLIT = 게임을 가운데에 높이 가득, 키패드를 둘로 나눠 양옆(왼쪽: 소프트키·방향키·통화·CLR, 오른쪽: 숫자).
// 순수 Kotlin — android.* 의존 없음, 단위 테스트 대상. 좌표는 가용 영역(인셋 제외) 왼쪽 위 원점, dp.
// 배치 편집기(예정)는 이 결과(게임 사각형 + 키패드 사각형들)를 출발점으로 쓴다.

package com.parkjeongseop.wipi

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 설정의 배치 선택. AUTO는 [computeScreenLayout]이 화면 모양으로 고른다. */
enum class LayoutMode { AUTO, STACK, SPLIT }

/** 키패드 한 덩어리: 화면 위치(바깥 여백 포함)와 그 안의 키 행들([KeypadModel.ROWS]의 일부 또는 전부) */
data class PadPlacement(val rect: RectDp, val rows: List<List<KeyDef>>)

/** 화면에 그릴 배치 결과 — 어떤 방식이든 이 모양으로 그린다 */
data class ScreenLayout(
    /** 실제로 쓴 방식(STACK 또는 SPLIT) */
    val mode: LayoutMode,
    val availWdp: Float,
    val availHdp: Float,
    val density: Float,
    val game: RectDp,
    val gamePx: RectPx,
    val gameScale: Float,
    val pads: List<PadPlacement>,
    /** ⏸·⚙ 아이콘 영역 (48dp 터치 2개) */
    val icons: RectDp,
    val iconsOutside: Boolean,
    /** 키 높이(dp) */
    val keyH: Float,
    val fits: Boolean,
    /** HUD 줄(방식별 세부) */
    val details: List<String>,
    /** 로그 한 줄 요약 */
    val summary: String,
)

/** SPLIT 키 크기 범위(dp). 키 폭이 하한보다 좁아지면 SPLIT을 쓰지 않는다. */
const val SPLIT_KEY_MIN_DP = 40f
const val SPLIT_KEY_MAX_W_DP = 72f
const val SPLIT_KEY_MAX_H_DP = 60f

/** SPLIT 게임 최소 폭(dp). 이보다 좁아지면 SPLIT을 쓰지 않는다(커버 화면에서 강제해도 STACK). */
const val SPLIT_MIN_GAME_W_DP = 180f

/** AUTO: SPLIT 게임 높이가 STACK보다 이 배 이상 클 때만 SPLIT (커버 화면은 STACK, 펼친 화면은 SPLIT) */
const val SPLIT_AUTO_GAIN = 1.2f

/** 3열 키패드 폭(바깥 여백 포함) */
fun padWidth(keyW: Float): Float = KEYPAD_COL_COUNT * keyW + (KEYPAD_COL_COUNT - 1) * KEYPAD_SPACING_DP + 2 * KEYPAD_MARGIN_DP

/** [rows]행 키패드 높이(바깥 여백 포함) */
fun padHeight(rows: Int, keyH: Float): Float = rows * keyH + (rows - 1) * KEYPAD_SPACING_DP + 2 * KEYPAD_MARGIN_DP

/**
 * [mode]대로 배치한다. AUTO: SPLIT이 가능하고 게임이 [SPLIT_AUTO_GAIN]배 이상 커지면 SPLIT, 아니면 STACK.
 * SPLIT을 골랐는데 화면이 좁아 키가 들어가지 않으면 STACK으로 돌아간다.
 */
fun computeScreenLayout(mode: LayoutMode, availWdp: Float, availHdp: Float, density: Float): ScreenLayout {
    val stack = computeStackLayout(availWdp, availHdp, density).toScreenLayout()
    if (mode == LayoutMode.STACK) return stack
    val split = computeSplitLayout(availWdp, availHdp, density) ?: return stack
    return when (mode) {
        LayoutMode.SPLIT -> split
        else -> if (split.game.height >= stack.game.height * SPLIT_AUTO_GAIN) split else stack
    }
}

fun StackLayout.toScreenLayout(): ScreenLayout = ScreenLayout(
    mode = LayoutMode.STACK,
    availWdp = availWdp,
    availHdp = availHdp,
    density = density,
    game = game,
    gamePx = gamePx,
    gameScale = gameScale,
    pads = listOf(PadPlacement(keypad, KeypadModel.ROWS)),
    icons = icons,
    iconsOutside = iconsOutside,
    keyH = keyH,
    fits = fits,
    details = buildList {
        add("STACK keyH ${keyH.roundToInt()}dp gap ${gap.roundToInt()}dp")
        add("keypad ${keypad.width.roundToInt()}x${keypad.height.roundToInt()}dp")
        if (!fits) add("OVERFLOW: keypad > avail")
    },
    summary = "STACK " + summary(),
)

/**
 * 좌우 분할. 화면이 좁아 양옆에 [SPLIT_KEY_MIN_DP] 키 3열을 두면 게임이 [SPLIT_MIN_GAME_W_DP]보다 좁아지면 null.
 * 1. 게임 = 가용 높이 가득(3:4). 양옆에 최소 키패드 폭이 안 남으면 게임 폭을 줄인다.
 * 2. 옆 칸 폭에서 키 폭(최대 [SPLIT_KEY_MAX_W_DP]), 키 높이 = 키 폭(범위 [SPLIT_KEY_MIN_DP]~[SPLIT_KEY_MAX_H_DP]).
 * 3. 키패드는 옆 칸 가로 가운데·아래 정렬(엄지 위치). 게임은 세로 가운데.
 * 4. ⏸·⚙는 게임 오른쪽 옆 위(오른쪽 키패드와 겹치면 왼쪽 옆 위, 그래도 겹치면 게임 우상단 위).
 */
fun computeSplitLayout(availWdp: Float, availHdp: Float, density: Float): ScreenLayout? {
    val w = max(0f, availWdp)
    val h = max(0f, availHdp)
    val aspect = SCREEN_WIDTH.toFloat() / SCREEN_HEIGHT
    val minPadW = padWidth(SPLIT_KEY_MIN_DP)

    var gameH = h
    var gameW = h * aspect
    if (gameW > w - 2 * minPadW) {
        gameW = w - 2 * minPadW
        gameH = gameW / aspect
    }
    if (gameW < SPLIT_MIN_GAME_W_DP || gameH <= 0f) return null

    val side = (w - gameW) / 2f
    val keyW = min(SPLIT_KEY_MAX_W_DP, (side - 2 * KEYPAD_MARGIN_DP - (KEYPAD_COL_COUNT - 1) * KEYPAD_SPACING_DP) / KEYPAD_COL_COUNT)
    val upperRows = KeypadModel.UPPER_ROWS
    val lowerRows = KeypadModel.LOWER_ROWS
    val tallest = max(upperRows.size, lowerRows.size)
    // 키 높이: 키 폭 기준, 가장 긴 키패드가 높이에 들어가도록
    val maxKeyHByHeight = (h - 2 * KEYPAD_MARGIN_DP - (tallest - 1) * KEYPAD_SPACING_DP) / tallest
    val keyH = min(min(keyW, SPLIT_KEY_MAX_H_DP), maxKeyHByHeight).coerceAtLeast(min(SPLIT_KEY_MIN_DP, maxKeyHByHeight))
    if (keyH < SPLIT_KEY_MIN_DP) return null

    val padW = padWidth(keyW)
    val game = RectDp(left = side, top = (h - gameH) / 2f, width = gameW, height = gameH)
    val upperH = padHeight(upperRows.size, keyH)
    val lowerH = padHeight(lowerRows.size, keyH)
    val leftPad = RectDp(left = (side - padW) / 2f, top = h - upperH, width = padW, height = upperH)
    val rightPad = RectDp(left = game.right + (side - padW) / 2f, top = h - lowerH, width = padW, height = lowerH)

    val iconsW = 2 * ICON_TOUCH_DP
    fun overlaps(a: RectDp, b: RectDp) = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom
    val rightIcons = RectDp(game.right + (side - iconsW) / 2f, 0f, iconsW, ICON_TOUCH_DP)
    val leftIcons = RectDp((side - iconsW) / 2f, 0f, iconsW, ICON_TOUCH_DP)
    val (icons, outside) = when {
        side >= iconsW && !overlaps(rightIcons, rightPad) -> rightIcons to true
        side >= iconsW && !overlaps(leftIcons, leftPad) -> leftIcons to true
        else -> RectDp(max(0f, game.right - iconsW), game.top, iconsW, ICON_TOUCH_DP) to false
    }

    val gamePx = RectPx(
        left = (game.left * density).roundToInt(),
        top = (game.top * density).roundToInt(),
        width = (game.width * density).roundToInt(),
        height = (game.height * density).roundToInt(),
    )
    val r = { v: Float -> v.roundToInt() }
    return ScreenLayout(
        mode = LayoutMode.SPLIT,
        availWdp = w,
        availHdp = h,
        density = density,
        game = game,
        gamePx = gamePx,
        gameScale = gamePx.width.toFloat() / SCREEN_WIDTH,
        pads = listOf(PadPlacement(leftPad, upperRows), PadPlacement(rightPad, lowerRows)),
        icons = icons,
        iconsOutside = outside,
        keyH = keyH,
        fits = true,
        details = listOf(
            "SPLIT key ${r(keyW)}x${r(keyH)}dp side ${r(side)}dp",
            "pads ${r(padW)}x${r(upperH)} / ${r(padW)}x${r(lowerH)}dp",
        ),
        summary = "SPLIT avail=${r(w)}x${r(h)}dp game=${r(gameW)}x${r(gameH)}dp@${r(game.left)},${r(game.top)} " +
            "px=${gamePx.width}x${gamePx.height} key=${r(keyW)}x${r(keyH)} side=${r(side)}" + if (outside) " icons=out" else "",
    )
}
