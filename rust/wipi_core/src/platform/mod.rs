mod audio;
mod database;
mod filesystem;
mod lcd;
mod screen;

use std::{
    path::PathBuf,
    sync::{
        Mutex,
        atomic::{AtomicBool, AtomicU64, Ordering},
    },
};

use wie_backend::{Filesystem, Instant, Platform, Screen, canvas::Font};

pub use lcd::LcdPlatform;
use screen::CaptureScreen;
pub use screen::CapturedFrame;

/// 게임이 요청한 진동 (duration_ms, intensity 0~100).
#[derive(Clone, Copy)]
pub struct VibrationRequest {
    pub duration_ms: u64,
    pub intensity: u8,
}

/// 일시정지를 뺀 게임 시계(ms). 실제 시각에서 멈춰 있던 시간을 뺀다 — 재개 때 게임 시간이 한꺼번에 뛰어
/// "밀린 시간 따라잡기"로 멈춘 것처럼 보이던 문제(Fold Retro 실기기, 2026-10-16) 방지. 단조 증가.
#[derive(Default)]
struct PauseClock {
    /// 지금 멈춰 있으면 멈춘 순간의 실제 시각
    paused_since: Option<u64>,
    /// 지금까지 멈춰 있던 시간의 합
    paused_total: u64,
}

impl PauseClock {
    fn set_paused(&mut self, paused: bool, wall_ms: u64) {
        match (paused, self.paused_since) {
            (true, None) => self.paused_since = Some(wall_ms),
            (false, Some(since)) => {
                self.paused_total += wall_ms.saturating_sub(since);
                self.paused_since = None;
            }
            _ => {}
        }
    }

    fn game_ms(&self, wall_ms: u64) -> u64 {
        let wall = self.paused_since.map_or(wall_ms, |since| since.min(wall_ms));
        wall.saturating_sub(self.paused_total)
    }
}

fn wall_ms() -> u64 {
    std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH).unwrap().as_millis() as u64
}

/// Host platform for mobile targets (and headless testing). All persistent
/// state lives under `base_path` (filesDir on Android, Documents on iOS).
pub struct MobilePlatform {
    /// 게임 텍스트 렌더링용 폰트 (wie 데스크톱과 동일한 neodgm — 글자 규격 12px 기준).
    font: Font,
    screen: CaptureScreen,
    filesystem: filesystem::FsFilesystem,
    database_repository: database::FsDatabaseRepository,
    audio_engine: audio::AudioEngine,
    /// 최근 진동 요청. 호스트가 폴링으로 소비한다 (콜백 없는 설계 유지).
    vibration: Mutex<Option<VibrationRequest>>,
    /// 게임이 종료를 요청했는지. 호스트가 폴링으로 감지해 세션을 정리한다.
    exit_requested: AtomicBool,
    /// 가상 시계 (epoch ms). 0이면 비활성(실시간). now() 호출마다 1ms 전진하는
    /// 결정적 시계 — flaky(타이밍 레이스) 재현/차단 실험용 (headless 전용).
    virtual_clock_ms: AtomicU64,
    /// 일시정지를 뺀 게임 시계 (now()가 쓴다)
    pause_clock: Mutex<PauseClock>,
}

impl MobilePlatform {
    pub fn new(base_path: PathBuf, width: u32, height: u32, soundfont_path: Option<PathBuf>) -> Self {
        Self {
            font: Font::try_from_static(include_bytes!("../../assets/neodgm.ttf")).expect("bundled font"),
            screen: CaptureScreen::new(width, height),
            filesystem: filesystem::FsFilesystem::new(base_path.join("fs")),
            database_repository: database::FsDatabaseRepository::new(base_path.join("db")),
            audio_engine: audio::AudioEngine::new(soundfont_path.as_deref()),
            vibration: Mutex::new(None),
            exit_requested: AtomicBool::new(false),
            virtual_clock_ms: AtomicU64::new(0),
            pause_clock: Mutex::new(PauseClock::default()),
        }
    }

    /// 가상 시계 활성화 — now()가 실시간 대신 호출마다 1ms 전진하는 결정적 시계를
    /// 반환하게 한다. start_epoch_ms는 게임이 보는 시작 시각 (0 금지 — 비활성 의미).
    pub fn enable_virtual_clock(&self, start_epoch_ms: u64) {
        self.virtual_clock_ms.store(start_epoch_ms, Ordering::SeqCst);
    }

    /// 가상 시계의 현재 값(epoch ms). 시계를 전진시키지 않는다. 비활성이면 None.
    pub fn virtual_now_ms(&self) -> Option<u64> {
        match self.virtual_clock_ms.load(Ordering::SeqCst) {
            0 => None,
            x => Some(x),
        }
    }

    pub fn screen_capture(&self) -> &CaptureScreen {
        &self.screen
    }

    /// 보류 중인 진동 요청을 가져오고 지운다.
    pub fn take_vibration(&self) -> Option<VibrationRequest> {
        self.vibration.lock().unwrap().take()
    }

    /// 게임의 종료 요청이 있었는지 확인하고 플래그를 지운다.
    pub fn take_exit_requested(&self) -> bool {
        self.exit_requested.swap(false, Ordering::SeqCst)
    }

