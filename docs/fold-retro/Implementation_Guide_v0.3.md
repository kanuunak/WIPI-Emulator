# Fold Retro Phone — 실행 가이드 v0.3 (클라우드 세션 크레딧 우선)

- 기준 명세: `docs/fold-retro/Fold_Retro_Phone_MVP_v0.3.md` (이하 "명세")
- 에이전트 공통 규칙: 저장소 루트 `AGENTS.md`
- 이 문서는 v0.2의 `Codex_Instructions_v0.2.md`를 대체한다. 로컬(Codex·Claude Code) 경로는 §6에 남겼다.
- 작성일: 2026-10-13

---

## 0. 실행 방식과 역할

### 0.1 쓸 수 있는 자원

| 자원 | 내용 | 기한 |
|---|---|---|
| **클라우드 세션 크레딧** | $98 / $100 남음. 클라우드 세션에 자동 적용, 다 쓰거나 만료되면 플랜의 일반 사용량으로 넘어감(사용량 화면 기준) | **2026-11-05 16:59 KST** |
| 무료 한도 리셋 1회 | 사용량 화면의 "Reset for free" | 2026-10-23 |
| ChatGPT Plus(Codex) | GPT-6.1 Sol, GPT-6 Luna. 5시간당 추정 Sol 15~160회, Luna 350~3,000회 | 월 구독 |
| GitHub Actions | 공개 저장소는 표준 러너 무료 | — |

- 클라우드 세션 = claude.ai/code(웹), Claude 앱의 **Code** 탭, 데스크톱 앱에서 **Cloud** 선택, 터미널 `claude --cloud`로 연 Claude Code 세션. 지금 같은 claude.ai 일반 대화는 해당하지 않는 것으로 보인다.
- 크레딧이 남아 있는 동안 클라우드 세션은 Pro의 일반 한도를 쓰지 않는다. 그래서 **구현은 클라우드 세션에 몰고**, Pro 일반 한도와 리셋 1회는 비상용으로 남긴다.

### 0.2 역할 배치

| 역할 | 담당 | 쓰는 한도 |
|---|---|---|
| 구현(Phase 0~4), 실기기 피드백 반영 | Claude Code 클라우드 세션 | 클라우드 크레딧 |
| .so·APK 빌드, 단위 테스트 | GitHub Actions(`android-debug` 워크플로) | 무료(공개 포크) |
| PR 교차 리뷰 | Codex(GitHub 자동 코드 리뷰 또는 로컬 CLI) | ChatGPT Plus |
| 실기기 테스트, PR 머지 | 사용자(Fold5) | — |
| 예비 구현 | 크레딧 소진 시 로컬 Codex / Claude Code(§6) | Plus / Pro |

### 0.3 Phase별 모델·effort와 크레딧 예상

| Phase | 모델 | effort | 예상 크레딧 |
|---|---|---|---|
| 0 빌드·CI·포크 식별 | Sonnet 5.5 | medium | $3~6 |
| 1 세션 연속성·입력 허브 | Opus 5.5 | high | $8~14 |
| 2 인셋·레이아웃 | Opus 5.5 | medium | $6~10 |
| 3 터치 키패드 | Opus 5.5 | high | $8~14 |
| 4 회귀·문서 | Sonnet 5.5 | medium | $2~5 |
| 실기기 피드백 반영(여러 번) | Sonnet 5.5, 어려운 버그만 Opus | medium | $10~25 |
| **합계** | | | **약 $40~75** |

- 예상치는 API 정가(Opus 5.5 입력 $4 / 출력 $20, Sonnet 5.5 $2 / $10, 100만 토큰당)로 크레딧이 차감된다고 가정하고, Phase 하나에 캐시 입력 수백만 토큰 + 출력 10만~20만 토큰을 쓴다고 본 **추정**이다. 크레딧의 실제 차감 기준은 공개되지 않았다.
- **Phase 0이 끝나면 설정 > 사용량에서 잔액을 확인**한다. 한 Phase가 $15를 넘으면 Phase 2와 피드백 반영을 Sonnet으로 내린다.
- 세션 안에서 `/model opus` 또는 `/model sonnet`, `/effort high` 또는 `/effort medium`처럼 값을 붙여 입력한다(클라우드 세션은 선택창 대신 인자 방식).
- 피할 것: Fable(추가 과금 가능), effort `xhigh`·`max`, 이유 없는 병렬 세션.
- 일정: 11월 5일 전에 Phase 4까지 끝나도록 **주 2개 Phase** 페이스를 권장한다.

---

## 1. 클라우드 세션의 제약과 이 가이드의 대응

| 제약 [확인: Claude Code 문서] | 대응 |
|---|---|
| 기기·adb가 없다 | APK는 GitHub Actions가 만들고 **드래프트 릴리스**로 올린다. 사용자가 Fold5 브라우저로 내려받아 설치·테스트 |
| 기본 네트워크(Trusted)에 `dl.google.com`이 없다. Gradle의 `google()` 저장소와 Android SDK·NDK 다운로드가 이 주소를 쓴다 | 환경 네트워크를 **Custom**(기본 목록 포함 + `dl.google.com`)으로 |
| Android SDK가 설치돼 있지 않다(JDK 21·Gradle·Rust·gh는 있음) | 환경 **설정 스크립트**로 SDK 설치(NDK는 CI에서만) — 부록 C |
| 설정 스크립트는 약 5분 안에 끝나야 캐시된다 | SDK 플랫폼·빌드 도구만 설치(수 분 이내 예상) |
| gitignore된 `CLAUDE.local.md`는 클라우드에 올라가지 않는다 | `.claude/CLAUDE.md`(첫 줄 `@../AGENTS.md` — import 경로는 그 파일 기준)를 커밋 |
| 에이전트가 CI를 고쳐서 "통과"로 만들 수 있다 | 워크플로는 설정 단계에서 한 번만 커밋(부록 D). Phase 세션은 `.github/workflows/`를 고치지 않고 변경안만 보고 |
| GitHub 프록시가 GraphQL을 막는다: `gh pr`·`gh issue`·`gh release list`가 403 [확인: 2026-10-13] | PR 생성·릴리스 조회는 REST(`gh api`) — AGENTS.md "클라우드 세션" 절 |
| Actions 로그 다운로드(`gh run view --log-failed`)가 403 [확인: 2026-10-13] | CI 마지막 단계가 실패 로그를 어노테이션으로 남기고 `gh run view`로 읽는다 — 부록 D |
| 빌드마다 디버그 서명 키가 다르면 폰에서 업데이트 설치가 실패하고, 지우고 깔면 게임·세이브가 사라진다 | **고정 디버그 키**를 저장소에 커밋 — 부록 E |
| VM 자원 약 4 vCPU / 16GB RAM / 30GB 디스크, 명령은 기본 2분(최대 10분) 후 백그라운드 전환 | Gradle·단위 테스트에는 충분. 긴 빌드는 CI가 담당 |

