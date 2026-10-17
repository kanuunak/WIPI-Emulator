// 스킨 팩(Phase 3.6): 폰 그림(이미지) 위에 게임 화면 자리와 키 영역을 skin.json으로 정한다.
// 규격 문서: docs/fold-retro/SKIN_FORMAT.md (사람·AI가 이미지에 맞춰 skin.json을 쓰는 규칙). 바꾸면 문서도 같이 바꾼다.
// 순수 Kotlin — android.* 의존 없음, 단위 테스트 대상. 이미지 읽기·저장은 SkinStore.kt, 그리기·입력은 SkinView.kt.
// 좌표는 모두 이미지 기준 px([SkinLayoutSpec.width]×[SkinLayoutSpec.height] — 실제 그림 해상도가 달라도 비율만 같으면 된다).

package com.parkjeongseop.wipi

import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** 키 영역 모양: 사각형 / 타원 / 둥근 사각형(radius) — 눌림 표시를 그릴 때 쓴다(누르기 판정은 사각형) */
enum class SkinKeyShape { RECT, OVAL, ROUND }

/** 사각형(왼쪽·위·오른쪽·아래) */
data class SkinBox(val left: Float, val top: Float, val right: Float, val bottom: Float) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top

    fun map(scale: Float, dx: Float, dy: Float) = SkinBox(dx + left * scale, dy + top * scale, dx + right * scale, dy + bottom * scale)

    fun overlapArea(o: SkinBox): Float =
        max(0f, min(right, o.right) - max(left, o.left)) * max(0f, min(bottom, o.bottom) - max(top, o.top))
}

data class SkinKey(val code: String, val box: SkinBox, val shape: SkinKeyShape = SkinKeyShape.RECT, val radius: Float = 0f)

data class SkinLayoutSpec(
    /** 몸체 그림(팩 안의 상대 경로) */
    val image: String,
    val width: Int,
    val height: Int,
    /** 모든 키가 눌린 모습의 그림(같은 비율, 선택). 눌린 키 영역만 이 그림으로 바꿔 그린다 */
    val pressed: String? = null,
    /** 그림 밖(남는 여백) 색 */
    val background: Long = 0xFF000000,
    /** 게임 화면 자리 */
    val screen: SkinBox,
    /** 게임 화면 자리에서 게임이 안 덮는 부분 색 */
    val screenColor: Long = 0xFF000000,
    /** true면 게임을 화면 자리에 꽉 채운다(비율 무시), false면 3:4 비율을 지켜 가운데 */
    val stretch: Boolean = false,
    /** [pressed]가 없을 때 눌린 키 위에 덮는 색 */
    val pressTint: Long = DEFAULT_PRESS_TINT,
    /** ⏸⚙ 아이콘 묶음의 왼쪽 위(이미지 px). 없으면 그림 오른쪽 위 */
    val icons: Pair<Float, Float>? = null,
    val keys: List<SkinKey>,
) {
    val aspect: Float get() = width.toFloat() / height

    companion object {
        /** 회색 45% — 흰 키는 어둡게, 검은 키는 밝게 보인다 */
        const val DEFAULT_PRESS_TINT = 0x73808080L
    }
}

/** [names]: 언어 코드별 이름(선택, 예: "ko") — 없으면 [name] */
data class SkinSpec(val name: String, val layouts: List<SkinLayoutSpec>, val names: Map<String, String> = emptyMap()) {
    fun displayName(language: String): String = names[language] ?: name
}

/** [errors]가 비어 있어야 쓸 수 있다. [warnings]는 동작은 하지만 확인할 것 */
data class SkinParseResult(val spec: SkinSpec?, val errors: List<String>, val warnings: List<String>)

/** 화면(가용 영역 px)에 놓인 스킨 */
data class SkinPlacement(
    val layout: SkinLayoutSpec,
    /** 그림 전체가 놓인 자리 */
    val image: SkinBox,
    val screen: SkinBox,
    /** 게임을 그릴 자리(정수 px) */
    val game: SkinBox,
    val scale: Float,
    /** 화면 px의 키 영역(layout.keys와 같은 순서) */
    val keys: List<SkinKey>,
    /** 누르기 판정(그리기 순서와 같은 사각형) */
    val keypad: KeypadLayout,
    /** ⏸⚙ 묶음 왼쪽 위 */
    val iconsX: Float,
    val iconsY: Float,
)

