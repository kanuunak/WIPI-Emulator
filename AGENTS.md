# AGENTS.md — Fold Retro Phone (ParkJeongseop/WIPI-Emulator 포크)

목표: Galaxy Z Fold5 커버 화면에서 위 게임 + 아래 터치 키패드로 WIPI/SKVM/J2ME 게임을 편하게 플레이한다.
- 명세: `docs/fold-retro/Fold_Retro_Phone_MVP_v0.3.md` — 작업 전에 해당 절을 반드시 읽는다.
- 실행 가이드(Phase 프롬프트·CI·클라우드 환경): `docs/fold-retro/Implementation_Guide_v0.3.md`
- **최신 인계: `docs/fold-retro/HANDOFF_Phase3.5.md`** — 현재 상태, 확립된 절차(코어 포크 수정 등), 다음 Phase 프롬프트(3.6 스킨, 개정 Phase 4). 명세·가이드보다 우선.
- 원작자 개발 메모 `CLAUDE.md`는 참고용이다. 그중 빌드 경로(macOS), wie rev(b75a03c1), "path 의존성" 서술은 낡았다. 실제 기준은 `rust/Cargo.toml`(`kanuunak/wie` @ d45b8a7c = ParkJeongseop/wie 38f7e04f + 우리 수정 2건)과 명세 §6.3.

## 절대 규칙

1. `rust/` 아래(wie·RustJava 의존성 포함)는 수정하지 않는다. 필요해 보이면 멈추고 이유를 보고한다. (승인된 예외: `wipi_core/src/session.rs` tick 간격 1ms — 발열, 2026-10-14 / wie를 `kanuunak/wie` 포크로 교체 — KTF DB EOF 읽기 0 채움, 붕어빵타이쿤3, 2026-10-14 / KTF DB 용량 16MB·MC_knlGetAccessLevel, 2026-10-15. wie 수정은 `kanuunak/wie`에 브랜치로 올리고 `rust/Cargo.toml` rev만 바꾼다)
2. Kotlin 패키지 `com.parkjeongseop.wipi`, `object WipiNative`, `external fun` 시그니처, `proguard-rules.pro`는 바꾸지 않는다(JNI 심볼 `Java_com_parkjeongseop_wipi_WipiNative_*`에 묶여 있음). 바꾸는 것은 `applicationId`뿐이다.
3. Composable에서 `WipiNative`를 직접 부르지 않는다. 키는 `KeyInputHub`, 세션·일시정지·볼륨·정지는 `EmulatorSession`을 거친다(Preview·단위 테스트 가능하게).
4. UI에서 키 반복(auto-repeat)을 만들지 않는다. 코어가 누른 키마다 100ms 간격으로 반복 이벤트를 만든다.
5. compileSdk/targetSdk 36, AGP 8.10.1, Kotlin 2.1.21, Compose BOM 2024.12.01을 유지한다. 새 의존성은 승인 없이 추가하지 않는다(승인됨: 테스트용 `junit:junit:4.13.2`, 미리보기용 `androidx.compose.ui:ui-tooling-preview`·`ui-tooling`(debug) — 버전은 BOM).
6. 새 문자열은 `res/values/strings.xml`(en)과 `res/values-ko/strings.xml`(ko) 둘 다에 넣는다.
7. 머신 고유 절대경로를 커밋하지 않는다. `rust/.cargo/config.toml`은 수정하지 않고 환경변수로 덮어쓴다. `RUSTFLAGS`·`CARGO_ENCODED_RUSTFLAGS`는 설정하지 않는다(16KB 정렬 플래그가 무시됨).
8. 게임 파일, 상용 게임 스크린샷·영상, 제조사·통신사 로고를 커밋하지 않는다. 테스트 자산은 `demo/`(MIT, 자체 제작)만 쓴다.
9. 빌드·테스트를 실제로 실행해 확인하기 전에는 "완료"라고 쓰지 않는다. 같은 오류로 두 번 실패하면 멈추고 보고한다.
10. 실기기에서만 확인할 수 있는 것(접기·펴기, 멀티터치 감각)은 "사용자 확인 필요"로 분리해 적는다.
11. `android/app/fold-retro-debug.keystore`는 다시 만들거나 바꾸지 않는다(폰에서 덮어쓰기 업데이트가 깨짐). 배포용 서명에 쓰지 않는다.

