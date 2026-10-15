// 배치 편집(사용자 직접 배치) 모델. 순수 Kotlin — android.* 의존 없음, 단위 테스트 대상.
// 게임 사각형·키패드 사각형들·⏸⚙ 아이콘 사각형을 사용자가 옮기고 크기를 바꾼 결과를 화면 크기(가용 영역 dp)별로 저장한다.
// 저장한 배치가 있으면 자동/위아래/좌우 계산 대신 그것을 쓴다(편집기 [기본값] = 저장 삭제).

package com.parkjeongseop.wipi

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 키패드 한 덩어리에 들어가는 행 묶음 */
enum class PadRows(val rows: List<List<KeyDef>>) {
    ALL(KeypadModel.ROWS),
    UPPER(KeypadModel.UPPER_ROWS),
    LOWER(KeypadModel.LOWER_ROWS),
    ;

    companion object {
        fun of(rows: List<List<KeyDef>>): PadRows? = entries.firstOrNull { it.rows == rows }
    }
}

data class PadSpec(val rows: PadRows, val rect: RectDp)

/** 편집 가능한 배치. 좌표는 가용 영역(인셋 제외) 왼쪽 위 원점, dp. */
data class CustomLayout(val game: RectDp, val pads: List<PadSpec>, val icons: RectDp) {
    val isSplit: Boolean get() = pads.size > 1

    /** 저장 문자열: `game=l,t,w,h;icons=l,t,w,h;pad=ALL:l,t,w,h;…` (로케일 무관) */
    fun serialize(): String = buildString {
        append("game=").append(rect(game))
        append(";icons=").append(rect(icons))
        pads.forEach { append(";pad=").append(it.rows.name).append(':').append(rect(it.rect)) }
    }

    /**
     * 그릴 배치로. 화면이 저장 때보다 조금 작으면(내비게이션 바 방식 변경 등, [MIN_FIT_SCALE] 이상) 전체를 같은 비율로
     * 줄이고, 각 사각형은 가용 영역 안으로 들인다. 그보다 훨씬 작으면 null → 호출자는 자동 배치를 쓴다.
     */
    fun toScreenLayout(availWdp: Float, availHdp: Float, density: Float): ScreenLayout? {
        val w = max(0f, availWdp)
        val h = max(0f, availHdp)
        val all = listOf(game, icons) + pads.map { it.rect }
        val maxRight = all.maxOf { it.right }
        val maxBottom = all.maxOf { it.bottom }
        val scale = min(1f, min(if (maxRight > 0f) w / maxRight else 1f, if (maxBottom > 0f) h / maxBottom else 1f))
        if (scale < MIN_FIT_SCALE) return null
        fun fit(r: RectDp) = LayoutEdit.clampInside(
            if (scale < 1f) RectDp(r.left * scale, r.top * scale, r.width * scale, r.height * scale) else r, w, h,
        )
        val g = fit(game)
        val i = LayoutEdit.clampInside(icons.copy(left = icons.left * scale, top = icons.top * scale), w, h) // 아이콘은 크기 고정
        val p = pads.map { PadPlacement(fit(it.rect), it.rows.rows) }
        val gamePx = RectPx(
            left = (g.left * density).roundToInt(),
            top = (g.top * density).roundToInt(),
            width = (g.width * density).roundToInt(),
            height = (g.height * density).roundToInt(),
        )
        val first = p.firstOrNull()
        val keyH = first?.let { (it.rect.height - 2 * KEYPAD_MARGIN_DP - (it.rows.size - 1) * KEYPAD_SPACING_DP) / it.rows.size } ?: 0f
        val r = { v: Float -> v.roundToInt() }
        return ScreenLayout(
            mode = LayoutMode.CUSTOM,
            availWdp = w,
            availHdp = h,
            density = density,
            game = g,
            gamePx = gamePx,
            gameScale = gamePx.width.toFloat() / SCREEN_WIDTH,
            pads = p,
            icons = i,
            iconsOutside = !LayoutEdit.overlaps(i, g),
            keyH = keyH,
            fits = true,
            details = listOf("CUSTOM pads ${p.size} keyH ${r(keyH)}dp") + p.map { "pad ${r(it.rect.width)}x${r(it.rect.height)}@${r(it.rect.left)},${r(it.rect.top)}" },
            summary = "CUSTOM avail=${r(w)}x${r(h)}dp game=${r(g.width)}x${r(g.height)}dp@${r(g.left)},${r(g.top)} pads=${p.size}",
        )
    }