object SkinPack {
    const val FORMAT = 1
    const val JSON_NAME = "skin.json"

    /** 그림 한 변 최대(px). 넘으면 [SkinStore]가 줄여 읽는다(좌표는 그대로) */
    const val MAX_IMAGE_SIDE = 4096

    /** 쓸 수 있는 키 코드(§3.3). HANGUP은 게임이 종료로 처리할 수 있어 받지 않는다 */
    val KEY_CODES: List<String> = KeypadModel.KEYS.map { it.code }

    fun parse(text: String): SkinParseResult {
        val errors = ArrayList<String>()
        val warnings = ArrayList<String>()
        val root = try {
            MiniJson.parse(text)
        } catch (e: JsonError) {
            return SkinParseResult(null, listOf(e.message ?: "bad JSON"), emptyList())
        }
        if (root !is Map<*, *>) return SkinParseResult(null, listOf("skin.json: top level must be an object { … }"), emptyList())

        when (val f = root["format"]) {
            null -> warnings += "\"format\" missing — assuming $FORMAT"
            is Double -> if (f.toInt() != FORMAT) errors += "\"format\": $f is not supported (this app reads format $FORMAT)"
            else -> errors += "\"format\" must be a number"
        }
        val name = (root["name"] as? String)?.takeIf { it.isNotBlank() } ?: run {
            errors += "\"name\" (non-empty string) is required"
            ""
        }
        val names = (root["names"] as? Map<*, *>)?.entries
            ?.mapNotNull { (k, v) -> if (k is String && v is String && v.isNotBlank()) k to v else null }?.toMap().orEmpty()
        val rawLayouts = root["layouts"] as? List<*>
        if (rawLayouts.isNullOrEmpty()) {
            errors += "\"layouts\" must be a non-empty array"
            return SkinParseResult(null, errors, warnings)
        }
        val layouts = rawLayouts.mapIndexedNotNull { i, raw -> parseLayout("layouts[$i]", raw, errors, warnings) }
        return if (errors.isEmpty()) SkinParseResult(SkinSpec(name, layouts, names), errors, warnings) else SkinParseResult(null, errors, warnings)
    }

    private fun parseLayout(at: String, raw: Any?, errors: MutableList<String>, warnings: MutableList<String>): SkinLayoutSpec? {
        if (raw !is Map<*, *>) {
            errors += "$at must be an object"
            return null
        }
        val before = errors.size
        val image = path(at, "image", raw["image"], required = true, errors)
        val pressed = path(at, "pressed", raw["pressed"], required = false, errors)
        val size = numbers(raw["size"], 2)
        val w = size?.get(0)?.roundToInt() ?: 0
        val h = size?.get(1)?.roundToInt() ?: 0
        if (size == null || w <= 0 || h <= 0) {
            errors += "$at.size must be [width, height] in pixels of the image"
            return null
        }
        val bounds = SkinBox(0f, 0f, w.toFloat(), h.toFloat())
        val screen = box("$at.screen", raw["screen"], bounds, errors)
        val background = color("$at.background", raw["background"], 0xFF000000, errors)
        val screenColor = color("$at.screenColor", raw["screenColor"], 0xFF000000, errors)
        val pressTint = color("$at.pressTint", raw["pressTint"], SkinLayoutSpec.DEFAULT_PRESS_TINT, errors)
        val stretch = when (val fit = raw["fit"]) {
            null, "contain" -> false
            "stretch" -> true
            else -> {
                errors += "$at.fit must be \"contain\" or \"stretch\" (got $fit)"
                false
            }
        }
        val icons = raw["icons"]?.let { v ->
            val p = numbers(v, 2)
            if (p == null || p[0] < 0 || p[1] < 0 || p[0] > w || p[1] > h) {
                errors += "$at.icons must be [x, y] inside the image"
                null
            } else p[0] to p[1]
        }

        val keys = ArrayList<SkinKey>()
        val rawKeys = raw["keys"] as? Map<*, *>
        if (rawKeys == null) {
            errors += "$at.keys must be an object like { \"UP\": [x, y, w, h], … }"
        } else {
            for ((k, v) in rawKeys) {
                val code = k as String
                val where = "$at.keys.$code"
                if (code !in KEY_CODES) {
                    errors += "$where: unknown key code (use: ${KEY_CODES.joinToString(" ")})"
                    continue
                }
                parseKey(where, code, v, bounds, errors)?.let { keys += it }
            }
            val missing = KEY_CODES.filter { c -> keys.none { it.code == c } }
            if (missing.isNotEmpty()) warnings += "$at: keys not on this skin: ${missing.joinToString(" ")}"
        }
        if (errors.size > before || screen == null || image == null) return null

        if (!stretch && abs(screen.width / screen.height - 0.75f) > 0.04f) {
            warnings += "$at.screen is ${screen.width.roundToInt()}x${screen.height.roundToInt()} (not 3:4) — the game keeps 3:4 and gets bars"
        }
        keys.filter { it.box.overlapArea(screen) > 0f }.forEach { warnings += "$at.keys.${it.code} overlaps the screen" }
        for (a in keys.indices) for (b in a + 1 until keys.size) {
            val ka = keys[a].box
            val kb = keys[b].box
            if (ka.overlapArea(kb) > 0.2f * min(ka.width * ka.height, kb.width * kb.height)) {
                warnings += "$at.keys.${keys[a].code} and ${keys[b].code} overlap a lot"
            }
        }
        return SkinLayoutSpec(image!!, w, h, pressed, background, screen, screenColor, stretch, pressTint, icons, keys)
    }

