# 빌드 방법 (Fold Retro)

## 버전 (Phase 0에서 확인)
- JDK 21(클라우드 세션) — 최소 17 / Gradle wrapper 8.11.1 / AGP 8.10.1 / Kotlin 2.1.21
- Android SDK platform 36, build-tools 35.0.0 (`ANDROID_HOME=/opt/android-sdk`)
- Rust 1.97.0 (최소 stable 1.88) / NDK 27.1.12297006 (CI 전용, 세션에는 미설치)

## 1. 클라우드 세션 (기기·adb·NDK 없음)
```sh
cd android && ./gradlew :app:testDebugUnitTest :app:compileDebugKotlin
```
- 의존성 캐시가 없는 첫 실행은 약 3분, 이후 약 40초.
- Maven Central이 일시적으로 HTTP 429를 줄 수 있다. 같은 명령을 다시 실행하면 통과했다.
- `assembleDebug`는 `.so`가 없어 `checkJniLibs`에서 실패하는 게 정상이다(CI가 담당).
- 현재 단위 테스트 소스는 없다(`testDebugUnitTest NO-SOURCE`). Phase 1부터 추가된다.

## 2. CI
`.github/workflows/android-debug.yml`이 푸시마다 NDK 설치 → .so → 단위 테스트 → 디버그 APK → 드래프트 릴리스 `debug-<번호>`를 만든다. 서명은 고정 디버그 키(`android/app/fold-retro-debug.keystore`)라 폰에서 덮어쓰기 업데이트가 된다. 개인 테스트 전용 키다.

## 3. 로컬 (macOS / Linux / Windows)
```sh
export ANDROID_NDK_HOME=<NDK 27.1.12297006 경로>
scripts/build-rust-android.sh          # arm64 기본, x86_64 | all 선택. Windows: scripts\build-rust-android.ps1
cd android && ./gradlew :app:assembleDebug     # Windows: .\gradlew.bat :app:assembleDebug
```
- `rust/.cargo/config.toml`은 고치지 않는다. 스크립트가 링커를 환경변수로 덮어쓴다.
- `RUSTFLAGS`, `CARGO_ENCODED_RUSTFLAGS`는 설정하지 않는다(16KB 정렬 플래그가 무시됨). 스크립트가 출력하는 LOAD 정렬이 `0x4000`이어야 한다.
- 개발 중 빌드 시간 단축: `CARGO_PROFILE_RELEASE_LTO=off`(최종 APK는 기본값).

## 4. 아직 실행하지 않은 것
- `build-rust-android.sh`/`.ps1` 실제 실행과 LOAD 정렬 확인(NDK가 세션에 없음 — CI에서 확인).
- `.ps1`은 PowerShell이 없어 실행 검증을 못 했다.