## 저장소 지도

| 경로 | 내용 |
|---|---|
| `android/app/src/main/java/com/parkjeongseop/wipi/MainActivity.kt` | 화면 전환, EmulatorScreen(게임+TouchKeypad), 하드웨어 키·스틱 매핑 |
| `.../WipiNative.kt` | JNI 선언: start, getFrame, keyDown/Up, setPaused, setVolume, stop, poll* |
| `.../Settings.kt` | SharedPreferences 설정, 설정 화면, 오픈소스 고지 목록 |
| `.../GameLibrary.kt` | SAF 임포트, `files/games/<id>/{게임, meta.json, cover.png, data/}` |
| 새 파일(이 포크) | `EmulatorSession.kt`, `SessionStopper.kt`(비동기 정지), `KeyInputHub.kt`, `KeypadModel.kt`, `TouchKeypad.kt`, `EmulatorLayout.kt`(세로 스택), `ScreenLayout.kt`(좌우 분할·AUTO 선택), `LayoutEditModel.kt`·`LayoutEditor.kt`(배치 편집), `ScreenFilter.kt`(화면 필터·EPX 셰이더), `LayoutHud.kt`, `CpuUsage.kt`, `Diagnostics.kt`(진단 로그 저장·공유), `ZipLayout.kt`(zip 속 게임 폴더 꺼내기·폴더 임포트 묶기) |
| `android/app/src/test/java/com/parkjeongseop/wipi/` | JVM 단위 테스트 |
| `rust/wipi_core/src/session.rs` | 전역 단일 세션, 키 중복 무시 + 100ms 반복, 일시정지 |
| `rust/wipi_android/src/bin/headless.rs` | UI 없는 실행 하니스(호스트에서 실행 가능) |
| `demo/` | `demo.jar`(J2ME), `demo_wipi.zip`(KTF WIPI-C), `keytest/`(키 검증 MIDlet) |
| `scripts/` | Rust → .so 빌드 스크립트 |

## 빌드

```sh
# 1) Rust .so (Rust를 안 고치면 wie rev당 1회). 스크립트가 NDK 경로를 환경변수로 넣고 jniLibs에 복사, 16KB 정렬을 출력
scripts/build-rust-android.sh          # Windows PowerShell: scripts\build-rust-android.ps1
# 2) APK
cd android && ./gradlew :app:assembleDebug     # Windows: .\gradlew.bat :app:assembleDebug
# 3) 단위 테스트
./gradlew :app:testDebugUnitTest
```

필요: JDK 17+, Android SDK(platform 36, build-tools 35.0.0), NDK 27.1.12297006(`ANDROID_NDK_HOME`), Rust stable ≥ 1.88 + `aarch64-linux-android` 타깃, adb.

CI: `.github/workflows/android-debug.yml`이 코드 푸시마다(`docs/`·`.md`만 바뀌면 건너뜀) .so → 단위 테스트 → 디버그 APK → 드래프트 릴리스(`debug-<번호>`)를 만든다. 실패하면 오류 줄을 어노테이션으로 남긴다.

## 클라우드 세션에서 일할 때

