package com.parkjeongseop.wipi

import android.net.Uri
import android.os.Bundle
import android.os.Process
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.InsertDriveFile
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.lifecycleScope
import java.io.File
import java.util.Locale
import kotlin.math.roundToInt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

const val SCREEN_WIDTH = 240
const val SCREEN_HEIGHT = 320
const val SOUNDFONT_NAME = "GeneralUser-GS.sf2"

/** 일시정지 중 폴링 루프가 깨어나는 간격(ms). 실행 중에는 16ms. */
private const val PAUSED_POLL_MS = 500L

class MainActivity : ComponentActivity() {
    private lateinit var library: GameLibrary
    private lateinit var settings: Settings
    private lateinit var haptics: Haptics
    private lateinit var skinStore: SkinStore

    /** 게임 화면 배치 설정(설정 오버레이에서 바꾸면 바로 다시 배치) */
    private var layoutMode by mutableStateOf(LayoutMode.AUTO)

    /** 게임 화면 필터(설정에서 바꾸면 바로 반영) */
    private var screenFilter by mutableStateOf(ScreenFilter.SHARP)

    /** 키패드 테마(설정에서 바꾸면 바로 반영) */
    private var keypadTheme by mutableStateOf(KeypadTheme.DEFAULT)

    /** 스킨 팩 id("" = 스킨 없음)와 키 영역 표시(설정에서 바꾸면 바로 반영) */
    private var skinId by mutableStateOf("")
    private var showSkinKeyAreas by mutableStateOf(false)

    /** 배경 이미지: 바꿀 때마다 증가 → 다시 읽는다. 어둡게 덮는 정도 */
    private var backgroundVersion by mutableIntStateOf(0)
    private var backgroundDim by mutableStateOf(0.3f)

    /** 배치 편집으로 저장·삭제할 때마다 증가 → 저장한 배치를 다시 읽는다 */
    private var customLayoutVersion by mutableIntStateOf(0)

    /** 에뮬 화면에서만 하드웨어 키를 게임으로 전달 (화면 = 세션 유무에서 파생) */
    private val emulatorActive: Boolean get() = EmulatorSession.activeGame != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        EmulatorSession.init(this)
        library = GameLibrary(this)
        settings = Settings(this)
        haptics = Haptics(this)
        reloadScreenSettings()
        skinStore = SkinStore(this)
        lifecycleScope.launch(Dispatchers.IO) { skinStore.installBuiltIns() }
        showSkinKeyAreas = settings.showSkinKeyAreas
        backgroundDim = settings.backgroundDim

