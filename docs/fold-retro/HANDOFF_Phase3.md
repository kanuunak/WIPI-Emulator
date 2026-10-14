# Phase 3 인계 메모 (2026-10-14, Phase 3.5 결과 반영)

다음 세션은 이 문서부터 읽는다. 명세·가이드보다 이 문서가 최신이다(충돌하면 이 문서 우선, 명세는 이미 개정 반영).

## 1. PR

| PR | 브랜치 | 내용 | 상태 |
|---|---|---|---|
| #4 | `claude/busy-volta-9tfvaj` | Phase 3: KeypadModel·TouchKeypad, 방향키 조이스틱, 입력 진단 HUD, 키패드 햅틱(EFFECT_CLICK), 일시정지 중 폴링 감속, 매 프레임 재구성 제거 | 머지됨 |
| #5 | `claude/busy-volta-9tfvaj-heat` | `rust/wipi_core/src/session.rs` tick 사이 최소 1ms sleep (발열, CPU 100%→12%, 사용자 승인 예외) | 머지됨 |
| Phase 3.5 | `ccr-949c1a62-b432ix` | 진단 로그 공유, HUD CPU %, 정지 비동기화, 오류 배너 수정 (§5) | PR 참고 |

## 2. 이번 세션에서 바뀐 결정

- **방향키 조이스틱**(사용자 결정, 명세 §5.2.2 개정): 방향키를 누른 채 움직이면 OK를 거치지 않는다. OK 중심 기준, 같은 축 반전은 중심선 너머 12dp, 축 전환은 OK 칸 밖 + 우세할 때만. OK는 직접 눌렀을 때만, 누르면 고정. NUM은 클러스터 내 슬라이드(히스테리시스 6dp), FUNC 고정.
- **의존성 승인**: `androidx.compose.ui:ui-tooling-preview`, `ui-tooling`(debug). AGENTS.md 규칙 5에 반영.
- **rust/ 승인 예외**: session.rs tick 간격 1ms만. 그 외 rust/ 수정은 여전히 승인 필요.
- **화면 방향**: 커버는 세로 고정 유지가 맞다 — 폰을 돌려 하는 피처폰 게임은 게임이 그림을 눕혀 그리므로 앱이 돌면 안 된다. 메인 화면(sw ≥ 600)은 Android 16이 고정을 무시해 돈다.

## 3. 사용자 실기기 결과

통과(Debug 10): KeyTest 판정표 a(21키 탭) b(OK 1초 → R 8~10) d(같은 키 두 손가락) e(4+5 동시) f(5 연타 10초) g(키 누른 채 홈) h(알림창) i(접기).

통과(Debug 12): **키패드 판정표 전 항목**(c 슬라이드, j FUNC 고정, k HELD:0, l·m 조이스틱 포함).

