// 게임 zip 정리: 게임 파일이 폴더 안에 들어 있는 zip("게임이름/__adf__", "게임이름/xxx.jar" …, 옆에 설치법 그림 등)에서
// 게임 폴더만 꺼내 맨 위로 다시 묶는다. 코어는 플랫폼 표시 파일이 zip 맨 위에 있어야 형식을 알아본다
// ("Unknown archive format"): KTF `__adf__`, LGT `app_info`, SKT `*.msd`, J2ME(ez-java) `*.jad`+`*.jar`
// (wipi_core create_emulator → 각 Emulator::loadable_archive).
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
     * 게임 폴더 = 플랫폼 표시 파일([isMarker])이 있는 폴더가 하나뿐이면 그 폴더(밖의 설치법 그림·readme 등은 버린다),
     * 표시 파일이 없으면 모든 항목이 들어 있는 최상위 폴더 하나.
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
     * 원칙: 사용자가 그 폴더를 zip으로 묶어 임포트한 것과 같게 만든다.
     * - 게임 폴더는 zip과 같은 규칙으로 찾는다(표시 파일이 있는 폴더, 없으면 모두를 감싼 폴더를 벗김).
     * - 표시 파일이 하나도 없고 맨 위 .jar가 하나뿐이면 그 jar를 그대로 쓴다(jar만 든 J2ME 폴더).
     * - 그 밖에는 게임 폴더 내용 전체를 `<폴더 이름>.zip`으로 묶는다(KTF·LGT·SKT 패키지, jad+jar).
     * 파일이 없으면 null.
     */
    fun fromFolder(folderName: String, files: List<Pair<String, ByteArray>>): Pair<String, ByteArray>? {
        if (files.isEmpty()) return null
        var entries = files
        // 표시 파일이 있는 폴더가 게임 폴더(밖의 파일은 버림), 없으면 모두를 감싼 폴더를 한 겹씩 벗긴다
        val markerDirs = markerDirs(entries.map { it.first })
        if (markerDirs.size == 1) {
            val prefix = markerDirs.single()
            entries = entries.filter { it.first.startsWith(prefix) }.map { (path, data) -> path.removePrefix(prefix) to data }
        } else if (markerDirs.isEmpty()) {
            while (true) {
                val first = entries.first().first.substringBefore('/', "")
                if (first.isEmpty() || entries.any { !it.first.startsWith("$first/") }) break
                entries = entries.map { (path, data) -> path.removePrefix("$first/") to data }
            }
        }

        val root = entries.filter { '/' !in it.first }
        val jars = root.filter { it.first.endsWith(".jar", ignoreCase = true) }
        if (markerDirs.isEmpty() && jars.size == 1) return jars.single()

        val name = folderName.ifBlank { "game" }
        return "$name.zip" to pack(entries)
    }

    /** 코어가 플랫폼을 알아보는 표시 파일: KTF `__adf__`, LGT `app_info`, SKT `*.msd`, J2ME(ez-java) `*.jad` */
    fun isMarker(path: String): Boolean {
        val base = path.substringAfterLast('/')
        return base == "__adf__" || base == "app_info" ||
            base.endsWith(".msd", ignoreCase = true) || base.endsWith(".jad", ignoreCase = true)
    }

    /** 표시 파일이 들어 있는 폴더 접두사들("a/b/", 맨 위면 "") */
    private fun markerDirs(names: List<String>): Set<String> =
        names.filter(::isMarker).map { it.substring(0, it.lastIndexOf('/') + 1) }.toSet()

    /**
     * 게임 폴더 접두사("a/b/", 맨 위면 ""). 표시 파일이 있는 폴더가 하나면 그 폴더, 없으면 모든 이름이 공유하는
     * 최상위 폴더 하나(없으면 null). 표시 파일 폴더가 여럿이면(게임 여러 개) 고르지 않는다 → null.
     */
    private fun gameRoot(names: List<String>): String? {
        val dirs = markerDirs(names)
        if (dirs.size == 1) return dirs.single()
        if (dirs.size > 1) return null
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