    companion object {
        /** 저장 때보다 화면이 이 비율보다 작아지면 저장 배치를 쓰지 않는다 */
        const val MIN_FIT_SCALE = 0.85f

        /** 저장 키: 가용 영역 크기(dp, 반올림). 커버·펼침 가로·펼침 세로가 각각 다른 키가 된다. */
        fun key(availWdp: Float, availHdp: Float): String = "${availWdp.roundToInt()}x${availHdp.roundToInt()}"

        /** 계산된 배치를 편집 출발점으로 */
        fun from(layout: ScreenLayout): CustomLayout = CustomLayout(
            game = layout.game,
            pads = layout.pads.map { PadSpec(PadRows.of(it.rows) ?: PadRows.ALL, it.rect) },
            icons = layout.icons,
        )

        fun parse(text: String): CustomLayout? = runCatching {
            var game: RectDp? = null
            var icons: RectDp? = null
            val pads = ArrayList<PadSpec>()
            for (part in text.split(';')) {
                val (k, v) = part.split('=', limit = 2)
                when (k) {
                    "game" -> game = parseRect(v)
                    "icons" -> icons = parseRect(v)
                    "pad" -> {
                        val (rows, rect) = v.split(':', limit = 2)
                        pads += PadSpec(PadRows.valueOf(rows), parseRect(rect))
                    }
                }
            }
            val keys = pads.flatMap { it.rows.rows.flatten() }
            // 모든 키가 정확히 한 번씩 있어야 한다(손상된 저장값 거부)
            if (keys.size != KeypadModel.KEYS.size || keys.toSet() != KeypadModel.KEYS.toSet()) return null
            CustomLayout(game!!, pads, icons!!)
        }.getOrNull()

        private fun rect(r: RectDp) = listOf(r.left, r.top, r.width, r.height).joinToString(",") { ((it * 10).roundToInt() / 10f).toString() }

        private fun parseRect(v: String): RectDp {
            val n = v.split(',').map { it.trim().toFloat() }
            require(n.size == 4 && n[2] >= 0f && n[3] >= 0f)
            return RectDp(n[0], n[1], n[2], n[3])
        }
    }
}

/** 편집 조작(옮기기·크기·나누기/합치기). 모두 가용 영역(w×h dp) 안으로 들인 결과를 돌려준다. */
object LayoutEdit {
    /** 게임 최소 폭(dp) — 원본 240px의 절반 */
    const val MIN_GAME_W_DP = 120f

    /** 편집기에서 키 최소 크기(dp) */
    const val MIN_KEY_DP = 28f

    /** 가장자리·가운데에 이만큼 가까우면 붙인다(dp) */
    const val SNAP_DP = 8f

    fun overlaps(a: RectDp, b: RectDp): Boolean = a.left < b.right && b.left < a.right && a.top < b.bottom && b.top < a.bottom

    /** 사각형을 영역 안으로(크면 줄이지 않고 왼쪽 위에 맞춘다) */
    fun clampInside(r: RectDp, w: Float, h: Float): RectDp =
        r.copy(left = r.left.coerceIn(0f, max(0f, w - r.width)), top = r.top.coerceIn(0f, max(0f, h - r.height)))

