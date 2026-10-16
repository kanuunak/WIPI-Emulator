# Phase 4 인계 메모 (2026-10-16) — MVP 마감

다음 세션은 **이 문서부터** 읽는다(가장 최신). 기능·결정의 상세는 `HANDOFF_Phase3.6.md`(스킨·게임별 설정)와 `HANDOFF_Phase3.5.md`(코어 포크 절차·DRM·배치 우선순위).

## 1. 상태

PR #7(`ccr-2a4c3abb-uqwv87` → `fold-retro`)에 Phase 3.6 + Phase 4. 사용자가 머지하면 MVP 범위가 끝난다.

| Phase 4 항목 | 결과 |
|---|---|
| README_FORK.md | 목적, wie 권장 문구·원본 앱 크레딧, 코어 포크(`kanuunak/wie` d45b8a7c = 38f7e04f + 2건, 커밋 링크), 설치, 빌드, 기능, 알려진 제한, 테스트 기기 |
| UPSTREAM_CANDIDATES.md | 앱 11건(우선순위·커밋·주의), wie 2건(커밋 링크, GetAccessLevel은 스텁임을 명시), 남은 STUBS |
| rust/ 점검 | `git diff --stat 43a6098 -- rust/` = session.rs(tick 1ms) + Cargo.toml + Cargo.lock(wie 출처 줄 13개만). 승인된 예외뿐 |
| 오픈소스 고지 자동 생성 | **새 도구·의존성 없이** `scripts/gen-licenses.py`(파이썬 표준 + cargo metadata + Gradle) → `assets/licenses/third_party.txt`(Rust 162 + Android 85, 원문 87종, 288KB). 앱 설정 > 정보 > 오픈소스 라이선스 > "Rust / Android". `ThirdPartyNoticesTest`가 wie·RustJava 버전 불일치를 잡는다 |
| 명세 §8·§7.4 | §8을 판정표로(에이전트/사용자, 근거), §7.4에 T13~T22 추가, §9·§4.2 갱신 |
| .so 16KB 정렬 | Debug 34 APK에서 확인: LOAD Align 0x4000 ×4, `zipalign -c -P 16` 통과, package `com.knifecrab.foldretro`, label Fold Retro / 폴드 레트로 |

단위 테스트 117개.

## 2. 사용자 확인 남은 것 (명세 §8 "남은 사용자 확인")

- T4(커버에서 플레이 중 펼치기)·T5(메인에서 접기, 계속 사용 켬/끔)·일시정지/백그라운드 BGM 멈춤 — 코드는 Phase 1부터 있으나 판정 기록이 없다.
- 원본(Play) 앱과 동시 설치(ID가 달라 충돌은 없음).
- Phase 4 APK로 T12 회귀 + T22(오픈소스 고지 "Rust / Android"가 열리고 스크롤되는지).
- T14 배치 편집 저장이 접기/펴기 뒤 유지, T15 화면 필터 커버.
- (선택) 3버튼 내비게이션, T9 게임패드.

## 3. 다음 후보 (요청이 있을 때)

- 개인 스킨 손보기(바형 위쪽 은색 띠 등) — 저장소 밖, `SKIN_FORMAT.md` 7B + `skin_compose.py`. 정면 이미지를 다시 받아야 한다(세션에 남지 않음).
- 업스트림 PR(UPSTREAM_CANDIDATES.md 우선순위 ★★★부터) — 보낼지 사용자 결정.
- 성능: 무거운 게임에서 에뮬 스레드가 코어 하나를 거의 다 씀 → 프로파일링(게임 파일 필요), 배속은 그 뒤.
- 게임 시작 직후 한 프레임 공통 설정이 보일 수 있음(게임별 설정) — 보고되면 수정.
- `session::stop()` 락 밖 join + 타임아웃(Rust, 승인 필요 — HANDOFF_Phase3 §5).
- P2(명세 §4.3): 일시정지 시간을 뺀 게임 시계, 오디오 스트림 실제 정지.

## 4. 의존성이 바뀌면

`python3 scripts/gen-licenses.py`를 다시 돌려 고지를 커밋한다(BUILD.md 4절). wie rev를 바꾸면 `ThirdPartyNoticesTest`가 실패해서 알려 준다.