    private fun parseKey(where: String, code: String, v: Any?, bounds: SkinBox, errors: MutableList<String>): SkinKey? = when (v) {
        is List<*> -> box(where, v, bounds, errors)?.let { SkinKey(code, it) }
        is Map<*, *> -> {
            val b = box("$where.rect", v["rect"], bounds, errors)
            val shape = when (val s = v["shape"]) {
                null, "rect" -> SkinKeyShape.RECT
                "oval" -> SkinKeyShape.OVAL
                "round" -> SkinKeyShape.ROUND
                else -> {
                    errors += "$where.shape must be \"rect\", \"oval\" or \"round\" (got $s)"
                    null
                }
            }
            val radius = (v["radius"] as? Double)?.toFloat() ?: 0f
            if (shape == SkinKeyShape.ROUND && radius <= 0f) errors += "$where: shape \"round\" needs \"radius\" (px)"
            if (b != null && shape != null) SkinKey(code, b, shape, radius) else null
        }
        else -> {
            errors += "$where must be [x, y, w, h] or { \"rect\": [x, y, w, h], \"shape\": … }"
            null
        }
    }

    private fun path(at: String, field: String, v: Any?, required: Boolean, errors: MutableList<String>): String? {
        if (v == null) {
            if (required) errors += "$at.$field (file name in the pack) is required"
            return null
        }
        val p = v as? String
        if (p.isNullOrBlank() || !isSafePath(p)) {
            errors += "$at.$field must be a relative file name inside the pack (got $v)"
            return null
        }
        return p
    }

    /** 팩 안의 상대 경로만(절대 경로·.. 금지) */
    fun isSafePath(p: String): Boolean =
        !p.startsWith("/") && !p.startsWith("\\") && p.split('/', '\\').none { it == ".." || it.isEmpty() } && !p.contains(':')

    private fun numbers(v: Any?, n: Int): List<Float>? {
        val l = v as? List<*> ?: return null
        if (l.size != n || l.any { it !is Double }) return null
        return l.map { (it as Double).toFloat() }
    }

    /** [x, y, w, h] → 사각형. [bounds](그림) 밖으로 나가면 오류 */
    private fun box(where: String, v: Any?, bounds: SkinBox, errors: MutableList<String>): SkinBox? {
        val n = numbers(v, 4)
        if (n == null || n[2] <= 0f || n[3] <= 0f) {
            errors += "$where must be [x, y, width, height] with width, height > 0"
            return null
        }
        val b = SkinBox(n[0], n[1], n[0] + n[2], n[1] + n[3])
        if (b.left < 0f || b.top < 0f || b.right > bounds.right + 0.5f || b.bottom > bounds.bottom + 0.5f) {
            errors += "$where ${fmt(n)} goes outside the image ${bounds.right.roundToInt()}x${bounds.bottom.roundToInt()}"
            return null
        }
        return b
    }