    /** 옮기기: 영역 안으로, 가장자리·가로 가운데에 붙임 */
    fun move(r: RectDp, dx: Float, dy: Float, w: Float, h: Float): RectDp {
        var left = r.left + dx
        var top = r.top + dy
        left = snap(left, listOf(0f, w - r.width, (w - r.width) / 2f))
        top = snap(top, listOf(0f, h - r.height, (h - r.height) / 2f))
        return clampInside(r.copy(left = left, top = top), w, h)
    }

    /** 게임 크기: 오른쪽 아래 모서리를 끈다. 3:4 유지, 최소 폭 [MIN_GAME_W_DP], 영역 밖으로 안 나감 */
    fun resizeGame(r: RectDp, dx: Float, dy: Float, w: Float, h: Float): RectDp {
        val aspect = SCREEN_WIDTH.toFloat() / SCREEN_HEIGHT
        // 가로·세로 중 더 많이 움직인 쪽을 따른다
        val wantW = if (abs(dx) >= abs(dy * aspect)) r.width + dx else (r.height + dy) * aspect
        val maxW = min(w - r.left, (h - r.top) * aspect)
        val newW = wantW.coerceIn(min(MIN_GAME_W_DP, maxW), maxW)
        return r.copy(width = newW, height = newW / aspect)
    }

    /** 키패드 크기: 자유 비율, 키가 [MIN_KEY_DP]보다 작아지지 않게, 영역 밖으로 안 나감 */
    fun resizePad(r: RectDp, rowCount: Int, dx: Float, dy: Float, w: Float, h: Float): RectDp {
        val minW = padWidth(MIN_KEY_DP)
        val minH = padHeight(rowCount, MIN_KEY_DP)
        return r.copy(
            width = (r.width + dx).coerceIn(min(minW, w - r.left), max(min(minW, w - r.left), w - r.left)),
            height = (r.height + dy).coerceIn(min(minH, h - r.top), max(min(minH, h - r.top), h - r.top)),
        )
    }

    /** 키패드 하나(7행) ↔ 둘(3행 + 4행). 키 크기는 지금 키패드 기준. */
    fun toggleSplit(layout: CustomLayout, w: Float, h: Float): CustomLayout {
        val first = layout.pads.first()
        val rowsNow = first.rows.rows.size
        val keyW = ((first.rect.width - 2 * KEYPAD_MARGIN_DP - (KEYPAD_COL_COUNT - 1) * KEYPAD_SPACING_DP) / KEYPAD_COL_COUNT).coerceAtLeast(MIN_KEY_DP)
        val keyH = ((first.rect.height - 2 * KEYPAD_MARGIN_DP - (rowsNow - 1) * KEYPAD_SPACING_DP) / rowsNow).coerceAtLeast(MIN_KEY_DP)
        val pads = if (layout.isSplit) {
            val padW = min(padWidth(keyW), w)
            val ph = min(padHeight(KeypadModel.ROWS.size, keyH), h)
            listOf(PadSpec(PadRows.ALL, RectDp((w - padW) / 2f, h - ph, padW, ph)))
        } else {
            // 양옆에 나란히(겹치지 않게 각각 최대 화면 폭의 절반)
            val padW = min(padWidth(keyW), w / 2f)
            val upperH = min(padHeight(KeypadModel.UPPER_ROWS.size, keyH), h)
            val lowerH = min(padHeight(KeypadModel.LOWER_ROWS.size, keyH), h)
            listOf(
                PadSpec(PadRows.UPPER, RectDp(0f, h - upperH, padW, upperH)),
                PadSpec(PadRows.LOWER, RectDp(w - padW, h - lowerH, padW, lowerH)),
            )
        }
        return layout.copy(pads = pads.map { it.copy(rect = clampInside(it.rect, w, h)) })
    }

    private fun snap(v: Float, targets: List<Float>): Float =
        targets.firstOrNull { abs(it - v) <= SNAP_DP } ?: v
}
