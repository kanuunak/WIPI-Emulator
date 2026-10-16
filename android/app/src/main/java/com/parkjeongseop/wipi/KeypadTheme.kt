// 키패드 테마(Phase 3.6). 순수 Kotlin — android.*·Compose 의존 없음, 단위 테스트 대상.
// 프리셋이 색(키·방향키·기능키·라벨·몸체·테두리)을 정하고, 모양·투명도·라벨 크기는 프리셋 기본값을 사용자가 바꿀 수 있다.
// 색은 ARGB Long(0xAARRGGBB)으로 둔다 — 그리기(TouchKeypad.drawKeys)에서 Compose Color로 바꾼다.
// 모든 디자인은 직접 정한 일반적인 색 조합이다(특정 제조사·통신사 디자인 아님, AGENTS.md 규칙 8).

package com.parkjeongseop.wipi

import kotlin.math.min
import kotlin.math.roundToInt

/** 키 모양: 둥근 사각형 / 네모 / 알약(짧은 변의 절반 반지름) */
enum class KeyShape { ROUNDED, SQUARE, PILL }

/** 라벨 글자 크기(sp) */
enum class LabelSize(val sp: Float) { SMALL(13f), MEDIUM(16f), LARGE(20f) }

/** 색 묶음. [body]가 있으면 키패드 덩어리 뒤에 판(몸체)을 깔고, [border]가 있으면 키 테두리를 1dp로 그린다. */
data class KeyPalette(
    val numKey: Long,
    val dpadKey: Long,
    val funcKey: Long,
    val label: Long,
    /** 눌린 키는 키 색을 이 색 쪽으로 [PRESS_MIX]만큼 섞는다 */
    val pressTint: Long,
    val body: Long? = null,
    val border: Long? = null,
)

/** 프리셋: 색 묶음 + 모양·투명도·라벨 크기의 기본값 */
enum class KeypadPreset(
    val palette: KeyPalette,
    val shape: KeyShape,
    val opacity: Float,
    val labelSize: LabelSize,
) {
    /** 기본 회색 — Phase 3.5까지의 모습 그대로 */
    GRAY(
        // 눌림 = 배경색(EMULATOR_BG) 쪽으로 40% — 예전 "알파 0.6"과 같은 색
        KeyPalette(numKey = 0xFF404040, dpadKey = 0xFF404040, funcKey = 0xFF404040, label = 0xFFFFFFFF, pressTint = 0xFF202020),
        KeyShape.ROUNDED, 1f, LabelSize.MEDIUM,
    ),

    /** 검정 폴더폰: 검은 몸체, 숫자키는 검정, 방향키는 은회색, 라벨은 밝은 회색 */
    FOLDER_BLACK(
        KeyPalette(
            numKey = 0xFF262626, dpadKey = 0xFF6B6E73, funcKey = 0xFF34363A, label = 0xFFE8E8E8,
            pressTint = 0xFF7FD4FF, body = 0xFF0E0E0F, border = 0xFF4A4A4A,
        ),
        KeyShape.ROUNDED, 1f, LabelSize.MEDIUM,
    ),

    /** 흰색 슬라이드폰: 밝은 몸체에 흰 네모 키, 진회색 라벨 */
    SLIDE_WHITE(
        KeyPalette(
            numKey = 0xFFFAFAFA, dpadKey = 0xFFDCDDE0, funcKey = 0xFFE9EAEC, label = 0xFF222222,
            pressTint = 0xFF5B8DEF, body = 0xFFC9CBCF, border = 0xFFA9ABB0,
        ),
        KeyShape.SQUARE, 1f, LabelSize.MEDIUM,
    ),

    /** 반투명: 게임 위에 겹쳐도 보이도록 흰 알약 키를 옅게, 테두리와 라벨은 또렷하게 */
    TRANSLUCENT(
        KeyPalette(
            numKey = 0xFFFFFFFF, dpadKey = 0xFFFFFFFF, funcKey = 0xFFFFFFFF, label = 0xFFFFFFFF,
            pressTint = 0xFFFFFFFF, border = 0xFFFFFFFF,
        ),
        KeyShape.PILL, 0.3f, LabelSize.MEDIUM,
    ),
    ;

    fun defaults(): KeypadTheme = KeypadTheme(this, shape, opacity, labelSize)
}

/**
 * 사용자가 고른 테마: 프리셋 + 모양·투명도·라벨 크기.
 * @param opacity 키 채움·몸체의 불투명도([MIN_OPACITY]~1). 라벨과 테두리는 [labelAlpha]·[borderAlpha]로 더 또렷하게.
 */