---

## 2. 1회 준비 (사용자, 30~40분)

1. **원본 체험(10분)**: Play 스토어의 원본 앱("WIPI 에뮬")을 Fold5에 설치하고, 저장소 `demo/demo.jar`를 폰으로 받아 앱의 + 버튼으로 임포트한 뒤 커버 화면에서 플레이. **플레이 중 펼쳐서** 무슨 일이 생기는지 메모.
2. **포크**: GitHub에서 `ParkJeongseop/WIPI-Emulator`를 포크(공개 유지 — Actions 무료). 포크의 **Actions 탭에서 워크플로 실행을 켠다**(포크는 기본으로 꺼져 있음).
3. **브랜치·파일 준비(PC)**:
   ```sh
   git clone https://github.com/<내ID>/WIPI-Emulator.git && cd WIPI-Emulator
   git checkout -b fold-retro 43a6098
   mkdir -p docs/fold-retro .claude .github/workflows
   # AGENTS.md → 루트, 명세·이 가이드 → docs/fold-retro/
   # .github/workflows/android-debug.yml ← 부록 D 내용
   printf '@../AGENTS.md\n' > .claude/CLAUDE.md   # 경로는 .claude/ 기준이라 ../ 필요
   git add -A && git commit -m "fold-retro: spec, agent rules, CI" && git push -u origin fold-retro
   ```
   GitHub 포크 설정에서 **기본 브랜치를 `fold-retro`로** 바꾼다(클라우드 세션이 이 브랜치에서 시작하도록).
   첫 CI 실행은 아직 Gradle wrapper가 없어 실패한다. 정상이다(Phase 0에서 해결).
4. **GitHub 연결**: claude.ai/code에서 GitHub를 연결하고 Claude GitHub App을 포크 저장소에 설치.
5. **클라우드 환경 만들기**(이름 예: `fold-retro`)
   - 네트워크: **Custom**, 기본 허용 목록 포함을 켜고 허용 도메인에 `dl.google.com`, `dl-ssl.google.com` 추가
   - 환경 변수: `ANDROID_HOME=/opt/android-sdk`
   - 설정 스크립트: 부록 C
6. **(선택) Codex 자동 코드 리뷰**: ChatGPT의 Codex 설정에서 GitHub를 연결하고 포크의 PR 자동 리뷰를 켠다. 안 되면 §5의 로컬 리뷰.
7. **Fold5**: 폰 브라우저에서 GitHub에 로그인(드래프트 릴리스는 로그인한 소유자만 보임). 첫 APK 설치 때 브라우저의 "출처를 알 수 없는 앱 설치"를 허용.

---

## 3. 작업 루프 (Phase마다)

1. claude.ai/code 또는 Claude 앱 Code 탭 → 저장소: 포크, 브랜치: `fold-retro`, 환경: `fold-retro` → 새 세션.
2. §0.3 표대로 `/model …`, `/effort …` 입력.
3. **§4의 [공통] 블록 + 해당 Phase 블록**을 붙여 넣는다.
4. 세션이 구현 → 세션 안 단위 테스트 → 브랜치 푸시·PR → CI 성공 확인 → PR·CI·드래프트 릴리스(`debug-<번호>`)를 보고한다.
5. Codex가 PR을 리뷰한다(§5). 차단 이슈는 같은 클라우드 세션에 붙여 넣어 고친다.
6. Fold5에서 GitHub → Releases → 해당 드래프트의 `app-debug.apk`를 받아 설치한다(같은 키라 덮어쓰기 업데이트). 세션이 남긴 "사용자 확인 목록"을 수행한다.
7. 결과(통과/실패 + 스크린샷)를 **같은 세션**에 보낸다. 세션이 쉬다가 VM이 회수됐으면 다시 열면 된다(대화는 복원됨).
8. 통과하면 PR을 `fold-retro`에 머지 → 다음 Phase는 **새 세션**(컨텍스트를 작게 유지해 크레딧 절약).
9. 각 Phase가 끝나면 사용량 화면에서 크레딧 잔액을 기록한다.

---

## 4. Phase 프롬프트 (클라우드 세션용)

### [공통] — 매 Phase 첫 메시지 맨 앞에 붙인다

```text
[공통] 이 세션은 Claude Code 클라우드 세션이다. 먼저 AGENTS.md와 docs/fold-retro/Fold_Retro_Phone_MVP_v0.3.md를 읽고 AGENTS.md 규칙을 지켜.
- 이 VM에는 기기와 adb가 없다. 기기에서만 확인할 수 있는 항목은 "사용자 확인 목록"(명세 §7.4의 ID 사용)으로 남긴다.
- 세션 안 검증: cd android && ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin
- GitHub는 프록시 경유라 GraphQL(gh pr, gh issue, gh release list)이 막힌다. PR 생성과 릴리스 조회는 AGENTS.md "클라우드 세션에서 일할 때" 절의 REST 명령(gh api)으로 한다.
- 끝나면: 작업 브랜치를 푸시하고 fold-retro 대상 PR을 연다 → `gh run watch`로 android-debug 워크플로가 성공할 때까지 확인하고, 실패하면 `gh run view <id>`의 "CI failure log" 어노테이션을 읽고 고친다 → PR 링크, CI 실행 링크, 드래프트 릴리스 이름(debug-<번호>)을 보고한다.
- 같은 오류로 두 번 막히면 멈추고 보고한다. 추측으로 "완료"라고 쓰지 않는다.
```

