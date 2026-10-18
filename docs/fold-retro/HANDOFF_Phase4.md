# Phase 4 인계 메모 (2026-10-16) — MVP 마감

다음 세션은 **이 문서부터** 읽는다(가장 최신). 기능·결정의 상세는 `HANDOFF_Phase3.6.md`(스킨·게임별 설정)와 `HANDOFF_Phase3.5.md`(코어 포크 절차·DRM·배치 우선순위).

## 1. 상태

PR #7(`ccr-2a4c3abb-uqwv87` → `fold-retro`)에 Phase 3.6 + Phase 4. 사용자가 머지하면 MVP 범위가 끝난다.

| Phase 4 항목 | 결과 |
|---|---|
| README.md | 목적, wie 권장 문구·원본 앱 크레딧, 코어 포크(`kanuunak/wie` d45b8a7c = 38f7e04f + 2건, 커밋 링크), 설치, 빌드, 기능, 알려진 제한, 테스트 기기 |
| UPSTREAM_CANDIDATES.md | 앱 11건(우선순위·커밋·주의), wie 2건(커밋 링크, GetAccessLevel은 스텁임을 명시), 남은 STUBS |
| rust/ 점검 | `git diff --stat 43a6098 -- rust/` = session.rs(tick 1ms, 일시정지를 플랫폼에 전달) + platform/mod.rs·audio.rs(`PauseClock`, 일시정지 때 소리 시퀀스 멈춤 — 2026-10-16 추가) + Cargo.toml + Cargo.lock(wie 출처 줄 13개만). 모두 승인된 예외 |
| 오픈소스 고지 자동 생성 | **새 도구·의존성 없이** `scripts/gen-licenses.py`(파이썬 표준 + cargo metadata + Gradle) → `assets/licenses/third_party.txt`(Rust 162 + Android 85, 원문 87종, 288KB). 앱 설정 > 정보 > 오픈소스 라이선스 > "Rust / Android". `ThirdPartyNoticesTest`가 wie·RustJava 버전 불일치를 잡는다 |
| 명세 §8·§7.4 | §8을 판정표로(에이전트/사용자, 근거), §7.4에 T13~T22 추가, §9·§4.2 갱신 |
| .so 16KB 정렬 | Debug 34 APK에서 확인: LOAD Align 0x4000 ×4, `zipalign -c -P 16` 통과, package `com.knifecrab.foldretro`, label Fold Retro / 폴드 레트로 |

단위 테스트 117개.

## 2. 사용자 확인

Debug 35 실기기(2026-10-16) 통과: T4·T5(접기·펴기), 일시정지·백그라운드 BGM 멈춤, T12 회귀, T22(오픈소스 고지 "Rust / Android"), T14(배치 편집 유지).
원본 앱과 동시 설치는 applicationId가 달라 확인 생략(사용자가 원본을 쓰지 않음).

T15 화면 필터 커버, 3버튼 내비게이션(키패드·스킨이 버튼 줄과 안 겹침)도 통과(Debug 35). 기기 SM-F946N, One UI 8.5 / Android 16(README 반영). 남은 것(선택): T9 게임패드.

**새 보고 → 수정(Debug 36)**: 가끔 일시정지를 풀면 게임이 멈춘 채로 있다(재현 방법 모름, 여닫기·백그라운드 복귀 뒤로 추정, 게임 종료 후 다시 켜면 정상).
유력 원인: `MobilePlatform::now()`가 실제 시각이라 멈춰 있던 시간만큼 게임 시계가 한 번에 뛴다(명세 §4.3 P2 "재개 시 시간 점프") →
"밀린 시간만큼 따라잡는" 게임은 그동안 갱신을 수천 번 돌며 멈춘 것처럼 보인다(오래 멈췄을수록 길다).
수정(rust/, 2026-10-16 사용자 승인): `MobilePlatform`의 `PauseClock` — `session::set_paused`가 플랫폼에 알리고 `now()`가 멈춘 시간을 뺀다.
같이 오디오 스레드도 일시정지에 시퀀스·출력을 멈추고 재개 때 멈춘 시간만큼 미뤄 이어서 재생한다(리듬 게임에서 음악과 게임 시계가 어긋나지 않게 — 전에는 둘 다 실제 시각이라 어긋나지 않았는데 시계만 고치면 어긋난다).
단위 테스트 2개(`platform::tests`). 확인: 오래(수 분) 일시정지·백그라운드 뒤 재개가 바로 이어지는지, 리듬 게임에서 재개 뒤 음악·노트가 맞는지.
확인 단서: 멈췄을 때 ⚙ 길게 → HUD의 wie-emulator가 ~100%면 따라잡는 중, ~0%면 다른 원인.
Debug 36 실기기(2026-10-17): 리듬스타 일시정지·재개 뒤 음악과 노트 일치, 배경음악 이어서 재생 — 통과. 멈춤 자체는 원래도 재현이 안 돼 "다시 보이면 패치"로 둔다
(다시 보이면: 그 순간 ⚙ 길게 HUD의 wie-emulator %와 진단 로그 파일을 받는다).

PR #7 머지(2026-10-17)로 MVP 범위(Phase 0~4) 마감.

## 3. 다음 후보 (요청이 있을 때)

- 개인 스킨 손보기(바형 위쪽 은색 띠 등) — 저장소 밖, `SKIN_FORMAT.md` 7B + `skin_compose.py`. 정면 이미지를 다시 받아야 한다(세션에 남지 않음).
- 업스트림 PR(UPSTREAM_CANDIDATES.md 우선순위 ★★★부터) — 보낼지 사용자 결정.
- 성능: 무거운 게임에서 에뮬 스레드가 코어 하나를 거의 다 씀 → 프로파일링(게임 파일 필요), 배속은 그 뒤.
- 게임 시작 직후 한 프레임 공통 설정이 보일 수 있음(게임별 설정) — 보고되면 수정.
- `session::stop()` 락 밖 join + 타임아웃(Rust, 승인 필요 — HANDOFF_Phase3 §5).
- 배경음악 박자 흔들림: MIDI 음표를 오디오 스레드가 실제 시각으로 신스에 넣고 신스는 512프레임(약 12ms) 덩어리로 렌더 → 음표가 덩어리 경계로 몰림 + 스레드 깨움 지연. 수정안: 신스 렌더 루프에서 샘플 단위로 이벤트 적용(rust/, 승인 보류 — 사용자: 리듬 게임 영향이 애매).

## 4. 의존성이 바뀌면

`python3 scripts/gen-licenses.py`를 다시 돌려 고지를 커밋한다(BUILD.md 4절). wie rev를 바꾸면 `ThirdPartyNoticesTest`가 실패해서 알려 준다.