        setContent {
            WipiTheme {
                WipiApp()
            }
        }
    }

    /** 화면 설정(게임별로 따로 둘 수 있는 것)을 지금 범위에서 다시 읽는다 */
    private fun reloadScreenSettings() {
        layoutMode = settings.layoutMode
        screenFilter = settings.screenFilter
        keypadTheme = settings.keypadTheme
        skinId = settings.skinId
        customLayoutVersion++
    }

    override fun onDestroy() {
        super.onDestroy()
        // 구성 변경(접기·펴기 등)이나 시스템의 Activity 파괴에서는 세션을 유지한다 — 이미 ON_PAUSE에서 일시정지·음소거됨
        if (isFinishing && !isChangingConfigurations) EmulatorSession.stop()
    }

    @Composable
    private fun WipiApp() {
        // 게임을 시작·종료할 때 화면 설정의 범위(게임별/전체)를 바꾸고 다시 읽는다
        val activeGameId = EmulatorSession.activeGame?.id
        LaunchedEffect(activeGameId) {
            settings.activeGameId = activeGameId
            reloadScreenSettings()
        }
        var games by remember { mutableStateOf(library.list()) }
        var errorMessage by remember { mutableStateOf<UiError?>(null) }
        var showSettings by remember { mutableStateOf(false) } // iOS sheet 대응 오버레이
        var showDiagnostics by remember { mutableStateOf(false) } // 진단 로그: 파일로 저장 / 공유 선택

        Box(modifier = Modifier.fillMaxSize()) {
            if (EmulatorSession.activeGame == null) {
                LibraryScreen(
                    games = games,
                    error = errorMessage,
                    onDismissError = { errorMessage = null },
                    onPlay = { entry ->
                        errorMessage = when (EmulatorSession.start(entry, soundfontPath())) {
                            StartResult.STARTED -> null
                            // 이전 게임 정지(백그라운드 join)가 안 끝남. 오래 걸리면 에뮬 스레드가 멈춘 것 → 앱 재시작 안내
                            StartResult.BUSY -> if (EmulatorSession.stopStuck) {
                                // 진단 로그의 마지막 오류(멈추기 전 오류)는 덮지 않는다 — 정지 상태는 Session 절과 logcat에 남는다
                                UiError(getString(R.string.error_previous_stuck), offerCloseApp = true)
                            } else {
                                UiError(getString(R.string.error_previous_stopping))
                            }
                            // 동기 실패는 형식 문제가 아니라 시작 자체 실패 (형식 오류는 로드 후 kind로 안내됨)
                            StartResult.FAILED -> pendingError() ?: run {
                                Diagnostics.recordError("start", "nativeStart returned false (${entry.filename})")
                                UiError(getString(R.string.error_start_failed))
                            }
                        }
                    },
                    onImport = { uri ->
                        val entry = library.importGame(uri)
                        games = library.list()
                        entry != null
                    },
                    onImportFolder = { uri ->
                        val entry = library.importFolder(uri)
                        games = library.list()
                        entry != null
                    },
                    onDelete = { entry ->
                        library.delete(entry)
                        settings.forgetGame(entry.id)
                        games = library.list()
                    },
                    onSettings = { showSettings = true },
                    onDiagnostics = { showDiagnostics = true },
                    onCloseApp = ::closeApp,
                )
            } else {
                EmulatorScreen(
                    onError = { error ->
                        errorMessage = error
                        EmulatorSession.stop()
                    },
                    onExit = { EmulatorSession.stop() },
                    onSettings = { showSettings = true },
                )
            }

            // 설정은 오버레이(iOS sheet 대응) — 에뮬 화면이 뒤에 살아있고 폴링도 계속된다
            if (showSettings) {
                SettingsFlow(
                    settings = settings,
                    onVolumeChanged = { EmulatorSession.applyVolume() }, // 일시정지 중이면 0 유지
                    onLayoutModeChanged = { layoutMode = it },
                    onScreenFilterChanged = { screenFilter = it },
                    onKeypadThemeChanged = { keypadTheme = it },
                    skinStore = skinStore,
                    onSkinChanged = { skinId = it },
                    onSkinKeyAreasChanged = { showSkinKeyAreas = it },
                    onBackgroundChanged = { backgroundVersion++ },
                    onBackgroundDimChanged = { backgroundDim = it },
                    onDiagnostics = { showDiagnostics = true },
                    onClose = { showSettings = false },
                )
            }
        }

        if (showDiagnostics) {
            DiagnosticsDialog(
                onSave = {
                    showDiagnostics = false
                    saveDiagnostics.launch(Diagnostics.fileName())
                },
                onShare = {
                    showDiagnostics = false
                    shareDiagnostics()
                },
                onDismiss = { showDiagnostics = false },
            )
        }
    }

    /**
     * 보류 중 오류를 사용자 안내 문구 + 진단 원문으로 변환 (없으면 null). 안내 문구는 iOS WipiCore.describeError와 동일 카피.
     * 원문 전체(레지스터·스택 덤프 포함)는 진단 로그에 기록하고, 배너에는 앞 몇 줄만 보인다.
     */
    private fun pendingError(): UiError? {
        val (kind, detail) = EmulatorSession.takeError() ?: return null
        Diagnostics.recordError(if (kind == 0) "load failed" else "runtime", detail)
        val message = getString(if (kind == 0) R.string.error_load_failed else R.string.error_runtime)
        return UiError(message, detail)
    }

    private var exportingDiagnostics = false

    /** 파일로 저장: 사용자가 고른 위치(예: 다운로드)에 전체 보고서를 쓴다. 권한 필요 없음(SAF). */
    private val saveDiagnostics = registerForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri ->
        if (uri == null) return@registerForActivityResult
        exportDiagnostics("save") {
            withContext(Dispatchers.IO) {
                contentResolver.openOutputStream(uri)?.use { Diagnostics.writeReport(this@MainActivity, it) }
                    ?: error("openOutputStream returned null")
            }
            Toast.makeText(this@MainActivity, R.string.diag_saved, Toast.LENGTH_SHORT).show()
        }
    }

    /** 공유: 보고서 파일(logcat 읽기 — 백그라운드)을 첨부 + 요약을 본문으로 공유 시트에 넘긴다 */
    private fun shareDiagnostics() = exportDiagnostics("share") {
        val (file, summary) = withContext(Dispatchers.IO) { Diagnostics.writeShareFile(this@MainActivity) }
        startActivity(Diagnostics.shareIntent(this@MainActivity, file, summary, getString(R.string.diag_dialog_title)))
    }

    private fun exportDiagnostics(what: String, block: suspend () -> Unit) {
        if (exportingDiagnostics) return
        exportingDiagnostics = true
        lifecycleScope.launch {
            try {
                block()
            } catch (e: Exception) {
                Log.w(LOG_TAG, "diagnostics $what failed", e)
                Toast.makeText(this@MainActivity, R.string.diag_failed, Toast.LENGTH_SHORT).show()
            } finally {
                exportingDiagnostics = false
            }
        }
    }

    /** 에뮬 스레드가 멈춰 정지가 끝나지 않을 때의 유일한 복구: 프로세스를 끝낸다(다음 실행은 라이브러리부터, 세이브는 게임이 저장한 시점까지) */
    private fun closeApp() {
        Log.w(LOG_TAG, "closing app: ${EmulatorSession.stopStatus()}")
        finishAndRemoveTask()
        Process.killProcess(Process.myPid())
    }

    /** assets의 사운드폰트를 filesDir로 복사하고 경로 반환 (실패 시 빈 문자열 → MIDI 무음) */
    private fun soundfontPath(): String = try {
        val file = File(filesDir, SOUNDFONT_NAME)
        if (!file.exists()) {
            assets.open(SOUNDFONT_NAME).use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            }
        }
        file.absolutePath
    } catch (e: Exception) {
        ""
    }

    /**
     * 진단: 시스템이 터치를 가로채면(뒤로가기 제스처 등) 앱은 ACTION_CANCEL을 받는다 → 레이아웃 HUD 입력 기록에 남긴다.
     * 터치 시작 위치(dp)도 남겨 화면 가장자리에서 시작했는지 확인한다.
     */
    override fun dispatchTouchEvent(event: MotionEvent): Boolean {
        if (emulatorActive) {
            val d = resources.displayMetrics.density
            fun pos(i: Int) = "${(event.getX(i) / d).roundToInt()},${(event.getY(i) / d).roundToInt()}dp"
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> InputTrace.add("sys down @${pos(0)}")
                MotionEvent.ACTION_CANCEL -> InputTrace.add("SYS CANCEL @${pos(0)} n=${event.pointerCount}")
            }
        }
        return super.dispatchTouchEvent(event)
    }

    // ── 하드웨어 입력: 키보드(wie_cli 배치) + 게임패드 버튼 ──

    private val hardwareKeyMap = mapOf(
        KeyEvent.KEYCODE_DPAD_UP to "UP",
        KeyEvent.KEYCODE_DPAD_DOWN to "DOWN",
        KeyEvent.KEYCODE_DPAD_LEFT to "LEFT",
        KeyEvent.KEYCODE_DPAD_RIGHT to "RIGHT",
        KeyEvent.KEYCODE_DPAD_CENTER to "OK",
        KeyEvent.KEYCODE_ENTER to "OK",
        KeyEvent.KEYCODE_SPACE to "OK",
        KeyEvent.KEYCODE_1 to "1",
        KeyEvent.KEYCODE_2 to "2",
        KeyEvent.KEYCODE_3 to "3",
        KeyEvent.KEYCODE_Q to "4",
        KeyEvent.KEYCODE_W to "5",
        KeyEvent.KEYCODE_E to "6",
        KeyEvent.KEYCODE_A to "7",
        KeyEvent.KEYCODE_S to "8",
        KeyEvent.KEYCODE_D to "9",
        KeyEvent.KEYCODE_Z to "*",
        KeyEvent.KEYCODE_X to "0",
        KeyEvent.KEYCODE_C to "#",
        KeyEvent.KEYCODE_4 to "4",
        KeyEvent.KEYCODE_5 to "5",
        KeyEvent.KEYCODE_6 to "6",
        KeyEvent.KEYCODE_7 to "7",
        KeyEvent.KEYCODE_8 to "8",
        KeyEvent.KEYCODE_9 to "9",
        KeyEvent.KEYCODE_0 to "0",
        KeyEvent.KEYCODE_STAR to "*",
        KeyEvent.KEYCODE_POUND to "#",
        KeyEvent.KEYCODE_DEL to "CLR",
        KeyEvent.KEYCODE_SHIFT_LEFT to "SOFT_L",
        KeyEvent.KEYCODE_SHIFT_RIGHT to "SOFT_R",
        KeyEvent.KEYCODE_F1 to "CALL",
        KeyEvent.KEYCODE_F2 to "HANGUP",
        // 게임패드 버튼 (iOS GCController 매핑과 동일: A→OK, B→CLR, X→*, Y→#, 숄더→소프트키)
        KeyEvent.KEYCODE_BUTTON_A to "OK",
        KeyEvent.KEYCODE_BUTTON_B to "CLR",
        KeyEvent.KEYCODE_BUTTON_X to "*",
        KeyEvent.KEYCODE_BUTTON_Y to "#",
        KeyEvent.KEYCODE_BUTTON_L1 to "SOFT_L",
        KeyEvent.KEYCODE_BUTTON_R1 to "SOFT_R",
    )

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (!emulatorActive) return super.onKeyDown(keyCode, event)
        val key = hardwareKeyMap[keyCode] ?: return super.onKeyDown(keyCode, event)
        if (event.repeatCount == 0) EmulatorSession.hub.press("hw:$keyCode", key) // 키 반복은 Rust 쪽에서 처리
        return true
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (!emulatorActive) return super.onKeyUp(keyCode, event)
        if (keyCode !in hardwareKeyMap) return super.onKeyUp(keyCode, event)
        EmulatorSession.hub.release("hw:$keyCode")
        return true
    }

    /** 게임패드 왼쪽 스틱 → 방향키 (iOS GCController leftThumbstick 매핑과 동일, 임계값 0.5) */
    override fun onGenericMotionEvent(event: MotionEvent): Boolean {
        if (!emulatorActive || event.source and InputDevice.SOURCE_JOYSTICK == 0) {
            return super.onGenericMotionEvent(event)
        }

        val x = event.getAxisValue(MotionEvent.AXIS_X)
        val y = event.getAxisValue(MotionEvent.AXIS_Y)

        // 방향마다 source 하나. 일시정지 등으로 releaseAll된 뒤에는 허브가 모르는 source라 다시 기울이면 새로 눌린다
        val hub = EmulatorSession.hub
        fun update(key: String, active: Boolean) {
            val source = "stick:$key"
            if (active && !hub.holds(source)) hub.press(source, key)
            else if (!active) hub.release(source)
        }
        update("LEFT", x < -0.5f)
        update("RIGHT", x > 0.5f)
        update("UP", y < -0.5f)
        update("DOWN", y > 0.5f)
        return true
    }

    // ── 에뮬레이터 화면 (iOS EmulatorScreenView가 명세) ──

    @OptIn(ExperimentalFoundationApi::class)
    @Composable
    private fun EmulatorScreen(onError: (UiError) -> Unit, onExit: () -> Unit, onSettings: () -> Unit) {
        val vibrateOut = remember { LongArray(2) }
        val paused = EmulatorSession.paused
        // 프레임·일시정지 상태는 EmulatorSession(프로세스 수명)에 있어 Activity 재생성 뒤에도 마지막 프레임을 바로 그린다.
        // frameVersion은 컴포지션에서 읽지 않는다(읽으면 매 프레임 화면 전체가 재구성된다 — 아래 Canvas draw에서만 읽음)

        // 시스템 뒤로가기: 실수 종료 방지를 위해 먼저 일시정지 메뉴, 일시정지 중 다시 누르면 종료 (에뮬 표준 패턴)
        BackHandler {
            if (paused) onExit() else EmulatorSession.pause(PauseReason.USER)
        }

        // 창 포커스 상실(알림창 내림 등): 눌린 키만 전부 해제 (일시정지는 ON_PAUSE가 담당)
        val windowFocused = LocalWindowInfo.current.isWindowFocused
        LaunchedEffect(windowFocused) {
            if (!windowFocused) EmulatorSession.hub.releaseAll()
        }

        // 게임 중 화면 자동꺼짐 방지
        DisposableEffect(Unit) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
            onDispose { window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
        }

        // 포커스를 잃으면(ON_PAUSE — iOS .inactive 대응) 일시정지.
        // 복귀해도 자동 재개하지 않고 오버레이 유지 (탭해야 재개)
        val lifecycleOwner = LocalLifecycleOwner.current
        DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_PAUSE) EmulatorSession.pause(PauseReason.SYSTEM)
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }

        LaunchedEffect(Unit) {
            while (isActive) {
                // 일시정지 중에는 tick이 멈춰 새 프레임·진동·종료가 생기지 않는다 → 16ms 폴링을 멈추고(발열·배터리)
                // 재개되면 즉시, 아니면 PAUSED_POLL_MS마다 한 번만 깨어 오류(일시정지 전 시작한 로드의 실패 등)를 확인한다
                if (EmulatorSession.paused) {
                    withTimeoutOrNull(PAUSED_POLL_MS) { snapshotFlow { EmulatorSession.paused }.first { !it } }
                    if (EmulatorSession.paused) {
                        pendingError()?.let {
                            onError(it)
                            return@LaunchedEffect
                        }
                        continue
                    }
                }
                EmulatorSession.pollFrame()
                // enabled가 아니어도 요청은 소비해야 큐가 쌓이지 않는다
                if (EmulatorSession.pollVibrate(vibrateOut) && settings.vibrationEnabled) {
                    haptics.play(vibrateOut[0], vibrateOut[1].toInt(), settings.vibrationScale)
                }
                if (EmulatorSession.pollExit()) {
                    onExit()
                    break
                }
                pendingError()?.let {
                    onError(it)
                    return@LaunchedEffect
                }
                delay(16)
            }
        }

        val frameImage = remember { EmulatorSession.frameBitmap.asImageBitmap() }
        val renderer = remember { GameRenderer(EmulatorSession.frameBitmap) }
        // 첫 프레임 도착 여부만 구독 (frameVersion 자체를 컴포지션에서 읽으면 매 프레임 재구성된다)
        val hasFrame by remember { derivedStateOf { EmulatorSession.frameVersion > 0 } }
        var showHud by rememberSaveable { mutableStateOf(false) } // 레이아웃 HUD, 기본 꺼짐
        var editing by rememberSaveable { mutableStateOf(false) } // 배치 편집(일시정지 메뉴에서)

        // 앱 CPU 사용률: HUD가 켜져 있을 때만 1초마다 /proc/self/stat·task/*/stat을 읽는다(발열 확인용, 코어 하나 = 100%).
        // 둘째 줄 = 많이 쓰는 스레드(wie-emulator = 에뮬, RenderThread·main = 화면, 그 외 오디오 등)
        var cpuLines by remember { mutableStateOf<List<String>>(emptyList()) }
        if (showHud) {
            LaunchedEffect(Unit) {
                val sampler = CpuUsage.Sampler()
                val cores = Runtime.getRuntime().availableProcessors()
                cpuLines = listOf("cpu …")
                while (isActive) {
                    val sample = withContext(Dispatchers.IO) { sampler.sample() }
                    if (sample != null) {
                        val paused = EmulatorSession.paused
                        val threads = sample.threads.joinToString(" ") { (name, p) -> "$name ${p.roundToInt()}" }
                        cpuLines = listOf(
                            "cpu ${"%.1f".format(Locale.ROOT, sample.total)}% (1 core=100%, ${cores}c)" + if (paused) " paused" else "",
                            " $threads",
                        )
                        Diagnostics.recordCpu(sample.total, threads, paused)
                    }
                    delay(1000)
                }
            }
        }

        // ⏸·⚙: 일시정지 + 설정. ⚙ 길게 누르기 = 레이아웃 HUD 켜기/끄기. 스킨 위에서는 어두운 바탕을 깔아 보이게 한다
        @Composable
        fun IconRow(modifier: Modifier, backdrop: Boolean) {
            Row(modifier = if (backdrop) modifier.background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(24.dp)) else modifier) {
                IconButton(onClick = { EmulatorSession.pause(PauseReason.USER) }) {
                    Icon(Icons.Filled.Pause, stringResource(R.string.emulator_paused), tint = Color.White.copy(alpha = if (backdrop) 0.85f else 0.6f))
                }
                Box(
                    modifier = Modifier
                        .size(ICON_TOUCH_DP.dp)
                        .combinedClickable(
                            role = Role.Button,
                            onLongClickLabel = stringResource(R.string.emulator_layout_hud_toggle),
                            onLongClick = { showHud = !showHud },
                            onClick = onSettings,
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Filled.Settings, stringResource(R.string.action_settings), tint = Color.White.copy(alpha = if (backdrop) 0.85f else 0.6f))
                }
            }
        }

        // 스킨(설정 > 화면 > 스킨): 고른 팩을 읽어 둔다. 배치는 화면 모양에 가장 가까운 것 — 그림은 배치가 바뀔 때만 다시 읽는다
        val skinEntry = remember(skinId) { skinStore.get(skinId) }

        // 배경 이미지(설정 > 화면 > 배경 이미지) — 없으면 null
        val background by produceState<ImageBitmap?>(null, backgroundVersion) {
            value = withContext(Dispatchers.IO) { skinStore.loadBackground()?.asImageBitmap() }
        }

        // 바깥: 시스템 바 뒤까지 배경을 칠한다(edge-to-edge). 크기 = 창(콘텐츠) 크기
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(EMULATOR_BG)) {
            background?.let { bg ->
                Image(bg, contentDescription = null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxSize())
                if (backgroundDim > 0f) Box(Modifier.fillMaxSize().background(Color.Black.copy(alpha = backgroundDim)))
            }
            val windowW = maxWidth.value
            val windowH = maxHeight.value

            // 안쪽: 안전 영역(safeDrawing 인셋 제외) 안에서 명세 §5.3 계산 결과대로 배치
            BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val density = LocalDensity.current.density
                // 이 화면 크기에 저장한 배치(배치 편집)가 있으면 그것, 없으면 설정(자동/위아래/좌우)대로 계산
                val layoutKey = CustomLayout.key(maxWidth.value, maxHeight.value)
                val custom = remember(layoutKey, customLayoutVersion) { settings.customLayout(layoutKey)?.let(CustomLayout::parse) }
                val layout = remember(maxWidth, maxHeight, density, layoutMode, custom) {
                    custom?.toScreenLayout(maxWidth.value, maxHeight.value, density)
                        ?: computeScreenLayout(layoutMode, maxWidth.value, maxHeight.value, density)
                }
                val metrics = layoutMetrics(windowW, windowH, layout)
                LaunchedEffect(metrics) { Log.i(LOG_TAG, metrics.logLine) }

                val skinLayout = skinEntry?.let { SkinPack.chooseLayout(it.spec, maxWidth.value, maxHeight.value) }
                val skinLoad by produceState<SkinLoad>(SkinLoad.Loading, skinEntry, skinLayout) {
                    value = if (skinEntry == null || skinLayout == null) SkinLoad.None else withContext(Dispatchers.IO) {
                        skinStore.loadBitmaps(skinEntry, skinLayout)
                            ?.let { SkinLoad.Loaded(LoadedSkin(skinLayout, it.body.asImageBitmap(), it.pressed?.asImageBitmap())) }
                            ?: SkinLoad.None.also { Log.w(LOG_TAG, "skin ${skinEntry.name}: image load failed, using keypad") }
                    }
                }
                val loaded = (skinLoad as? SkinLoad.Loaded)?.skin

                if (editing) {
                    LayoutEditor(
                        initial = CustomLayout.from(layout),
                        availWdp = maxWidth.value,
                        availHdp = maxHeight.value,
                        frame = frameImage,
                        hasFrame = hasFrame,
                        keypadTheme = keypadTheme,
                        onSave = {
                            settings.setCustomLayout(layoutKey, it.serialize())
                            customLayoutVersion++
                            editing = false
                        },
                        onCancel = { editing = false },
                        onReset = {
                            settings.setCustomLayout(layoutKey, null)
                            customLayoutVersion++
                            editing = false
                        },
                    )
                } else if (loaded != null) {
                    SkinScreen(
                        skin = loaded,
                        hub = EmulatorSession.hub,
                        onFeedback = { if (settings.keypadHaptics) haptics.keyTap() },
                        onTrace = InputTrace::add,
                        showKeyAreas = showSkinKeyAreas,
                        fillBackground = background == null,
                        drawGame = { if (EmulatorSession.frameVersion > 0) with(renderer) { draw(frameImage, screenFilter) } },
                        iconsWdp = 2 * ICON_TOUCH_DP,
                        iconsHdp = ICON_TOUCH_DP,
                        icons = { IconRow(Modifier, backdrop = true) },
                    )
                    if (showHud) LayoutHud(metrics, InputTrace.lines, cpuLines)
                } else if (skinLoad == SkinLoad.Loading) {
                    // 스킨 그림을 읽는 중(짧다) — 키패드가 잠깐 보였다 바뀌지 않게 비워 둔다
                } else {
                // 게임: draw 단계에서만 frameVersion을 읽어 새 프레임마다 다시 그리기만 한다
                // 필터(설정 > 화면 > 화면 필터)도 draw 단계에서 읽는다
                Canvas(modifier = Modifier.offset(layout.game.left.dp, layout.game.top.dp).size(layout.game.width.dp, layout.game.height.dp)) {
                    if (EmulatorSession.frameVersion > 0) {
                        with(renderer) { draw(frameImage, screenFilter) }
                    }
                }
                if (!hasFrame) {
                    Box(
                        modifier = Modifier.offset(layout.game.left.dp, layout.game.top.dp).size(layout.game.width.dp, layout.game.height.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.emulator_loading), color = Color.White) }
                }

                // 키패드(STACK = 7행 하나, SPLIT = 왼쪽 3행 + 오른쪽 4행) — 키 사각형은 KeypadModel이 각 크기에서 계산
                layout.pads.forEach { pad ->
                    TouchKeypad(
                        hub = EmulatorSession.hub,
                        // 설정은 누를 때마다 읽는다(설정 오버레이에서 바꾸면 바로 반영)
                        onFeedback = { if (settings.keypadHaptics) haptics.keyTap() },
                        rows = pad.rows,
                        theme = keypadTheme,
                        onTrace = InputTrace::add,
                        modifier = Modifier
                            .offset(pad.rect.left.dp, pad.rect.top.dp)
                            .size(pad.rect.width.dp, pad.rect.height.dp),
                    )
                }

                if (showHud) LayoutHud(metrics, InputTrace.lines, cpuLines)

                IconRow(Modifier.offset(layout.icons.left.dp, layout.icons.top.dp), backdrop = false)
                }
            }

            if (paused && !editing) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black.copy(alpha = 0.6f))
                        .pointerInput(Unit) { detectTapGestures { EmulatorSession.resume() } }
                        .windowInsetsPadding(WindowInsets.safeDrawing),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            Icons.Filled.PauseCircle, null,
                            tint = Color.White,
                            modifier = Modifier.size(64.dp),
                        )
                        Text(stringResource(R.string.emulator_paused), color = Color.White, fontSize = 18.sp, modifier = Modifier.padding(top = 8.dp))
                        Text(stringResource(R.string.emulator_resume_hint), color = Color.White.copy(alpha = 0.7f), fontSize = 14.sp)
                        // 배치 편집: 게임은 일시정지된 채로 편집 화면을 연다(닫으면 다시 이 메뉴)
                        // 스킨을 쓰는 중이면 배치는 스킨이 정한다 → 배치 편집 없음
                        if (skinEntry == null) {
                            OutlinedButton(onClick = { editing = true }, modifier = Modifier.padding(top = 24.dp)) {
                                Text(stringResource(R.string.emulator_edit_layout), color = Color.White)
                            }
                        }
                        Button(onClick = onExit, modifier = Modifier.padding(top = if (skinEntry == null) 12.dp else 24.dp)) {
                            Text(stringResource(R.string.emulator_exit))
                        }
                        // 이 게임에만 적용: 켜면 지금 화면 설정(스킨·테마·배치·필터)을 이 게임 몫으로, 끄면 전체 설정으로
                        EmulatorSession.activeGame?.let { game ->
                            var gameOnly by remember(game.id) { mutableStateOf(settings.isGameOverride(game.id)) }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.padding(top = 20.dp),
                            ) {
                                Column(modifier = Modifier.padding(end = 12.dp)) {
                                    Text(stringResource(R.string.emulator_game_only), color = Color.White, fontSize = 15.sp)
                                    Text(
                                        stringResource(if (gameOnly) R.string.emulator_game_only_on else R.string.emulator_game_only_off),
                                        color = Color.White.copy(alpha = 0.7f),
                                        fontSize = 12.sp,
                                    )
                                }
                                Switch(checked = gameOnly, onCheckedChange = {
                                    gameOnly = it
                                    settings.setGameOverride(game.id, it)
                                    reloadScreenSettings()
                                })
                            }
                        }
                    }
                }
            }
        }
    }

    // ── 게임 라이브러리 화면 (iOS LibraryView가 명세) ──

    @OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
    @Composable
    private fun LibraryScreen(
        games: List<GameEntry>,
        error: UiError?,
        onDismissError: () -> Unit,
        onPlay: (GameEntry) -> Unit,
        onImport: (Uri) -> Boolean,
        onImportFolder: (Uri) -> Boolean,
        onDelete: (GameEntry) -> Unit,
        onSettings: () -> Unit,
        onDiagnostics: () -> Unit,
        onCloseApp: () -> Unit,
    ) {
        // 오류 배너가 떠 있으면 뒤로가기는 배너부터 닫는다(앱이 백그라운드로 가면 다시 열어도 배너가 그대로 남아 있었다)
        BackHandler(enabled = error != null) { onDismissError() }

        var importError by remember { mutableStateOf<String?>(null) }
        val readError = stringResource(R.string.import_read_error)
        var deleteTarget by remember { mutableStateOf<GameEntry?>(null) }

        // zip/jar만 (iOS allowedContentTypes 대응). octet-stream은 파일관리자가 타입을 모르는 경우 대비
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null && !onImport(uri)) importError = readError
        }
        // 폴더(zip을 풀어 둔 것 등) — 앱이 게임 파일 하나로 묶어 저장한다
        val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
            if (uri != null && !onImportFolder(uri)) importError = readError
        }
        var addMenu by remember { mutableStateOf(false) }

        // Material 표준: TopAppBar(좌정렬 타이틀 + 우측 액션들)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        Box {
                            IconButton(onClick = { addMenu = true }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add_game)) }
                            DropdownMenu(expanded = addMenu, onDismissRequest = { addMenu = false }) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_import_file)) },
                                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.InsertDriveFile, null) },
                                    onClick = {
                                        addMenu = false
                                        picker.launch(arrayOf("application/zip", "application/java-archive", "application/octet-stream"))
                                    },
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.action_import_folder)) },
                                    leadingIcon = { Icon(Icons.Filled.Folder, null) },
                                    onClick = {
                                        addMenu = false
                                        folderPicker.launch(null)
                                    },
                                )
                            }
                        }
                        IconButton(onClick = onSettings) { Icon(Icons.Filled.Settings, stringResource(R.string.action_settings)) }
                    },
                )
            },
        ) { innerPadding ->
            Box(modifier = Modifier.fillMaxSize().padding(innerPadding)) {
                Column(modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
                if (games.isEmpty()) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Icon(
                            Icons.Filled.SportsEsports, null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(48.dp),
                        )
                        Text(stringResource(R.string.library_empty_title), style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 12.dp))
                        Text(
                            stringResource(R.string.library_empty_hint),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(minSize = 100.dp),
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        modifier = Modifier.padding(top = 16.dp),
                    ) {
                        items(games, key = { it.id }) { game ->
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                modifier = Modifier.combinedClickable(
                                    onClick = { onPlay(game) },
                                    onLongClick = { deleteTarget = game },
                                ),
                            ) {
                                Box(
                                    modifier = Modifier.size(100.dp).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp)),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    game.cover?.let {
                                        Image(
                                            bitmap = it.asImageBitmap(),
                                            contentDescription = null,
                                            filterQuality = FilterQuality.None, // 저해상도 아이콘을 nearest로 확대
                                            modifier = Modifier.fillMaxSize().padding(8.dp),
                                        )
                                    } ?: Icon(
                                        Icons.Filled.SportsEsports, null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(32.dp),
                                    )
                                }
                                Text(
                                    game.name,
                                    fontSize = 12.sp,
                                    maxLines = 2,
                                    textAlign = TextAlign.Center,
                                    modifier = Modifier.padding(top = 4.dp).height(34.dp),
                                )
                            }
                        }
                    }
                }
            }

                // 실행 에러: 하단 배너(탭·닫기·뒤로가기로 닫힘). 원문은 앞 몇 줄만 — 레지스터·스택 덤프까지 넣으면
                // 배너가 화면 전체를 덮어 앱이 멈춘 것처럼 보였다. 전체 원문은 [로그 공유]의 진단 로그에 들어간다.
                error?.let { ErrorBanner(it, onDismissError, onDiagnostics, onCloseApp, Modifier.align(Alignment.BottomCenter)) }
            }
        }

        // 임포트 실패 알럿 (iOS alert 대응)
        importError?.let {
            AlertDialog(
                onDismissRequest = { importError = null },
                title = { Text(stringResource(R.string.import_failed_title)) },
                text = { Text(it) },
                confirmButton = { TextButton(onClick = { importError = null }) { Text(stringResource(R.string.action_ok)) } },
            )
        }

        // 삭제 확인 (iOS contextMenu의 확인 단계 대응 — 세이브까지 지워지므로 필수)
        deleteTarget?.let { target ->
            AlertDialog(
                onDismissRequest = { deleteTarget = null },
                title = { Text(stringResource(R.string.delete_game_title)) },
                text = { Text(stringResource(R.string.delete_game_message, target.name)) },
                confirmButton = {
                    TextButton(onClick = {
                        onDelete(target)
                        deleteTarget = null
                    }) { Text(stringResource(R.string.action_delete), color = MaterialTheme.colorScheme.error) }
                },
                dismissButton = { TextButton(onClick = { deleteTarget = null }) { Text(stringResource(R.string.action_cancel)) } },
            )
        }
    }
}