### Phase 0 — 빌드·CI·포크 식별·검증 자산 (Sonnet 5.5 / medium)

```text
[Phase 0] 명세 §3, §6, §7.1, §9를 읽어. 이번 단계는 앱 동작을 바꾸지 않는다.

1. 환경 점검: java -version, echo $ANDROID_HOME, ls $ANDROID_HOME/platforms $ANDROID_HOME/build-tools, rustc --version, AGENTS.md의 gh api 명령으로 저장소 접근 확인(gh auth status의 "invalid"는 프록시 때문이라 정상), curl -sI https://dl.google.com/dl/android/maven2/ | head -1 (Custom 네트워크 확인). 문제가 있으면 원인과 내가 환경 설정에서 바꿀 것을 알려 줘.
2. Gradle wrapper 8.11.1: 빈 임시 폴더에서 `gradle wrapper --gradle-version 8.11.1`을 실행해 만든 gradlew, gradlew.bat, gradle/wrapper/*를 android/로 복사(설치된 Gradle 버전과 상관없이 동작). gradlew 실행 권한 유지.
3. scripts/build-rust-android.sh와 scripts/build-rust-android.ps1: 가이드 부록 B 참고 구현. rust/.cargo/config.toml 수정 금지.
4. android/app/build.gradle.kts:
   - applicationId = "com.<ID>.foldretro" (ID는 내게 물어봐), versionName = "0.3.1-fold.0.1". namespace와 Kotlin 패키지는 그대로.
   - checkJniLibs 태스크: src/main/jniLibs/arm64-v8a/libwipi_android.so가 없으면 "scripts/build-rust-android 먼저 실행" 메시지로 실패. merge*JniLibFolders 태스크가 의존(단위 테스트는 .so 없이 실행).
   - testImplementation("junit:junit:4.13.2")
   - 고정 디버그 서명: 가이드 부록 E대로 android/app/fold-retro-debug.keystore를 만들고 signingConfigs의 debug에 지정.
5. app_name: values "Fold Retro", values-ko "폴드 레트로".
6. demo/stubs/javax/microedition/lcdui/Canvas.java에 `protected void keyRepeated(int keyCode) {}` 추가. demo/keytest/에 가이드 부록 A의 KeyTest.java와 build.sh를 넣고 keytest.jar 빌드·커밋.
7. Settings.kt 오픈소스 고지에 Neo둥근모 항목: 이름 "NeoDunggeunmo", 역할 license_role_font(en "In-game text font", ko "게임 화면 글꼴"), 본문은 저작권 표기(원본 글꼴은 1990년대 김중태 님이 퍼블릭 도메인으로 공개, 변환·추가 디자인 Dalgona) + SIL Open Font License 1.1 원문 전체.
8. 세션 안 검증 명령 실행. (선택) 시간이 남으면 sdkmanager로 "ndk;27.1.12297006"을 설치해 build-rust-android.sh를 한 번 돌려 LOAD 정렬 0x4000 확인.
9. docs/fold-retro/BUILD.md: 클라우드 세션·CI·로컬(Windows/macOS) 빌드 방법, 실제 버전과 소요 시간.
10. [공통]의 마무리 절차(푸시·PR·CI·드래프트 릴리스).

사용자 확인 목록: (a) 드래프트 릴리스 APK를 Fold5에 설치 — 원본 앱과 동시 설치, 이름 "폴드 레트로" (b) GitHub에서 demo/demo.jar, demo/keytest/keytest.jar를 폰으로 받아 앱의 +로 임포트·실행 (c) KeyTest에서 아무 키 1초 누름 → R이 8~10인지.
```

### Phase 1 — 세션 연속성 + 입력 허브 로직 (Opus 5.5 / high)

```text
[Phase 1] 명세 §3.2~3.4, §5.1, §5.2.1, §5.2.4, §7.2(KeyInputHub)를 읽어.

1. KeyInputHub.kt (순수 Kotlin, android.* import 금지): 명세 §5.2.1 그대로. interface KeySink { fun down(key: String); fun up(key: String) }. 시계·지연 실행은 생성자 주입. MIN_HOLD_MS = 40.
2. app/src/test/java/com/parkjeongseop/wipi/KeyInputHubTest.kt: 명세 §7.2 KeyInputHub 8개 케이스. 가짜 sink가 순서를 기록하고 정확한 순서를 단정.
3. EmulatorSession.kt (Kotlin object): 명세 §5.1 설계 2번. 실제 sink(WipiNative)와 메인 Looper Handler 지연 실행 연결. 마지막 프레임 Bitmap과 frameVersion 보관.
4. AndroidManifest.xml configChanges를 명세 §5.1 설계 1번 값으로.
5. MainActivity.kt 최소 수정: 화면 상태를 EmulatorSession.activeGame에서 파생 / paused·프레임은 EmulatorSession / onDestroy는 isFinishing && !isChangingConfigurations일 때만 stop() / ON_PAUSE → pause(SYSTEM), ⏸·뒤로가기 → pause(USER), 오버레이 탭 → resume() / 볼륨 변경은 EmulatorSession.applyVolume() 경유 / 하드웨어 키·스틱은 "hw:<keyCode>", "stick:<방향>" source로 허브 경유(repeatCount > 0 무시 유지) / 기존 KeyButton도 허브 경유("touchkey:<key>"), 키패드 모양은 그대로 / LocalWindowInfo.current.isWindowFocused가 false면 hub.releaseAll() / Composable 안의 WipiNative 직접 호출 제거.
6. 세션 안 검증 + [공통] 마무리.

사용자 확인 목록: 명세 §7.4 T4(커버에서 플레이 중 펼치기), T5(접기, 커버 화면 앱 계속 사용 켬/끔), T6(홈·알림창·최근 앱), T8(화면 꺼짐), 그리고 일시정지 중 소리가 멈추는지.
```

