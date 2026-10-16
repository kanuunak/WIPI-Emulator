// 게임 라이브러리 — 임포트한 게임을 filesDir/games/<UUID>/에 영구 저장하고 관리.
// 표지·게임명은 패키지(zip) 안의 big.icon/__adf__에서 뽑아 캐시한다 (iOS GameLibrary와 대칭).

package com.parkjeongseop.wipi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import org.json.JSONObject
import java.io.File
import java.nio.charset.Charset
import java.util.UUID

data class GameEntry(
    val id: String,
    val name: String,
    val cover: Bitmap?,
    val gameFile: File,
    val filename: String, // 포맷 감지용 원본 파일명(.zip/.jar 확장자 유지)
    val dataDir: File, // 게임별 세이브 경로 (games/<id>/data) — 삭제 시 함께 제거
)

class GameLibrary(context: Context) {
    private val root = File(context.filesDir, "games").apply { mkdirs() }
    private val contentResolver = context.contentResolver


    /** 저장된 게임들을 스캔 (이름순) */
    fun list(): List<GameEntry> =
        (root.listFiles() ?: emptyArray())
            .filter { it.isDirectory }
            .mapNotNull { load(it) }
            .sortedBy { it.name }

    private fun load(dir: File): GameEntry? {
        val metaFile = File(dir, "meta.json")
        if (!metaFile.exists()) return null
        return try {
            val meta = JSONObject(metaFile.readText())
            val filename = meta.getString("filename")
            val gameFile = File(dir, filename)
            if (!gameFile.exists()) return null

            val cover = File(dir, "cover.png").takeIf { it.exists() }?.let { BitmapFactory.decodeFile(it.path) }
            GameEntry(
                id = dir.name,
                name = meta.getString("name"),
                cover = cover,
                gameFile = gameFile,
                filename = filename,
                dataDir = File(dir, "data"),
            )
        } catch (e: Exception) {
            null
        }
    }

    /** SAF Uri에서 게임을 임포트 (복사 + 표지/이름 추출·캐시). 실패 시 null. */
    fun importGame(uri: Uri): GameEntry? {
        val filename = queryDisplayName(uri) ?: uri.lastPathSegment ?: "game.zip"
        val raw = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
        // 게임이 폴더 안에 든 zip은 게임 폴더만 맨 위로 꺼내 저장한다(코어는 __adf__·jar가 맨 위에 있어야 형식을 알아본다)
        val bytes = if (filename.endsWith(".zip", ignoreCase = true)) ZipLayout.unwrapGameFolder(raw) ?: raw else raw
        return store(filename, bytes)
    }

    /**
     * SAF 폴더(OpenDocumentTree)에서 게임을 임포트: 폴더 안 파일을 전부 읽어 게임 파일 하나로 만든다
     * (zip을 풀어 둔 폴더를 다시 묶지 않고 바로 넣기). 실패하거나 [MAX_FOLDER_BYTES]를 넘으면 null.
     */
    fun importFolder(treeUri: Uri): GameEntry? {
        val rootId = DocumentsContract.getTreeDocumentId(treeUri)
        val folderName = queryDisplayName(DocumentsContract.buildDocumentUriUsingTree(treeUri, rootId)) ?: "game"
        val files = ArrayList<Pair<String, ByteArray>>()
        var total = 0L

        fun walk(docId: String, prefix: String): Boolean {
            val children = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, docId)
            val columns = arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,
            )
            val rows = contentResolver.query(children, columns, null, null, null)?.use { c ->
                buildList<Triple<String, String, String?>> { while (c.moveToNext()) add(Triple(c.getString(0), c.getString(1), c.getString(2))) }
            } ?: return false
            for ((id, name, mime) in rows) {
                if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                    if (!walk(id, "$prefix$name/")) return false
                    continue
                }
                val data = contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(treeUri, id))
                    ?.use { it.readBytes() } ?: return false
                total += data.size
                if (total > MAX_FOLDER_BYTES) return false
                files += "$prefix$name" to data
            }
            return true
        }

        return try {
            if (!walk(rootId, "")) return null
            val (filename, bytes) = ZipLayout.fromFolder(folderName, files.sortedBy { it.first }) ?: return null
            store(filename, bytes)
        } catch (e: Exception) {
            null
        }
    }

    /** 게임 파일을 games/<UUID>/에 저장하고 표지·이름을 추출해 캐시 */
    private fun store(filename: String, bytes: ByteArray): GameEntry? {
        val id = UUID.randomUUID().toString()
        val dir = File(root, id).apply { mkdirs() }
        File(dir, filename).writeBytes(bytes)

        WipiNative.nativeGameIcon(bytes)?.let { File(dir, "cover.png").writeBytes(it) }

        // 게임명: __adf__(EUC-KR) → 없으면 파일명(확장자 제거)
        val name = WipiNative.nativeGameName(bytes)
            ?.toString(Charset.forName("EUC-KR"))
            ?: filename.substringBeforeLast('.')

        File(dir, "meta.json").writeText(
            JSONObject().put("name", name).put("filename", filename).toString()
        )

        return load(dir)
    }

    companion object {
        /** 폴더 임포트 최대 크기(전부 메모리로 읽는다). WIPI 게임은 대개 수 MB. */
        const val MAX_FOLDER_BYTES = 64L * 1024 * 1024
    }

    fun delete(entry: GameEntry) {
        File(root, entry.id).deleteRecursively()
    }

    private fun queryDisplayName(uri: Uri): String? =
        contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
}
