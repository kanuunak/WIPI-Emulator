package com.parkjeongseop.wipi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GameScopeTest {
    private class MapStore(val m: MutableMap<String, Any?> = linkedMapOf()) : KeyValueStore {
        override fun keys() = m.keys.toSet()
        override fun get(key: String) = m[key]
        override fun put(key: String, value: Any?) {
            if (value == null) m.remove(key) else m[key] = value
        }
    }

    private fun store() = MapStore(
        linkedMapOf(
            "skinId" to "builtin-silver_flip",
            "screenFilter" to "LCD",
            "customLayout.344x835" to "game=0,0,344,459",
            "soundEnabled" to false, // 화면 설정이 아님 → 게임별로 복사하지 않음
            "backgroundDim" to 0.3f,
        ),
    )

    @Test
    fun keysByScope() {
        assertEquals("skinId", GameScope.key(null, "skinId"))
        assertEquals("game.abc.skinId", GameScope.key("abc", "skinId"))
        assertTrue(GameScope.isScoped("customLayout.904x2192"))
        assertFalse(GameScope.isScoped("soundEnabled"))
        assertFalse(GameScope.isScoped("backgroundDim"))
    }

    @Test
    fun enableCopiesScreenSettingsOnly() {
        val s = store()
        assertNull(GameScope.scopeFor(s, "g1"))
        GameScope.enable(s, "g1")
        assertEquals("g1", GameScope.scopeFor(s, "g1"))
        assertEquals("builtin-silver_flip", s.get("game.g1.skinId"))
        assertEquals("LCD", s.get("game.g1.screenFilter"))
        assertEquals("game=0,0,344,459", s.get("game.g1.customLayout.344x835"))
        assertNull(s.get("game.g1.soundEnabled"))
        assertNull(s.get("game.g1.backgroundDim"))
        // 다른 게임은 그대로 전체
        assertNull(GameScope.scopeFor(s, "g2"))
        assertNull(GameScope.scopeFor(s, null))
    }

    @Test
    fun gameChangesDoNotTouchGlobalAndForgetRestores() {
        val s = store()
        GameScope.enable(s, "g1")
        s.put(GameScope.key("g1", "skinId"), "my-razr")
        assertEquals("builtin-silver_flip", s.get("skinId"))
        // 다시 켜도(이미 켜짐) 게임 값을 덮어쓰지 않는다
        GameScope.enable(s, "g1")
        assertEquals("my-razr", s.get("game.g1.skinId"))
        GameScope.forget(s, "g1")
        assertNull(GameScope.scopeFor(s, "g1"))
        assertTrue(s.keys().none { it.startsWith("game.g1.") })
        assertEquals("builtin-silver_flip", s.get("skinId"))
    }

    @Test
    fun enableDoesNotCopyOtherGames() {
        val s = store()
        GameScope.enable(s, "g1")
        GameScope.enable(s, "g2")
        assertTrue(s.keys().none { it.startsWith("game.g2.game.") })
        // forget은 비슷한 id의 다른 게임을 건드리지 않는다
        GameScope.enable(s, "g10")
        GameScope.forget(s, "g1")
        assertEquals("g10", GameScope.scopeFor(s, "g10"))
    }
}