### Phase 2 — 인셋 · 세로 스택 레이아웃 · 실측 (Opus 5.5 / medium)

```text
[Phase 2] 명세 §2, §3.4, §5.3, §7.2(EmulatorLayout)를 읽어.

1. EmulatorLayout.kt (순수 Kotlin): computeStackLayout + 결과 data class. 명세 §5.3 알고리즘 1~6.
2. EmulatorLayoutTest.kt: 명세 §7.2 레이아웃 케이스 + §5.3 Fold5 커버 추정치 표와 ±1dp 일치(표의 인셋 가정값 입력).
3. EmulatorScreen: BoxWithConstraints + Modifier.windowInsetsPadding(WindowInsets.safeDrawing) → 계산 결과대로 배치. 기존 KeyButton 높이 = keyH. ⏸·⚙는 안전 영역 안 게임 우상단 오버레이.
4. 게임 그리기를 Canvas draw 단계로(frameVersion은 draw에서만 읽기, FilterQuality.None).
5. 레이아웃 HUD: ⚙ 길게 누르기로 켜고 끈다(이번엔 릴리스·디버그 모두, 기본 꺼짐). 화면에 창 dp, density, 인셋(statusBars, navigationBars, displayCutout, systemGestures, mandatorySystemGestures), keyH, 게임 dp·px·배율을 크게 표시 — 사용자가 스크린샷으로 실측값을 전달할 수 있게. Log.i("FoldRetro", 요약)도 남긴다.
6. 세션 안 검증 + [공통] 마무리.

사용자 확인 목록: 커버·메인 세로·메인 가로 각각 HUD를 켠 스크린샷(내비게이션 제스처/3버튼 둘 다 가능하면), 하단 키 행과 내비게이션 영역이 겹치지 않는지, 메인 가로에서 크래시·겹침이 없는지. 스크린샷을 받으면 docs/fold-retro/DEVICE_METRICS.md에 기록하고 명세 §5.3 추정치와 비교한다(다음 세션 또는 이 세션의 후속 작업).
```

### Phase 3 — 터치 키패드 재작성 (Opus 5.5 / high)

```text
[Phase 3] 명세 §3.3, §5.2.2~5.2.5, §7.2(KeypadModel)를 읽어.

1. KeypadModel.kt (순수 Kotlin): 키 정의(기존 배열 유지, HANGUP 없음, cluster DPAD/NUM/FUNC), layout(키 사각형, Phase 2의 keyH·폭), hitTestInitial(간격은 6dp 이내 최근접), hitTestMove(같은 클러스터만, 히스테리시스 6dp, FUNC 고정), 영역 밖 32dp 이탈 판정.
2. KeypadModelTest.kt: 명세 §7.2 KeypadModel 케이스.
3. TouchKeypad.kt: 루트 pointerInput 하나, awaitPointerEventScope 루프에서 event.changes 전부 처리 / 뗌은 change.pressed == false(소비 여부 무시) / 처리한 change는 consume / 루프 finally에서 touch: source 전부 release / 눌림 표시, 햅틱(누름·슬라이드), 방향키 블록에 Modifier.systemGestureExclusion() / 허브 주입(WipiNative 직접 호출 금지) / 가짜 허브를 쓰는 @Preview 1개.
4. MainActivity의 Keypad/KeyButton을 TouchKeypad로 교체, 안 쓰는 코드 삭제.
5. 세션 안 검증 + [공통] 마무리.

사용자 확인 목록: 명세 §5.2.5 KeyTest 판정표 전체, §7.4 T2, T3, T7.
```

### Phase 4 — 회귀 · 문서 · 마감 (Sonnet 5.5 / medium)

```text
[Phase 4] 명세 §7.4, §8, §9를 읽어.

1. README_FORK.md: 포크 목적, 원본 앱·wie 크레딧(wie LICENSE의 권장 문구 "This project is based on wie (https://github.com/dlunch/wie), created by Inseok Lee." 포함), 설치 방법(드래프트 릴리스 APK), 빌드 방법(BUILD.md 링크), 알려진 제한(재개 시 게임 시간 점프, 게임 호환성은 코어에 달림, 일시정지 중에도 신스 스레드 동작), 테스트한 기기와 OS.
2. docs/fold-retro/UPSTREAM_CANDIDATES.md: 업스트림 제안 후보(접기·펴기 세션 종료, 에뮬 화면 인셋, 일시정지 음소거, neodgm 고지)와 각 diff 요약.
3. `git diff --stat 43a6098 -- rust/`가 비어 있음을 확인(rebase했다면 기준은 `git merge-base HEAD upstream/main`).
4. 명세 §8 승인 기준 체크리스트를 채운다(에이전트 확인 / 사용자 확인 필요 구분).
5. [공통] 마무리.

사용자 확인 목록: 명세 §7.4 T12 회귀 전체, §8에서 남은 항목.
```

---

## 5. Codex 교차 리뷰

