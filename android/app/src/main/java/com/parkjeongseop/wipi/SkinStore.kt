// 스킨 팩 저장소: SAF로 고른 zip(skin.json + 그림)을 filesDir/skins/<id>/에 풀어 두고 목록·삭제·그림 읽기를 맡는다.
// zip 안에서 skin.json이 폴더 속에 있어도 그 폴더를 팩의 맨 위로 본다. 검사(규격·그림 파일·비율)를 통과해야 저장한다.

package com.parkjeongseop.wipi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.ByteArrayInputStream
import java.io.File
import java.util.Locale
import java.util.UUID
import java.util.zip.ZipInputStream
import kotlin.math.abs
import kotlin.math.max

data class SkinEntry(val id: String, val name: String, val spec: SkinSpec, val dir: File)

/** 가져오기 결과: 성공이면 [entry], 실패면 [errors](규격 검사 원문 — AI에게 그대로 넘겨 고치게 할 수 있다) */
data class SkinImportResult(val entry: SkinEntry?, val errors: List<String>, val warnings: List<String>)

/** 그리기용으로 읽은 배치 하나의 그림 */
class SkinBitmaps(val body: Bitmap, val pressed: Bitmap?)

class SkinStore(context: Context) {
    private val root = File(context.filesDir, "skins").apply { mkdirs() }
    private val contentResolver = context.contentResolver
    private val assets = context.assets

    /**
     * 앱에 든 기본 스킨(assets/skins/<이름>.zip, demo/skins/make_skins.py로 만든 그림)을 skins/builtin-<이름>/에 풀어 둔다.
     * 이미 같은 판(zip 크기)이 있으면 건너뛴다. 백그라운드에서 부른다.
     */
    fun installBuiltIns() {
        val names = runCatching { assets.list(BUILTIN_DIR) }.getOrNull().orEmpty().filter { it.endsWith(".zip") }
        for (file in names) {
            val id = BUILTIN_PREFIX + file.removeSuffix(".zip")
            val bytes = runCatching { assets.open("$BUILTIN_DIR/$file").use { it.readBytes() } }.getOrNull() ?: continue
            val stamp = File(File(root, id), BUILTIN_STAMP)
            if (stamp.exists() && stamp.readText() == bytes.size.toString()) continue
            File(root, id).deleteRecursively()
            val result = importZip(bytes, id)
            if (result.entry != null) stamp.writeText(bytes.size.toString())
            else android.util.Log.w(LOG_TAG, "built-in skin $file: ${result.errors}")
        }
    }

    fun list(): List<SkinEntry> =
        (root.listFiles() ?: emptyArray()).filter { it.isDirectory }.mapNotNull { load(it) }.sortedBy { it.name }

    fun get(id: String?): SkinEntry? = id?.takeIf { it.isNotEmpty() }?.let { File(root, it) }?.takeIf { it.isDirectory }?.let { load(it) }

    private fun load(dir: File): SkinEntry? {
        val json = File(dir, SkinPack.JSON_NAME).takeIf { it.exists() } ?: return null
        val spec = runCatching { SkinPack.parse(json.readText()).spec }.getOrNull() ?: return null
        return SkinEntry(dir.name, spec.displayName(Locale.getDefault().language), spec, dir)
    }

    fun delete(id: String) {
        if (id.isNotEmpty() && !id.contains('/') && !id.contains("..")) File(root, id).deleteRecursively()
    }

    fun import(uri: Uri): SkinImportResult {
        val bytes = try {
            contentResolver.openInputStream(uri)?.use { readLimited(it.readBytes()) }
        } catch (e: Exception) {
            null
        } ?: return fail("could not read the file (max ${MAX_ZIP_BYTES / 1024 / 1024} MB)")
        return importZip(bytes)
    }

    private fun readLimited(b: ByteArray): ByteArray? = b.takeIf { it.size <= MAX_ZIP_BYTES }