/** 라이브러리 하단 오류 배너의 내용. [detail]은 영어 진단 원문(전체는 진단 로그로). */
data class UiError(val message: String, val detail: String? = null, val offerCloseApp: Boolean = false)

/** 배너에 보일 원문 줄 수. Fatal error 원문은 첫 줄에 원인이 있고 그 뒤는 레지스터·스택 덤프다. */
private const val ERROR_DETAIL_LINES = 3

@Composable
private fun ErrorBanner(error: UiError, onDismiss: () -> Unit, onDiagnostics: () -> Unit, onCloseApp: () -> Unit, modifier: Modifier = Modifier) {
    val color = MaterialTheme.colorScheme.onErrorContainer
    Column(
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.errorContainer)
            .pointerInput(Unit) { detectTapGestures { onDismiss() } }
            .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 4.dp),
    ) {
        Text(error.message, color = color, fontSize = 14.sp, modifier = Modifier.fillMaxWidth())
        error.detail?.let { detail ->
            Text(
                detail.lineSequence().filter { it.isNotBlank() }.take(ERROR_DETAIL_LINES).joinToString("\n"),
                color = color.copy(alpha = 0.8f),
                fontSize = 11.sp,
                lineHeight = 14.sp,
                fontFamily = FontFamily.Monospace,
                maxLines = ERROR_DETAIL_LINES,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            if (error.offerCloseApp) {
                TextButton(onClick = onCloseApp) { Text(stringResource(R.string.action_close_app), color = color) }
            }
            TextButton(onClick = onDiagnostics) { Text(stringResource(R.string.action_diag_log), color = color) }
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_close), color = color) }
        }
    }
}

/** 진단 로그 내보내기 방법 선택. 공유만으로는 기기에 남지 않는다(받는 앱이 첨부를 무시하면 본문 요약만 간다). */
@Composable
private fun DiagnosticsDialog(onSave: () -> Unit, onShare: () -> Unit, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.diag_dialog_title)) },
        text = { Text(stringResource(R.string.diag_dialog_message)) },
        confirmButton = {
            Row {
                TextButton(onClick = onShare) { Text(stringResource(R.string.action_share)) }
                TextButton(onClick = onSave) { Text(stringResource(R.string.action_save_file)) }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) } },
    )
}

/** 스킨 그림 읽기 상태 */
private sealed interface SkinLoad {
    data object Loading : SkinLoad
    data object None : SkinLoad
    data class Loaded(val skin: LoadedSkin) : SkinLoad
}