- **자동**: Codex의 GitHub 코드 리뷰를 포크에 켜 두면 PR마다 리뷰가 달린다. Plus 요금제의 클라우드 통합 기능이다.
- **수동(로컬 CLI)**: GPT-6.1 Sol / Medium.
  ```text
  git fetch origin 후 `git diff origin/fold-retro...origin/<PR 브랜치>`를 docs/fold-retro/Fold_Retro_Phone_MVP_v0.3.md 기준으로 리뷰해 줘. 코드는 고치지 말고 지적만.
  확인: 1) AGENTS.md 절대 규칙 위반 2) 생명주기(재생성·프로세스 종료·일시정지 중 상태, onDestroy 가드, 메인 스레드) 3) 입력 엣지 케이스(취소, 같은 키 다중 포인터, 슬라이드, 영역 이탈, 일시정지 중 뗌, 하드웨어 키 공존, 최소 누름 시간 중 재입력) 4) Composable 안 WipiNative 호출 5) 실패할 수 있는 단정인지(의미 없는 테스트) 6) en/ko 문자열, 하드코딩 픽셀·기종 상수 7) rust/ 변경.
  출력: 차단 / 비차단 / 테스트 공백, 각 파일:줄과 한 줄 수정 제안.
  ```

---

## 6. 크레딧이 끝났거나 로컬로 할 때

- 같은 Phase 블록을 로컬 Codex(GPT-6.1 Sol High, 단순 작업은 GPT-6 Luna) 또는 로컬 Claude Code(Sonnet 5.5 medium / `opusplan`)에 붙여 넣는다. [공통] 블록 대신 "기기가 연결돼 있으면 AGENTS.md의 기기 검증 명령으로 직접 확인"을 붙인다.
- 로컬 준비물: Android Studio(SDK Platform 36, Build-Tools 35.0.0, NDK 27.1.12297006, Platform-Tools), Rust + `aarch64-linux-android` 타깃(Windows는 MSVC 툴체인 + VS Build Tools C++), JDK 17+, `ANDROID_HOME`·`ANDROID_NDK_HOME`, Fold5 USB 디버깅.
- 로컬 Claude Code는 Pro 일반 한도를 쓴다. 10월 23일 전에 한도에 막히면 사용량 화면의 무료 리셋을 쓴다.

---

## 7. 막혔을 때

| 상황 | 할 일 |
|---|---|
| 세션 시작이 실패하거나 SDK 설치가 안 됨 | 설정 스크립트는 항상 0으로 끝나게 돼 있다. 세션 안에서 `ls $ANDROID_HOME`로 확인 → 다운로드가 막혔으면 Custom 허용 도메인에 `*.gvt1.com` 추가, 그래도 안 되면 잠시 Full로 바꿔 확인 |
| Gradle이 AGP·AndroidX를 못 받음 | `dl.google.com`이 허용 목록에 있는지 확인(`google()` 저장소가 이 주소를 씀) |
| 워크플로 파일 푸시가 거부됨 | 사용자가 GitHub 웹(Add file → Create new file)이나 PC에서 직접 커밋 |
| CI의 Rust 빌드가 느림 | 부록 D의 rust-cache 단계 유지. Cargo.lock이 그대로면 두 번째부터 빨라진다 |
| 폰에서 업데이트 설치 실패("앱이 설치되지 않았습니다") | 서명이 다르다는 뜻. 고정 키(부록 E)가 적용된 빌드인지 확인. 키가 바뀐 뒤 한 번은 앱 삭제 후 설치가 불가피(게임·세이브 삭제됨 — 미리 백업) |
| 같은 오류로 2번 실패 | 멈추고 오류 원문·시도·가설 보고 |
| `rust/` 수정이 필요해 보임 | 멈추고 이유 보고(P2로 분리) |
| 크레딧 잔액이 예상보다 빨리 줆 | 남은 Phase를 Sonnet으로, 세션당 작업 범위를 줄인다. 피드백 반영은 모아서 한 번에 |

---

## 부록 A. KeyTest MIDlet (MIT, 이번 검토에서 헤드리스 실행 확인)

`demo/keytest/src/KeyTest.java`

```java
// KeyTest — 입력 경로 검증용 MIDlet (MIT, Fold Retro Phone 포크 자체 제작)
// 화면: P(눌림)/R(반복)/U(뗌) 누적 수, 현재 눌린 키 수(HELD), 최근 이벤트 13개.
// 모든 손가락을 뗐는데 HELD가 0이 아니면 '키 고착' 버그.
// RustJava 제약: 문자열 '+' 연결 금지(StringBuilder 미구현) → StringBuffer만 사용.
import javax.microedition.lcdui.Canvas;
import javax.microedition.lcdui.Display;
import javax.microedition.lcdui.Graphics;
import javax.microedition.midlet.MIDlet;

public class KeyTest extends MIDlet {
    private Screen screen;

    protected void startApp() {
        if (screen == null) {
            screen = new Screen();
            Display.getDisplay(this).setCurrent(screen);
        }
    }

    protected void pauseApp() {}

    protected void destroyApp(boolean unconditional) {}

    static class Screen extends Canvas {
        private final String[] log = new String[13];
        private final int[] held = new int[32];
        private int heldCount = 0;
        private int presses = 0, repeats = 0, releases = 0;

        private void add(char type, int code) {
            StringBuffer sb = new StringBuffer();
            sb.append(type).append(' ').append(code).append("  ga=").append(getGameAction(code));
            for (int i = log.length - 1; i > 0; i--) log[i] = log[i - 1];
            log[0] = sb.toString();
            repaint();
        }

        private void hold(int code) {
            for (int i = 0; i < heldCount; i++) if (held[i] == code) return;
            if (heldCount < held.length) held[heldCount++] = code;
        }

        private void unhold(int code) {
            for (int i = 0; i < heldCount; i++) {
                if (held[i] == code) {
                    held[i] = held[--heldCount];
                    return;
                }
            }
        }

        protected void keyPressed(int c) { presses++; hold(c); add('P', c); }
        protected void keyRepeated(int c) { repeats++; add('R', c); }
        protected void keyReleased(int c) { releases++; unhold(c); add('U', c); }

        protected void paint(Graphics g) {
            g.setColor(0x000000);
            g.fillRect(0, 0, 240, 320);
            g.setColor(heldCount == 0 ? 0x80FF80 : 0xFFD040);
            StringBuffer h = new StringBuffer();
            h.append("P:").append(presses).append(" R:").append(repeats).append(" U:").append(releases).append(" HELD:").append(heldCount);
            g.drawString("KEY TEST", 4, 2, Graphics.TOP | Graphics.LEFT);
            g.drawString(h.toString(), 4, 20, Graphics.TOP | Graphics.LEFT);
            g.setColor(0xFFFFFF);
            for (int i = 0; i < log.length; i++) {
                if (log[i] != null) g.drawString(log[i], 4, 44 + i * 21, Graphics.TOP | Graphics.LEFT);
            }
        }
    }
}
```

