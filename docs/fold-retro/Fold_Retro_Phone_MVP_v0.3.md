# Fold Retro Phone — 프로젝트 기획 및 MVP 개발 명세 v0.3 (Galaxy Z Fold5 기준)

- 작성일: 2026-10-13
- 상태: Claude 교차 검토 반영 + 실행 경로 변경(클라우드 세션 크레딧 우선) / 구현 전
- 기준 소스: `ParkJeongseop/WIPI-Emulator` @ `43a6098` (v0.3.1, 2026-10-09)
- 기준 코어: `ParkJeongseop/wie` @ `38f7e04f` (`rust/Cargo.toml`에 git rev로 고정, `dlunch/wie`의 포크)
- 기준 기기: Galaxy Z Fold5 (커버 6.2" 904×2316, 메인 7.6" 1812×2176)
- 가칭: Fold Retro Phone (서비스·상표명 확정 아님)

**표기 규칙**
- **[확인]** 소스 코드·공식 문서·직접 실행으로 확인함
- **[추정]** 근거가 있는 추정. 실기기에서 확인 필요
- **[미검증]** 확인하지 못함

함께 쓰는 파일: `AGENTS.md`(저장소 루트, 에이전트 공통 규칙), `Implementation_Guide_v0.3.md`(실행 가이드 — 역할·모델·Phase 프롬프트·CI·클라우드 환경).

v0.3 변경: 구현은 Claude Code 클라우드 세션(크레딧), 빌드는 GitHub Actions, 리뷰는 Codex로 역할을 바꿨다. 그에 맞춰 P0-1에 CI·고정 디버그 서명을 넣고, 에이전트 지시 파일 방식(§6.2)과 테스트 주체(§7.1)를 고쳤다.

---

## 0. v0.1 대비 핵심 변경

| # | v0.1의 가정 | v0.2 결정 | 근거 |
|---|---|---|---|
| 1 | "상단 게임 + 하단 키패드" 화면을 새로 만든다 | **이미 있다.** 기존 `EmulatorScreen`을 고친다 | `MainActivity.kt:356-375` [확인] |
| 2 | 엔진 키 반복 여부를 확인 후 결정 | 코어가 누른 키마다 100ms 간격 `Keyrepeat`를 만든다. **UI는 반복을 절대 만들지 않는다** | `session.rs:132-138` [확인] |
| 3 | 키: 방향·OK·소프트키·0~9·`*`·`#` | **CLR, CALL 추가**(기존 키패드에 있음). HANGUP은 터치 키패드에서 제외 | `session.rs:54-80`, `MainActivity.kt:569-577` [확인] |
| 4 | 접기·펴기 때 세션 보존 "노력" | **지금 구조에선 펴는 순간 게임이 종료된다** → P0 버그 수정 | Manifest `configChanges` 누락 + `onDestroy()`의 무조건 `nativeStop()` [코드 확인, 동작 추정] |
| 5 | 하단 안전 영역 고려 | targetSdk 36 → Android 15+에서 edge-to-edge 강제. 에뮬 화면은 인셋 처리가 없다 → P0 | Android 16 동작 변경 문서 [확인], 코드 [확인] |
| 6 | 저작권 문제없는 테스트 게임 확보 필요 | 저장소에 자체 데모 2종(MIT)이 있고 실행된다. 키 전수 검증용 **KeyTest MIDlet**을 추가 | `demo/` [확인, 헤드리스 실행] |
| 7 | 햅틱 ON/OFF·강도는 P1 신규 | 이미 구현됨 → 신규 작업 제외 | `Settings.kt` [확인] |
| 8 | AUTO/RETRO/NORMAL 3모드가 P0 | P0는 **세로 스택 레이아웃 하나를 완성**. 가로 분할 + 모드 선택은 P1 | 레이아웃이 하나뿐일 때 모드 전환은 의미가 없음 |
| 9 | `CLAUDE.md` 빌드 절차를 따른다 | macOS 절대경로·옛 rev(`b75a03c1`)·"path 의존성" 서술은 낡았다 | `CLAUDE.md:12,23,46` vs `rust/Cargo.toml:12-17` [확인] |
| 10 | Codex가 실기기까지 검증 | 빌드·단위 테스트·adb 설치/스크린샷/로그는 에이전트, **접기·멀티터치 감각은 사용자** | 역할 분리 |
| 11 | 라이선스 MIT 확인 | .so에 들어가는 Rust 크레이트 164개 전부 허용형(GPL 없음). 다만 **앱 고지에 neodgm(OFL) 등 누락** | `cargo metadata`, 폰트 메타데이터 [확인] |
| 12 | 일시정지하면 안전 | 일시정지해도 **오디오 스레드는 계속 돈다**(BGM 계속 재생 가능) → 일시정지 시 음소거를 P0로 | `platform/audio.rs` [코드 확인, 체감 추정] |
| 13 | "좁고 긴 커버"가 폴드 공통 | Fold5는 맞다(≈23.1:9). 하지만 2026년형 Fold8은 커버가 10:16 와이드 → 실측 창 크기 기반 계산 원칙 유지 | Samsung 공식 가이드 [확인] |
| 14 | Codex 구현 → Claude 리뷰 | **Claude Code 클라우드 세션(크레딧)이 구현**, GitHub Actions가 .so·APK 빌드, Codex가 PR 리뷰, 사용자가 폰에서 테스트 | 실행 가이드 v0.3 §0 |
| 15 | (없음) | 클라우드·CI·로컬 빌드의 서명을 **고정 디버그 키**로 통일 | 키가 바뀌면 폰에서 업데이트가 실패하고, 재설치하면 게임·세이브가 지워짐 |

---

## 1. 한 줄 정의

**Galaxy Z Fold5의 좁고 긴 커버 화면을 2000년대 한국 피처폰 게임기처럼 쓰는 Android 앱.** 위에는 원본 게임(240×320)을 종횡비 그대로, 아래에는 당시 배열의 터치 키패드(소프트키·방향키·확인·통화·취소·숫자패드)를 둔다. 기존 오픈소스 앱(WIPI-Emulator)을 포크해 **세션 연속성·입력 정확도·커버 화면 최적화**를 더한다.

---

## 2. 기준 기기와 플랫폼 조건

### 2.1 Galaxy Z Fold5

| 항목 | 커버 화면 | 메인 화면 | 상태 |
|---|---|---|---|
| 크기 | 6.2" | 7.6" | [확인] |
| 해상도(세로 기준) | 904×2316 | 1812×2176 | [확인] |
| 비율 | 약 23.1:9 (세로/가로 ≈ 2.56) | 약 6:5 | [확인] |
| dp 크기 | 344×882dp | 690×829dp | [실측: Phase 2 HUD, 배율 2.625, 화면 확대/축소 기본] `DEVICE_METRICS.md` |
| 최소 폭(sw) | 600dp 미만 → 휴대폰 취급 | 600dp 이상 → 대화면 취급 | [추정] |
| OS | Android 13 출시, OS 4회 업그레이드 정책. 공개 정보상 최신은 One UI 8.5(Android 16) | | [확인: 공개 사양] / 실기기 버전 [미검증] |

- dp·인셋은 **Phase 2에서 앱이 직접 측정해 로그로 남기고**, 그 값으로 위 추정치를 교체한다.
- 실기기 OS 확인: `adb shell getprop ro.build.version.release`, `adb shell getprop ro.build.version.oneui`.

### 2.2 이 앱(targetSdk 36)에 적용되는 Android 동작 [확인: Android 공식 문서]

1. **Edge-to-edge 강제**: Android 15+ 기기에서 targetSdk 35 이상 앱은 시스템 바 뒤까지 그려진다. Android 16 기기에서 targetSdk 36 앱은 예외 설정(opt-out)도 불가. → 모든 화면이 인셋을 직접 처리해야 한다.
2. **대화면 방향·크기 제한 무시**: Android 16 + targetSdk 36이면 sw ≥ 600dp 화면(= Fold5 메인 화면)에서 `screenOrientation="portrait"` 등이 무시된다. 커버 화면(sw < 600dp)은 기존대로 세로 고정. 예외는 `android:appCategory="game"` 선언 앱. 임시 opt-out 속성은 API 37 타깃부터 사라진다.
3. **예측형 뒤로가기**: targetSdk 36에서 기본 활성. 앱은 `BackHandler`를 써서 영향이 작을 것으로 본다 [추정].
4. **구성 변경**: `configChanges`에 선언하지 않은 구성 변경은 Activity를 파괴·재생성한다. 접기·펴기는 구성 변경이다.
5. **제스처 제외 영역**: 시스템은 제외 영역의 세로 길이를 200dp까지만 인정한다(몰입형 sticky 상태 등은 예외).

