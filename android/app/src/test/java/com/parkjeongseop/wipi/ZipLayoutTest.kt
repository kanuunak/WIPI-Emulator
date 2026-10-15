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
        val flat = ZipLayout.unwrapGameFolder(wrapped)
        assertNotNull(flat)
        assertEquals(
            mapOf("010100D5.jar" to "JAR", "__adf__" to "AID:010100D5", "p/" to null, "p/cert.c2s" to "C"),
            read(flat!!),
        )
    }

    @Test
    fun takesAdfFolderAndDropsExtrasBesideIt() {
        // 실제 팩: 맨 위에 설치법 그림 + 게임 폴더 → 게임 폴더만
        val wrapped = zip(
            "이노티아2 설치법.png" to "PNG",
            "이노티아연대기2-wipi1.2/" to null,
            "이노티아연대기2-wipi1.2/010100D2.jar" to "JAR",
            "이노티아연대기2-wipi1.2/__adf__" to "AID",
            "이노티아연대기2-wipi1.2/p/i_pack.dat" to "P",
        )
        assertEquals(mapOf("010100D2.jar" to "JAR", "__adf__" to "AID", "p/i_pack.dat" to "P"), read(ZipLayout.unwrapGameFolder(wrapped)!!))
        // 같은 구조의 폴더 임포트
        val (name, data) = ZipLayout.fromFolder(
            "받은 파일",
            listOf("이노티아2 설치법.png" to b("PNG"), "이노티아연대기2-wipi1.2/__adf__" to b("AID"), "이노티아연대기2-wipi1.2/1.jar" to b("J")),
        )!!
        assertEquals("받은 파일.zip", name)
        assertEquals(mapOf("__adf__" to "AID", "1.jar" to "J"), read(data))
    }

    @Test
    fun deepAdfFolderAndMultipleGames() {
        assertEquals(mapOf("__adf__" to "A"), read(ZipLayout.unwrapGameFolder(zip("a/b/__adf__" to "A", "readme.txt" to "R"))!!))
        // 게임이 둘이면 고르지 않는다
        assertNull(ZipLayout.unwrapGameFolder(zip("g1/__adf__" to "A", "g2/__adf__" to "B")))
        // __adf__가 이미 맨 위면 고칠 것 없음
        assertNull(ZipLayout.unwrapGameFolder(zip("__adf__" to "A", "x/readme.txt" to "R")))
    }

    @Test
    fun unwrapsCp949FolderName() {
        // UTF-8 플래그 없이 CP949로 저장된 폴더 이름(옛 압축 프로그램)도 바이트 그대로 비교해 벗긴다
        val wrapped = zip("붕어빵/" to null, "붕어빵/__adf__" to "A", "붕어빵/1.jar" to "J", charset = Charset.forName("EUC-KR"))
        assertEquals(mapOf("__adf__" to "A", "1.jar" to "J"), read(ZipLayout.unwrapGameFolder(wrapped)!!))
    }

    @Test
    fun leavesFlatOrMixedZipAlone() {
        assertNull(ZipLayout.unwrapGameFolder(zip("__adf__" to "A", "1.jar" to "J")))
        assertNull(ZipLayout.unwrapGameFolder(zip("game/1.jar" to "J", "readme.txt" to "R"))) // __adf__ 없고 섞임
        // __adf__가 있는 폴더가 게임 폴더 — 그 밖(b/)은 버린다
        assertEquals(mapOf("__adf__" to "A"), read(ZipLayout.unwrapGameFolder(zip("a/__adf__" to "A", "b/1.jar" to "J"))!!))
        assertNull(ZipLayout.unwrapGameFolder(zip("only/" to null)))
    }

    @Test
    fun leavesNonAsciiInnerNamesAlone() {
        assertNull(ZipLayout.unwrapGameFolder(zip("g/__adf__" to "A", "g/게임.jar" to "J")))
    }

    @Test
    fun notAZipIsNull() {
        assertNull(ZipLayout.unwrapGameFolder(byteArrayOf(1, 2, 3, 4)))
        assertNull(ZipLayout.unwrapGameFolder(ByteArray(0)))
    }

    @Test
    fun contentIsPreservedByteForByte() {
        val payload = ByteArray(5000) { (it * 31).toByte() }
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zos ->
            zos.putNextEntry(ZipEntry("w/client.bin")); zos.write(payload); zos.closeEntry()
        }
        val flat = ZipLayout.unwrapGameFolder(out.toByteArray())!!
        ZipInputStream(ByteArrayInputStream(flat)).use { zis ->
            assertEquals("client.bin", zis.nextEntry!!.name)
            assertArrayEquals(payload, zis.readBytes())
        }
    }

    private fun b(s: String) = s.toByteArray()

    @Test
    fun folderWithAdfBecomesZip() {
        // zip을 풀어 둔 KTF 폴더: __adf__ + jar + 데이터 폴더 → <폴더 이름>.zip (안쪽 경로 유지)
        val (name, data) = ZipLayout.fromFolder(
            "이노티아",
            listOf("010100D5.jar" to b("J"), "__adf__" to b("A"), "p/cert.c2s" to b("C"), "big.icon" to b("I")),
        )!!
        assertEquals("이노티아.zip", name)
        assertEquals(mapOf("010100D5.jar" to "J", "__adf__" to "A", "p/cert.c2s" to "C", "big.icon" to "I"), read(data))
    }

    @Test
    fun folderWrappedInSubfolderIsUnwrapped() {
        // 고른 폴더 안에 게임 폴더가 한 겹 더 있을 때(여러 겹이어도)
        val (name, data) = ZipLayout.fromFolder("다운로드", listOf("x/y/__adf__" to b("A"), "x/y/1.jar" to b("J")))!!
        assertEquals("다운로드.zip", name)
        assertEquals(mapOf("__adf__" to "A", "1.jar" to "J"), read(data))
    }

    @Test
    fun j2meFolderUsesItsJar() {
        // __adf__ 없이 jar 하나(+ jad) → jar를 그대로
        val (name, data) = ZipLayout.fromFolder("hc", listOf("heart.jad" to b("D"), "heart.jar" to b("JARBYTES")))!!
        assertEquals("heart.jar", name)
        assertEquals("JARBYTES", String(data))
    }

    @Test
    fun ambiguousFolderIsZipped() {
        // jar가 둘이면 고르지 않고 묶는다(코어가 판단)
        val (name, _) = ZipLayout.fromFolder("g", listOf("a.jar" to b("1"), "b.jar" to b("2")))!!
        assertEquals("g.zip", name)
        assertNull(ZipLayout.fromFolder("g", emptyList()))
    }
}