    /// 일시정지: 게임 시계를 멈추고(재개 때 시간 점프 없음) 소리 시퀀스도 그 자리에서 멈춘다(재개 때 이어서 —
    /// 리듬 게임에서 음악과 게임 시계가 어긋나지 않게).
    pub fn set_paused(&self, paused: bool) {
        self.pause_clock.lock().unwrap().set_paused(paused, wall_ms());
        self.audio_engine.set_paused(paused);
    }

    /// 볼륨 설정 (0.0~1.0, 0이면 음소거) — PCM(효과음)/MIDI(배경음악) 분리, 호스트 사운드 설정용
    pub fn set_volume(&self, pcm: f32, midi: f32) {
        self.audio_engine.set_volume(pcm, midi);
    }
}

/// MobilePlatform을 Arc로 공유하기 위한 래퍼 — 에뮬레이터가 Box<dyn Platform>을
/// 소유하는 동안에도 호스트(UI/테스트 하니스)가 화면 캡처에 접근할 수 있게 함
pub struct SharedPlatform(pub std::sync::Arc<MobilePlatform>);

impl Platform for SharedPlatform {
    fn font(&self) -> &Font {
        self.0.font()
    }
    fn screen(&self) -> &dyn Screen {
        self.0.screen()
    }
    fn now(&self) -> Instant {
        self.0.now()
    }
    fn database_repository(&self) -> &dyn wie_backend::DatabaseRepository {
        self.0.database_repository()
    }
    fn filesystem(&self) -> &dyn Filesystem {
        self.0.filesystem()
    }
    fn audio_sink(&self) -> Box<dyn wie_backend::AudioSink> {
        self.0.audio_sink()
    }
    fn write_stdout(&self, buf: &[u8]) {
        self.0.write_stdout(buf)
    }
    fn write_stderr(&self, buf: &[u8]) {
        self.0.write_stderr(buf)
    }
    fn exit(&self) {
        self.0.exit()
    }
    fn vibrate(&self, duration_ms: u64, intensity: u8) {
        self.0.vibrate(duration_ms, intensity)
    }
}

impl Platform for MobilePlatform {
    fn font(&self) -> &Font {
        &self.font
    }

    fn screen(&self) -> &dyn Screen {
        &self.screen
    }

    fn now(&self) -> Instant {
        // 가상 시계 활성 시: 호출마다 1ms 전진 (실행 간 완전 결정적)
        let virtual_ms = self.virtual_clock_ms.load(Ordering::SeqCst);
        if virtual_ms != 0 {
            let t = self.virtual_clock_ms.fetch_add(1, Ordering::SeqCst);
            return Instant::from_epoch_millis(t);
        }

        Instant::from_epoch_millis(self.pause_clock.lock().unwrap().game_ms(wall_ms()) as _)
    }

    fn database_repository(&self) -> &dyn wie_backend::DatabaseRepository {
        &self.database_repository
    }

    fn filesystem(&self) -> &dyn Filesystem {
        &self.filesystem
    }

    fn audio_sink(&self) -> Box<dyn wie_backend::AudioSink> {
        Box::new(self.audio_engine.sink())
    }

    fn write_stdout(&self, buf: &[u8]) {
        tracing::info!("stdout: {}", String::from_utf8_lossy(buf));
    }

    fn write_stderr(&self, buf: &[u8]) {
        tracing::warn!("stderr: {}", String::from_utf8_lossy(buf));
    }

    fn exit(&self) {
        tracing::info!("app requested exit");
        self.exit_requested.store(true, Ordering::SeqCst);
    }

    fn vibrate(&self, duration_ms: u64, intensity: u8) {
        tracing::info!("vibrate({duration_ms}ms, {intensity}%)");
        *self.vibration.lock().unwrap() = Some(VibrationRequest { duration_ms, intensity });
    }
}

#[cfg(test)]
mod tests {
    use super::PauseClock;

    #[test]
    fn pause_clock_excludes_paused_time() {
        let mut c = PauseClock::default();
        assert_eq!(c.game_ms(1_000), 1_000);
        c.set_paused(true, 1_000);
        // 멈춘 동안 게임 시계는 멈춘 순간에 머문다
        assert_eq!(c.game_ms(1_000), 1_000);
        assert_eq!(c.game_ms(601_000), 1_000);
        c.set_paused(false, 601_000);
        // 재개 직후 = 멈춘 순간, 그 뒤로는 실제 시각과 같은 빠르기
        assert_eq!(c.game_ms(601_000), 1_000);
        assert_eq!(c.game_ms(601_050), 1_050);
        // 두 번째 일시정지도 누적
        c.set_paused(true, 602_000);
        c.set_paused(false, 612_000);
        assert_eq!(c.game_ms(612_000), 2_000);
    }

    #[test]
    fn pause_clock_ignores_repeated_calls_and_is_monotonic() {
        let mut c = PauseClock::default();
        c.set_paused(false, 500); // 멈추지 않았는데 재개 → 변화 없음
        assert_eq!(c.game_ms(500), 500);
        c.set_paused(true, 1_000);
        c.set_paused(true, 5_000); // 이미 멈춤 → 처음 시각 유지
        c.set_paused(false, 9_000);
        assert_eq!(c.game_ms(9_000), 1_000);
        let mut last = 0;
        for (paused, t) in [(true, 9_500), (false, 9_700), (true, 9_800), (false, 20_000)] {
            c.set_paused(paused, t);
            let g = c.game_ms(t);
            assert!(g >= last, "{g} < {last}");
            last = g;
        }
    }
}