### 2.3 Samsung 접기·펴기 동작 [확인: Samsung 지원 문서]

- **펼칠 때(커버 → 메인)**: 커버 화면에서 열려 있던 앱은 자동으로 메인 화면에서 이어진다.
- **접을 때(메인 → 커버)**: 설정 > 디스플레이 > "커버 화면에서 앱 계속 사용"에서 앱별로 정한다. 켜지 않은 앱은 접으면 화면에서 닫힌다(이때 앱이 받는 생명주기 이벤트는 실기기로 확인 — §7.4 T5).
- 그러므로 **"커버에서 플레이 중 펼치기"는 매번 일어나는 기본 시나리오**이고, 지금 구조에서 게임이 종료되는 경로다(§5.1).

---

## 3. 기반 프로젝트 실측 결과 (이미 있는 것)

### 3.1 구조 [확인]

```
android/app/src/main/java/com/parkjeongseop/wipi/
  MainActivity.kt   (621줄) 라이브러리/에뮬 화면 전환, EmulatorScreen(게임+키패드),
                    하드웨어 키·게임패드·스틱 매핑, Keypad/KeyButton
  WipiNative.kt     JNI 선언 (object, init에서 System.loadLibrary("wipi_android"))
  Settings.kt       SharedPreferences 설정 + 설정 화면 + 오픈소스 고지 목록
  GameLibrary.kt    SAF 임포트, files/games/<UUID>/{게임파일, meta.json, cover.png, data/}
  Haptics.kt, Theme.kt
rust/wipi_core/src/session.rs          전역 단일 세션(static SESSION), 전용 스레드, 키 채널
rust/wipi_android/src/jni_bridge.rs    JNI 함수
rust/wipi_android/src/bin/headless.rs  UI 없는 실행 하니스(프레임 BMP 저장, 키 스크립트)
rust/wipi_core/src/platform/           화면 캡처, 파일시스템, DB, 오디오(rodio + rustysynth)
demo/                                  자체 데모 demo.jar(J2ME), demo_wipi.zip(KTF WIPI-C) + 소스 (MIT)
```

### 3.2 JNI API (`WipiNative`) [확인]

| 함수 | 동작 |
|---|---|
| `nativeInit(context)` | 로깅·오디오(ndk-context) 초기화. 앱 시작 시 1회 |
| `nativeStart(bytes, filename, dataDir, soundfontPath): Boolean` | 세션 시작. 기존 세션이 있으면 정지 후 교체 |
| `nativeGetFrame(IntArray(240*320)): Boolean` | **마지막 호출 이후 새 프레임이 있을 때만** true |
| `nativeKeyDown(key)` / `nativeKeyUp(key)` | 문자열 키코드 |
| `nativeSetPaused(Boolean)` | tick 정지(게임 세계 동결). 오디오 스레드는 멈추지 않음 |
| `nativeSetVolume(pcm, midi)` | 0~1 |
| `nativePollVibrate` / `nativePollExit` / `nativeGetError` | 폴링 |
| `nativeStop()` | 에뮬 스레드 정지 후 join (**동기**, 현재 UI 스레드에서 호출) |
| `nativeGameIcon` / `nativeGameName` | 패키지 메타데이터 |

- **세션 존재 여부를 묻는 API는 없다.** → Kotlin 쪽이 세션 상태를 직접 들고 있어야 한다(§5.1).

### 3.3 키 코드와 코어의 키 처리 [확인: `session.rs`]

- 키 문자열: `UP DOWN LEFT RIGHT OK 0~9 * # CLR SOFT_L SOFT_R CALL HANGUP` (그 외는 경고 후 무시)
- 코어는 키별 "눌림 집합"을 가진다. **이미 눌린 키의 Down, 안 눌린 키의 Up은 무시**한다.
  - 결과: 두 손가락이 같은 키를 누르면 **첫 손가락을 떼는 순간 키가 떨어진다** → UI가 키별 참조 카운트를 해야 한다.
- 눌린 키마다 100ms가 지나면 `Keyrepeat`를 반복 생성한다(초기 지연 없음). wie의 데스크톱·웹 프런트엔드와 같은 규약이다.
- 일시정지 중에는 키 채널을 처리하지 않고 재개 후 처리한다.
- Down과 Up이 같은 tick에 들어오면 게임이 못 잡는 경우가 있다(원작자 메모: "0ms 탭은 게임이 못 잡음").
- 화면은 240×320 고정이다(`wipi_core` 상수 + JNI 버퍼 크기). 게임별 해상도 대응은 없다. UI는 이 값을 한 곳(상수)에서만 참조한다.
- J2ME 키코드(코어 키맵 `wie-j2me/src/emulator.rs` + KeyTest 헤드리스 실행으로 확인): UP −1, DOWN −2, LEFT −3, RIGHT −4, OK −5, SOFT_L −6, SOFT_R −7, CLR −8, CALL −10, HANGUP −11, 숫자는 ASCII('0'=48 … '9'=57), `*`=42, `#`=35. WIPI(KTF/LGT/SKT) 게임은 플랫폼별 코드로 따로 변환된다.

### 3.4 기존 Android UI [확인: `MainActivity.kt`]

- 에뮬 화면 = `Column[ 게임 Box(weight 1, ContentScale.Fit, FilterQuality.None) + Keypad ]`. 즉 **위 게임/아래 키패드, 비율 유지, nearest 렌더링은 이미 구현**되어 있다.
- 키패드: 7행×3열, 키 높이 44dp, 간격 4dp, 바깥 여백 8dp(합계 약 348dp). 배열:
  ```
  ◁(SOFT_L)  ▲      ▷(SOFT_R)
  ◀          OK     ▶
  📞(CALL)   ▼      CLR
  1 2 3 / 4 5 6 / 7 8 9 / * 0 #
  ```
- 입력: 키마다 개별 `pointerInput + detectTapGestures`. 누르면 Down, 떼거나 취소되면 Up. 한계:
  1. 손가락을 옆 키로 **미끄러뜨려 바꿀 수 없다**(Compose는 down 시점에 맞은 대상에게만 그 포인터를 전달).
  2. 같은 키 두 손가락 문제(§3.3).
  3. 44dp로 권장 터치 크기 48dp보다 작다.
- 일시정지: ON_PAUSE·뒤로가기·⏸ 버튼 → `nativeSetPaused(true)` + 오버레이. 복귀해도 자동 재개하지 않는다(탭하면 재개).
- 하드웨어 키보드·게임패드·왼쪽 스틱 매핑이 있다. `repeatCount > 0`은 무시(코어가 반복 담당).
- 상태: `screen`, `paused`, 프레임 모두 `remember` → **Activity 재생성 시 소실**. `onDestroy()`는 **무조건** `nativeStop()`.
- 인셋: 라이브러리 화면은 `Scaffold`로 처리되지만 **에뮬 화면은 `fillMaxSize()`만 쓰고 인셋 처리가 없다.**
- 햅틱: 키 다운 시 `performHapticFeedback(VIRTUAL_KEY)`, 설정 `keypadHaptics`(기본 ON). 게임 진동 세기 배율 설정도 있다.

### 3.5 빌드 체인 [확인]

