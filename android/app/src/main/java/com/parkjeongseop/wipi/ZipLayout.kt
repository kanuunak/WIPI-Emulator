// 게임 zip 정리: 파일이 전부 폴더 한 겹 안에 들어 있는 zip("게임이름/__adf__", "게임이름/xxx.jar" …)을
// 폴더 없이 다시 묶는다. 코어는 __adf__·jar가 zip 맨 위에 있어야 형식을 알아본다("Unknown archive format").
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
     * 모든 항목이 같은 최상위 폴더 하나 아래에 있으면 그 폴더를 벗긴 zip을, 아니면 null(그대로 쓰면 됨).
     * 벗긴 이름에 ASCII가 아닌 글자가 있으면 null: 이름 인코딩(CP949/UTF-8)을 확실히 되살릴 수 없어서다
     * (KTF·LGT 패키지 안쪽 이름은 __adf__·숫자.jar 같은 ASCII라 대개 벗길 수 있다).
     */
    fun unwrapSingleFolder(zip: ByteArray): ByteArray? {
        val entries = try {
            readEntries(zip)
        } catch (e: ZipException) {
            return null
        } catch (e: IllegalArgumentException) {
            return null // 이름 디코딩 실패
        }
        if (entries.isEmpty()) return null

        val first = entries.first().first
        val slash = first.indexOf('/')
        if (slash <= 0) return null
        val prefix = first.substring(0, slash + 1)
        if (entries.any { !it.first.startsWith(prefix) }) return null

        val stripped = entries.mapNotNull { (name, data) ->
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