data class KeypadTheme(
    val preset: KeypadPreset = KeypadPreset.GRAY,
    val shape: KeyShape = preset.shape,
    val opacity: Float = preset.opacity,
    val labelSize: LabelSize = preset.labelSize,
) {
    val palette: KeyPalette get() = preset.palette

    fun keyColor(cluster: KeyCluster): Long = when (cluster) {
        KeyCluster.NUM -> palette.numKey
        KeyCluster.DPAD -> palette.dpadKey
        KeyCluster.FUNC -> palette.funcKey
    }

    /** 눌린 키 채움색: 키 색을 [KeyPalette.pressTint] 쪽으로 섞는다 */
    fun pressedColor(cluster: KeyCluster): Long = mixArgb(keyColor(cluster), palette.pressTint, PRESS_MIX)

    /** 채움 불투명도. 눌리면 더 진하게(반투명 테마에서도 눌림이 보이게) */
    fun fillAlpha(pressed: Boolean): Float = if (pressed) min(1f, opacity + PRESS_ALPHA_BOOST) else opacity

    /** 라벨은 키가 옅어도 읽히게 최소 [MIN_LABEL_ALPHA] */
    val labelAlpha: Float get() = maxOf(opacity, MIN_LABEL_ALPHA)

    /** 테두리는 채움보다 또렷하게 */
    val borderAlpha: Float get() = maxOf(opacity, MIN_BORDER_ALPHA)

    /** 키 모서리 반지름(px) */
    fun cornerRadiusPx(widthPx: Float, heightPx: Float, density: Float): Float = when (shape) {
        KeyShape.ROUNDED -> min(ROUNDED_CORNER_DP * density, min(widthPx, heightPx) / 2f)
        KeyShape.SQUARE -> min(SQUARE_CORNER_DP * density, min(widthPx, heightPx) / 2f)
        KeyShape.PILL -> min(widthPx, heightPx) / 2f
    }

    /** 저장 문자열: `preset=GRAY;shape=ROUNDED;opacity=100;label=MEDIUM` (로케일 무관, 투명도는 %) */
    fun serialize(): String =
        "preset=${preset.name};shape=${shape.name};opacity=${(opacity * 100).roundToInt()};label=${labelSize.name}"

    companion object {
        const val ROUNDED_CORNER_DP = 8f
        const val SQUARE_CORNER_DP = 2f
        const val PRESS_MIX = 0.4f
        const val PRESS_ALPHA_BOOST = 0.35f
        const val MIN_OPACITY = 0.2f
        const val MIN_LABEL_ALPHA = 0.85f
        const val MIN_BORDER_ALPHA = 0.6f

        val DEFAULT = KeypadTheme()

        /** [serialize]의 역. 모르는 값은 프리셋 기본값, 문자열이 비었거나 프리셋을 모르면 null */
        fun parse(s: String?): KeypadTheme? {
            if (s.isNullOrBlank()) return null
            val fields = s.split(';').mapNotNull { part ->
                val i = part.indexOf('=')
                if (i <= 0) null else part.substring(0, i).trim() to part.substring(i + 1).trim()
            }.toMap()
            val preset = fields["preset"]?.let { runCatching { KeypadPreset.valueOf(it) }.getOrNull() } ?: return null
            val shape = fields["shape"]?.let { runCatching { KeyShape.valueOf(it) }.getOrNull() } ?: preset.shape
            val opacity = fields["opacity"]?.toIntOrNull()?.let { (it / 100f).coerceIn(MIN_OPACITY, 1f) } ?: preset.opacity
            val label = fields["label"]?.let { runCatching { LabelSize.valueOf(it) }.getOrNull() } ?: preset.labelSize
            return KeypadTheme(preset, shape, opacity, label)
        }

        /** ARGB 두 색을 채널별로 섞는다(t=0 → a, t=1 → b) */
        fun mixArgb(a: Long, b: Long, t: Float): Long {
            fun ch(c: Long, shift: Int) = ((c shr shift) and 0xFF).toInt()
            fun mix(shift: Int) = (ch(a, shift) + (ch(b, shift) - ch(a, shift)) * t).roundToInt().coerceIn(0, 255).toLong()
            return (mix(24) shl 24) or (mix(16) shl 16) or (mix(8) shl 8) or mix(0)
        }
    }
}
