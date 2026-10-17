// 게임별 설정(Phase 3.6). 순수 Kotlin — android.* 의존 없음, 단위 테스트 대상.
// 화면 관련 설정(스킨·키패드 테마·배치 방식·저장 배치·화면 필터)을 게임마다 따로 둘 수 있다.
// 저장 키: 전체 = `<이름>`, 게임 = `game.<게임 id>.<이름>`. 게임에 "이 게임에만 적용"이 켜져 있을 때만 게임 키를 읽고 쓴다.
// 켜는 순간 지금 전체 값을 게임 키로 복사하고(보던 화면 그대로 시작), 끄면 게임 키를 모두 지워 전체 설정으로 돌아간다.

package com.parkjeongseop.wipi

/** 설정 저장소(SharedPreferences나 테스트용 맵). put(키, null) = 지우기 */
interface KeyValueStore {
    fun keys(): Set<String>
    fun get(key: String): Any?
    fun put(key: String, value: Any?)
}

object GameScope {
    /** 게임마다 따로 둘 수 있는 설정 이름 */
    val SCOPED_NAMES: List<String> = listOf("layoutMode", "screenFilter", "keypadTheme", "skinId")

    /** 배치 편집 저장 배치는 화면 크기별 키(`customLayout.<W>x<H>`)라 앞부분으로 고른다 */
    const val CUSTOM_LAYOUT_PREFIX = "customLayout."

    private const val ENABLED = "override"

    fun prefix(gameId: String): String = "game.$gameId."

    fun isScoped(name: String): Boolean = name in SCOPED_NAMES || name.startsWith(CUSTOM_LAYOUT_PREFIX)

    /** 읽고 쓸 키: 게임 범위가 있으면 게임 키, 없으면 전체 키 */
    fun key(scope: String?, name: String): String = if (scope == null) name else prefix(scope) + name

    fun isEnabled(store: KeyValueStore, gameId: String): Boolean = store.get(prefix(gameId) + ENABLED) == true

    /** 지금 쓸 범위: 그 게임에 켜져 있으면 게임 id, 아니면 null(전체) */
    fun scopeFor(store: KeyValueStore, gameId: String?): String? = gameId?.takeIf { isEnabled(store, it) }

    /** 켜기: 전체 값(화면 관련)을 게임 키로 복사. 이미 켜져 있으면 그대로 */
    fun enable(store: KeyValueStore, gameId: String) {
        if (isEnabled(store, gameId)) return
        val p = prefix(gameId)
        store.keys().filter { !it.startsWith("game.") && isScoped(it) }.forEach { store.put(p + it, store.get(it)) }
        store.put(p + ENABLED, true)
    }

    /** 끄기(또는 게임 삭제): 이 게임의 키를 모두 지운다 */
    fun forget(store: KeyValueStore, gameId: String) {
        val p = prefix(gameId)
        store.keys().filter { it.startsWith(p) }.forEach { store.put(it, null) }
    }
}