- 기기와 adb가 없다. 기기 항목은 "사용자 확인 목록"(명세 §7.4 ID)으로 남긴다.
- 세션 안 검증: `cd android && ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin`. `assembleDebug`는 .so가 없어 실패하는 게 정상이다(CI가 담당).
- NDK는 꼭 필요할 때만 세션 안에서 설치한다(세션이 끝나면 사라짐).
- `.github/workflows/` 수정이 필요하면 변경안만 보고하고 사용자에게 커밋을 요청한다.
- GitHub는 프록시를 거친다. `GH_TOKEN`이 `proxy-injected`라 `gh auth status`는 invalid로 나오지만 정상이다. GraphQL을 쓰는 `gh pr`·`gh issue`·`gh release list`는 403이므로 REST(`gh api`)를 쓴다. git 원격이 프록시 주소일 수 있으니 저장소를 직접 지정한다.
  ```sh
  R=kanuunak/WIPI-Emulator
  gh api repos/$R --jq .full_name                          # 접근 확인
  gh api repos/$R/pulls -f base=fold-retro -f head=<작업 브랜치> \
    -f title='<제목>' -f body='<본문>' --jq .html_url        # PR 열기
  gh run list -R $R --branch <작업 브랜치> -L 3             # CI 실행 찾기
  gh run watch <run-id> -R $R --exit-status                # 끝날 때까지 대기
  gh run view <run-id> -R $R                               # 실패 시 "CI failure log" 어노테이션 = 오류 줄 + 끝 40줄
  gh api repos/$R/releases --jq '.[0] | {name, draft}'     # 드래프트 릴리스 확인
  ```
  `gh run view --log-failed`는 로그 서버가 막혀 실패한다. 어노테이션으로 부족하면 실패한 단계를 세션 안에서 재현한다.
- 끝나면: 작업 브랜치 푸시 → `fold-retro` 대상 PR → CI 성공 확인(실패하면 어노테이션을 읽고 수정) → PR·CI·드래프트 릴리스 이름 보고.

## 기기 검증 명령

```sh
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n <applicationId>/com.parkjeongseop.wipi.MainActivity
# 게임 주입(디버그 빌드). 확장자(.zip/.jar)로 형식을 판별하므로 유지.
# meta.json은 PC에서 파일로 만들어 push(중첩 따옴표 문제 회피). 내용: {"name":"KeyTest","filename":"game.jar"}
adb push demo/keytest/keytest.jar /data/local/tmp/game.jar
adb push meta.json /data/local/tmp/meta.json
adb shell "run-as <applicationId> sh -c 'mkdir -p files/games/keytest && cp /data/local/tmp/game.jar /data/local/tmp/meta.json files/games/keytest/'"
adb exec-out screencap -p > shot.png        # 현재 켜진 화면(접힌 상태면 커버)
adb logcat -s FoldRetro                     # 레이아웃·입력 디버그 로그
adb shell input swipe X Y X Y 120           # 탭(0ms 탭은 게임이 못 잡음)
adb shell input swipe X Y X Y 1000          # 1초 길게 누르기
```

## 함정 (원작자 CLAUDE.md에서 발췌)

- logcat 로그 레벨을 올리지 않는다(TRACE면 에뮬레이션이 수십 배 느려짐).
- 게임은 시간 기반으로 로딩한다. 화면이 검으면 몇 초 기다린다.
- `nativeInit`의 ndk-context 초기화(오디오)는 건드리지 않는다.
- AVD에서 소리가 이상하면 콜드 부팅(`-no-snapshot-load`)부터.
- J2ME 데모 코드: 문자열 `+` 연결 금지(RustJava에 StringBuilder 없음) → `StringBuffer`. `Random.nextInt(int)`에 의존하지 않는다.
- 헤드리스 실행은 출력 경로 상위에 `wie_data/`(세이브)를 만든다. 재현이 필요하면 실행마다 새 디렉터리.

## 보고 형식 (각 Phase 끝)

1. 변경 파일 목록
2. 실행한 명령과 결과(성공/실패, 핵심 로그)
3. 단위 테스트 결과
4. 기기에서 확인한 것 / 사용자 확인이 필요한 것(체크리스트 ID)
5. 알려진 문제와 다음 단계