발열(Debug 13, #5): 체감으로는 구분이 어려웠다 → Phase 3.5에서 HUD에 앱 CPU %를 넣었다(아래). 숫자로 다시 확인.

입력 진단 스크린샷(§4): 아직 없음 → §4 조사는 열린 채로 둔다.

Phase 3.5 실기기 결과(Debug 16):
- 통과: 오류 배너 뒤로가기로 닫힘·앱 안 멈춤, T3 하트 캐치 회귀.
- 진단 로그 공유: 카톡·Jota 등을 고르면 앱만 열리고 내용이 안 갔다 → 그 앱들은 text/plain 공유에서 첨부(EXTRA_STREAM)를 무시하고 본문(EXTRA_TEXT)만 읽는다. 공유는 기기에 저장도 안 된다. → 수정: [파일로 저장](SAF, 다운로드 등) / [공유](본문에 요약 + 전체 파일 첨부) 선택 대화상자.
- HUD CPU: 하트 캐치에서 약 100% 전후(코어 하나 = 100%, 8코어 기기라 기기 전체의 약 1/8). 호스트 11~12%는 화면 그리기·사운드 없이 잰 값이라 직접 비교 불가 → HUD 둘째 줄에 스레드별 상위 4개를 넣었다(어디서 쓰는지 확인용).

Debug 18 실기기 결과(2026-10-15, 메인 화면 가로 829x690dp):
- 붕어빵타이쿤3 정상, 데몬헌터 정상, 진단 로그 파일 저장 정상.
- HUD CPU(코어 하나 = 100%): 붕어빵타이쿤3 60%(wie-emulator 40), 데몬헌터 100~132%(wie-emulator 79~89, main 12~31, AudioTrack 5~10), 퍼즐 게임 133%(wie-emulator 99, AudioTrack 19). → 무거운 게임은 에뮬 스레드가 코어 하나를 거의 다 쓴다. 이 상태에선 배속을 넣어도 실제로 빨라지지 않는다(성능 개선이 먼저). 다만 "바쁜 대기"(게임이 시간을 확인하며 도는 루프)도 99%로 보이므로 느림 자체의 증거는 아니다.
- 메인 화면 가로에서 ⏸·⚙가 게임 우상단(점수·MENU 표시)을 가렸다 → 옆에 자리가 있으면 게임 밖 오른쪽에 둔다(`EmulatorLayout` 7번, `iconsOutside`).
- 이노티아 연대기2(KTF, 사용자 팩): "Unknown archive format" = zip 안이 폴더 한 겹으로 감싸져 있음 → 임포트 때 벗김(`ZipLayout.unwrapSingleFolder`, 기존 항목은 삭제 후 다시 임포트). 벗긴 뒤 headless: 설치 공간 부족(코어 KTF DB 용량 한도 1MB < 게임 요구 2103KB) → 실험으로 16MB 올리면 설치·로딩·로고 통과 → `MC_knlGetAccessLevel` 미구현(Unimplemented, 실행 오류) → 실험으로 값을 돌려주면 게임 자체 "인증오류 1001"(cert.c2s, 원래 폰 인증). **복제 방지 인증이라 우회 작업은 하지 않는다.** 용량 한도·GetAccessLevel은 다른 게임에도 걸릴 수 있는 호환성 항목으로 남긴다(wie 수정 = 승인 필요, 아직 안 함).

Phase 3.5 빌드에서 사용자 확인 필요(§7.4 ID):
- T12/오류: 지원 안 되는 게임 실행 → 오류 배너(안내 문구 + 원문 앞 3줄 + [로그 공유][닫기]) → 뒤로가기로 배너 닫힘, 앱이 멈추지 않음. 게임 화면에서 오류가 나면 바로 라이브러리로.
- 진단 로그: 설정 > 정보 > 진단 로그 저장·공유, 그리고 오류 배너의 [진단 로그] → [파일로 저장] → 다운로드에 파일이 생기고 "Last error"에 오류 원문 전체, logcat 부분이 채워져 있는지. [공유] → 카톡 등에서 요약 본문이 보이는지.
- HUD CPU 둘째 줄(스레드별): 하트 캐치에서 `wie-emulator`·`RenderThread`·`main`·오디오 스레드가 각각 몇 %인지 스크린샷.
- HUD CPU %(⚙ 길게 누르기, 맨 위 하늘색 줄): 같은 게임에서 값이 일정한지. 참고: 호스트 측정 약 11~12%(코어 하나 = 100% 기준). 일시정지 중엔 "paused"가 붙고 낮아져야 정상.
- T3: 정상 게임(하트 캐치) 회귀 — 실행·조작·종료(⏸ → 게임 종료) 후 다시 실행.

## 4. 진행 중 조사: 슬라이드 도중 입력 끊김

Debug 10 영상: ◀를 화면 **왼쪽 끝 가까이** 누른 채 위로 밀자 ▲가 눌리기 전에 ◀가 떼지고(`U -3`), 그 뒤 같은 손가락 입력이 전부 무시됨. 모델·허브 로직은 같은 경로를 단위 테스트로 재현해 정상 → 앱 밖에서 터치가 취소된 것으로 추정(가장자리 뒤로가기 제스처가 터치를 가로챔, `systemGestureExclusion`이 안 먹었을 가능성).

진단 장치(Debug 11+): ⚙ 길게 누르기 → 레이아웃 HUD 아래 노란 "input" 기록(최근 10줄, `Log.i("FoldRetro", "input …")`).
- `SYS CANCEL @x,ydp` = Activity가 ACTION_CANCEL 받음(시스템이 가로챔)
- `#id KEY CANCEL` / `up` / `left area` / `dropped (hub released)` / `loop end` = 키패드가 키를 놓은 이유
- `sys down @x,ydp` = 터치 시작 위치(가장자리 30dp 안인지)

사용자 스크린샷을 받으면: SYS CANCEL이면 원인 확정 → 대응 후보(①제외 영역 확인·로그 ②왼쪽 열 키를 가장자리에서 조금 띄우기 ③P1 몰입 모드), 아니면 기록대로 추적. 원인 해결 후 진단 HUD 입력 기록은 유지해도 됨(기본 꺼짐, 비용 작음).

## 5. 지원 안 되는 게임에서 "fatal error" 후 멈춤 — Phase 3.5에서 처리

증상(사용자): 지원 안 되는 게임을 열면 fatal error가 뜨고 멈춘 것처럼 되며, 뒤로가기 등을 해도 꺼지지 않는다.

**판정(Phase 3.5, 사용자 스크린샷 + 코드)**: 이 증상은 ANR이 아니라 **오류 배너가 화면 전체를 덮은 것**이다.
- 스크린샷: 앱은 이미 라이브러리(상단 바 "폴드 레트로" + ⚙)로 돌아와 있고, 빨간 배너에 `Fatal error: net.wie.WieError: Fatal error: Allocation failure` + 레지스터·스택 덤프 전체가 들어가 화면을 다 덮었다.
- 코드: tick이 Err를 돌려주면 에뮬 스레드는 오류를 저장하고 **바로 끝난다**(session.rs `Ok(Err(e)) => … break`). 그래서 이어지는 `nativeStop`의 join은 즉시 끝난다 → 이 경로에서 UI 정지는 없다.
- 뒤로가기가 안 먹은 이유: 라이브러리에 BackHandler가 없어 뒤로가기 = 앱이 백그라운드로(Android 12+ 루트 Activity는 finish하지 않음) → 다시 열면 같은 배너(remember 상태)가 그대로.
- 수정: 배너에는 안내 문구 + 원문 앞 3줄(고정폭, 말줄임)만, [로그 공유][닫기] 버튼, 라이브러리에서 뒤로가기 = 배너 닫기. 원문 전체는 진단 로그로.
- `Allocation failure`의 원인(붕어빵타이쿤3, KTF — 사용자 파일로 headless 재현): 게임이 첫 실행에 없는 "option" DB를 읽기 모드로 연다 → KTF 코어는 없는 DB도 빈 DB로 열어 준다(데몬헌터 때문에 의도된 동작) → 결과 확인 없이 8바이트를 읽는데 EOF라 받는 칸이 스택 쓰레기로 남는다 → 그 값(0x4025566c)을 크기로 `MC_knlAlloc` → 약 1GB 요청 실패.
- 수정(사용자 승인, B안): wie `stream_read`가 EOF에서 받는 칸을 0으로 채운다(최대 256바이트, NULL 제외, 반환값 -23 유지). `kanuunak/wie`(ParkJeongseop/wie 포크) 브랜치 `fix/ktf-db-read-eof-zero` @ d9675f5f, `rust/Cargo.toml`이 이 rev를 쓴다. wie 단위 테스트 34개 통과(신규 1). headless: 타이틀→튜토리얼→붕어빵 틀 화면까지, 40초 오류 없음. 남은 STUBS: `MC_knlUnsetTimer`·`MC_mdaClipSetVolume`·`MC_mdaClipGetVolume`·`Display`·`WIPICX_incMemInterface`. 기각한 A안(KTF도 없는 DB를 읽기 모드로 열면 -12)은 데몬헌터를 깨뜨릴 수 있다. 사용자 실기기: 데몬헌터 정상.

원래 추정(아래)은 **다른 경로의 잠재 위험**으로 남는다 — Kotlin 쪽 방어는 Phase 3.5에서 넣었다:

코드로 본 추정(미검증):
- "Fatal error: …"는 wie `WieError::FatalError` 문구. tick이 Err를 돌려주면 세션 루프가 끝나고 앱이 배너와 함께 라이브러리로 돌아가야 정상.
- 멈춤 후보: 게임 코드가 한 tick 안에서 양보하지 않고 계속 돌면(ARM/자바 무한 루프 등) 에뮬 스레드가 tick에서 안 돌아온다. 이때 종료 경로 `EmulatorSession.stop()` → `nativeStop()` → `session::stop()`이 **UI 스레드에서 SESSION 락을 쥔 채 thread.join()** → 영원히 대기 → 앱 전체 정지(ANR). 다음 `session::start`도 낡은 세션을 join하므로 같은 문제.
- 자바 스레드의 uncaught exception은 앱 오류로 안 올라오고 조용한 프리즈가 된다(원작자 CLAUDE.md).
- 가능성: ARM 코어는 1만 명령마다 양보하므로(wie-core-arm `yields_every_ten_thousand_instructions`) ARM 무한 루프로는 tick이 안 멈춘다. 남는 경우는 코어의 Rust 핸들러·자바 인터프리터 안에서 양보 없이 도는 경우 — 드물지만 배제 못 함.
- **Kotlin 방어(Phase 3.5)**: `EmulatorSession.stop()`이 상태를 즉시 초기화(UI는 바로 라이브러리)하고 `nativeStop`은 `SessionStopper`가 백그라운드 스레드에서 실행. 정지가 끝나기 전에는 새 게임 시작을 막고("정리 중" 안내), 3초가 넘으면 "응답 없음 + [앱 종료]"(프로세스 종료가 유일한 복구). 정지 중에는 키 sink가 네이티브를 부르지 않는다(SESSION 락에서 UI가 막히지 않게).
- **남은 rust/ 변경안(승인 필요, 아직 안 함)**: ① `session::stop()`/`start()`가 SESSION 락을 놓은 뒤 join하고, 종료 신호 채널 `recv_timeout`(예: 2초)으로 기다렸다 넘으면 스레드를 분리(detach)·경고 로그 → 멈춘 에뮬 스레드가 있어도 새 게임 시작 가능(대가: 멈춘 스레드가 CPU 코어 하나를 계속 씀 — 프로세스 재시작 전까지). ② tick 무응답 감지: 에뮬 스레드가 tick 시작 시각을 원자값으로 남기고 JNI `nativeTickStalledMs()`로 노출 → 게임 화면에서 "응답 없음" 안내. ②는 `WipiNative` external 추가라 AGENTS.md 규칙 2와도 걸린다.

## 6. 다음 단계 제안

1. 사용자: Phase 3.5 빌드로 §3의 확인 목록 진행. 문제가 생기면 진단 로그 파일을 그대로 첨부(앱·기기·오류 원문·logcat이 한 파일에 있음).
2. Phase 4(가이드 §4 그대로) — 단, 항목 3의 `git diff --stat 43a6098 -- rust/`는 이제 비어 있지 않다. session.rs 승인 예외(#5)만 있어야 정상으로 판정(Phase 3.5는 rust/를 건드리지 않았다).
3. 사용자 결정 대기: §5의 rust/ 변경안 ①②(실제로 tick이 멈추는 게임이 나오면 우선순위가 올라간다). 지원 안 되는 게임 파일을 받으면 headless로 오류·STUBS 확인.
   - 배속(사용자 요청): 게임이 보는 시계(`MobilePlatform::now`)에 배율을 곱하는 방식 + `nativeSetSpeed` JNI 1개(규칙 1·2 예외 필요). 사용자 결정 = **먼저 측정**: HUD 스레드별 CPU에서 `wie-emulator`가 90~100%면 폰이 못 따라가는 것(배속 무의미, 성능 개선), 한참 낮으면 배속 구현. 같은 장치로 일시정지 중 시계를 멈추면 재개 시 시간 점프(명세 §4.3 P2)도 풀린다.
   - wie 수정 절차(이번에 확립): `kanuunak/wie`에 `fix/…` 브랜치 → 코어 테스트(`cargo test -p <크레이트>`) → headless로 해당 게임·데모 확인 → `rust/Cargo.toml` rev 교체(`cargo metadata`로 Cargo.lock의 wie 출처 줄만 갱신, lock 재생성 금지).
4. §4 입력 끊김: 사용자 입력 기록 스크린샷을 받으면 판정.
5. P1 백로그 추가분: 화면 방향 고정 옵션(메인 화면용), 키패드 햅틱 세기, 가로 분할(SPLIT, 커버 가로 포함).

Phase 3.5에서 생긴 것(코드 위치):
- `Diagnostics.kt`: 마지막 게임·오류·CPU를 SharedPreferences("diagnostics")에 기록, 보고서 = 앱 버전·빌드(`BuildConfig.CI_BUILD` = CI면 `debug-<번호> <커밋>`, 로컬이면 `local`) + 기기·Android + 세션 상태 + 마지막 게임/CPU/오류 원문 + `logcat -d --pid -t 2000`. `cacheDir/diagnostics/`에 쓰고 FileProvider(`<applicationId>.diagnostics`, `res/xml/diagnostic_paths.xml`)로 공유 시트.
- `CpuUsage.kt`: `/proc/self/stat` utime+stime 차이 → %(코어 하나 = 100%). HUD가 켜져 있을 때만 1초마다.
- `SessionStopper.kt`: 비동기 정지 + 정지 중/응답 없음 상태(단위 테스트 `SessionStopperTest`).
- 클라우드 세션 팁: Maven Central이 429를 줄 때가 있다 → 세션 전용 Gradle init 스크립트로 구글 미러(`maven-central.storage-download.googleapis.com/maven2/`)를 `beforeSettings`에서 먼저 넣으면 된다(커밋하지 않음).

## 7. Phase 3.5 프롬프트 (가이드 §4 [공통] 블록 뒤에 붙인다)

```text
[Phase 3.5] docs/fold-retro/HANDOFF_Phase3.md를 먼저 읽어(이 문서가 명세보다 최신). 명세 §3.2, §5.1, §4.3도 읽어.

0. PR #4(Phase 3)와 #5(발열)가 fold-retro에 머지됐는지 확인하고, 안 됐으면 나에게 물어봐. 작업은 최신 fold-retro에서 분기.
1. 진단 HUD 결과 처리: 내가 첨부한 입력 기록 스크린샷이 있으면 HANDOFF §4대로 원인을 판정하고 고친다(대응안이 여러 개면 물어봐).
2. 진단 로그 내보내기(Kotlin만): 설정 > 정보에 "진단 로그 공유". 내용 = 앱 버전·빌드, 기기·Android 버전, 마지막 게임 이름·형식, 마지막 오류 원문, 자기 프로세스 logcat(logcat -d --pid=<pid>, 최근 2000줄 이내). 텍스트 파일로 공유 시트에 넘긴다(FileProvider는 androidx.core에 있음 — 새 의존성 없이). 실행 오류 배너에도 같은 공유 버튼. 로그 레벨은 올리지 않는다. 문자열 en/ko 둘 다.
3. "fatal error 후 멈춤, 뒤로가기로도 안 꺼짐": HANDOFF §5의 추정(UI 스레드의 nativeStop이 멈춘 에뮬 스레드를 join)을 먼저 코드로 검증. Kotlin으로 할 수 있는 것부터: 정지를 UI 스레드 밖에서 하고 UI는 바로 라이브러리로, 정리가 끝나기 전 새 게임 시작은 막고 안내. rust/ 수정(예: join 타임아웃·스레드 분리, tick 무응답 감지)이 필요하면 멈추고 변경안과 이유를 보고해 승인을 받는다(AGENTS.md 규칙 1). 내가 게임 파일을 세션에 올리면 headless로 재현해 오류·STUBS 줄을 보고한다 — 게임 파일은 절대 커밋하지 않는다.
4. 세션 안 검증 + [공통] 마무리. 보고는 한국어로.

사용자 확인 목록: 지원 안 되는 게임 실행 → 오류 표시 → 뒤로가기(2번)로 라이브러리 복귀, 앱이 멈추지 않음 / 진단 로그 공유 → 파일이 열리고 오류 원문 포함 / 정상 게임(하트 캐치) 회귀.
```