| 항목 | 값 |
|---|---|
| AGP / Kotlin / Compose BOM | 8.10.1 / 2.1.21 / 2024.12.01 |
| compileSdk / targetSdk / minSdk | 36 / 36 / 26 |
| ABI | arm64-v8a(실기기), x86_64(AVD) |
| Gradle wrapper | **없음.** 원작자는 Homebrew의 gradle@8을 직접 실행. AGP 8.10은 Gradle ≥ 8.11.1, JDK 17, Build Tools 35.0.0 필요 |
| Rust | edition 2024 + `jni_bridge.rs`의 let-chains → **stable 1.88 이상**. 이번 검토에서 1.97로 호스트(Linux) 빌드 성공(약 5분, 2코어, LTO 끔) |
| NDK | 27.1.12297006, API 26 링커(AAudio 때문) |
| `rust/.cargo/config.toml` | 원작자 macOS 절대경로(`/Users/parkjeongseop/...darwin-x86_64`) 하드코딩 → **다른 PC에서 그대로는 실패**(같은 구성으로 실험: `linker ... not found`) |
| .so 연동 | Gradle이 Rust를 빌드하지 않는다. `cargo build` 후 `jniLibs/<abi>/`에 수동 복사(gitignore 대상). **없으면 앱이 실행 즉시 크래시**(`System.loadLibrary` 실패) |
| 16KB 페이지 정렬 | config의 `rustflags`로 `-z max-page-size=16384` 지정. `RUSTFLAGS` 환경변수를 설정하면 이 값이 통째로 무시된다(실험: 정렬 0x4000 → 0x1000) |

---

## 4. 범위 재조정

### 4.1 P0 — MVP (끝나면 Fold5 커버에서 실제로 쓸 만한 상태)

| ID | 항목 | 완료 판정 |
|---|---|---|
| P0-1 | **재현 가능한 빌드**: Gradle wrapper, OS 무관 Rust 빌드 스크립트, CI(GitHub Actions) 빌드·드래프트 릴리스, 고정 디버그 서명, .so 누락 시 빌드 실패, 16KB 정렬 확인 | CI 성공, 깨끗한 체크아웃에서 문서대로 APK 생성, 폰에서 덮어쓰기 업데이트 |
| P0-2 | **포크 식별**: applicationId·앱 이름만 변경(패키지·JNI 그대로) | Play 스토어 원본과 동시 설치 |
| P0-3 | **세션 연속성**: configChanges 보강, onDestroy 가드, 프로세스 수명 세션 컨트롤러(실행·일시정지·마지막 프레임), 일시정지 시 음소거 | 플레이 중 펴기·접기·홈 복귀에서 게임이 종료되지 않음 |
| P0-4 | **입력**: KeyInputHub(참조 카운트·최소 누름 시간·전체 해제), 단일 포인터 처리기 키패드(클러스터 내 슬라이드), 하드웨어 키도 허브 경유, 방향키 영역 제스처 제외 | KeyTest 판정표(§5.2.5) 전부 통과 |
| P0-5 | **레이아웃**: safeDrawing 인셋, 세로 스택 계산기(키 48dp 목표·40dp 하한), 게임 최대화·종횡비 정확, 프레임 갱신이 키패드를 재구성하지 않게 | Fold5 커버에서 겹침·잘림 0, 측정 로그와 계산값 일치 |
| P0-6 | **검증 자산**: KeyTest MIDlet(MIT), 단위 테스트(허브·히트테스트·레이아웃), 레이아웃 HUD/로그 | `testDebugUnitTest` 통과, KeyTest 실기기 확인 |
| P0-7 | **회귀 없음**: 라이브러리·임포트·삭제·사운드·진동·설정·고지·하드웨어 키·게임패드 | §7.4 체크리스트 |
| P0-8 | **고지 보강**: neodgm(OFL-1.1) 항목 추가 | 설정 > 오픈소스 라이선스에 표시 |

### 4.2 P1 — MVP 직후

- **가로/정사각 분할 레이아웃(SPLIT)**: 좌 방향키·중앙 게임·우 숫자패드. AUTO는 창의 세로/가로 비가 1.5 미만이면 SPLIT(= Fold5 메인 화면, 가로 회전). 수동 선택 AUTO/STACK/SPLIT.
- 키패드 크기 프리셋(작게/보통/크게 → 키 높이 목표 44/48/52dp).
- 정수배 스케일 토글(Fold5 커버: 3배 = 720×960px, 가로 80%).
- 게임 화면 몰입 모드(시스템 바 숨김, 스와이프 시 잠깐 표시). 제스처 제외 200dp 한도도 풀린다.
- ⏸·⚙ 아이콘 배치 선택(게임 위 오버레이 / 전용 줄).
- 슬라이드 시 햅틱 틱, 키 라벨·색 다듬기, 앱 아이콘 교체.
- **배포 전 필수**: 전체 오픈소스 고지 자동 생성(Rust: `cargo about`, Android: AndroidX/Compose/Kotlin).

### 4.3 P2 — 후순위

- (Rust `wipi_core`) 일시정지 시간을 뺀 게임 시계 — 재개 시 시간 점프 해결. 현재 `now()`가 실제 시각(`SystemTime`)이라 재개하면 멈춰 있던 시간만큼 타이머가 한꺼번에 발화한다.
- (Rust `wipi_core`) 일시정지 시 오디오 스트림 실제 정지, 오디오 포커스 처리.
- `nativeStop()`을 UI 스레드 밖으로(긴 tick 중 종료 시 ANR 예방).
- 게임별 키 배치 프리셋, 8방향 옵션, Flex 모드(반접힘), 기종별 정밀 튜닝.
- 업스트림 PR(세션 종료·인셋·음소거 수정은 원본 앱 사용자 모두에게 유익).

### 4.4 삭제·대체

- `FoldingFeature`/Jetpack WindowManager: 필요 없음. 의존성을 추가하지 않는다.
- 햅틱 ON/OFF·강도: 기존 구현을 그대로 쓴다.
- "피처폰 모드 해제(NORMAL)": 뜻이 모호하다 → SPLIT 레이아웃(P1)으로 대체.
- 컨트롤 투명도·색상 테마: P1 → P2.

---

## 5. 설계 상세

### 5.1 세션 연속성 (P0-3)

**문제** [코드 확인 / 동작 추정]
- Manifest가 `configChanges="orientation|screenSize|keyboardHidden"`만 선언한다(`AndroidManifest.xml:16-17`). 접기·펴기 때 바뀌는 `smallestScreenSize`·`screenLayout`이 없어 **Activity가 재생성**된다.
- `onDestroy()`가 **무조건** `nativeStop()`을 부른다(`MainActivity.kt:105-108`) → 재생성만으로 게임 종료. `screen`도 `remember`라 라이브러리로 돌아간다.
- `paused`도 화면 지역 상태라, 세션이 살아남는 경로에서는 "코어는 정지, 오버레이는 없음" 같은 멈춘 화면이 생길 수 있다.
- 게임패드·키보드 연결(`keyboard`, `navigation` 변경)도 같은 경로로 게임을 끝낸다.
- 오디오 스레드는 일시정지 플래그와 무관하게 돈다(`audio.rs:73-160`) → 일시정지·백그라운드에서도 BGM이 계속 날 수 있다.

**설계**
1. Manifest:
   ```xml
   android:configChanges="orientation|screenSize|smallestScreenSize|screenLayout|density|keyboard|keyboardHidden|navigation"
   ```
2. `EmulatorSession` — Kotlin `object`(프로세스 수명 = 네이티브 static `SESSION`과 같은 수명)
   - 상태(Compose snapshot state): `activeGame: GameEntry?`, `pauseReason: NONE | USER | SYSTEM`, `frameBitmap`(240×320, 마지막 프레임), `frameVersion: Int`
   - `start(entry)`: `nativeStart` → 성공 시 상태 설정 → 볼륨 적용
   - `pause(reason)`: `hub.releaseAll()` → `nativeSetPaused(true)` → `nativeSetVolume(0, 0)`
   - `resume()`: `nativeSetPaused(false)` → 설정 볼륨 복원
   - `stop()`: `hub.releaseAll()` → `nativeStop()` → 상태 초기화
   - `applyVolume()`: 일시정지 중이면 항상 0, 아니면 `settings.effectiveVolumes()`
   - **`WipiNative`의 키·일시정지·볼륨·정지 호출은 이 객체와 허브 sink에서만 한다.**
