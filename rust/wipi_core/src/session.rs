//! 에뮬레이터 세션 관리 — Android(JNI)/iOS(C ABI) 브리지가 공유.
//!
//! Threading model: start()가 tick 루프를 도는 전용 에뮬레이터 스레드를 만든다.
//! UI 스레드는 채널로 키 이벤트를 보내고 take_frame()으로 최신 프레임을 폴링한다.
//! 콜백 없음 — 브리지 계층에 런타임 의존성이 생기지 않는다.

use std::{
    collections::HashMap,
    path::PathBuf,
    sync::{
        Arc, Mutex,
        atomic::{AtomicBool, Ordering},
        mpsc::{Receiver, Sender, channel},
    },
    thread::JoinHandle,
    time::{Duration, Instant},
};

use wie_backend::{Event, KeyCode};

use crate::platform::{CapturedFrame, MobilePlatform, SharedPlatform, VibrationRequest};

enum KeyEvent {
    Down(KeyCode),
    Up(KeyCode),
}

/// 에러 종류 — 호스트가 사용자 안내 문구를 고르는 기준. message는 영어 진단 원문(로그/제보용).
#[derive(Clone, Copy, PartialEq, Eq, Debug)]
pub enum ErrorKind {
    /// 게임 로드 실패 (지원하지 않는 형식, 손상된 파일 등)
    LoadFailed,
    /// 실행 중 오류 (미구현 API 등 호환성 문제)
    Runtime,
}

#[derive(Clone, Debug)]
pub struct SessionError {
    pub kind: ErrorKind,
    pub message: String,
}

struct Session {
    key_tx: Sender<KeyEvent>,
    stop: Arc<AtomicBool>,
    paused: Arc<AtomicBool>,
    platform: Arc<MobilePlatform>,
    error: Arc<Mutex<Option<SessionError>>>,
    thread: Option<JoinHandle<()>>,
}

static SESSION: Mutex<Option<Session>> = Mutex::new(None);

fn parse_key(key: &str) -> Option<KeyCode> {
    Some(match key {
        "UP" => KeyCode::UP,
        "DOWN" => KeyCode::DOWN,
        "LEFT" => KeyCode::LEFT,
        "RIGHT" => KeyCode::RIGHT,
        "OK" => KeyCode::OK,
        "0" => KeyCode::NUM0,
        "1" => KeyCode::NUM1,
        "2" => KeyCode::NUM2,
        "3" => KeyCode::NUM3,
        "4" => KeyCode::NUM4,
        "5" => KeyCode::NUM5,
        "6" => KeyCode::NUM6,
        "7" => KeyCode::NUM7,
        "8" => KeyCode::NUM8,
        "9" => KeyCode::NUM9,
        "#" => KeyCode::HASH,
        "*" => KeyCode::STAR,
        "CLR" => KeyCode::CLEAR,
        "SOFT_L" => KeyCode::LEFT_SOFT_KEY,
        "SOFT_R" => KeyCode::RIGHT_SOFT_KEY,
        "CALL" => KeyCode::CALL,
        "HANGUP" => KeyCode::HANGUP,
        _ => return None,
    })
}