    fun importZip(zip: ByteArray, id: String = UUID.randomUUID().toString()): SkinImportResult {
        // 1) zip을 읽어 파일 목록으로 (디렉터리·숨김 파일 제외, 전체 크기 제한)
        val files = LinkedHashMap<String, ByteArray>()
        var total = 0L
        try {
            ZipInputStream(ByteArrayInputStream(zip)).use { zin ->
                while (true) {
                    val e = zin.nextEntry ?: break
                    if (e.isDirectory) continue
                    val name = e.name.replace('\\', '/')
                    if (name.startsWith("__MACOSX/") || name.substringAfterLast('/').startsWith(".")) continue
                    val data = zin.readBytes()
                    total += data.size
                    if (total > MAX_UNZIPPED_BYTES) return fail("pack is too big when unzipped (max ${MAX_UNZIPPED_BYTES / 1024 / 1024} MB)")
                    files[name] = data
                }
            }
        } catch (e: Exception) {
            return fail("not a zip file (${e.message})")
        }

        // 2) skin.json 찾기(가장 얕은 것) → 그 폴더가 팩의 맨 위
        val jsonPath = files.keys.filter { it.substringAfterLast('/') == SkinPack.JSON_NAME }.minByOrNull { it.count { c -> c == '/' } }
            ?: return fail("no ${SkinPack.JSON_NAME} in the zip")
        val base = jsonPath.substringBeforeLast('/', "").let { if (it.isEmpty()) "" else "$it/" }
        val packFiles = files.filterKeys { it.startsWith(base) }.mapKeys { it.key.removePrefix(base) }

        // 3) 규격 검사 + 그림 검사
        val parsed = SkinPack.parse(String(packFiles.getValue(SkinPack.JSON_NAME), Charsets.UTF_8))
        val spec = parsed.spec ?: return SkinImportResult(null, parsed.errors, parsed.warnings)
        val errors = ArrayList<String>()
        spec.layouts.forEachIndexed { i, l ->
            checkImage("layouts[$i].image", l.image, l, packFiles, errors)
            l.pressed?.let { checkImage("layouts[$i].pressed", it, l, packFiles, errors) }
        }
        if (errors.isNotEmpty()) return SkinImportResult(null, errors, parsed.warnings)

        // 4) 저장(쓰는 파일만: skin.json + 그림)
        val dir = File(root, id)
        return try {
            val needed = setOf(SkinPack.JSON_NAME) + spec.layouts.flatMap { listOfNotNull(it.image, it.pressed) }
            for (name in needed) {
                val out = File(dir, name)
                if (!out.canonicalPath.startsWith(dir.canonicalPath + File.separator)) error("bad path $name")
                out.parentFile?.mkdirs()
                out.writeBytes(packFiles.getValue(name))
            }
            SkinImportResult(load(dir), emptyList(), parsed.warnings)
        } catch (e: Exception) {
            dir.deleteRecursively()
            fail("could not save the pack (${e.message})")
        }
    }

    private fun checkImage(where: String, name: String, l: SkinLayoutSpec, files: Map<String, ByteArray>, errors: MutableList<String>) {
        val data = files[name] ?: run {
            errors += "$where: file \"$name\" is not in the zip (names are case-sensitive)"
            return
        }
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(data, 0, data.size, o)
        if (o.outWidth <= 0 || o.outHeight <= 0) {
            errors += "$where: \"$name\" is not a PNG/JPEG/WebP image"
            return
        }
        val aspect = o.outWidth.toFloat() / o.outHeight
        if (abs(aspect / l.aspect - 1f) > ASPECT_TOLERANCE) {
            errors += "$where: \"$name\" is ${o.outWidth}x${o.outHeight} but size says ${l.width}x${l.height} (different shape)"
        }
    }

    /** 배치 하나의 그림을 읽는다(한 변 [SkinPack.MAX_IMAGE_SIDE] 이하로 줄여서). 실패하면 null */
    fun loadBitmaps(entry: SkinEntry, layout: SkinLayoutSpec): SkinBitmaps? {
        val body = decode(File(entry.dir, layout.image)) ?: return null
        return SkinBitmaps(body, layout.pressed?.let { decode(File(entry.dir, it)) })
    }

    private fun decode(f: File): Bitmap? {
        if (!f.exists()) return null
        val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(f.path, o)
        var sample = 1
        while (max(o.outWidth, o.outHeight) / sample > SkinPack.MAX_IMAGE_SIDE) sample *= 2
        return BitmapFactory.decodeFile(f.path, BitmapFactory.Options().apply { inSampleSize = sample })
    }

    private fun fail(msg: String) = SkinImportResult(null, listOf(msg), emptyList())

    companion object {
        const val MAX_ZIP_BYTES = 40L * 1024 * 1024
        const val MAX_UNZIPPED_BYTES = 80L * 1024 * 1024
        const val ASPECT_TOLERANCE = 0.02f
        const val BUILTIN_DIR = "skins"
        const val BUILTIN_PREFIX = "builtin-"
        private const val BUILTIN_STAMP = ".builtin"
    }
}