3. MainActivity
   - 화면 = `EmulatorSession.activeGame != null ? Emulator : Library` (상태를 파생, 별도 저장하지 않음)
   - `onDestroy`: `if (isFinishing && !isChangingConfigurations) EmulatorSession.stop()`. 그 외(시스템이 Activity만 파괴)는 세션 유지 — 이미 ON_PAUSE에서 일시정지·음소거됨.
   - 프레임 폴링 결과는 `EmulatorSession.frameBitmap`에 쓴다 → 재생성 뒤에도 마지막 프레임을 바로 그린다(코어는 새 프레임이 생겨야만 프레임을 준다).
   - 프로세스가 죽으면 세션도 없다 → 라이브러리부터 시작. 게임 진행은 게임이 스스로 저장한 시점까지만 남는다(세이브 스테이트 없음).
4. 일시정지 트리거
   - ON_PAUSE → `pause(SYSTEM)`, ⏸ 버튼·뒤로가기 → `pause(USER)`, 오버레이 탭 → `resume()` (기존 UX 유지: 자동 재개 안 함)
   - 창 포커스 상실(`LocalWindowInfo.current.isWindowFocused == false`, 예: 알림창) → `hub.releaseAll()`만
5. 알려진 한계(P2): 재개 시 게임 시간 점프, 게임에 `pauseApp` 같은 생명주기 통지 없음(원작자 메모).

### 5.2 입력 (P0-4)

#### 5.2.1 KeyInputHub — 순수 Kotlin, Android 의존 없음, 단위 테스트 대상

- API: `press(source, key)`, `move(source, newKey)`, `release(source)`, `releaseAll()`
- source 예: `touch:<pointerId>`, `hw:<keyCode>`, `stick:LEFT`
- 키별 holder 집합을 두고 **0→1일 때만 `sink.down(key)`, 1→0일 때만 `sink.up(key)`**.
- **최소 누름 시간** `MIN_HOLD_MS = 40`(튜닝 대상): 손가락을 뗄 때 down 후 40ms 미만이면 up을 down+40ms로 미룬다. 미뤄진 up이 있는데 같은 키가 다시 눌리면 대기 중 up을 즉시 보낸 다음 down. `move`(슬라이드)는 지연 없이 `up(old)` → `down(new)` 순서.
- `releaseAll()`: 대기 중 up을 즉시 보내고 눌린 키를 전부 up, 상태 초기화. 이후 모르는 source의 `release`는 무시.
- **키 반복 생성 기능은 넣지 않는다**(코어 담당).
- 시간·지연은 주입형(`clock`, `scheduler`) → 테스트에서 가짜 시계로 진행.
- 메인 스레드 전용(Compose 포인터 처리와 `onKeyDown`이 모두 메인 스레드).
- 실제 sink = `WipiNative.nativeKeyDown/Up`.

#### 5.2.2 키패드 모델 — `KeypadModel.kt`, 순수 Kotlin

- 키 정의: `code`, `label`, `cluster`. **배열은 기존과 동일**(§3.4) — 당시 배열을 유지한다.
  - `DPAD` = UP, DOWN, LEFT, RIGHT, OK
  - `NUM` = 0~9, `*`, `#`
  - `FUNC` = SOFT_L, SOFT_R, CALL, CLR
  - HANGUP은 터치 키패드에 두지 않는다(게임이 종료로 처리할 수 있음). 하드웨어 F2 매핑은 유지.
- `layout(widthPx, heightPx, metrics) → List<KeyRect>`: **그리기와 히트테스트가 같은 사각형을 쓴다.**
- 히트테스트 규칙
  - 처음 누를 때: 키 사각형 안이면 그 키. 키 사이 간격이면 6dp 이내의 가장 가까운 키.
  - 움직일 때: `NUM`은 **같은 클러스터 안에서만** 슬라이드로 키가 바뀐다. 새 키 사각형 안쪽으로 6dp 이상 들어와야 바뀐다(히스테리시스). 간격이나 클러스터 밖이면 현재 키를 유지한다.
  - 움직일 때 — 방향키 조이스틱 [개정 2026-10-14, 사용자 결정. Phase 3 실기기 피드백: ◀↔▶ 사이 OK에 걸려 왔다갔다가 안 됨]
    - 방향키(▲▼◀▶)는 방향끼리만 바뀌고 OK로는 바뀌지 않는다. 현재 키 사각형 안이면 유지, 다른 방향키 사각형 안쪽 6dp면 그 키.
    - 그 밖은 OK 중심 기준: 같은 축 반전(◀↔▶, ▲↔▼)은 중심선 너머 12dp(데드존). 축 전환(좌우↔상하)은 OK 칸 위아래(좌우) 경계 + 6dp를 넘고 그 축이 6dp 이상 우세할 때만 — 좌우로 흔드는 중 위아래 흔들림으로 ▲▼가 눌리지 않게.
    - OK는 처음에 직접 눌렀을 때만 눌리고, 누른 채 움직여도 OK 유지(FUNC처럼 고정).
  - `FUNC` 키는 슬라이드하지 않는다(뗄 때까지 유지).
  - 키패드 영역 밖으로 32dp 넘게 벗어나면 release.

#### 5.2.3 TouchKeypad — Compose

- 키패드 루트에 `pointerInput` **하나**. `awaitPointerEventScope` 루프에서 `event.changes`를 전부 처리한다(멀티터치).
- 뗌 판정은 `change.pressed == false`로 한다(소비 여부 무시). 취소(ACTION_CANCEL)도 이 경로로 들어오는 것으로 본다 [추정]. 추가로 루프의 `finally`에서 이 키패드의 `touch:` source를 전부 release.
- 처리한 change는 consume.
- 누를 때와 슬라이드로 키가 바뀔 때 햅틱(`settings.keypadHaptics`가 켜져 있으면 `VIRTUAL_KEY`).
- 방향키 블록(1~3행, 약 152dp)에 `Modifier.systemGestureExclusion()` — 좌우 가장자리 뒤로가기 제스처 오작동 방지(세로 200dp 한도 이내).
- Composable은 `WipiNative`를 직접 부르지 않는다(허브를 주입) → Preview·테스트 가능. `WipiNative`는 `init`에서 네이티브 라이브러리를 로드하므로 Preview에서 참조하면 깨진다.

#### 5.2.4 하드웨어 키·스틱

- 기존 매핑(`MainActivity.kt:210-251`)을 유지하고 `hw:<keyCode>`, `stick:<방향>` source로 허브를 거친다 → 일시정지 때 함께 해제된다.
- `repeatCount > 0` 무시는 유지한다.

#### 5.2.5 KeyTest 판정표 (P0-4 완료 기준)

KeyTest 화면 상단: `P:눌림 R:반복 U:뗌 HELD:현재 눌린 키 수` (HELD가 0이면 초록, 아니면 노랑)

| 동작 | 기대 결과 |
|---|---|
| 21개 키를 하나씩 탭 | 키마다 P·U 한 쌍, 코드가 §3.3과 일치 |
| OK를 1초 누름 | P 1, R 8~10, U 1 |
| ▲에서 ▶로 미끄러짐(떼지 않음) | U(−1) 다음 P(−4) |
| ◀를 누른 채 OK 가운데 근처를 좌우로 흔들기 | ◀·▶가 번갈아 P/U, OK(−5)는 안 나옴 |
| OK를 누른 채 손가락 이동 | 다른 키로 바뀌지 않음 |
| 같은 키를 두 손가락으로 눌렀다가 하나씩 뗌 | P 1회, 두 번째 손가락을 뗄 때 U 1회 |
| 서로 다른 두 키(4와 5) 동시 | P 2회, HELD:2 |
| 5를 초당 10회 연타(10초) | P = U = 탭 수(누락 0) |
| 키를 누른 채 홈 / 알림창 내림 / 접기 | 복귀 후 HELD:0 |
| ◁·▷·📞·CLR을 누른 채 손가락 이동 | 다른 키로 바뀌지 않음 |
| 모든 손가락을 뗀 뒤 | 항상 HELD:0 |

### 5.3 레이아웃 (P0-5)