fn emulator_thread(
    platform: Arc<MobilePlatform>,
    filename: String,
    game_data: Vec<u8>,
    key_rx: Receiver<KeyEvent>,
    stop: Arc<AtomicBool>,
    paused: Arc<AtomicBool>,
    error: Arc<Mutex<Option<SessionError>>>,
) {
    let mut emulator = match crate::create_emulator(Box::new(SharedPlatform(platform.clone())), &filename, &game_data) {
        Ok(x) => x,
        Err(e) => {
            *error.lock().unwrap() = Some(SessionError {
                kind: ErrorKind::LoadFailed,
                message: e.to_string(),
            });
            return;
        }
    };

    let mut pressed: HashMap<KeyCode, Instant> = HashMap::new();

    // tick 간격 하한. emulator.tick()은 할 일이 없으면(모든 태스크가 sleep 중) 즉시 반환하므로, 쉬지 않고 부르면
    // 옛날 게임에서도 CPU 코어 하나를 100% 쓴다(발열·배터리). tick이 이 간격보다 빨리 끝나면 남은 시간만큼 잔다.
    // 무거운 tick(코어 예산 최대 8ms)이 간격을 넘기면 자지 않으므로 처리량은 줄지 않는다.
    // 간격 = 게임 태스크가 늦게 깨는 최대 시간. 호스트 측정(데모 2종, 15초): 쉬지 않음 CPU 100% →
    // 1ms 11~12%, 2ms 10~12%, 4ms 9~11%로 CPU는 거의 같고 프레임 지연만 커져서(WIPI-C 데모 fps −2.5% / −4% / −7%) 1ms.
    const TICK_INTERVAL: Duration = Duration::from_millis(1);

    while !stop.load(Ordering::SeqCst) {
        // 일시정지: tick을 멈춰 게임 세계를 얼린다 (RetroArch 등 에뮬 표준의 auto-pause).
        // 키 이벤트는 채널에 남아 재개 후 처리된다.
        if paused.load(Ordering::SeqCst) {
            std::thread::sleep(std::time::Duration::from_millis(50));
            continue;
        }

        let tick_start = Instant::now();

        // 코어의 panic(에뮬레이션 버그)이 스레드를 조용히 죽이면 앱은 "프리즈"로만
        // 보인다 — catch_unwind로 Runtime 에러로 승격해 호스트가 표시하게 한다.
        // panic 후엔 emulator를 다시 만지지 않고 곧장 break하므로 AssertUnwindSafe가 안전하다.
        let step = std::panic::catch_unwind(std::panic::AssertUnwindSafe(|| {
            while let Ok(event) = key_rx.try_recv() {
                match event {
                    KeyEvent::Down(key) => {
                        if let std::collections::hash_map::Entry::Vacant(e) = pressed.entry(key) {
                            emulator.handle_event(Event::Keydown(key));
                            e.insert(Instant::now());
                        }
                    }
                    KeyEvent::Up(key) => {
                        if pressed.remove(&key).is_some() {
                            emulator.handle_event(Event::Keyup(key));
                        }
                    }
                }
            }

            let now = Instant::now();
            for (key, time) in pressed.iter_mut() {
                if now.duration_since(*time).as_millis() > 100 {
                    emulator.handle_event(Event::Keyrepeat(*key));
                    *time = now;
                }
            }

            if platform.screen_capture().take_redraw_request() {
                emulator.handle_event(Event::Redraw);
            }

            emulator.tick()
        }));

        match step {
            Ok(Ok(())) => {
                if let Some(rest) = TICK_INTERVAL.checked_sub(tick_start.elapsed()) {
                    std::thread::sleep(rest);
                }
            }
            Ok(Err(e)) => {
                *error.lock().unwrap() = Some(SessionError {
                    kind: ErrorKind::Runtime,
                    message: e.to_string(),
                });
                break;
            }
            Err(payload) => {
                let message = payload
                    .downcast_ref::<&str>()
                    .map(|s| (*s).to_string())
                    .or_else(|| payload.downcast_ref::<String>().cloned())
                    .unwrap_or_else(|| "panic in emulator core".to_string());
                tracing::error!("emulator core panicked: {message}");
                *error.lock().unwrap() = Some(SessionError {
                    kind: ErrorKind::Runtime,
                    message: format!("core panic: {message}"),
                });
                break;
            }
        }
    }
}

