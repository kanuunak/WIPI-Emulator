package com.parkjeongseop.wipi

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

class ZipLayoutTest {
    /** 이름 → 내용(null이면 폴더) 순서대로 zip을 만든다. [charset]은 UTF-8 플래그 없는 이름 인코딩 시험용 */
    private fun zip(vararg entries: Pair<String, String?>, charset: Charset = Charsets.UTF_8): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out, charset).use { zos ->
            for ((name, data) in entries) {
                zos.putNextEntry(ZipEntry(name))
                if (data != null) zos.write(data.toByteArray())
                zos.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun read(zip: ByteArray): Map<String, String?> {
        val result = LinkedHashMap<String, String?>()
        ZipInputStream(ByteArrayInputStream(zip)).use { zis ->
            while (true) {
                val e = zis.nextEntry ?: break
                result[e.name] = if (e.isDirectory) null else String(zis.readBytes())
            }
        }
        return result
    }

    @Test
    fun unwrapsSingleTopFolder() {
        // 이노티아 연대기2 팩 구조: 한글 폴더 한 겹 안에 __adf__·jar·p/
        val wrapped = zip(
            "이노티아연대기2-wipi1.2/" to null,
            "이노티아연대기2-wipi1.2/010100D5.jar" to "JAR",
            "이노티아연대기2-wipi1.2/__adf__" to "AID:010100D5",
            "이노티아연대기2-wipi1.2/p/" to null,
            "이노티아연대기2-wipi1.2/p/cert.c2s" to "C",
        )
        val flat = ZipLayout.unwrapSingleFolder(wrapped)
        assertNotNull(flat)
        assertEquals(
            mapOf("010100D5.jar" to "JAR", "__adf__" to "AID:010100D5", "p/" to null, "p/cert.c2s" to "C"),
            read(flat!!),
        )
    }

    @Test
    fun unwrapsCp949FolderName() {
        // UTF-8 플래그 없이 CP949로 저장된 폴더 이름(옛 압축 프로그램)도 바이트 그대로 비교해 벗긴다
        val wrapped = zip("붕어빵/" to null, "붕어빵/__adf__" to "A", "붕어빵/1.jar" to "J", charset = Charset.forName("EUC-KR"))
        assertEquals(mapOf("__adf__" to "A", "1.jar" to "J"), read(ZipLayout.unwrapSingleFolder(wrapped)!!))
    }

    @Test
    fun leavesFlatOrMixedZipAlone() {
        assertNull(ZipLayout.unwrapSingleFolder(zip("__adf__" to "A", "1.jar" to "J")))
        assertNull(ZipLayout.unwrapSingleFolder(zip("game/__adf__" to "A", "readme.txt" to "R")))
        assertNull(ZipLayout.unwrapSingleFolder(zip("a/__adf__" to "A", "b/1.jar" to "J")))
        assertNull(ZipLayout.unwrapSingleFolder(zip("only/" to null)))
    }

    @Test
    fun leavesNonAsciiInnerNamesAlone() {
        assertNull(ZipLayout.unwrapSingleFolder(zip("g/__adf__" to "A", "g/게임.jar" to "J")))
    }

    @Test
    fun notAZipIsNull() {
        assertNull(ZipLayout.unwrapSingleFolder(byteArrayOf(1, 2, 3, 4)))
        assertNull(ZipLayout.unwrapSingleFolder(ByteArray(0)))
    }

    @Test
    fun contentIsPreservedByteForByte() {
        val payload = ByteArray(5000) { (it * 31).toByte() }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("w/client.bin")); zos.write(payload); zos.closeEntry()
        }
        val flat = ZipLayout.unwrapSingleFolder(out.toByteArray())!!
        ZipInputStream(ByteArrayInputStream(flat)).use { zis ->
            assertEquals("client.bin", zis.nextEntry!!.name)
            assertArrayEquals(payload, zis.readBytes())
        }
    }
}
