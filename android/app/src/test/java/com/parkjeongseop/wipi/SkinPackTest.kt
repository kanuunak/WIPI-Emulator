package com.parkjeongseop.wipi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MiniJsonTest {
    @Test
    fun parsesValues() {
        val v = MiniJson.parse("""{"a": [1, -2.5, 3e2], "b": "x\"y\u00e9\n", "c": true, "d": null, "e": {}}""") as Map<*, *>
        assertEquals(listOf(1.0, -2.5, 300.0), v["a"])
        assertEquals("x\"yé\n", v["b"])
        assertEquals(true, v["c"])
        assertTrue(v.containsKey("d") && v["d"] == null)
        assertEquals(emptyMap<String, Any?>(), v["e"])
    }

    @Test
    fun reportsLineOfError() {
        val e = runCatching { MiniJson.parse("{\n  \"a\": 1,\n  \"b\" 2\n}") }.exceptionOrNull()
        assertTrue(e is JsonError)
        assertTrue(e!!.message!!, e.message!!.contains("line 3"))
        assertTrue(runCatching { MiniJson.parse("[1, 2,]") }.isFailure)
        assertTrue(runCatching { MiniJson.parse("{} x") }.isFailure)
    }
}

class SkinPackTest {
    private fun allKeys(): String {
        // 3열 × 7행 격자(이미지 600x1500, 화면 60,60,480,640 아래)
        val rows = KeypadModel.ROWS
        return rows.flatMapIndexed { r, row ->
            row.mapIndexed { c, k -> "\"${k.code}\": [${40 + c * 180}, ${760 + r * 100}, 160, 90]" }
        }.joinToString(",\n")
    }

    private val good = """
        {
          "format": 1,
          "name": "Test Phone",
          "layouts": [
            {
              "image": "body.png",
              "pressed": "pressed.png",
              "size": [600, 1500],
              "screen": [60, 60, 480, 640],
              "background": "#101010",
              "keys": {
                ${allKeys()}
              }
            },
            {
              "image": "wide.png",
              "size": [1600, 1000],
              "screen": [500, 100, 600, 800],
              "fit": "stretch",
              "pressTint": "#80FFFFFF",
              "icons": [1450, 20],
              "keys": { "OK": { "rect": [100, 400, 100, 100], "shape": "oval" }, "5": { "rect": [1300, 400, 120, 90], "shape": "round", "radius": 20 } }
            }
          ]
        }
    """.trimIndent()

    @Test
    fun parsesGoodPack() {
        val r = SkinPack.parse(good)
        assertTrue(r.errors.toString(), r.errors.isEmpty())
        val spec = r.spec!!
        assertEquals("Test Phone", spec.name)
        assertEquals(2, spec.layouts.size)
        val p = spec.layouts[0]
        assertEquals(21, p.keys.size)
        assertEquals(0xFF101010, p.background)
        assertEquals("pressed.png", p.pressed)
        val w = spec.layouts[1]
        assertTrue(w.stretch)
        assertEquals(0x80FFFFFF, w.pressTint)
        assertEquals(SkinKeyShape.OVAL, w.keys.first { it.code == "OK" }.shape)
        assertEquals(20f, w.keys.first { it.code == "5" }.radius, 0f)
        // 두 번째 배치는 키가 둘뿐 → 빠진 키 경고
        assertTrue(r.warnings.any { it.startsWith("layouts[1]: keys not on this skin") })
        assertFalse(r.warnings.any { it.startsWith("layouts[0]: keys not on this skin") })
    }

    @Test
    fun reportsUsefulErrors() {
        fun errors(json: String) = SkinPack.parse(json).also { assertNull(it.spec) }.errors.joinToString("\n")
        assertTrue(errors("{ nope }").contains("JSON line 1"))
        assertTrue(errors("""{"name":"x","layouts":[]}""").contains("\"layouts\" must be a non-empty array"))
        val e = errors(
            """{"format":1,"name":"x","layouts":[{"image":"../a.png","size":[100,200],"screen":[0,0,90,120],
               "keys":{"HANGUP":[0,150,10,10],"OK":[95,150,10,10],"UP":[0,0,0,5]},"background":"red"}]}""",
        )
        assertTrue(e, e.contains("layouts[0].image must be a relative file name"))
        assertTrue(e, e.contains("layouts[0].keys.HANGUP: unknown key code"))
        assertTrue(e, e.contains("layouts[0].keys.OK [95, 150, 10, 10] goes outside the image 100x200"))
        assertTrue(e, e.contains("layouts[0].keys.UP must be [x, y, width, height]"))
        assertTrue(e, e.contains("layouts[0].background must be \"#RRGGBB\""))
        assertTrue(errors("""{"format":2,"name":"x","layouts":[{}]}""").contains("format\": 2.0 is not supported"))
    }

    @Test
    fun warnsAboutScreenShapeAndOverlaps() {
        val r = SkinPack.parse(
            """{"format":1,"name":"x","layouts":[{"image":"a.png","size":[100,200],"screen":[0,0,100,100],
               "keys":{"1":[0,90,50,50],"2":[10,100,50,50]}}]}""",
        )
        assertNotNull(r.spec)
        val w = r.warnings.joinToString("\n")
        assertTrue(w, w.contains("not 3:4"))
        assertTrue(w, w.contains("keys.1 overlaps the screen"))
        assertTrue(w, w.contains("keys.1 and 2 overlap a lot"))
    }