`demo/keytest/build.sh`

```sh
#!/bin/sh
# KeyTest 빌드: JDK 17+의 javac --release 8 (RustJava classfile 범위) → keytest.jar
set -e
cd "$(dirname "$0")"
JAVAC="${JAVA_HOME:+$JAVA_HOME/bin/}javac"
JAR="${JAVA_HOME:+$JAVA_HOME/bin/}jar"

rm -rf build keytest.jar
mkdir -p build
"$JAVAC" --release 8 -Xlint:-options -encoding UTF-8 -cp ../stubs -d build $(find ../stubs src -name "*.java")
rm -rf build/javax   # 스텁은 런타임에 에뮬레이터가 제공

cat > manifest.txt << 'EOF'
Manifest-Version: 1.0
MIDlet-Name: KeyTest
MIDlet-Version: 1.0.0
MIDlet-Vendor: FoldRetro
MIDlet-1: Key Test, , KeyTest
MicroEdition-Profile: MIDP-2.0
MicroEdition-Configuration: CLDC-1.1
EOF

"$JAR" cfm keytest.jar manifest.txt -C build .
echo "OK: $(pwd)/keytest.jar"
```

- 헤드리스 확인(선택, `rust/`에서): `cargo run --release -p wipi_android --bin headless -- ../demo/keytest/keytest.jar /tmp/kt/frame 5 "1:OK,2:SOFT_L,3:CLR"` → `frame_final.bmp`에 `P:3 R:0 U:3 HELD:0`. 헤드리스는 키 반복을 만들지 않으므로 R은 0이 정상(반복은 앱 세션에서만 생김).

---

## 부록 B. Rust → .so 빌드 스크립트 참고 구현

Phase 0에서 실제로 돌려 보고 고쳐 쓴다. CI(부록 D)는 bash 버전을 쓴다. 원리: 명세 §6.3.

- 검토 중 확인: bash 스크립트는 가짜 NDK·cargo로 환경변수 이름·복사 경로를 점검했다. Cargo가 환경변수 링커를 설정 파일보다 우선하고, 이때 config의 16KB `rustflags`는 유지되며, `RUSTFLAGS`를 설정하면 정렬이 0x1000(4KB)으로 떨어지는 것도 작은 크레이트로 실험해 확인했다.

`scripts/build-rust-android.sh` (macOS / Linux)

```bash
#!/usr/bin/env bash
# Rust 코어 → Android .so (macOS / Linux). Windows는 build-rust-android.ps1 사용.
# 사용: ANDROID_NDK_HOME=<NDK 27.1.12297006 경로> scripts/build-rust-android.sh [arm64|x86_64|all]
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
NDK="${ANDROID_NDK_HOME:?ANDROID_NDK_HOME에 NDK 27.1.12297006 경로를 지정하세요}"
case "$(uname -s)" in
  Darwin) HOST=darwin-x86_64 ;;   # Apple Silicon도 darwin-x86_64 디렉터리를 쓴다
  Linux)  HOST=linux-x86_64 ;;
  *) echo "이 스크립트는 macOS/Linux 전용입니다. Windows는 build-rust-android.ps1"; exit 1 ;;
esac
TC="$NDK/toolchains/llvm/prebuilt/$HOST/bin"
[ -d "$TC" ] || { echo "NDK 툴체인 없음: $TC"; exit 1; }
unset RUSTFLAGS CARGO_ENCODED_RUSTFLAGS   # config.toml의 16KB 정렬 플래그 보존

build() {  # $1 rust 타깃, $2 ABI 디렉터리, $3 clang 접두어
  local target="$1" abi="$2" clang="$TC/$3-linux-android26-clang"
  local upper; upper="$(echo "${target//-/_}" | tr '[:lower:]' '[:upper:]')"
  export "CARGO_TARGET_${upper}_LINKER=$clang"
  export "CC_${target//-/_}=$clang"
  export "AR_${target//-/_}=$TC/llvm-ar"
  (cd "$ROOT/rust" && cargo build --release --locked -p wipi_android --target "$target")
  mkdir -p "$ROOT/android/app/src/main/jniLibs/$abi"
  cp "$ROOT/rust/target/$target/release/libwipi_android.so" "$ROOT/android/app/src/main/jniLibs/$abi/"
  echo "== $abi LOAD 정렬 (Align 0x4000 이어야 함)"
  "$TC/llvm-readelf" -lW "$ROOT/android/app/src/main/jniLibs/$abi/libwipi_android.so" | grep LOAD
}

case "${1:-arm64}" in
  arm64)  build aarch64-linux-android arm64-v8a aarch64 ;;
  x86_64) build x86_64-linux-android x86_64 x86_64 ;;
  all)    build aarch64-linux-android arm64-v8a aarch64; build x86_64-linux-android x86_64 x86_64 ;;
  *) echo "인자: arm64 | x86_64 | all"; exit 1 ;;
esac
```

`scripts/build-rust-android.ps1` (Windows PowerShell — 이 검토 환경에 PowerShell이 없어 실행 검증은 못 함. Phase 0에서 확인)

