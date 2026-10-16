package com.parkjeongseop.wipi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KeypadThemeTest {
    @Test
    fun serializeRoundTripForEveryPresetAndOverride() {
        for (preset in KeypadPreset.entries) {
            assertEquals(preset.defaults(), KeypadTheme.parse(preset.defaults().serialize()))
            for (shape in KeyShape.entries) for (label in LabelSize.entries) {
                val t = KeypadTheme(preset, shape, 0.55f, label)
                assertEquals(t, KeypadTheme.parse(t.serialize()))
            }
        }
    }

    @Test
    fun parseToleratesMissingOrUnknownFields() {
        assertNull(KeypadTheme.parse(null))
        assertNull(KeypadTheme.parse(""))
        assertNull(KeypadTheme.parse("preset=NOPE"))
        // 프리셋만 있으면 나머지는 프리셋 기본값
        assertEquals(KeypadPreset.TRANSLUCENT.defaults(), KeypadTheme.parse("preset=TRANSLUCENT"))
        // 모르는 모양·라벨은 프리셋 기본값, 투명도는 범위 안으로
        val t = KeypadTheme.parse("preset=SLIDE_WHITE;shape=HEX;opacity=5;label=HUGE;extra=1")!!
        assertEquals(KeyShape.SQUARE, t.shape)
        assertEquals(LabelSize.MEDIUM, t.labelSize)
        assertEquals(KeypadTheme.MIN_OPACITY, t.opacity, 0f)
        assertEquals(1f, KeypadTheme.parse("preset=GRAY;opacity=250")!!.opacity, 0f)
    }

    @Test
    fun defaultGrayLooksLikeBefore() {
        // Phase 3.5까지: 키 0xFF404040, 둥근 8dp, 눌림 = 알파 0.6을 배경 0xFF202020 위에 그린 색(0x33)
        val t = KeypadTheme.DEFAULT
        assertEquals(KeypadPreset.GRAY, t.preset)
        assertEquals(0xFF404040, t.keyColor(KeyCluster.NUM))
        assertEquals(0xFF333333, t.pressedColor(KeyCluster.NUM))
        assertEquals(1f, t.fillAlpha(pressed = true), 0f)
        assertEquals(8f * 2.625f, t.cornerRadiusPx(200f, 120f, 2.625f), 0.001f)
    }

    @Test
    fun pressedIsAlwaysVisible() {
        // 눌린 키는 색이 바뀌거나(불투명 테마) 더 진해진다(반투명 테마)
        for (preset in KeypadPreset.entries) {
            val t = preset.defaults()
            for (cluster in KeyCluster.entries) {
                val colorChanged = t.pressedColor(cluster) != t.keyColor(cluster)
                val alphaChanged = t.fillAlpha(true) > t.fillAlpha(false)
                assertTrue("$preset $cluster 눌림이 안 보임", colorChanged || alphaChanged)
            }
        }
        val translucent = KeypadPreset.TRANSLUCENT.defaults()
        assertTrue(translucent.fillAlpha(true) - translucent.fillAlpha(false) >= 0.3f)
        assertEquals(1f, KeypadTheme(opacity = 0.9f).fillAlpha(true), 0f)
    }

    @Test
    fun cornerRadiusByShape() {
        val d = 3f
        assertEquals(24f, KeypadTheme(shape = KeyShape.ROUNDED).cornerRadiusPx(300f, 150f, d), 0f)
        assertEquals(6f, KeypadTheme(shape = KeyShape.SQUARE).cornerRadiusPx(300f, 150f, d), 0f)
        assertEquals(75f, KeypadTheme(shape = KeyShape.PILL).cornerRadiusPx(300f, 150f, d), 0f)
        assertEquals(40f, KeypadTheme(shape = KeyShape.PILL).cornerRadiusPx(80f, 150f, d), 0f)
        // 아주 작은 키에서도 반지름은 짧은 변의 절반을 넘지 않는다
        assertEquals(5f, KeypadTheme(shape = KeyShape.ROUNDED).cornerRadiusPx(10f, 30f, d), 0f)
    }

    @Test
    fun labelsAndBordersStayReadableWhenTranslucent() {
        val t = KeypadTheme(KeypadPreset.TRANSLUCENT, opacity = KeypadTheme.MIN_OPACITY)
        assertTrue(t.labelAlpha >= KeypadTheme.MIN_LABEL_ALPHA)
        assertTrue(t.borderAlpha >= KeypadTheme.MIN_BORDER_ALPHA)
        assertEquals(1f, KeypadTheme.DEFAULT.labelAlpha, 0f)
    }

    @Test
    fun mixArgb() {
        assertEquals(0xFF000000, KeypadTheme.mixArgb(0xFF000000, 0xFFFFFFFF, 0f))
        assertEquals(0xFFFFFFFF, KeypadTheme.mixArgb(0xFF000000, 0xFFFFFFFF, 1f))
        assertEquals(0xFF808080, KeypadTheme.mixArgb(0xFF000000, 0xFFFFFFFF, 0.5f))
        assertEquals(0x80FF0000, KeypadTheme.mixArgb(0x00FF0000, 0xFFFF0000, 0.5f))
        assertNotEquals(KeypadPreset.FOLDER_BLACK.palette.numKey, KeypadPreset.FOLDER_BLACK.palette.dpadKey)
    }
}