- 루트: `BoxWithConstraints` + `Modifier.windowInsetsPadding(WindowInsets.safeDrawing)`. 배경색은 시스템 바 뒤까지 칠한다.
- 계산은 순수 함수 `computeStackLayout(availWdp, availHdp, density, prefs)` (`EmulatorLayout.kt`):
  1. 키패드 폭 = min(가용 폭, 420dp), 키 높이 목표 `keyH = 48dp`
  2. 키패드 높이 = 7·keyH + 6·4dp + 2·8dp
  3. 게임 최대 높이 = 가용 높이 − 키패드 높이 → 3:4로 Fit
  4. 게임 높이가 가용 높이의 45% 미만이면 keyH를 40dp까지 1dp씩 줄인다(키는 40dp 미만 금지)
  5. 게임이 가로 폭에 막혀 세로 공간이 남으면, 남는 만큼만 keyH를 키운다(최대 56dp). 그래도 남으면 게임과 키패드 사이 여백으로 둔다
  6. 배치: 게임은 위(가로 가운데), 키패드는 아래 정렬(엄지 도달성)
  7. (P1) 정수배: px 기준 `s = floor(min(gameMaxWpx/240, gameMaxHpx/320))`
- 출력: 게임 사각형(dp·px), keyH, 키패드 사각형, 아이콘 배치, 로그용 한 줄 요약.
- ⏸·⚙ 아이콘: P0는 지금처럼 게임 우상단 오버레이(48dp 터치 영역, 안전 영역 안).
- 게임 그리기: 프레임 버전 state를 **게임 Canvas의 draw 단계에서만** 읽는다(`drawImage(..., filterQuality = FilterQuality.None)`) → 매 프레임 키패드가 재구성되지 않는다.
- 레이아웃 HUD: 디버그 빌드에서 ⚙ 길게 누르기(또는 숨김 설정)로 창 dp·density·인셋·keyH·게임 크기·키 사각형을 표시하고, 레이아웃이 바뀔 때마다 `Log.i("FoldRetro", 요약)` 1회.

**Fold5 커버 예상치** [추정 — Phase 2 실측: 제스처 내비 인셋 위 32 / 아래 15dp → 가용 835dp, keyH 48, 게임 344×459dp(가로 100%). 상세 `DEVICE_METRICS.md`]

| 조건 | 가용 높이 | 키 높이 | 게임(dp) | 가로 채움 |
|---|---|---|---|---|
| 제스처 내비(상 28 / 하 24dp 가정) | 830dp | 48dp | 340×454 | 약 99% |
| 3버튼 내비(하 48dp 가정) | 806dp | 48dp | 322×430 | 약 94% |
| (P1) 정수 3배 | 830dp | 56dp(상한) | 274×366 (720×960px) | 80% |
| 원본 앱(인셋 미처리) | 882dp(바 뒤까지) | 44dp | 344×459 | 100%, 하단 키 행이 내비 영역과 겹침 |

- 메인 화면(세로, 약 690×829dp): 키패드 폭 420dp로 가운데, 게임 약 301×401dp. 쓸 수는 있지만 작다 → P1 SPLIT.
- 메인 화면 가로(Android 16이 세로 고정을 무시): 키 높이를 줄여 게임을 지킨다. 크래시·겹침만 없으면 P0 통과, 본격 대응은 P1.

### 5.4 설정 (P1)

`layoutMode`(AUTO/STACK/SPLIT), `keypadSize`(S/M/L), `integerScale`, `immersive`, `iconPlacement`. 기존 `Settings` 클래스(SharedPreferences)에 추가하고 문자열은 en/ko 모두 넣는다.

---

## 6. 포크·빌드 전략 (P0-1, P0-2)

### 6.1 포크 원칙

- GitHub에서 포크 → 기준 커밋 `43a6098`에서 `fold-retro` 브랜치. `upstream` 원격 유지.
- **새 파일 위주, 기존 파일은 최소 수정** → 업스트림 갱신(대부분 wie rev 업데이트)을 rebase하기 쉽게.
- **applicationId만 변경**: `com.<본인ID>.foldretro`. `namespace`, Kotlin 패키지, `WipiNative`, proguard 규칙은 유지한다. JNI 심볼(`Java_com_parkjeongseop_wipi_WipiNative_*`)이 클래스의 패키지에 묶여 있어서, 패키지를 바꾸면 Rust를 다시 빌드해야 한다.
- 앱 이름: en "Fold Retro" / ko "폴드 레트로"(원본 "WIPI 에뮬"과 구분). 아이콘 교체는 P1.
- versionName: `0.3.1-fold.0.1` 형식.
- 원본 앱과 저장 공간이 분리되므로 게임은 다시 임포트한다.

### 6.2 에이전트 지시 파일

- 저장소 루트에 `AGENTS.md`(포크 규칙·명령) → Codex가 자동으로 읽는다.
- 원작자 `CLAUDE.md`는 고치지 않는다(머지 충돌 방지). Claude Code는 `CLAUDE.md`가 있으면 기본적으로 `AGENTS.md`를 읽지 않으므로, **`.claude/CLAUDE.md` 첫 줄에 `@../AGENTS.md`를 넣어 커밋**한다. import 상대경로는 import하는 파일 기준이라 `@AGENTS.md`로 쓰면 없는 `.claude/AGENTS.md`를 찾는다 [확인: Claude Code 메모리 문서]. gitignore되는 `CLAUDE.local.md`와 달리 클라우드 세션에도 올라간다. 첫 세션에서 `/context`로 메모리 파일에 AGENTS.md가 잡히는지 확인하고, Phase 프롬프트도 매번 "AGENTS.md를 먼저 읽어"로 시작한다.
- 이 명세와 지시서는 `docs/fold-retro/`에 둔다.

### 6.3 빌드 절차 (OS 무관)

필요 도구: JDK 17+, Android SDK(platform 36, build-tools 35.0.0), NDK 27.1.12297006, Rust stable ≥ 1.88 + `rustup target add aarch64-linux-android`(AVD용으로 `x86_64-linux-android`), adb.

1. `android/`에 Gradle wrapper **8.11.1**을 만들어 커밋한다(`gradlew`, `gradlew.bat`, `gradle/wrapper/*`).
2. Rust는 `rust/.cargo/config.toml`을 **고치지 않고** 환경변수로 덮어쓴다. Cargo에서는 환경변수가 설정 파일보다 우선하고, 설정 파일의 `[env]`는 이미 있는 환경변수를 덮지 않는다. [확인: 작은 크레이트로 실험 — 환경변수 링커가 설정 파일의 없는 경로를 이기고, 설정의 16KB `rustflags`는 그대로 적용됨]

   | 변수 | 값 (`HOST` = darwin-x86_64 / linux-x86_64 / windows-x86_64) |
   |---|---|
   | `CARGO_TARGET_AARCH64_LINUX_ANDROID_LINKER` | `$NDK/toolchains/llvm/prebuilt/$HOST/bin/aarch64-linux-android26-clang` (Windows는 `.cmd`) |
   | `CC_aarch64_linux_android` | 위와 같음 |
   | `AR_aarch64_linux_android` | `$NDK/toolchains/llvm/prebuilt/$HOST/bin/llvm-ar` (Windows는 `.exe`) |
   | x86_64 타깃 | 같은 패턴(`X86_64`, `x86_64-linux-android26-clang`) |

   - `RUSTFLAGS`, `CARGO_ENCODED_RUSTFLAGS`는 설정하지 않는다(설정하면 16KB 정렬 플래그가 무시됨).
