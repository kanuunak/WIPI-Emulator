package com.parkjeongseop.wipi

import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.InputDevice
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.WindowManager
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PauseCircle
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SportsEsports
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import java.io.File
import kotlin.math.roundToInt
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
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

    /** 에뮬 화면에서만 하드웨어 키를 게임으로 전달 (화면 = 세션 유무에서 파생) */
    private val emulatorActive: Boolean get() = EmulatorSession.activeGame != null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        EmulatorSession.init(this)
        library = GameLibrary(this)
        settings = Settings(this)
        haptics = Haptics(this)

        setContent {
            WipiTheme {
                WipiApp()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        // 구성 변경(접기·펴기 등)이나 시스템의 Activity 파괴에서는 세션을 유지한다 — 이미 ON_PAUSE에서 일시정지·음소거됨
        if (isFinishing && !isChangingConfigurations) EmulatorSession.stop()
    }

    @Composable
    private fun WipiApp() {
        var games by remember { mutableStateOf(library.list()) }
        var errorMessage by remember { mutableStateOf<String?>(null) }
        var showSettings by remember { mutableStateOf(false) } // iOS sheet 대응 오버레이

        Box(modifier = Modifier.fillMaxSize()) {
            if (EmulatorSession.activeGame == null) {
                LibraryScreen(
                    games = games,
                    error = errorMessage,
                    onDismissError = { errorMessage = null },
                    onPlay = { entry ->
                        if (EmulatorSession.start(entry, soundfontPath())) {
                            errorMessage = null
                        } else {
                            // 동기 실패는 형식 문제가 아니라 시작 자체 실패 (형식 오류는 로드 후 kind로 안내됨)
                            errorMessage = pendingError() ?: getString(R.string.error_start_failed)
                        }
                    },
                    onImport = { uri ->
                        val entry = library.importGame(uri)
                        games = library.list()
                        entry != null
                    },
                    onDelete = { entry ->
                        library.delete(entry)
                        games = library.list()
                    },
                    onSettings = { showSettings = true },
                )
            } else {
                EmulatorScreen(
                    onError = { message ->
                        errorMessage = message
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
                    onClose = { showSettings = false },
                )
            }
        }
    }

    /** 보류 중 오류를 사용자 안내 문구 + 진단 원문으로 변환 (없으면 null). iOS WipiCore.describeError와 동일 카피. */
    private fun pendingError(): String? {
        val (kind, detail) = EmulatorSession.takeError() ?: return null
        return when (kind) {
            0 -> getString(R.string.error_load_failed, detail)
            else -> getString(R.string.error_runtime, detail)
        }
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
    private fun EmulatorScreen(onError: (String) -> Unit, onExit: () -> Unit, onSettings: () -> Unit) {
        val vibrateOut = remember { LongArray(2) }
        val paused = EmulatorSession.paused
        // 프레임·일시정지 상태는 EmulatorSession(프로세스 수명)에 있어 Activity 재생성 뒤에도 마지막 프레임을 바로 그린다
        val frameVersion = EmulatorSession.frameVersion
        val frame = remember(frameVersion) {
            if (frameVersion > 0) EmulatorSession.frameBitmap.asImageBitmap().also { it.prepareToDraw() } else null
        }

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
        // 첫 프레임 도착 여부만 구독 (frameVersion 자체를 컴포지션에서 읽으면 매 프레임 재구성된다)
        val hasFrame by remember { derivedStateOf { EmulatorSession.frameVersion > 0 } }
        var showHud by rememberSaveable { mutableStateOf(false) } // 레이아웃 HUD, 기본 꺼짐

        // 바깥: 시스템 바 뒤까지 배경을 칠한다(edge-to-edge). 크기 = 창(콘텐츠) 크기
        BoxWithConstraints(modifier = Modifier.fillMaxSize().background(EMULATOR_BG)) {
            val windowW = maxWidth.value
            val windowH = maxHeight.value

            // 안쪽: 안전 영역(safeDrawing 인셋 제외) 안에서 명세 §5.3 계산 결과대로 배치
            BoxWithConstraints(modifier = Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)) {
                val density = LocalDensity.current.density
                val layout = remember(maxWidth, maxHeight, density) {
                    computeStackLayout(maxWidth.value, maxHeight.value, density)
                }
                val metrics = layoutMetrics(windowW, windowH, layout)
                LaunchedEffect(metrics) { Log.i(LOG_TAG, metrics.logLine) }

                // 게임: draw 단계에서만 frameVersion을 읽어 새 프레임마다 다시 그리기만 한다
                Canvas(modifier = Modifier.offset(layout.game.left.dp, layout.game.top.dp).size(layout.game.width.dp, layout.game.height.dp)) {
                    if (EmulatorSession.frameVersion > 0) {
                        drawImage(
                            frameImage,
                            dstSize = IntSize(size.width.roundToInt(), size.height.roundToInt()),
                            filterQuality = FilterQuality.None,
                        )
                    }
                }
                if (!hasFrame) {
                    Box(
                        modifier = Modifier.offset(layout.game.left.dp, layout.game.top.dp).size(layout.game.width.dp, layout.game.height.dp),
                        contentAlignment = Alignment.Center,
                    ) { Text(stringResource(R.string.emulator_loading), color = Color.White) }
                }

                // 키패드 영역(바깥 여백 포함, 높이 = keypadHeight(keyH)) — 키 사각형은 KeypadModel이 이 크기에서 계산
                TouchKeypad(
                    hub = EmulatorSession.hub,
                    hapticsEnabled = settings.keypadHaptics,
                    modifier = Modifier
                        .offset(layout.keypad.left.dp, layout.keypad.top.dp)
                        .size(layout.keypad.width.dp, layout.keypad.height.dp),
                )

                if (showHud) LayoutHud(metrics)

                // 게임 우상단(안전 영역 안): 일시정지 + 설정. ⚙ 길게 누르기 = 레이아웃 HUD 켜기/끄기
                Row(modifier = Modifier.offset(layout.icons.left.dp, layout.icons.top.dp)) {
                    IconButton(onClick = { EmulatorSession.pause(PauseReason.USER) }) {
                        Icon(Icons.Filled.Pause, stringResource(R.string.emulator_paused), tint = Color.White.copy(alpha = 0.6f))
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
                        Icon(Icons.Filled.Settings, stringResource(R.string.action_settings), tint = Color.White.copy(alpha = 0.6f))
                    }
                }
            }

            if (paused) {
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
                        Button(onClick = onExit, modifier = Modifier.padding(top = 24.dp)) {
                            Text(stringResource(R.string.emulator_exit))
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
        error: String?,
        onDismissError: () -> Unit,
        onPlay: (GameEntry) -> Unit,
        onImport: (Uri) -> Boolean,
        onDelete: (GameEntry) -> Unit,
        onSettings: () -> Unit,
    ) {
        var importError by remember { mutableStateOf<String?>(null) }
        val readError = stringResource(R.string.import_read_error)
        var deleteTarget by remember { mutableStateOf<GameEntry?>(null) }

        // zip/jar만 (iOS allowedContentTypes 대응). octet-stream은 파일관리자가 타입을 모르는 경우 대비
        val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
            if (uri != null && !onImport(uri)) importError = readError
        }

        // Material 표준: TopAppBar(좌정렬 타이틀 + 우측 액션들)
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.app_name)) },
                    actions = {
                        IconButton(onClick = {
                            picker.launch(arrayOf("application/zip", "application/java-archive", "application/octet-stream"))
                        }) { Icon(Icons.Filled.Add, stringResource(R.string.action_add_game)) }
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

                // 실행 에러: 하단 배너, 탭하면 닫힘
                error?.let {
                    Text(
                        it,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .background(MaterialTheme.colorScheme.errorContainer)
                            .pointerInput(Unit) { detectTapGestures { onDismissError() } }
                            .padding(12.dp),
                    )
                }
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