/// 게임을 로드하고 에뮬레이터 스레드를 시작한다.
/// 단일 세션 앱이므로 이전 세션이 남아 있으면(호스트 정리 누락 등) 오류 대신 정리하고 시작한다.
pub fn start(filename: String, game_data: Vec<u8>, data_dir: PathBuf, soundfont_path: Option<PathBuf>) -> anyhow::Result<()> {
    let mut session = SESSION.lock().unwrap();
    if let Some(mut stale) = session.take() {
        tracing::warn!("session already running - stopping stale session before start");
        stale.stop.store(true, Ordering::SeqCst);
        if let Some(thread) = stale.thread.take() {
            let _ = thread.join();
        }
    }

    let platform = Arc::new(MobilePlatform::new(data_dir, crate::SCREEN_WIDTH, crate::SCREEN_HEIGHT, soundfont_path));
    let (key_tx, key_rx) = channel();
    let stop = Arc::new(AtomicBool::new(false));
    let paused = Arc::new(AtomicBool::new(false));
    let error = Arc::new(Mutex::new(None));

    let thread = {
        let (platform, stop, paused, error) = (platform.clone(), stop.clone(), paused.clone(), error.clone());
        std::thread::Builder::new()
            .name("wie-emulator".into())
            .stack_size(16 * 1024 * 1024)
            .spawn(move || emulator_thread(platform, filename, game_data, key_rx, stop, paused, error))?
    };

    *session = Some(Session {
        key_tx,
        stop,
        paused,
        platform,
        error,
        thread: Some(thread),
    });

    Ok(())
}

/// 에뮬레이션 일시정지/재개. 일시정지 중엔 tick이 멈춰 게임 세계가 얼어붙는다.
pub fn set_paused(paused: bool) {
    let session = SESSION.lock().unwrap();
    if let Some(session) = session.as_ref() {
        session.paused.store(paused, Ordering::SeqCst);
        // 게임 시계·소리 시퀀스도 멈춘다(재개 때 시간 점프 없음 — Fold Retro, 2026-10-16 사용자 승인)
        session.platform.set_paused(paused);
    }
}

/// 볼륨 (0.0~1.0, 0이면 음소거) — PCM(효과음)/MIDI(배경음악) 분리, 호스트 사운드 설정용
pub fn set_volume(pcm: f32, midi: f32) {
    let session = SESSION.lock().unwrap();
    if let Some(session) = session.as_ref() {
        session.platform.set_volume(pcm, midi);
    }
}

/// 마지막 paint 이후 새 프레임이 있으면 반환한다.
pub fn take_frame() -> Option<CapturedFrame> {
    let session = SESSION.lock().unwrap();
    session.as_ref()?.platform.screen_capture().take_frame()
}

/// 게임이 요청한 보류 중인 진동을 가져온다 (호스트가 실제 진동 하드웨어로 전달).
pub fn take_vibration() -> Option<VibrationRequest> {
    let session = SESSION.lock().unwrap();
    session.as_ref()?.platform.take_vibration()
}

/// 게임이 종료를 요청했는지 확인한다 (호스트가 세션을 정리하고 UI를 되돌린다).
pub fn take_exit_requested() -> bool {
    let session = SESSION.lock().unwrap();
    session.as_ref().is_some_and(|s| s.platform.take_exit_requested())
}

pub fn key_down(key: &str) {
    send_key(key, KeyEvent::Down)
}

pub fn key_up(key: &str) {
    send_key(key, KeyEvent::Up)
}

fn send_key(key: &str, make: fn(KeyCode) -> KeyEvent) {
    let Some(keycode) = parse_key(key) else {
        tracing::warn!("unknown key: {key}");
        return;
    };

    let session = SESSION.lock().unwrap();
    if let Some(session) = session.as_ref() {
        let _ = session.key_tx.send(make(keycode));
    }
}

/// 보류 중인 오류(종류+진단 원문)를 가져오고 지운다.
pub fn take_error() -> Option<SessionError> {
    let session = SESSION.lock().unwrap();
    session.as_ref().and_then(|s| s.error.lock().unwrap().take())
}

/// 에뮬레이터 스레드를 정지하고 세션을 정리한다.
pub fn stop() {
    let mut session = SESSION.lock().unwrap();
    if let Some(mut session) = session.take() {
        session.stop.store(true, Ordering::SeqCst);
        if let Some(thread) = session.thread.take() {
            let _ = thread.join();
        }
    }
}
