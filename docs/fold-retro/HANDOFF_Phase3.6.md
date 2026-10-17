# Phase 3.6 인계 메모 (2026-10-16)

> **최신 인계는 `HANDOFF_Phase4.md`**. 이 문서는 Phase 3.6의 상태와 결정(스킨·게임별 설정)의 근거로 남긴다.

다음 세션은 **이 문서부터** 읽는다. `HANDOFF_Phase3.5.md`보다 최신이다(충돌하면 이 문서 우선).
3.5 문서는 코어 포크 절차·DRM 원칙·배치 우선순위 등 확립된 결정의 근거로 계속 유효하다.

## 1. 상태

PR #7(`ccr-2a4c3abb-uqwv87` → `fold-retro`)에 Phase 3.6과 Phase 4가 들어 있다. 실기기 확인은 Debug 29~34에서 모두 통과했다(사용자, 2026-10-16).

| 영역 | 내용 | 실기기 |
|---|---|---|
| 키패드 테마 | 설정 > 화면 > 키패드 테마: 프리셋 4종(기본 회색·검정 폴더폰·흰색 슬라이드폰·반투명) + 모양(둥근/네모/알약)·글자 크기·불투명도. 글자색 자동 대비(3:1), 옅은 키 그림자 | 통과(Debug 30) |
| 스킨 팩 | 폰 그림 + `skin.json`(화면 자리·키 영역·눌린 그림). 같은 터치 처리(멀티터치·조이스틱·슬라이드). zip 가져오기(검사 원문 + 복사)·삭제·키 영역 표시. 기본 스킨 2종(코드로 그림, 세로·가로) | 통과(Debug 31~33) |
| 배경 이미지 | 스킨 밖·키패드 뒤 사진(꽉 채움) + 어둡게 0~80% | 통과(Debug 34) |
| 게임별 설정 | 일시정지 메뉴 "이 게임에만 적용": 스킨·테마·배치 방식·저장 배치·필터를 `game.<id>.<이름>` 키로. 켜면 복사, 끄면 지움. 게임 삭제 시 함께 삭제 | 통과(Debug 34) |

단위 테스트 115개. 빌드: Debug 29(첫 테마) ~ Debug 34(게임별 설정).

## 2. 이번에 확립된 결정

- **스킨 = 하우징 + 키패드 전체**(사용자 결정). 원래 계획의 3단계(직접 그린 폰 테두리)와 4단계(내 사진 스킨)를 스킨 팩 하나로 합쳤다. 스킨 "없음"이면 키패드 테마 + 배치 설정.
- **스킨 만드는 법 두 가지**(`SKIN_FORMAT.md` 7·8절):
  - 7A 실물 그대로: 사진 한 장을 그대로(소장·감상용).
  - **7B 플레이용 재조립(권장)**: 사진을 부품으로 잘라 904×2192에 게임 700~780px + 키패드 폭 가득. **바깥 하우징(옆 레일·옆 버튼·둥근 모서리·경첩 손잡이·아래 턱)은 걷어내고 앞면만 가장자리까지** — 실제 폰 테두리가 옛 폰 테두리 역할을 한다(실기기 피드백: 하우징을 그리면 "사진을 띄운" 느낌).
  - 도구: `demo/skins/skin_compose.py`(spread·nine·round_cut·place_box·save_pack). 키 영역은 자를 때 쓴 변환으로 계산한다.
- **실제 제품 사진 스킨은 저장소에 넣지 않는다**(규칙 8). 사용자가 개인용으로 레이저·애니콜 바형·롤리팝을 기기에 가지고 있다(zip은 세션에서 파일로 전달, 저장소·세션 어디에도 남지 않음). 다시 만들 때는 사용자에게 정면 이미지를 받아 7B 요청문으로.
- 게임별 범위에 넣지 않는 것: 소리·진동·배경 이미지(전체 하나).
- 세션 빌드 환경: 이 클라우드 세션은 Maven Central이 404여서, 커밋하지 않는 init 스크립트로 `maven-central.storage-download.googleapis.com` 미러를 붙여 gradle을 돌렸다(아래 6절).

## 3. 사용자 확인 남은 것