```powershell
# Build the Rust core into an Android .so (Windows). Usage:
#   $env:ANDROID_NDK_HOME = "$env:LOCALAPPDATA\Android\Sdk\ndk\27.1.12297006"
#   .\scripts\build-rust-android.ps1 -Abi arm64
# ASCII only on purpose: Windows PowerShell 5.1 reads BOM-less files in the ANSI code page.
param([ValidateSet("arm64", "x86_64", "all")][string]$Abi = "arm64")
$ErrorActionPreference = "Stop"
$Root = Split-Path -Parent $PSScriptRoot
$Ndk = $env:ANDROID_NDK_HOME
if (-not $Ndk) { throw "Set ANDROID_NDK_HOME to the NDK 27.1.12297006 directory" }
$Tc = Join-Path $Ndk "toolchains\llvm\prebuilt\windows-x86_64\bin"
if (-not (Test-Path $Tc)) { throw "NDK toolchain not found: $Tc" }
# Keep the 16KB page-size flag from rust/.cargo/config.toml (RUSTFLAGS would replace it)
Remove-Item Env:RUSTFLAGS -ErrorAction SilentlyContinue
Remove-Item Env:CARGO_ENCODED_RUSTFLAGS -ErrorAction SilentlyContinue

function Build-Abi([string]$Target, [string]$AbiDir, [string]$Prefix) {
  $clang = Join-Path $Tc "$Prefix-linux-android26-clang.cmd"
  $lower = $Target -replace '-', '_'
  $upper = $lower.ToUpper()
  Set-Item "Env:CARGO_TARGET_${upper}_LINKER" $clang
  Set-Item "Env:CC_$lower" $clang
  Set-Item "Env:AR_$lower" (Join-Path $Tc "llvm-ar.exe")
  Push-Location (Join-Path $Root "rust")
  try {
    cargo build --release --locked -p wipi_android --target $Target
    if ($LASTEXITCODE -ne 0) { throw "cargo build failed: $Target" }
  } finally { Pop-Location }
  $dest = Join-Path $Root "android\app\src\main\jniLibs\$AbiDir"
  New-Item -ItemType Directory -Force $dest | Out-Null
  Copy-Item (Join-Path $Root "rust\target\$Target\release\libwipi_android.so") $dest -Force
  Write-Host "== $AbiDir LOAD alignment (expect Align 0x4000)"
  & (Join-Path $Tc "llvm-readelf.exe") -lW (Join-Path $dest "libwipi_android.so") | Select-String "LOAD"
}

if ($Abi -in @("arm64", "all"))  { Build-Abi "aarch64-linux-android" "arm64-v8a" "aarch64" }
if ($Abi -in @("x86_64", "all")) { Build-Abi "x86_64-linux-android" "x86_64" "x86_64" }
```

- 개발 중 빌드 시간을 줄이려면 실행 전에 `CARGO_PROFILE_RELEASE_LTO=off`(PowerShell: `$env:CARGO_PROFILE_RELEASE_LTO="off"`). 최종 APK는 기본값(LTO 켬)으로 다시 빌드.
- Windows에서 cargo가 `.cmd` 링커를 실행하는 것은 원작자도 쓰던 방식이다(`rust/.cargo/config.toml` 하단 주석).

---

## 부록 C. 클라우드 환경 설정 스크립트

환경 설정의 **Setup script** 칸에 붙여 넣는다. 함께 설정할 것: 네트워크 **Custom**(기본 목록 포함 + `dl.google.com`, `dl-ssl.google.com`), 환경 변수 `ANDROID_HOME=/opt/android-sdk`.

```bash
#!/bin/bash
# Fold Retro 클라우드 환경: Android SDK(platform 36, build-tools 35)만 설치한다.
# NDK와 .so·APK 빌드는 GitHub Actions가 맡는다. 실패해도 세션은 시작되도록 항상 0으로 끝낸다.
SDK=/opt/android-sdk
SDKM="$SDK/cmdline-tools/latest/bin/sdkmanager"
command -v unzip > /dev/null || apt-get install -y unzip > /dev/null 2>&1
if [ ! -x "$SDKM" ]; then
  mkdir -p "$SDK/cmdline-tools" && cd /tmp || exit 0
  # 빌드 번호(11076708)는 Android 개발자 사이트 "Command line tools only"의 최신 파일명으로 바꿔도 된다
  curl -fsSL -o cmdline-tools.zip https://dl.google.com/android/repository/commandlinetools-linux-11076708_latest.zip \
    && unzip -q -o cmdline-tools.zip -d "$SDK/cmdline-tools" \
    && rm -rf "$SDK/cmdline-tools/latest" \
    && mv "$SDK/cmdline-tools/cmdline-tools" "$SDK/cmdline-tools/latest"
fi
if [ -x "$SDKM" ]; then
  yes | "$SDKM" --sdk_root="$SDK" --licenses > /dev/null 2>&1
  "$SDKM" --sdk_root="$SDK" "platforms;android-36" "build-tools;35.0.0" > /dev/null 2>&1
fi
exit 0
```

- 5분 안에 끝나면 환경이 캐시돼 다음 세션부터 바로 시작한다.
- 이 스크립트는 검토 환경에서 실행 검증하지 못했다(이 환경은 `dl.google.com`이 막혀 있음). Phase 0의 1번 점검에서 확인한다.

---

## 부록 D. CI 워크플로 — `.github/workflows/android-debug.yml`

설정 단계에서 한 번 커밋한다(§2-3). 푸시할 때마다 .so → 단위 테스트 → 디버그 APK → **드래프트 릴리스**(`debug-<실행 번호>`, 저장소 소유자만 보임)에 APK를 붙인다.