3. 스크립트 `scripts/build-rust-android.sh`(macOS·Linux) 또는 `.ps1`(Windows PowerShell): 빌드 → `android/app/src/main/jniLibs/<abi>/libwipi_android.so` 복사 → `llvm-readelf -lW`로 LOAD 세그먼트 정렬이 `0x4000`인지 출력.
4. `app/build.gradle.kts`에 `checkJniLibs` 태스크: arm64 .so가 없으면 APK 빌드 실패. jniLibs 병합 태스크(`merge*JniLibFolders`)가 의존하게 해서 단위 테스트는 .so 없이도 돈다.
5. 개발 중엔 `CARGO_PROFILE_RELEASE_LTO=off`로 빌드 시간을 줄여도 된다(최종 APK는 기본 LTO).
6. Rust를 고치지 않는 한 .so는 wie rev당 한 번만 빌드하면 된다.
7. **CI**: `.github/workflows/android-debug.yml`(실행 가이드 부록 D)이 푸시마다 NDK 설치 → .so → 단위 테스트 → 디버그 APK → 드래프트 릴리스 `debug-<번호>`. 공개 포크라 표준 러너가 무료다. 워크플로 파일은 설정 단계에서 한 번 커밋하고, 구현 세션은 고치지 않는다(변경이 필요하면 변경안만 보고).
8. **고정 디버그 서명**: `android/app/fold-retro-debug.keystore`를 한 번 만들어 커밋하고 `signingConfigs.debug`에 지정(실행 가이드 부록 E). 공개 저장소의 키이므로 개인 테스트 전용.

---

## 7. 테스트 전략

### 7.1 누가 무엇을 실행하나

- **클라우드 세션**(기기 없음): `./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`, 푸시·PR(GraphQL이 막혀 REST `gh api`로 — AGENTS.md), `gh run watch`로 CI 확인, 실패 원인은 `gh run view`의 어노테이션으로 읽는다. 기기 항목은 "사용자 확인 목록"으로 남긴다.
- **CI**: .so 빌드, 단위 테스트, 디버그 APK, 드래프트 릴리스.
- **사용자**: Fold5에 APK 설치, §5.2.5·§7.4 수동 테스트, 스크린샷 전달.
- **로컬 에이전트**(크레딧 소진 시 등): 아래 명령을 직접 실행한다.

로컬에서 쓰는 명령

- `./gradlew :app:testDebugUnitTest` — KeyInputHub, KeypadModel, EmulatorLayout
- `./gradlew :app:assembleDebug`
- (선택) 헤드리스: `cargo run --release -p wipi_android --bin headless -- <게임> <출력prefix> 6 "1:LEFT,2:RIGHT"` — 호스트에서 코어 동작 확인. Linux는 `libasound2-dev` 필요.
- 기기가 연결돼 있으면 adb로: 설치·실행, 게임 주입(`run-as`, 원작자 `CLAUDE.md` 방식에서 패키지명만 교체), 스크린샷(`adb exec-out screencap -p > out.png`), 로그(`adb logcat -s FoldRetro`), 홈/복귀(`input keyevent KEYCODE_HOME`), 백그라운드 프로세스 종료(`am kill`).
- adb로 탭할 때는 `input swipe x y x y 120`을 쓴다(0ms 탭은 게임이 못 잡음). 길게 누르기는 `input swipe x y x y 1000`.

### 7.2 단위 테스트 필수 케이스

KeyInputHub
1. 단일 press/release → down, up
2. 두 source가 같은 키 → down 1회, 둘 다 뗀 뒤에만 up
3. move(UP→RIGHT) → up(UP), down(RIGHT) 순서
4. 40ms 미만 탭 → up이 down+40ms에 발생(가짜 시계)
5. 지연 up 대기 중 같은 키 재입력 → up 즉시, 그다음 down
6. `releaseAll()` → 대기·눌림 전부 up, 이후 모르는 source release는 무동작
7. `hw:` + `touch:`가 같은 키 → 참조 카운트 정상
8. down/up 호출 순서가 항상 키별로 교대(down 두 번 연속 금지)

KeypadModel
- 각 키 중심점 → 해당 키, 간격 안의 점 → 가까운 키(6dp 이내), 그보다 멀면 없음
- 슬라이드 히스테리시스(경계 근처 떨림 무시), 클러스터 간 이동 금지, FUNC 고정
- 방향키 조이스틱: ◀↔▶ 흔들기에 OK 없음, 위아래 흔들림 무시, 축 전환은 OK 칸 밖에서만, OK 고정, 데드존 떨림 무시

EmulatorLayout
- Fold5 커버 제스처/3버튼/화면 확대, Fold5 메인 세로·가로, 일반 폰(411×914dp), 10:16 와이드 커버(약 454×717dp)
- 단정: 크기 음수 없음, 게임 비율 오차 < 0.5%, keyH ≥ 40dp, 모든 사각형이 가용 영역 안, 키패드 하단이 인셋 위

### 7.3 KeyTest 실기기 판정 — §5.2.5 표

### 7.4 Fold5 실기기 매트릭스(사용자 수행)

테스트 조합: 내비게이션(제스처 / 3버튼) × "커버 화면에서 앱 계속 사용"(켬 / 끔)

| ID | 시나리오 | 통과 기준 |
|---|---|---|
| T1 | 커버에서 실행, demo.jar·keytest.jar 임포트 | 라이브러리 표시, 실행됨 |
| T2 | KeyTest 판정표 전 항목 | §5.2.5 |
| T3 | 하트 캐치를 방향키와 4/6으로 플레이 | 조작 지연·누락 체감 없음 |
| T4 | 커버에서 플레이 중 펼치기 | 게임 계속, 라이브러리로 안 튕김, 키 고착 없음 |
| T5 | 메인에서 플레이 중 접기(계속 사용 켬/끔) | 켬: 커버에서 계속 / 끔: 백그라운드, 소리 멈춤, 다시 열면 일시정지 오버레이 → 탭으로 재개 |
| T6 | 홈 → 복귀, 최근 앱 전환, 알림창 내리기(키 누른 채) | 일시정지·HELD:0 |
| T7 | 방향키를 누른 채 좌우 가장자리로 문지르기 / 뒤로가기 | 방향키 위에선 뒤로가기 미발동, 뒤로가기 1회 = 일시정지, 2회 = 종료 |
| T8 | 화면 꺼짐·잠금 후 복귀 | 크래시 없음, 일시정지 |
| T9 | (선택) 블루투스 게임패드·키보드 연결/해제 | 세션 유지 |
| T10 | 메인 화면 세로·가로 회전 | 크래시·겹침 없음 |
| T11 | 백그라운드에서 `adb shell am kill <appId>` 후 재실행 | 라이브러리부터, 크래시 없음, 게임 세이브 유지 |
| T12 | 회귀: 임포트·삭제·사운드/진동 설정·고지 화면·게임패드 | 원본과 동일 |

---

## 8. MVP 승인 기준

- [ ] 깨끗한 체크아웃에서 `docs/fold-retro/BUILD.md`대로 APK가 만들어진다(실제 로그 첨부).
- [ ] CI(android-debug)가 성공하고, 드래프트 릴리스 APK가 Fold5에 덮어쓰기 설치된다(게임·세이브 유지).
- [ ] .so 16KB 정렬 확인 결과가 기록돼 있다.
- [ ] Play 원본과 동시 설치되고, 앱 이름이 구분된다.
- [ ] `testDebugUnitTest`가 통과하고, §7.2 케이스가 모두 있다.
- [ ] KeyTest 판정표 전 항목 통과(실기기).
- [ ] T4·T5에서 게임이 종료되지 않는다.
- [ ] Fold5 커버에서 게임 비율이 정확하고, 키가 내비게이션·제스처 영역과 겹치지 않는다(스크린샷).
- [ ] 일시정지·백그라운드에서 BGM이 멈춘다.
- [ ] 포크가 `rust/`를 바꾸지 않았다(`git diff --stat <기준> -- rust/`가 비어 있음. 기준은 처음엔 `43a6098`, 업스트림으로 rebase한 뒤엔 `git merge-base HEAD upstream/main`).
- [ ] T12 회귀 없음, 알려진 제한이 README에 적혀 있다.

---

## 9. 라이선스·저작권