    private fun fmt(n: List<Float>) = n.joinToString(", ", "[", "]") { if (it == it.roundToInt().toFloat()) it.roundToInt().toString() else it.toString() }

    /** "#RRGGBB" 또는 "#AARRGGBB" */
    private fun color(where: String, v: Any?, default: Long, errors: MutableList<String>): Long {
        if (v == null) return default
        return parseColor(v as? String) ?: run {
            errors += "$where must be \"#RRGGBB\" or \"#AARRGGBB\" (got $v)"
            default
        }
    }

    fun parseColor(s: String?): Long? {
        if (s == null || !s.startsWith("#")) return null
        val hex = s.substring(1)
        val v = hex.toLongOrNull(16) ?: return null
        return when (hex.length) {
            6 -> 0xFF000000 or v
            8 -> v
            else -> null
        }
    }

    /** 화면 모양(가로/세로 비율)에 가장 가까운 배치 */
    fun chooseLayout(spec: SkinSpec, availW: Float, availH: Float): SkinLayoutSpec {
        if (availW <= 0f || availH <= 0f) return spec.layouts.first()
        val target = availW / availH
        return spec.layouts.minBy { abs(ln(it.aspect / target)) }
    }

    /**
     * [layout]을 가용 영역([availW]×[availH] px)에 비율을 지켜 가운데 놓는다. 게임은 화면 자리 안에 3:4(또는 꽉 채움),
     * 정수 px로 맞춘다(nearest 확대가 고르게). 아이콘 묶음 크기는 [iconsW]×[iconsH] px.
     */
    fun place(layout: SkinLayoutSpec, availW: Float, availH: Float, density: Float, iconsW: Float, iconsH: Float): SkinPlacement {
        val s = min(availW / layout.width, availH / layout.height)
        val dx = (availW - layout.width * s) / 2f
        val dy = (availH - layout.height * s) / 2f
        val image = SkinBox(dx, dy, dx + layout.width * s, dy + layout.height * s)
        val screen = layout.screen.map(s, dx, dy)
        val game = if (layout.stretch) {
            SkinBox(screen.left.roundToInt().toFloat(), screen.top.roundToInt().toFloat(), screen.right.roundToInt().toFloat(), screen.bottom.roundToInt().toFloat())
        } else {
            val g = min(screen.width / SCREEN_WIDTH, screen.height / SCREEN_HEIGHT)
            val gw = (SCREEN_WIDTH * g).roundToInt()
            val gh = (SCREEN_HEIGHT * g).roundToInt()
            val gl = (screen.left + (screen.width - gw) / 2f).roundToInt()
            val gt = (screen.top + (screen.height - gh) / 2f).roundToInt()
            SkinBox(gl.toFloat(), gt.toFloat(), (gl + gw).toFloat(), (gt + gh).toFloat())
        }
        val keys = layout.keys.map { it.copy(box = it.box.map(s, dx, dy), radius = it.radius * s) }
        val rects = keys.map { k ->
            KeyRect(KeypadModel.KEYS.first { it.code == k.code }, k.box.left, k.box.top, k.box.right, k.box.bottom)
        }
        val keypad = KeypadLayout(availW, availH, KeypadMetrics(density), rects)
        val margin = 8f * density
        val (ix, iy) = layout.icons?.let { (x, y) -> dx + x * s to dy + y * s }
            ?: ((image.right - iconsW - margin) to (image.top + margin))
        return SkinPlacement(
            layout = layout,
            image = image,
            screen = screen,
            game = game,
            scale = s,
            keys = keys,
            keypad = keypad,
            iconsX = ix.coerceIn(0f, max(0f, availW - iconsW)),
            iconsY = iy.coerceIn(0f, max(0f, availH - iconsH)),
        )
    }
}
