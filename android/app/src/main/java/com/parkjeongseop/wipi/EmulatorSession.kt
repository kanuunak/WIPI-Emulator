// 에뮬레이터 세션 컨트롤러 (명세 §5.1 설계 2번).
// 프로세스 수명 object = 네이티브 static SESSION과 같은 수명. Activity가 재생성돼도(접기·펴기 등)
// 실행 중인 게임·일시정지 상태·마지막 프레임이 남는다. 프로세스가 죽으면 세션도 없다 → 라이브러리부터.
// WipiNative의 키·일시정지·볼륨·정지·폴링 호출은 이 객체와 허브 sink에서만 한다.

package com.parkjeongseop.wipi

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Color
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class PauseReason { NONE, USER, SYSTEM }

object EmulatorSession {
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 모든 키 입력의 단일 경로. 실제 sink = WipiNative. */
    val hub = KeyInputHub(
        sink = object : KeySink {
            override fun down(key: String) = WipiNative.nativeKeyDown(key)
            override fun up(key: String) = WipiNative.nativeKeyUp(key)
        },
        clock = SystemClock::uptimeMillis,
        schedule = { delayMs, action -> mainHandler.postDelayed(action, delayMs) },
    )

    private var settings: Settings? = null

    /** 실행 중인 게임 (null = 세션 없음 → 라이브러리 화면) */
    var activeGame by mutableStateOf<GameEntry?>(null)
        private set

    var pauseReason by mutableStateOf(PauseReason.NONE)
        private set

    val paused: Boolean get() = pauseReason != PauseReason.NONE

    /** 마지막 프레임(240×320). 코어는 새 프레임이 생겨야만 프레임을 주므로 재생성 뒤 즉시 그리려고 보관한다. */
    val frameBitmap: Bitmap by lazy { Bitmap.createBitmap(SCREEN_WIDTH, SCREEN_HEIGHT, Bitmap.Config.ARGB_8888) }

    /** 프레임이 갱신될 때마다 증가. 0이면 아직 프레임 없음. */
    var frameVersion by mutableIntStateOf(0)
        private set

    private val pixels = IntArray(SCREEN_WIDTH * SCREEN_HEIGHT)

    /** Activity.onCreate마다 호출해도 된다. WipiNative 초기화 + 볼륨 설정 원본 연결. */
    fun init(context: Context) {
        WipiNative.init(context)
        if (settings == null) settings = Settings(context.applicationContext)
    }

    /** nativeStart → 성공 시 상태 설정 → 볼륨 적용 */
    fun start(entry: GameEntry, soundfontPath: String): Boolean {
        hub.releaseAll()
        entry.dataDir.mkdirs()
        val ok = WipiNative.nativeStart(entry.gameFile.readBytes(), entry.filename, entry.dataDir.absolutePath, soundfontPath)
        if (ok) {
            frameBitmap.eraseColor(Color.BLACK)
            frameVersion = 0
            pauseReason = PauseReason.NONE
            activeGame = entry
            applyVolume()
        }
        return ok
    }

    /** 키 전부 해제 → tick 정지 → 음소거. 이미 일시정지 중이면 처음 이유를 유지한다. */
    fun pause(reason: PauseReason) {
        require(reason != PauseReason.NONE)
        if (activeGame == null) return
        hub.releaseAll()
        if (pauseReason == PauseReason.NONE) pauseReason = reason
        WipiNative.nativeSetPaused(true)
        applyVolume()
    }

    /** tick 재개 → 설정 볼륨 복원 */
    fun resume() {
        if (activeGame == null) return
        pauseReason = PauseReason.NONE
        WipiNative.nativeSetPaused(false)
        applyVolume()
    }

    /** 키 전부 해제 → 에뮬 스레드 정지(동기) → 상태 초기화 */
    fun stop() {
        hub.releaseAll()
        WipiNative.nativeStop()
        activeGame = null
        pauseReason = PauseReason.NONE
        frameVersion = 0
    }

    /** 일시정지 중이면 항상 0(오디오 스레드는 일시정지와 무관하게 돈다), 아니면 설정 볼륨 */
    fun applyVolume() {
        if (activeGame == null) return
        val (pcm, midi) = if (paused) 0f to 0f else settings?.effectiveVolumes() ?: (1f to 1f)
        WipiNative.nativeSetVolume(pcm, midi)
    }

    /** 새 프레임이 있으면 [frameBitmap]에 복사하고 [frameVersion] 증가 */
    fun pollFrame(): Boolean {
        if (!WipiNative.nativeGetFrame(pixels)) return false
        frameBitmap.setPixels(pixels, 0, SCREEN_WIDTH, 0, 0, SCREEN_WIDTH, SCREEN_HEIGHT)
        frameVersion++
        return true
    }

    /** 게임의 보류 중 진동 요청. 있으면 true, out(길이 2)에 [durationMs, intensity 0~100]. */
    fun pollVibrate(out: LongArray): Boolean = WipiNative.nativePollVibrate(out)

    /** 게임이 종료를 요청했는지 */
    fun pollExit(): Boolean = WipiNative.nativePollExit()

    /** 보류 중 오류 (kind 0=로드 실패, 1=실행 중 오류) to 영어 진단 원문. 없으면 null. */
    fun takeError(): Pair<Int, String>? {
        val kind = IntArray(1)
        val detail = WipiNative.nativeGetError(kind) ?: return null
        return kind[0] to detail
    }
}