| 대상 | 라이선스 | 상태 |
|---|---|---|
| WIPI-Emulator 코드 | MIT, © 2026 Park Jeongseop | [확인] `LICENSE` |
| wie | MIT, © 2020 Inseok Lee + 출처 표기 권장 문구("This project is based on wie …") | [확인] |
| RustJava, smaf | MIT (Inseok Lee) | [확인] 크레이트 메타데이터 |
| .so에 들어가는 Rust 크레이트 164개 | 전부 허용형: MIT, Apache-2.0, BSD-3, Zlib, Unicode-3.0, 0BSD, Unlicense. **GPL·LGPL·MPL 없음** | [확인] `cargo metadata`(aarch64-linux-android, 일반 의존성, proc-macro 제외) |
| neodgm.ttf (Neo둥근모 1.530) | SIL OFL 1.1. .so에 `include_bytes!`로 내장 | [확인] 폰트 메타데이터. **앱 고지 목록에 없음** |
| GeneralUser GS 2.0.3 (assets, 약 32MB) | 자체 라이선스 v2.0: 소프트웨어에 포함·수정 허용. 저자가 일부 샘플의 출처를 100% 보증하지 못한다고 명시 | [확인] sf2 내장 정보 |
| Apache-2.0 단독 크레이트 | cpal, ab_glyph, ab_glyph_rasterizer, owned_ttf_parser, zopfli | [확인] 앱 고지엔 cpal만 링크 수준 |

**의무와 조치**
- 개인 기기에서만 쓰면(배포 없음) 고지 의무가 생길 일은 사실상 없다. **APK를 남에게 주거나 GitHub 릴리스로 올리는 순간** MIT·Apache-2.0·OFL·Unicode·BSD 고지가 필요하다 → P0-8(neodgm) + 배포 전 P1(전체 고지).
- 원본 앱의 이름("WIPI 에뮬")·아이콘을 쓰지 않는다(출시 앱과 혼동). Samsung·Galaxy·통신사·제조사 상표와 로고를 앱 이름·아이콘에 쓰지 않는다. README에 "Galaxy Z Fold5에서 테스트" 같은 사실 서술은 괜찮다.
- 게임 파일·상용 게임 스크린샷·영상은 커밋하지 않는다. 다운로드 링크를 두지 않는다. DRM 우회 코드를 넣지 않는다. README·스크린샷에는 `demo/`의 자체 데모와 KeyTest만 쓴다.
- 게임 파일 보유·백업의 적법성 판단은 이 명세의 범위 밖이다(법률 자문 아님).

---

## 10. 기술 리스크와 대응

| 리스크 | 영향 | 대응 |
|---|---|---|
| 다른 PC에서 Rust 빌드 실패(경로·NDK·툴체인) | 착수 차단 | 환경변수 오버라이드 스크립트, 실패 2회면 중단·보고 |
| .so 누락 APK | 실행 즉시 크래시 | `checkJniLibs` 태스크 |
| 접기·펴기 재생성으로 게임 종료 | 핵심 경험 붕괴 | §5.1 (configChanges + 세션 컨트롤러 + onDestroy 가드) |
| 키 고착·누락·중복 반복 | 플레이 불가 | 허브 참조 카운트, 최소 누름 시간, releaseAll 트리거, UI 반복 금지 |
| 인셋 겹침 | 하단 키 오작동 | safeDrawing, 실측 로그 |
| 재개 시 게임 시간 점프 | 리듬 게임 판정 어긋남 | 알려진 제한으로 명시, P2(Rust) |
| 업스트림의 빠른 변경 | 충돌 | 커밋 고정, 새 파일 위주, 주기적 rebase |
| 에이전트의 추측성 "완료" | 품질 저하 | 단계별 실행 로그·테스트 결과 필수, 사용자 실기기 체크 분리 |
| 빌드마다 디버그 서명이 다름 | 폰 업데이트 실패, 재설치 시 게임·세이브 소실 | 고정 디버그 키(§6.3-8) |
| 클라우드 기본 네트워크에 `dl.google.com` 없음 | Gradle·SDK 다운로드 실패 | 환경 네트워크 Custom + 허용 도메인 추가(실행 가이드 §1) |
| 클라우드 크레딧 소진·만료(11/5) | 구현 중단 | Phase별 잔액 기록, Sonnet 비중 조절, 로컬 경로로 전환(실행 가이드 §6) |

---

## 11. 의도적으로 하지 않는 것

- 에뮬레이터 코어(`rust/`, wie) 수정 — P2 항목은 별도 승인 후.
- SDK·AGP·Kotlin·Compose 버전 업그레이드.
- FoldingFeature·삼성 전용 API·모델별 해상도 상수 의존.
- 게임 다운로드·ROM 공유·DRM 우회, 접근성 서비스로 다른 앱 입력 가로채기.
- 모든 WIPI/SKVM/J2ME 게임의 호환 약속.
- 처음부터 공개 출시·수익화. 먼저 개인 Fold5에서 재미있게 돌아가는 데모.

---

## 12. 아직 결정하지 않은 사항

- applicationId에 쓸 본인 ID, 최종 앱 이름·아이콘.
- 개발 PC OS(Windows / macOS) → 빌드 스크립트 종류(.ps1 / .sh).
- 메인 화면 정책: 그대로 두고 P1에서 SPLIT 추가(기본안) vs `android:appCategory="game"` 선언으로 대화면 방향 무시 정책 예외 받기(세로 고정 유지, 다만 앱 분류가 '게임'이 됨).
- `MIN_HOLD_MS`(40), 히스테리시스(6dp) — KeyTest로 튜닝.
- 포크 공개 유지(기본안: 공개 — Actions 무료, 코드는 MIT) vs 비공개 복제본(Actions 무료 분량 제한). APK는 드래프트 릴리스라 공개되지 않는다.
- 업스트림 기여 여부: 세션 종료·인셋·음소거 수정은 원본 사용자 모두에게 이득이다. 원작자는 "기능·흐름은 iOS가 명세"라는 원칙이 있어 Android 전용 UI는 받아들여질지 불확실하다.

---

## 부록 A. 교차 검토 상세 (요청 7항목)

### A1. 기존 오픈소스 선정과 포크 전략 — 판정: 선정 적절, 범위는 축소

- [확인] WIPI-Emulator는 Kotlin/Compose + Rust 공통 코어 + JNI 구조이고, Play·App Store에 출시돼 있으며, 2026년 9~10월에 20건 넘게 커밋될 만큼 활발하다. 키패드·햅틱·게임패드·라이브러리·일시정지가 이미 있다. 코어는 순수 인터프리터(wie + RustJava)다.
- [확인] **"위 게임/아래 키패드"는 이미 구현돼 있다.** 그래서 이 프로젝트의 실제 작업은 "새 화면 구축"이 아니라 "기존 화면의 결함 수정 + 커버 화면 최적화"다.
- 대안 비교
  - 업스트림 PR: 세션 종료·인셋·음소거는 원본 앱 사용자 모두의 버그라 PR 가치가 크다. 다만 수용 여부와 시점은 원작자에게 달렸다.
  - 포크(권장): 개인 데모라는 목표에 맞고 통제할 수 있다. applicationId만 바꾸고 diff를 작게 유지한다.
  - 웹판(wie-web) 등: 커버 화면 키패드 경험에 불리하다.
- 수정안: Phase 0 전에 **Play 스토어 원본을 Fold5 커버에서 10분 써 보고** 체감 문제를 적어 둔다(비용 0, 기대치 보정).

### A2. Android/Compose/Rust JNI 빌드 현실성 — 판정: 가능, 단 원본 그대로는 다른 PC에서 안 됨

- [확인] 이번 검토에서 Linux 호스트에서 Rust 1.97로 `wipi_core` + 헤드리스 하니스를 빌드했고(약 5분), `demo.jar`·`demo_wipi.zip`·KeyTest 프로토타입이 실행돼 프레임과 키 입력이 확인됐다.
- [확인] 막히는 지점: Gradle wrapper 없음, `.cargo/config.toml`의 macOS 절대경로, .so 수동 복사(없으면 실행 즉시 크래시), `RUSTFLAGS`로 16KB 플래그가 사라지는 함정, `CLAUDE.md`의 낡은 rev·path 의존성 서술.
- [미검증] Android 타깃(.so) 크로스 빌드와 Gradle 빌드는 이 검토 환경에 NDK·SDK가 없어 실행하지 못했다. → Phase 0의 첫 산출물.
- 수정안: §6.3.

### A3. 방향키·숫자 키패드 멀티터치와 길게 누르기 — 판정: v0.1 방향은 맞고, 실제 코어 동작에 맞춰 구체화