- 없음(3.6 범위). 바형 개인 스킨의 위쪽 은색 띠가 조금 아쉬움 — Phase 4 뒤에도 거슬리면 따로 요청(개인 스킨, 저장소 밖).
- 3.5에서 넘어온 것: 화면 필터를 커버에서 한 번, 배치 편집 접기/펴기 뒤 유지.

## 4. 알려진 한계·후보

- 게임 시작 직후 한 프레임 정도 공통 설정 모습이 보일 수 있다(게임 범위로 다시 읽는 것이 LaunchedEffect라서). 보고되면 시작 전에 범위를 정하도록 옮긴다.
- 스킨은 세로 그림만 있으면 펼친 화면에서 가운데 작게 나온다(가로 배치는 기본 스킨에만 있음).
- 그 밖에는 `HANDOFF_Phase3.5.md` 4절 그대로(무거운 게임 CPU, LGT·SKT 이름, `session::stop` 락, 남은 STUBS, 배포 전 고지 자동화).

## 5. 다음 세션 프롬프트

### Phase 4 — 회귀 · 문서 · 마감

Phase 4는 이 세션에서 이어서 끝냈다. 결과는 `HANDOFF_Phase4.md`(최신). 아래는 기록용.

```text
[Phase 4] docs/fold-retro/HANDOFF_Phase3.6.md를 먼저 읽어. 명세 §7.4, §8, §9를 읽어.

1. README_FORK.md: 포크 목적, 원본 앱·wie 크레딧(wie LICENSE 권장 문구 "This project is based on wie (https://github.com/dlunch/wie), created by Inseok Lee." 포함), 코어는 `kanuunak/wie` 포크(ParkJeongseop/wie 38f7e04f + KTF 호환 수정 2건)임을 명시, 설치(드래프트 릴리스 APK), 빌드(BUILD.md), 기능(좌우 분할·배치 편집·화면 필터·키패드 테마·스킨 팩·게임별 설정·배경 이미지·진단 로그·폴더 임포트), 알려진 제한(재개 시 게임 시간 점프, 호환성은 코어에 달림, DRM 인증 게임 미지원, 무거운 게임 CPU), 테스트한 기기와 OS.
2. docs/fold-retro/UPSTREAM_CANDIDATES.md: 업스트림 후보 — 앱(접기·펴기 세션 유지, 인셋, 일시정지 음소거, neodgm 고지, 비동기 정지, zip/폴더 임포트, 스킨 팩) / wie(KTF DB EOF 0 채움, DB 용량, GetAccessLevel — `kanuunak/wie` 커밋 링크).
3. rust 변경 점검: `git diff --stat 43a6098 -- rust/`에는 session.rs(tick 1ms)와 Cargo.toml·Cargo.lock(wie 출처를 kanuunak/wie로)만 있어야 한다. 그 밖의 rust/ 변경이 있으면 보고.
4. 오픈소스 고지 자동 생성(배포 전 필수, 명세 §4.2 P1) — 새 도구·의존성이 필요하면 변경안만 보고하고 승인받는다.
5. 명세 §8 승인 기준 체크리스트(에이전트 확인 / 사용자 확인 필요 구분). §7.4에 새 기능 회귀 행 추가: 좌우 분할, 배치 편집 저장·유지, 화면 필터 5종, 키패드 테마, 스킨 팩, 게임별 설정, 배경 이미지, zip·폴더 임포트, 진단 로그 저장.
6. [공통] 마무리.
```

## 6. 세션 빌드 메모 (클라우드)

```sh
# Maven Central이 막힌 세션에서만. 파일은 스크래치에 두고 커밋하지 않는다
cat > /tmp/mirror.init.gradle <<'EOF'
def mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
beforeSettings { settings ->
    settings.pluginManagement.repositories { maven { url mirror } }
    settings.dependencyResolutionManagement.repositories { maven { url mirror } }
    settings.buildscript.repositories { maven { url mirror } }
}
rootProject { buildscript { repositories { maven { url mirror } } } }
EOF
cd android && ./gradlew -I /tmp/mirror.init.gradle :app:testDebugUnitTest :app:compileDebugKotlin
```