```yaml
name: android-debug
on:
  push:
    paths-ignore:
      - 'docs/**'
      - '**/*.md'
  workflow_dispatch:
permissions:
  contents: write
concurrency:
  group: android-debug-${{ github.ref }}
  cancel-in-progress: true
defaults:
  run:
    shell: bash   # bash -eo pipefail: '| tee'가 실패를 가리지 않는다
jobs:
  build:
    runs-on: ubuntu-24.04
    timeout-minutes: 60
    steps:
      - uses: actions/checkout@v5
      - name: Android SDK 36 / NDK 27.1
        run: |
          SDKM="$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
          yes | "$SDKM" --licenses > /dev/null || true
          "$SDKM" --install "ndk;27.1.12297006" "platforms;android-36" "build-tools;35.0.0" > "$RUNNER_TEMP/ci.log" 2>&1
          echo "ANDROID_NDK_HOME=$ANDROID_HOME/ndk/27.1.12297006" >> "$GITHUB_ENV"
      - name: Rust Android target
        run: rustup target add aarch64-linux-android 2>&1 | tee "$RUNNER_TEMP/ci.log"
      - name: Rust cache (optional)
        uses: Swatinem/rust-cache@v2
        with:
          workspaces: rust
      - name: Build libwipi_android.so (arm64-v8a)
        run: bash scripts/build-rust-android.sh arm64 2>&1 | tee "$RUNNER_TEMP/ci.log"
      - name: Unit tests + debug APK
        working-directory: android
        run: |
          { chmod +x gradlew && ./gradlew --no-daemon :app:testDebugUnitTest :app:assembleDebug; } 2>&1 | tee "$RUNNER_TEMP/ci.log"
      - name: Draft release with APK
        env:
          GH_TOKEN: ${{ github.token }}
        run: |
          gh release create "debug-${GITHUB_RUN_NUMBER}" android/app/build/outputs/apk/debug/app-debug.apk \
            --draft --target "${GITHUB_SHA}" \
            --title "Debug ${GITHUB_RUN_NUMBER} (${GITHUB_REF_NAME})" \
            --notes "commit ${GITHUB_SHA}" 2>&1 | tee "$RUNNER_TEMP/ci.log"
      # 클라우드 세션은 로그 파일을 내려받지 못할 수 있다. 실패한 단계의 오류 줄과 끝부분을
      # 어노테이션으로 남겨서 'gh run view <id>'만으로 원인을 읽게 한다.
      - name: Failure log as annotation
        if: failure()
        run: |
          LOG="$RUNNER_TEMP/ci.log"
          if [ ! -s "$LOG" ]; then
            echo "::error title=CI failure log::(no build output captured)"
            exit 0
          fi
          {
            grep -nE '(^e: |error(\[E[0-9]+\])?:|FAILED|What went wrong|Exception|No such file|not found)' "$LOG" | tail -n 30 || true
            echo '----- last 40 lines -----'
            tail -n 40 "$LOG"
          } | cut -c1-240 > "$RUNNER_TEMP/fail.txt"
          MSG=$(sed -e 's/%/%25/g' -e 's/\r//g' "$RUNNER_TEMP/fail.txt" | awk 'BEGIN { ORS = "%0A" } { print }')
          echo "::error title=CI failure log::${MSG}"
```

- 설정 커밋 직후의 첫 실행은 Gradle wrapper와 빌드 스크립트가 없어 실패한다. Phase 0 이후부터 성공해야 한다.
- **실패 원인 읽기**: 클라우드 세션은 `gh run view`(REST)는 되지만 `--log-failed`의 로그 다운로드는 막힌다 [확인: 2026-10-13, 결과 서버 403]. 그래서 마지막 단계가 실패한 단계의 오류 줄과 끝 40줄을 어노테이션 "CI failure log"로 남기고, 세션은 `gh run view <id>`로 읽는다.
- `docs/`와 `.md`만 바뀐 푸시는 CI를 돌리지 않는다(`paths-ignore`). 필요하면 Actions 화면에서 수동 실행(`workflow_dispatch`).
- 드래프트가 쌓이면 Releases 화면에서 오래된 것을 지운다.
- Rust 릴리스 빌드(LTO)는 캐시가 없을 때 수 분 걸린다. Cargo.lock이 그대로면 두 번째 실행부터 캐시를 쓴다.
- 검증 상태 [확인: 2026-10-13 포크 첫 실행]: checkout, SDK·NDK 설치, Rust 타깃, 캐시 단계 통과 → 빌드 스크립트가 없어 예상대로 실패 → 어노테이션에 `No such file or directory`가 보임. .so·APK·릴리스 단계는 Phase 0에서 확인한다.

---

## 부록 E. 고정 디버그 서명 키

CI 러너는 매번 새 디버그 키를 만들기 때문에 그대로 두면 빌드마다 서명이 바뀐다. 서명이 바뀌면 폰에서 덮어쓰기 업데이트가 실패하고, 지웠다 깔면 임포트한 게임과 세이브가 사라진다.

```sh
keytool -genkeypair -v -storetype PKCS12 \
  -keystore android/app/fold-retro-debug.keystore \
  -storepass android -keypass android -alias androiddebugkey \
  -keyalg RSA -keysize 2048 -validity 10000 \
  -dname "CN=Android Debug,O=Android,C=US"
```

`android/app/build.gradle.kts`의 `signingConfigs` 블록:

```kotlin
signingConfigs {
    // 개인용 고정 디버그 키: 클라우드·CI·로컬 빌드의 서명을 같게 해서 폰에서 덮어쓰기 업데이트(게임·세이브 유지)
    getByName("debug") {
        storeFile = file("fold-retro-debug.keystore")
        storePassword = "android"
        keyAlias = "androiddebugkey"
        keyPassword = "android"
    }
    if (keystorePropsFile.exists()) {
        create("release") { /* 기존 내용 그대로 */ }
    }
}
```

- 원본 `.gitignore`는 `android/*.keystore`, `android/app/*.jks`만 무시하므로 `android/app/fold-retro-debug.keystore`는 커밋된다.
- 공개 저장소에 올라가는 키다. **개인 테스트용으로만** 쓰고, 남에게 배포하는 APK에는 절대 쓰지 않는다.
- 이 키는 한 번 만든 뒤 다시 만들지 않는다(다시 만들면 폰의 앱을 지우고 새로 깔아야 함).
