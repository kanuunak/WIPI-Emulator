// 게임 zip 정리: 게임 파일이 폴더 안에 들어 있는 zip("게임이름/__adf__", "게임이름/xxx.jar" …, 옆에 설치법 그림 등)에서
// 게임 폴더만 꺼내 맨 위로 다시 묶는다. 코어는 __adf__·jar가 zip 맨 위에 있어야 형식을 알아본다("Unknown archive format").
// 폴더 임포트도 여기서 게임 파일 하나로 만든다(코어는 zip/jar 바이트 하나만 받는다).
// 순수 Kotlin(java.util.zip) — JVM 단위 테스트 대상.

package com.parkjeongseop.wipi

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipException
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

object ZipLayout {
    /**
     * 게임 폴더를 찾아 그 안만 맨 위로 꺼낸 zip을, 고칠 게 없으면 null(그대로 쓰면 됨).
     * 게임 폴더 = `__adf__`가 정확히 하나 있으면 그 폴더(밖의 설치법 그림·readme 등은 버린다),
     * `__adf__`가 없으면 모든 항목이 들어 있는 최상위 폴더 하나.
     * 꺼낸 이름에 ASCII가 아닌 글자가 있으면 null: 이름 인코딩(CP949/UTF-8)을 확실히 되살릴 수 없어서다
     * (KTF·LGT 패키지 안쪽 이름은 __adf__·숫자.jar 같은 ASCII라 대개 꺼낼 수 있다).
     */
    fun unwrapGameFolder(zip: ByteArray): ByteArray? {
        val entries = try {
            readEntries(zip)
        } catch (e: ZipException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null // 이름 디코딩 실패
        }
        if (entries.isEmpty()) return null

        val prefix = gameRoot(entries.map { it.first }) ?: return null
        if (prefix.isEmpty()) return null

        val stripped = entries.mapNotNull { (name, data) ->
            if (!name.startsWith(prefix)) return@mapNotNull null
            name.removePrefix(prefix).takeIf { it.isNotEmpty() }?.let { it to data }
        }
        if (stripped.isEmpty()) return null
        if (stripped.any { (name, _) -> name.any { it.code > 0x7e || it.code < 0x20 } }) return null

        val out = ByteArrayOutputStream(zip.size)
        ZipOutputStream(out).use { zos ->
            for ((name, data) in stripped) {
                zos.putNextEntry(ZipEntry(name))
                if (data != null) zos.write(data)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /**
     * 폴더 임포트: 폴더 안 파일들(상대 경로 to 내용)을 코어가 받는 게임 파일 하나로 만든다 → (파일 이름, 내용).
     * - 파일이 전부 하위 폴더 하나 안에 있으면 그 폴더를 벗긴다(위 zip과 같은 규칙).
     * - `__adf__`가 없고 맨 위 .jar가 하나뿐이면 그 jar를 그대로 쓴다(J2ME: jar + jad 폴더).
     * - 그 밖에는 폴더 내용 전체를 `<폴더 이름>.zip`으로 묶는다(KTF·LGT 패키지: __adf__ + jar + 데이터).
     * 파일이 없으면 null.
     */
    fun fromFolder(folderName: String, files: List<Pair<String, ByteArray>>): Pair<String, ByteArray>? {
        if (files.isEmpty()) return null
        var entries = files
        // __adf__가 있는 폴더가 게임 폴더(밖의 파일은 버림), 없으면 모두를 감싼 폴더를 한 겹씩 벗긴다
        val adf = entries.filter { it.first == "__adf__" || it.first.endsWith("/__adf__") }
        if (adf.size == 1) {
            val prefix = adf.single().first.removeSuffix("__adf__")
            entries = entries.filter { it.first.startsWith(prefix) }.map { (path, data) -> path.removePrefix(prefix) to data }
        }
        while (true) {
            val first = entries.first().first.substringBefore('/', "")
            if (first.isEmpty() || entries.any { !it.first.startsWith("$first/") }) break
            entries = entries.map { (path, data) -> path.removePrefix("$first/") to data }
        }

        val root = entries.filter { '/' !in it.first }
        val jars = root.filter { it.first.endsWith(".jar", ignoreCase = true) }
        if (root.none { it.first == "__adf__" } && jars.size == 1) return jars.single()

        val name = folderName.ifBlank { "game" }
        return "$name.zip" to pack(entries)
    }

    /**
     * 게임 폴더 접두사("a/b/", 맨 위면 ""). `__adf__`가 정확히 하나면 그 폴더, 없으면 모든 이름이 공유하는
     * 최상위 폴더 하나(없으면 null). `__adf__`가 여럿이면(게임 여러 개) 고르지 않는다 → null.
     */
    private fun gameRoot(names: List<String>): String? {
        val adf = names.filter { it == "__adf__" || it.endsWith("/__adf__") }
        if (adf.size == 1) return adf.single().removeSuffix("__adf__")
        if (adf.size > 1) return null
        val first = names.first()
        val slash = first.indexOf('/')
        if (slash <= 0) return null
        val prefix = first.substring(0, slash + 1)
        return prefix.takeIf { p -> names.all { it.startsWith(p) } }
    }

    /** (상대 경로 to 내용) → zip. 이름은 UTF-8로 쓴다. */
    fun pack(files: List<Pair<String, ByteArray>>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            for ((path, data) in files) {
                zos.putNextEntry(ZipEntry(path))
                zos.write(data)
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    /** (이름, 내용 — 폴더면 null). 이름은 UTF-8 플래그가 없으면 ISO-8859-1로 읽는다(바이트 보존, 접두사 비교용). */
    private fun readEntries(zip: ByteArray): List<Pair<String, ByteArray?>> {
        val result = ArrayList<Pair<String, ByteArray?>>()
        ZipInputStream(ByteArrayInputStream(zip), Charsets.ISO_8859_1).use { zis ->
            while (true) {
                val entry = zis.nextEntry ?: break
                result += entry.name to if (entry.isDirectory) null else zis.readBytes()
            }
        }
        return result
    }
}