- [확인] 코어가 키별 눌림 집합 + 100ms `Keyrepeat`를 가진다. 그러니 "길게 누르기"는 UI가 할 일이 없다(누름을 유지하기만 하면 됨). 여러 키 동시 누름도 코어가 지원한다.
- v0.1이 놓친 것: ① 같은 키 다중 포인터(코어 멱등성 때문에 조기 해제), ② 슬라이드로 키 바꾸기(현재 불가), ③ 취소·포커스 상실·일시정지 때 전체 해제의 구체적 트리거, ④ 0ms 탭(같은 tick의 down/up), ⑤ 하드웨어 키와 터치의 공존, ⑥ CLR·CALL 누락.
- 수정안: §5.2(허브 + 단일 포인터 처리기 + 클러스터 슬라이드 + 최소 누름 시간), §5.2.5(판정표).

### A4. 커버 화면 적응과 접기·펴기 세션 보존 — 판정: 원칙은 맞지만 핵심 결함을 놓침

- [확인] 현재 Manifest·`onDestroy`·`remember` 구조에서는 **커버에서 펼치는 순간 Activity가 재생성되고 게임이 종료된다**(동작은 [추정], 근거는 코드와 Android 문서).
- [확인] Android 16 + targetSdk 36: 메인 화면에선 세로 고정이 무시된다. edge-to-edge는 해제할 수 없다. 에뮬 화면엔 인셋 처리가 없다.
- [확인] `FoldingFeature`는 필요 없다. 실측 창 크기만으로 충분하다(v0.1 원칙 유지).
- 수정안: §5.1, §5.3.

### A5. MVP에서 과하거나 빠진 것

- 과함: 새 화면 구축, FoldingFeature, 3모드 P0, 햅틱 신규 작업(이미 있음), 게임별 키 프리셋·투명도·테마(P1 → P2).
- 빠짐: 세션 종료 버그, 인셋, 일시정지 음소거, CLR·CALL, 재현 가능한 빌드, .so 누락 방지, applicationId 분리, 키 검증 자산(KeyTest), 단위 테스트, 레이아웃 HUD, neodgm 고지, 에이전트·사용자 검증 분리.
- 결과: §4.

### A6. 저작권·라이선스·오픈소스 위험 — 판정: 코드 라이선스 위험은 낮음

- 실질 위험은 ① 배포할 때의 고지 누락, ② 출시 앱과의 브랜딩 혼동, ③ 게임 자산 유출. → §9.

### A7. Codex 명세 수정 사항과 권장 순서

v0.1에서 고쳐야 할 기술적 오류
1. `EmulatorSessionAdapter`, `FoldRetroScreen` 같은 가칭 → 실제는 `WipiNative`(JNI) + `EmulatorScreen`. 새로 만들 것은 `EmulatorSession`, `KeyInputHub`, `KeypadModel`, `TouchKeypad`, `EmulatorLayout`.
2. "실제 키 이름은 소스 확인 후 매핑" → 확정: §3.3.
3. "Compose에서 중복 반복 생성 금지(엔진이 구현했다면)" → 확정: 코어가 구현했으니 UI 반복 금지.
4. "Preferences/DataStore 중 기존 방식" → 기존은 SharedPreferences(`Settings.kt`).
5. "Phase 1 Compose Preview" → Composable이 `WipiNative`를 직접 부르면 Preview가 깨진다. 콜백·허브 주입으로 분리.
6. "실기기 검증" 주체 → 에이전트와 사용자로 분리(§7).
7. "CLAUDE.md 빌드 절차" → 낡은 부분이 있다. `rust/Cargo.toml`과 이 명세 §6.3이 기준.

권장 순서(각 단계가 실기기로 확인 가능하도록)
1. **Phase 0**: 빌드 재현 + 포크 식별 + KeyTest + neodgm 고지
2. **Phase 1**: 세션 연속성 + 입력 허브 로직(기존 키패드를 허브 경유로만 바꿈) + 음소거
3. **Phase 2**: 인셋 + 레이아웃 계산기 + HUD(실측값 수집) + 프레임 그리기 분리
4. **Phase 3**: 터치 키패드 재작성(모델·히트테스트·슬라이드) + KeyTest 판정
5. **Phase 4**: 회귀·문서·APK

세션을 입력보다 먼저 하는 이유: 폴드 테스트 중에 게임이 계속 꺼지면 다른 어떤 검증도 할 수 없고, 허브의 `releaseAll`은 세션 일시정지에 붙어 있기 때문이다.

---

## 부록 B. 근거

**소스(커밋 43a6098)**
- `android/app/src/main/AndroidManifest.xml:16-17` — screenOrientation, configChanges
- `android/app/src/main/java/com/parkjeongseop/wipi/MainActivity.kt` — 105-108 onDestroy, 112 screen 상태, 210-258 하드웨어 키, 320-332 ON_PAUSE, 334-354 폴링, 356-375 레이아웃, 569-621 키패드
- `android/app/build.gradle.kts:17-29` — namespace, applicationId, SDK, ABI
- `android/app/src/main/java/com/parkjeongseop/wipi/Settings.kt:265-281` — 고지 목록(neodgm 없음)
- `rust/wipi_core/src/session.rs` — 54-80 키 파싱, 102-138 눌림 집합·반복, 107-110 일시정지, 175-209 start, 271-280 stop
- `rust/wipi_android/src/jni_bridge.rs` — JNI 표면, let-chains
- `rust/wipi_core/src/platform/audio.rs:73-160` — 일시정지와 무관한 오디오 스레드
- `rust/wipi_core/src/platform/mod.rs` — `now()` = SystemTime, neodgm 내장
- `rust/.cargo/config.toml:7-21` — macOS 절대경로, 16KB 플래그
- `rust/Cargo.toml:12-17` — wie git rev 38f7e04f
- `CLAUDE.md:12,23,46` — 낡은 rev·path 의존성 서술
- `demo/` — 자체 데모(MIT)

**이번 검토에서 직접 실행**
- `cargo metadata --locked --filter-platform aarch64-linux-android` → 크레이트 164개 라이선스 집계
- `cargo build --release --locked -p wipi_android --bin headless` (Rust 1.97, Linux) 성공
- 헤드리스: `demo.jar`, `demo_wipi.zip`, KeyTest 프로토타입 실행·프레임 확인
- Cargo 우선순위 실험: 설정 파일의 없는 링커 → 실패, `CARGO_TARGET_<triple>_LINKER` 지정 → 성공 + LOAD 정렬 0x4000, `RUSTFLAGS` 추가 → 0x1000

**문서**
- Android 16 동작 변경: https://developer.android.com/about/versions/16/behavior-changes-16
- 앱 방향·종횡비·크기 조정: https://developer.android.com/develop/adaptive-apps/guides/app-orientation-aspect-ratio-resizability
- 구성과 연속성: https://developer.android.com/guide/topics/large-screens/configuration-and-continuity
- 제스처 제외 200dp 한도(View 문서 미러): https://learn.microsoft.com/dotnet/api/android.views.view.systemgestureexclusionrects
- AGP 8.10 호환표: https://developer.android.com/build/releases/agp-8-10-0-release-notes
- Samsung 접기·펴기 앱 연속성: https://www.samsung.com/us/support/answer/ANS10003219
- Galaxy Z Fold5 사양: https://en.wikipedia.org/wiki/Samsung_Galaxy_Z_Fold_5
- Galaxy Z Fold8(10:16 커버): https://www.samsung.com/au/mobile/buying-guide/galaxy-z-fold8-features-specs/
- Rust 1.88(let-chains 안정화): https://blog.rust-lang.org/2025/06/26/Rust-1.88.0
- Codex AGENTS.md: https://learn.chatgpt.com/docs/agent-configuration/agents-md
- Claude Code 메모리(CLAUDE.md·AGENTS.md): https://code.claude.com/docs/en/memory
- Claude Code 클라우드 세션: https://code.claude.com/docs/en/claude-code-on-the-web
- 클라우드 환경(네트워크·설치 도구·설정 스크립트): https://code.claude.com/docs/en/cloud-environments
