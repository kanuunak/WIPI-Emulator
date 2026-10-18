<div align="center">

# Fold Retro (폴드 레트로)

**Galaxy Z Fold5 커버 화면용 한국 피처폰 게임(WIPI / SKVM / J2ME) 에뮬레이터 — 개인용 포크**

[원본 앱](https://github.com/ParkJeongseop/WIPI-Emulator) · [에뮬레이션 코어 wie](https://github.com/dlunch/wie) · [원본 README](docs/ORIGINAL_README.md)

</div>

---

Galaxy Z Fold5 **커버 화면**에서 2000년대 한국 피처폰 게임(WIPI / SKVM / J2ME)을 "위 게임 + 아래 키패드"로 편하게 하기 위한 개인용 포크입니다.

> This project is based on wie (https://github.com/dlunch/wie), created by Inseok Lee.
>
> 원본 앱: [ParkJeongseop/WIPI-Emulator](https://github.com/ParkJeongseop/WIPI-Emulator)("WIPI 에뮬", MIT, © 2026 Park Jeongseop) — 이 저장소는 그 Android 앱을 Fold5 커버 화면용으로 고친 포크입니다.
> 에뮬레이션 코어 wie와 순수 Rust JVM [RustJava](https://github.com/dlunch/RustJava)는 Inseok Lee(dlunch)의 작품입니다(MIT).

**게임 파일은 들어 있지 않습니다.** 직접 가진 게임 파일만 불러올 수 있습니다. 복제 방지(DRM) 인증을 우회하는 기능은 넣지 않습니다.

---

## 원본과 다른 점

| | 원본 앱 | Fold Retro |
|---|---|---|
| 앱 이름 / ID | WIPI 에뮬 / `com.parkjeongseop.wipi` | 폴드 레트로 / `com.knifecrab.foldretro` (원본과 함께 설치 가능) |
| 화면 배치 | 게임 + 키패드 고정 | 커버(위아래)·펼친 화면(좌우 분할) 자동, 배치 편집으로 직접 배치 |
| 접기·펴기 | 화면이 다시 만들어지며 게임이 끝남 | 세션 유지(일시정지·음소거 후 탭하면 이어서) |
| 일시정지 | 재개하면 멈춘 시간만큼 게임 시계가 한 번에 뜀 | 게임 시계·음악이 멈췄던 자리부터 이어서(리듬 게임 싱크 유지) |
| 꾸미기 | — | 화면 필터, 키패드 테마, 스킨 팩, 배경 이미지, 게임별 설정 |
| 키패드 | 단순 터치 | 멀티터치, 방향키 조이스틱식 미끄러짐, 숫자 슬라이드, 햅틱, 시스템 제스처 제외 |
| 코어 | ParkJeongseop/wie @ 38f7e04f | **`kanuunak/wie` 포크** @ d45b8a7c = 38f7e04f + KTF 호환 수정 2건(아래) |

## 기능

- **화면 배치**: 자동 / 위아래(커버) / 좌우 분할(펼친 화면 — 게임을 높이 가득, 키패드를 양옆으로). ⏸⚙ 아이콘은 넓은 창에서 게임 밖으로.
- **배치 편집**: 일시정지 → [배치 편집]. 게임·키패드·아이콘을 끌어 옮기고 ●로 크기 조절, 키패드 나누기/합치기. 화면 모양별로 저장.
- **화면 필터**: 선명 / 부드럽게 / 스캔라인 / LCD 격자 / 업스케일(EPX 셰이더, Android 13+).
- **키패드 테마**: 기본 회색 / 검정 폴더폰 / 흰색 슬라이드폰 / 반투명 + 키 모양(둥근·네모·알약)·글자 크기·불투명도.
- **스킨 팩**: 폰 그림 위에 게임 화면 자리와 키 영역을 정한 묶음(zip). 기본 스킨 2종(코드로 그린 실버·검정 폴더폰) + 직접 만든 스킨 가져오기.
  규격과 만드는 법(사람·AI용 요청문 포함): [`docs/fold-retro/SKIN_FORMAT.md`](docs/fold-retro/SKIN_FORMAT.md).
- **배경 이미지**: 스킨 밖·키패드 뒤에 사진 + 어둡게.
- **게임별 설정**: 일시정지 메뉴 "이 게임에만 적용" — 스킨·테마·배치·필터를 게임마다 따로.
- **임포트**: zip·jar 파일, zip 안 폴더에 든 게임, 게임 폴더째(+ → 게임 폴더). 압축 해제 앱이 흩어 놓은 표시 파일도 제자리로.
- **진단**: 오류 배너(원문 + 진단 로그), 진단 로그 파일로 저장·공유, ⚙ 길게 누르기 = 레이아웃·입력·CPU HUD.
- 원본 기능 유지: 게임 라이브러리(표지·이름 자동), 소리(PCM + MIDI 사운드폰트), 진동, 하드웨어 키보드·게임패드.

## 설치

1. 이 저장소의 **Releases**에서 최신 드래프트 `Debug <번호>`의 `app-debug.apk`를 받습니다(드래프트는 저장소 소유자에게만 보입니다).
2. 처음 한 번 브라우저의 "출처를 알 수 없는 앱 설치"를 허용합니다.
3. 같은 고정 디버그 키로 서명되므로 다음부터는 덮어쓰기 업데이트가 됩니다(게임·세이브 유지).

이 APK는 개인 테스트용 디버그 빌드입니다. 배포용이 아닙니다.

## 빌드

[`docs/fold-retro/BUILD.md`](docs/fold-retro/BUILD.md) 참고. 요약:

```sh
scripts/build-rust-android.sh                 # Rust 코어 → jniLibs (NDK 27.1.12297006)
cd android && ./gradlew :app:assembleDebug    # APK
./gradlew :app:testDebugUnitTest              # 단위 테스트
```

푸시마다 GitHub Actions(`.github/workflows/android-debug.yml`)가 .so → 단위 테스트 → APK → 드래프트 릴리스를 만듭니다.

## 코어 포크 (`kanuunak/wie`)

브랜치 `fix/ktf-storage-access-level` @ [`d45b8a7c`](https://github.com/kanuunak/wie/commit/d45b8a7c6c80d854e14af7932d20695cbaa29c68) = ParkJeongseop/wie `38f7e04f` +

| 커밋 | 내용 | 계기 |
|---|---|---|
| [`d9675f5f`](https://github.com/kanuunak/wie/commit/d9675f5f13248a24d709ecefc79c822197b291d2) | KTF DB `stream_read`가 EOF에서 받는 칸을 0으로 채움 | 붕어빵타이쿤3 "Allocation failure" |
| [`d45b8a7c`](https://github.com/kanuunak/wie/commit/d45b8a7c6c80d854e14af7932d20695cbaa29c68) | KTF DB 용량 한도 1MB → 16MB, `MC_knlGetAccessLevel` 구현(0) | 이노티아 연대기2 설치 공간 부족·미구현 API |

앱 쪽 Rust(`wipi_core`) 변경은 두 가지입니다: tick 사이 최소 1ms 쉬기(발열), 일시정지를 뺀 게임 시계 + 일시정지 때 소리 시퀀스 멈춤(재개 후 멈춤·시간 점프).

## 알려진 제한

- 게임에 일시정지를 알리는 생명주기 신호(pauseApp 등)는 없습니다. 에뮬이 게임 전체를 얼렸다가 이어서 돌립니다.
- 일시정지한 동안 게임 시계도 멈춥니다(재개 때 시간이 한꺼번에 뛰지 않게). 그래서 게임 안 시각은 이번 실행에서 멈춰 있던 시간만큼 실제 시각보다 늦고, 게임을 다시 켜면 맞춰집니다. 실제 날짜·시각을 쓰는 게임(출석 등)은 참고하세요.
- **호환성은 코어에 달려 있습니다**: 실행되지 않는 게임은 진단 로그(오류 원문)를 저장해 두면 원인 확인에 도움이 됩니다.
- **복제 방지 인증 게임**(예: 인증오류 1001)은 지원하지 않습니다. 인증 우회는 하지 않습니다.
- **무거운 게임 CPU**: 에뮬레이션 스레드가 코어 하나를 거의 다 쓰는 게임이 있습니다(옛 폰 = 단일 코어 흉내). 배속 기능은 효과가 없어 넣지 않았습니다.
- LGT·SKT 게임은 이름을 뽑지 못해 파일·폴더 이름으로 표시됩니다.
- 스킨이 세로 그림만 있으면 펼친 화면에서는 가운데 작게 나옵니다.

## 테스트한 기기

- Samsung Galaxy Z Fold5(SM-F946N) — 커버 화면(344 × 882dp) / 펼친 화면 세로·가로, 제스처·3버튼 내비게이션. 측정값: [`docs/fold-retro/DEVICE_METRICS.md`](docs/fold-retro/DEVICE_METRICS.md)
- One UI 8.5 / Android 16 (빌드 BP4A.251205.006.F946NKSS6GZI2)

## 문서

| 문서 | 내용 |
|---|---|
| [`docs/fold-retro/SKIN_FORMAT.md`](docs/fold-retro/SKIN_FORMAT.md) | 스킨 팩 규격, 실물 그대로 / 플레이용 재조립 만드는 법, AI 요청문 |
| [`docs/fold-retro/BUILD.md`](docs/fold-retro/BUILD.md) | 빌드(클라우드·CI·로컬), 오픈소스 고지 다시 만들기 |
| [`docs/fold-retro/HANDOFF_Phase4.md`](docs/fold-retro/HANDOFF_Phase4.md) | 최신 상태·다음 후보(개발 인계) |
| [`docs/fold-retro/Fold_Retro_Phone_MVP_v0.3.md`](docs/fold-retro/Fold_Retro_Phone_MVP_v0.3.md) | 명세·테스트 매트릭스·승인 기준 |
| [`docs/fold-retro/UPSTREAM_CANDIDATES.md`](docs/fold-retro/UPSTREAM_CANDIDATES.md) | 원본으로 돌려보낼 만한 변경 |
| [`AGENTS.md`](AGENTS.md) | AI 에이전트 작업 규칙 |
| [`docs/ORIGINAL_README.md`](docs/ORIGINAL_README.md) | 원작자 앱 README 원문(App Store·Play 링크, 개인정보 처리방침) |

## 라이선스

- 이 포크의 코드: MIT(원본 [`LICENSE`](LICENSE) 그대로, © 2026 Park Jeongseop). 포크에서 추가한 코드도 같은 MIT로 둡니다.
- wie·RustJava·smaf: MIT © 2020 Inseok Lee. 그 밖의 Rust·Android 의존성과 폰트(Neo둥근모, OFL 1.1)·사운드폰트(GeneralUser GS) 고지는 앱의 설정 > 정보 > 오픈소스 라이선스에 있습니다.
- `demo/`의 데모 게임과 기본 스킨은 이 저장소에서 직접 만든 것(MIT)입니다. 실제 제품 사진·로고가 들어간 스킨은 저장소에 넣지 않습니다.