    @Test
    fun safePaths() {
        assertTrue(SkinPack.isSafePath("body.png"))
        assertTrue(SkinPack.isSafePath("img/body.png"))
        assertFalse(SkinPack.isSafePath("/body.png"))
        assertFalse(SkinPack.isSafePath("../body.png"))
        assertFalse(SkinPack.isSafePath("a//b.png"))
        assertFalse(SkinPack.isSafePath("C:\\x.png"))
    }

    @Test
    fun colors() {
        assertEquals(0xFF112233, SkinPack.parseColor("#112233"))
        assertEquals(0x80112233, SkinPack.parseColor("#80112233"))
        assertNull(SkinPack.parseColor("112233"))
        assertNull(SkinPack.parseColor("#12345"))
    }

    @Test
    fun choosesLayoutByScreenShape() {
        val spec = SkinPack.parse(good).spec!!
        // Fold5 커버(세로로 긴 화면) → 세로 그림, 펼친 가로 → 가로 그림
        assertEquals("body.png", SkinPack.chooseLayout(spec, 904f, 2200f).image)
        assertEquals("wide.png", SkinPack.chooseLayout(spec, 2176f, 1700f).image)
    }

    @Test
    fun placesImageCenteredAndGameAt3by4() {
        val layout = SkinPack.parse(good).spec!!.layouts[0]
        // 가용 900x2400 px: 그림 600x1500 → 배율 1.5(가로 기준), 세로 여백 (2400-2250)/2 = 75
        val p = SkinPack.place(layout, 900f, 2400f, density = 2.5f, iconsW = 240f, iconsH = 120f)
        assertEquals(1.5f, p.scale, 1e-4f)
        assertEquals(SkinBox(0f, 75f, 900f, 2325f), p.image)
        assertEquals(SkinBox(90f, 165f, 810f, 1125f), p.screen)
        // 화면 자리 720x960 = 3:4 → 게임이 꽉 찬다(정수 px)
        assertEquals(SkinBox(90f, 165f, 810f, 1125f), p.game)
        // 키도 같은 배율·위치로(누르기 판정 = 그리기)
        val one = p.keypad.rectOf("1")!!
        assertEquals(40f * 1.5f, one.left, 1e-3f)
        assertEquals(75f + 1060f * 1.5f, one.top, 1e-3f)
        assertEquals("1", p.keypad.hitTestInitial(one.centerX, one.centerY)?.code)
        // 아이콘: 그림 오른쪽 위(8dp 안쪽)
        assertEquals(900f - 240f - 20f, p.iconsX, 1e-3f)
        assertEquals(75f + 20f, p.iconsY, 1e-3f)
    }

    @Test
    fun gameKeeps3by4InWideScreenUnlessStretch() {
        val r = SkinPack.parse(
            """{"format":1,"name":"x","layouts":[{"image":"a.png","size":[1000,1000],"screen":[100,100,800,300],"keys":{}}]}""",
        )
        val layout = r.spec!!.layouts[0]
        val p = SkinPack.place(layout, 1000f, 1000f, 1f, 96f, 48f)
        // 300 높이에 3:4 → 225x300, 가운데
        assertEquals(225f, p.game.width, 0f)
        assertEquals(300f, p.game.height, 0f)
        assertEquals(100f + (800f - 225f) / 2f, p.game.left, 1f)
        val stretched = SkinPack.place(layout.copy(stretch = true), 1000f, 1000f, 1f, 96f, 48f)
        assertEquals(SkinBox(100f, 100f, 900f, 400f), stretched.game)
    }

    @Test
    fun dpadJoystickWorksOnSkinKeys() {
        // 스킨 키도 KeypadLayout 규칙 그대로: ◀를 누른 채 OK를 지나 ▶로
        val layout = SkinPack.parse(good).spec!!.layouts[0]
        val p = SkinPack.place(layout, 600f, 1500f, 1f, 96f, 48f)
        val left = p.keypad.rectOf("LEFT")!!
        val right = p.keypad.rectOf("RIGHT")!!
        val start = p.keypad.hitTestInitial(left.centerX, left.centerY)!!
        assertEquals("LEFT", start.code)
        assertEquals("RIGHT", p.keypad.hitTestMove(start, right.centerX, right.centerY).code)
    }

    @Test
    fun bundledSkinsAreValid() {
        // demo/skins/make_skins.py가 만든 기본 스킨(앱 assets에 zip으로 들어간다) — 경고도 없어야 한다
        val dir = java.io.File("../../demo/skins")
        val packs = dir.listFiles()?.filter { java.io.File(it, "skin.json").exists() }.orEmpty()
        assertTrue("demo/skins에 스킨이 없음 (${dir.absolutePath})", packs.isNotEmpty())
        for (pack in packs) {
            val r = SkinPack.parse(java.io.File(pack, "skin.json").readText())
            assertTrue("${pack.name}: ${r.errors}", r.errors.isEmpty())
            assertTrue("${pack.name}: ${r.warnings}", r.warnings.isEmpty())
            for (l in r.spec!!.layouts) {
                assertTrue(java.io.File(pack, l.image).exists())
                assertEquals(SkinPack.KEY_CODES.toSet(), l.keys.map { it.code }.toSet())
            }
            assertTrue(r.spec!!.displayName("ko") != r.spec!!.name)
        }
    }
}
