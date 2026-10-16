package com.parkjeongseop.wipi

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/** 자동 생성 오픈소스 고지(scripts/gen-licenses.py)가 있고, 의존성이 바뀌었는데 다시 만들지 않은 것을 잡는다 */
class ThirdPartyNoticesTest {
    private val notices = File("src/main/assets/licenses/third_party.txt")

    @Test
    fun noticesExistAndCoverCoreAndAndroid() {
        assertTrue("scripts/gen-licenses.py를 돌려 ${notices.path}를 만들 것", notices.exists())
        val text = notices.readText()
        for (needle in listOf("wie-backend", "rustjava-runtime", "cpal", "androidx.compose.ui:ui", "kotlin-stdlib", "TERMS AND CONDITIONS")) {
            assertTrue("고지에 $needle 없음", text.contains(needle))
        }
    }

    @Test
    fun everyWieCrateInCargoLockIsListedWithSameVersion() {
        // Cargo.lock의 wie·RustJava 크레이트 버전이 고지 목록과 같아야 한다(rev를 바꾸면 고지도 다시 생성)
        val lock = File("../../rust/Cargo.lock").readText()
        val text = notices.readText()
        val pkg = Regex("""\[\[package]]\nname = "([^"]+)"\nversion = "([^"]+)"\nsource = "git\+https://github\.com/[^/]+/(wie|RustJava)""")
        val found = pkg.findAll(lock).map { it.groupValues[1] to it.groupValues[2] }.toList()
        assertTrue("Cargo.lock에서 wie 크레이트를 못 찾음", found.isNotEmpty())
        // 앱에 링크되지 않는 크레이트(도구·테스트용)는 목록에 없을 수 있다 — 목록에 있는 이름은 버전이 맞아야 한다
        for ((name, version) in found) {
            val listed = Regex("""(?m)^${Regex.escape(name)} (\S+) — """).find(text)?.groupValues?.get(1) ?: continue
            assertTrue("$name: Cargo.lock $version, 고지 $listed — gen-licenses.py 다시 실행", listed == version)
        }
    }
}
