// 진단 로그: 마지막 게임·오류·CPU 측정값을 기록해 두고(SharedPreferences — 앱이 죽어도 남는다), 공유할 때
// 앱·기기 정보와 자기 프로세스 logcat을 붙여 텍스트 파일 하나로 만든다. 공유는 FileProvider(androidx.core) 경유.
// 로그 레벨은 바꾸지 않는다 — 평소에 남는 로그(info 이상)만 담긴다.

package com.parkjeongseop.wipi

import android.content.ClipData
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.os.Process
import android.util.Log
import androidx.core.content.FileProvider
import androidx.core.content.edit
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit

object Diagnostics {
    /** logcat에서 가져올 최대 줄 수(최근 것부터) */
    const val LOGCAT_LINES = 2000

    private const val DIR = "diagnostics" // res/xml/diagnostic_paths.xml의 cache-path와 같아야 한다
    private var prefs: SharedPreferences? = null

    fun init(context: Context) {
        if (prefs == null) prefs = context.applicationContext.getSharedPreferences("diagnostics", Context.MODE_PRIVATE)
    }

    fun authority(context: Context) = "${context.packageName}.diagnostics"

    /** 게임 시작 직전에 기록(시작하다 멈춰도 어떤 게임이었는지 남도록) */
    fun recordGameStart(entry: GameEntry) {
        prefs?.edit {
            putString("game_name", entry.name)
            putString("game_file", entry.filename)
            putLong("game_size", entry.gameFile.length())
            putLong("game_at", System.currentTimeMillis())
        }
    }

    /** 오류 원문 기록 + logcat에도 남긴다(공유 시 logcat 부분에서 앞뒤 맥락을 볼 수 있게) */
    fun recordError(kind: String, detail: String) {
        Log.w(LOG_TAG, "session error ($kind): $detail")
        prefs?.edit {
            putString("error_kind", kind)
            putString("error_detail", detail)
            putString("error_game", prefs?.getString("game_name", null))
            putLong("error_at", System.currentTimeMillis())
        }
    }

    /** 레이아웃 HUD가 1초마다 잰 CPU 사용률(코어 하나 = 100%) */
    fun recordCpu(percent: Float, paused: Boolean) {
        prefs?.edit {
            putFloat("cpu_percent", percent)
            putBoolean("cpu_paused", paused)
            putString("cpu_game", EmulatorSession.activeGame?.name)
            putLong("cpu_at", System.currentTimeMillis())
        }
    }

    /** 보고서를 cacheDir/diagnostics/에 쓰고 파일을 돌려준다. logcat을 읽으므로 백그라운드 스레드에서 부른다. */
    fun writeReport(context: Context): File {
        val dir = File(context.cacheDir, DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() } // 이전 보고서는 남기지 않는다
        val file = File(dir, "fold-retro-diagnostics-${SimpleDateFormat("yyyyMMdd-HHmmss", Locale.ROOT).format(Date())}.txt")
        file.writeText(buildReport(context))
        return file
    }

    /** 공유 시트(chooser) Intent. 받는 앱에 이 파일 하나만 읽기 권한을 준다. */
    fun shareIntent(context: Context, file: File, title: String): Intent {
        val uri = FileProvider.getUriForFile(context, authority(context), file)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.name)
            clipData = ClipData.newRawUri(file.name, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        return Intent.createChooser(send, title)
    }

    private fun time(ms: Long): String =
        if (ms <= 0L) "-" else SimpleDateFormat("yyyy-MM-dd HH:mm:ss Z", Locale.ROOT).format(Date(ms))

    private fun buildReport(context: Context): String = buildString {
        val p = prefs
        fun line(key: String, value: Any?) = appendLine("$key: ${value ?: "-"}")

        appendLine("Fold Retro diagnostic log")
        line("generated", time(System.currentTimeMillis()))

        appendLine("\n== App ==")
        line("package", context.packageName)
        line("version", "${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE}, ${BuildConfig.BUILD_TYPE})")
        line("build", BuildConfig.CI_BUILD)

        appendLine("\n== Device ==")
        line("device", "${Build.MANUFACTURER} ${Build.MODEL} (${Build.DEVICE})")
        line("android", "${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
        line("fingerprint", Build.FINGERPRINT)
        line("abi", Build.SUPPORTED_ABIS.joinToString())
        line("cores", Runtime.getRuntime().availableProcessors())
        val config = context.resources.configuration
        line("screen", "${config.screenWidthDp}x${config.screenHeightDp}dp sw${config.smallestScreenWidthDp} d${context.resources.displayMetrics.density}")
        line("locale", config.locales[0])

        appendLine("\n== Session ==")
        line("active game", EmulatorSession.activeGame?.let { "${it.name} (${it.filename})" })
        line("paused", EmulatorSession.pauseReason)
        line("stopping", EmulatorSession.stopStatus())

        appendLine("\n== Last game ==")
        line("name", p?.getString("game_name", null))
        line("file", p?.getString("game_file", null))
        line("format", p?.getString("game_file", null)?.substringAfterLast('.', "")?.uppercase())
        line("size", p?.getLong("game_size", -1L)?.takeIf { it >= 0 }?.let { "$it bytes" })
        line("started", time(p?.getLong("game_at", 0L) ?: 0L))

        appendLine("\n== Last CPU (layout HUD, 1 core = 100%) ==")
        if (p?.contains("cpu_percent") == true) {
            line("cpu", "%.1f%%".format(Locale.ROOT, p.getFloat("cpu_percent", 0f)) + if (p.getBoolean("cpu_paused", false)) " (paused)" else "")
            line("game", p.getString("cpu_game", null))
            line("measured", time(p.getLong("cpu_at", 0L)))
        } else {
            appendLine("(not measured — long-press the in-game gear icon to show the HUD)")
        }

        appendLine("\n== Last error ==")
        if (p?.contains("error_detail") == true) {
            line("kind", p.getString("error_kind", null))
            line("game", p.getString("error_game", null))
            line("at", time(p.getLong("error_at", 0L)))
            appendLine("detail:")
            appendLine(p.getString("error_detail", ""))
        } else {
            appendLine("(none)")
        }

        appendLine("\n== logcat (this process, last $LOGCAT_LINES lines) ==")
        append(readLogcat())
    }

    /** 자기 프로세스 로그만(--pid). 앱은 자기 로그를 권한 없이 읽을 수 있다. 실패하면 이유를 적는다. */
    private fun readLogcat(): String = try {
        val proc = ProcessBuilder(
            "logcat", "-d", "-v", "threadtime", "--pid=${Process.myPid()}", "-t", LOGCAT_LINES.toString(),
        ).redirectErrorStream(true).start()
        val text = proc.inputStream.bufferedReader().use { it.readText() }
        if (!proc.waitFor(5, TimeUnit.SECONDS)) proc.destroy()
        text.ifEmpty { "(empty)\n" }
    } catch (e: Exception) {
        "(logcat failed: $e)\n"
    }
}
