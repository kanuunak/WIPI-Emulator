# 업스트림 후보 (2026-10-16)

이 포크(`kanuunak/WIPI-Emulator`, `fold-retro` 브랜치)와 코어 포크(`kanuunak/wie`)에서 원본으로 돌려보낼 만한 변경을 모은다.
보낼지·언제 보낼지는 사용자가 정한다. 커밋 해시는 이 저장소 기준이다.

원본:
- 앱: [ParkJeongseop/WIPI-Emulator](https://github.com/ParkJeongseop/WIPI-Emulator) (Android + iOS, Android 패키지 `com.parkjeongseop.wipi`)
- 코어: [ParkJeongseop/wie](https://github.com/ParkJeongseop/wie)(앱이 rev로 고정해 쓰는 포크) → [dlunch/wie](https://github.com/dlunch/wie)(최종 업스트림)

## 1. 앱 (ParkJeongseop/WIPI-Emulator)

보내기 전에 공통으로 할 일: 포크 전용 부분(`applicationId` `com.knifecrab.foldretro`, 앱 이름 "폴드 레트로", 디버그 서명 키, Fold5 전용 문구)을 빼고, iOS에 같은 기능이 필요한지 원작자와 맞춘다(원본은 "기능은 iOS가 명세" 원칙).

| 우선 | 변경 | 커밋 | 원본에 주는 이점 | 보낼 때 주의 |
|---|---|---|---|---|
| ★★★ | **접기·펴기·키보드 연결에도 세션 유지** — Manifest `configChanges` 보강, `onDestroy`는 `isFinishing && !isChangingConfigurations`일 때만 정지, 상태를 `EmulatorSession`(프로세스 수명)으로 | `3889a8d` | 폴더블·태블릿·키보드 연결 시 게임이 끝나던 버그 수정 | 작은 PR로 분리 가능(Manifest + onDestroy + 세션 객체) |
| ★★★ | **일시정지·백그라운드 때 음소거** — `pause()`가 볼륨 0, `resume()`이 설정 볼륨 복원 | `3889a8d` | 백그라운드에서 BGM이 계속 나던 문제 | 위와 같은 PR |
| ★★★ | **tick 사이 최소 1ms 쉬기**(`rust/wipi_core/src/session.rs`) | `3b6c775` | 가벼운 게임도 CPU 코어 하나 100% → 약 11%(발열·배터리). fps 영향 −2.5% | iOS에도 같은 코어라 함께 이득. 측정 근거가 커밋 주석에 있음 |
| ★★☆ | **edge-to-edge 인셋 처리**(`safeDrawing` 안에 배치, 배경은 바 뒤까지) | `d5de62d` | targetSdk 35+에서 키 행이 내비게이션 바와 겹치던 문제 | 원본 레이아웃(고정 배치)에 맞춰 인셋 부분만 떼어 내기 |
| ★★☆ | **Neo둥근모(OFL 1.1) 고지** — 코어가 .so에 내장하는 폰트인데 앱 고지 목록에 없었음 | `14abc0e` | 라이선스 고지 누락 보완 | iOS 고지 화면(LicensesView)에도 추가 필요 |
| ★★☆ | **비동기 정지**(`SessionStopper`) — 정지를 백그라운드로, 3초 넘게 안 끝나면 "앱 종료" 안내 | `aac51d5` | 멈춘 게임을 끌 때 UI가 얼던 문제 | — |
| ★★☆ | **zip·폴더 임포트 보강**(`ZipLayout`) — zip 안 폴더에 든 게임 꺼내기, 폴더째 임포트, 압축 해제 앱이 흩어 놓은 `X-__adf__` 되돌리기, LGT `app_info`·SKT `*.msd`·`*.jad` 인식 | `7d39872` `be9fef2` `3107421` `ac26d85` | 사용자 팩에서 "Unknown archive format"이 크게 줄어듦. 순수 Kotlin + 단위 테스트 | iOS GameLibrary에도 같은 규칙 필요 |
| ★★☆ | **멀티터치 터치 키패드 + 입력 허브**(`KeyInputHub`·`KeypadModel`·`TouchKeypad`) — 손가락별 source, 최소 누름 40ms, 같은 클러스터 슬라이드, 방향키 조이스틱식, 시스템 제스처 제외 | `3889a8d` `27830f2` `2ddfcbc` | 빠른 연타 누락·두 손가락 동시 입력·◀↔▶ 흔들기 문제 | 원본 키패드 모양과 다르면 모델·허브만 먼저 |
| ★☆☆ | 오류 배너 개선 + 진단 로그 저장·공유 | `aac51d5` `3d4a90d` | 호환성 제보가 쉬워짐 | 개인정보: logcat 일부가 들어감(사용자가 직접 공유) |
| ★☆☆ | 일시정지 중 프레임 폴링 감속(16ms → 500ms) | `27830f2` | 일시정지 중 발열·배터리 | — |
| ☆☆☆ | 좌우 분할·배치 편집·화면 필터·키패드 테마·스킨 팩·게임별 설정 | `629c591` `36cf27f` `d3bdd43` `5ae4064` `ffb7be7` `b8e260a` 등 | 큰 화면·취향 기능 | 규모가 커서 원작자 의향을 먼저 묻는다. 스킨 규격은 `SKIN_FORMAT.md` |

## 2. 코어 (wie)

`kanuunak/wie`(ParkJeongseop/wie 포크). 둘 다 KTF(WIPI-C) 저장소 API이고 단위 테스트가 있다.

| 변경 | 커밋 | 내용 | 보낼 곳·주의 |
|---|---|---|---|
| KTF DB 읽기 EOF 0 채움 | [`d9675f5f`](https://github.com/kanuunak/wie/commit/d9675f5f13248a24d709ecefc79c822197b291d2) (브랜치 `fix/ktf-db-read-eof-zero`) | `stream_read`가 파일 끝에서 요청한 칸 중 못 채운 부분을 0으로(최대 256바이트, 반환값 −23 유지) | 붕어빵타이쿤3 "Allocation failure" 해결. 실제 KTF 폰 동작과 일치하는지 근거 자료가 있으면 함께. ParkJeongseop/wie → dlunch/wie |
| KTF DB 용량 16MB, `MC_knlGetAccessLevel` | [`d45b8a7c`](https://github.com/kanuunak/wie/commit/d45b8a7c6c80d854e14af7932d20695cbaa29c68) (브랜치 `fix/ktf-storage-access-level`, 위 커밋 위에 쌓음) | 저장 공간 한도 1MB → 16MB, GetAccessLevel이 0을 돌려줌(스텁) | 이노티아 연대기2 설치 공간 부족 해결. **GetAccessLevel의 실제 의미·값은 확인 못 함**(0·1 모두 같은 동작) — PR에 "스텁"으로 명시. 이 게임은 그 뒤 복제 방지 인증(1001)에서 멈추며, 우회는 하지 않는다 |

보낼 때: upstream main 위로 다시 올려(rebase) 충돌을 정리하고, `cargo test -p wie_ktf`·clippy를 돌린 뒤 PR. 머지되면 이 저장소 `rust/Cargo.toml`을 해당 upstream rev로 되돌린다(원작자 메모의 원칙: 포크는 "미머지 델타"만).

## 3. 남은 STUBS(보고만)

동작에 영향이 없었던 미구현 API: `MC_knlUnsetTimer`, `MC_mdaClipSetVolume`, `MC_mdaClipGetVolume`. 코어 이슈로 남길 만하다.
