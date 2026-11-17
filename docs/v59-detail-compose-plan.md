# v59 — 운동 상세 화면 Compose 이전 계획

계획 문서다. 이 PR에는 코드 변경이 없다. 근거는 `MainActivity.kt`(1,256줄), `architecture/`(앱 모듈), `verify-compose-architecture.py`, v53~v58 문서의 정적 검토이며, 런타임 동작은 확인하지 않았다.

읽지 않은 것: `SessionStore`, `SetRecordStore`, 테스트 본문, 매니페스트 본문. 테스트/스크립트는 태그 참조 수만 grep으로 셌다. 각 PR 착수 시 해당 파일을 먼저 읽어야 한다. 저장소 루트의 `architecture/`는 없어서 `app/src/main/java/com/move60/workout/architecture/`를 읽었다.

## 0. 현재 구조에서 먼저 알아야 할 점

| # | 사실 | 이전에 미치는 영향 |
| --- | --- | --- |
| A | `MainActivity`는 `android.app.Activity`이고 XML 없이 코드로 뷰를 만든다 | `ComposeView`를 쓰려면 `ComponentActivity`로 바꿔야 한다(수명주기/저장 상태 소유자 필요) |
| B | 상세 화면은 자체 `SessionStore` 인스턴스를, Compose 홈은 Hilt 싱글턴 `SessionStore`를 쓴다. 둘은 `reloadIfChanged()`로 맞춘다 | 이전 중 한 화면에 두 인스턴스를 섞으면 덮어쓰기 위험. 마지막 단계 전까지 상세 화면은 기존 인스턴스 하나만 쓴다 |
| C | 세트 저장 경로가 둘이고 동작이 다르다 (아래 표) | `WorkoutViewModel.saveSet`을 그대로 재사용하면 동작이 바뀐다. 재사용 금지, 차이를 먼저 맞춘다 |
| D | 갱신 주기: 상세 500ms `ticker`, 홈 뷰모델 1,000ms | 타이머 표시가 1초씩 튈 수 있다. 상세는 500ms 유지 |
| E | 루트 뷰가 시스템 바+컷아웃 인셋을 패딩으로 처리한다 | 내부 `ComposeView`에서 인셋을 또 적용하면 이중 여백. 마지막 단계 전까지 Compose 쪽 인셋 처리 금지 |
| F | 진입점: 홈 도크, 알림(`WorkoutService`), 위젯(`WorkoutWidget`)이 모두 `MainActivity`를 직접 연다. 매니페스트에서 `exported` | 클래스 이름과 매니페스트 항목은 전 과정에서 유지 |
| G | `verify-compose-architecture.py`가 `MainActivity.kt` 안의 문자열을 검사한다: `Haptics.exerciseChanged(this)`, `이동했어요`, `BottomSheetDialog`, `guide_sheet_handle`, `setOnClickListener { sheet.dismiss() }` | 해당 블록을 옮기는 PR에서 같은 의미의 검사를 새 파일 대상으로 옮긴다. 검사를 지우거나 약하게 만들지 않는다 |
| H | `makeTimerCard()`의 `timerValue`·`timerLabel`·`timeMeta`·`timerBadge`·`heartRateValue`·`progress`는 만들어지지만 화면에 붙지 않는다. `showNotificationDiagnostics()`·`openLockscreenSettings()`는 파일 안에 호출부가 없다 | 이전 대상에서 제외. 삭제는 마지막 단계에서 전체 참조 확인 후 |

세트 저장 경로 차이(C):

| 항목 | 상세 `buildSetTable` | `DefaultWorkoutRepository.saveSet` |
| --- | --- | --- |
| 휴식 시간 | `TrainingProgress.restDuration(task)` | 45초 고정, 실행 중일 때만 |
| 저장된 RPE | 기존 값 유지(없으면 "5") | 항상 "5"로 덮어씀 |
| Room `upsertSet` | 안 함 | 함 |
| 체크 해제 | `skipRest()` 호출 | 호출 없음 |
| 근력 단계가 아닐 때 | 저장함(안내 시트에서 입력 가능) | 무시 |
| 마지막 세트 후 | 토스트 + 진동 + 다음 종목 | 없음 |
| 일시정지/종료 시 | `timer` 스냅샷 저장 + 위젯 갱신 + 복구 모드면 서비스 미시작 | 서비스 중지만 |

## 1. UI 블록 목록

| ID | 블록 | 현재 코드 | 주요 태그 |
| --- | --- | --- | --- |
| B1 | 헤더(뒤로 48dp + 제목) + "오늘의 운동 / 요일·부위" 줄 | `constructInterface` | `detail_header`, `detail_back`, `detail_title`, `phone_day_info` |
| B2 | 세트 휴식 카드(+15초, 건너뛰기) | `constructInterface`, `refreshTimer` | `phone_rest`, `phone_rest_time`, `phone_rest_add`, `phone_rest_skip` |
| B3 | 단계 아코디언 틀(칩, 상태, 펼침) | `renderStageAccordion` | `phone_stage_stack`, `phone_step_{i}`, `phone_step_header_{i}`, `phone_step_chip_{i}`, `phone_step_chevron_{i}` |
| B4 | 예정/완료 단계 읽기 전용 행 | `buildStageRows` | `phone_step_preview_{i}`, `phone_step_row_{i}_{j}`, `phone_step_row_meta_{i}_{j}` |
| B5 | 진행 단계 항목 행(체크/회전 링/예정, 탭 이동) | `renderPhaseList` | `phone_phase_list`, `phone_phase_row_{i}`, `phone_phase_row_time_{i}` |
| B6 | 진행 단계 타이머 영역(항목명, RPE 칩, 남은/초과 시간, 진행 막대, 다음 안내) + 길게 눌러 초기화 | `makeTimerCard`, `refreshTimer` | `phone_timer_card`, `phone_phase_rpe`, `phone_phase_time_row`, `phone_phase_time`, `phone_phase_bar` |
| B7 | 세트 입력 표(kg/회 스테퍼, 완료 버튼, 지난 기록) | `buildSetTable`, `renderCurrentExercise` | `phone_current_exercise`, `set-{id}-{n}-kg/-reps/-check/-kg-minus/…` |
| B8 | 자세 안내 진입 행 | `renderCurrentExercise` | `phone_current_guide` |
| B9 | 안내 바텀시트(도식, 세트 표, 동작 순서, 영상 검색, 지난 기록) | `showDetails`, `showGuideSheet` | `guide_sheet_handle`, `popup_swap` |
| B10 | 대체 운동 선택 다이얼로그 | `showExerciseSwap` | — |
| B11 | 고정 도크(진행 막대, 단계·항목, 시간, 심박 알약, kcal, 워치 kcal, 이전/시작/다음) | `makeWorkoutControls`, `refreshTimer`, `dockButton` | `phone_dock_progress`, `phone_session_calories`, `phone_watch_kcal`, `phone_play`, `phone_prev`, `phone_next` |
| B12 | 운동 종료 확인 + 운동 결과 다이얼로그 | `makeWorkoutControls`, `showWorkoutResult` | — |
| B13 | 안전 복구 화면/다이얼로그 | `showRecoveryScreen`, `onCreate` | — |
| B14 | 화면 틀(인셋, 스크롤, `ticker`, 서비스 동기화, 단계 전환음) | `onCreate`, `ticker`, `syncService`, `onResume` | 스크롤 설명 "운동 내용 스크롤" |

## 2. 블록별 영향 범위

테스트 참조 수(태그·클래스명 grep): `PhoneFeaturesTest` 75, `TrainingSyncTest` 13, `BodyFocusTest` 11, `verify-v36-ui.py` 10, `smoke-android.sh` 5, `verify-design-tokens.py` 5, `verify-compose-architecture.py` 3. 블록별 배분은 각 PR에서 확인한다.

| 블록 | 읽는 데이터 | 쓰기/콜백 | 권한·건강 데이터 | 검사 영향 |
| --- | --- | --- | --- | --- |
| B1 | `session.day`, `bodyFocus` | `leaveToOverview()`(작업 루트면 홈 실행 후 종료) | 없음 | `detail_*` 태그 |
| B2 | `restSeconds()`, `stage`, `running`, `finished` | `adjustRest(15)`, `skipRest()`, `syncService()` | 없음 | `phone_rest*` |
| B3 | `routine.stages`, `stage`, `completed`, `running`, `finished`, `elapsed()`, `bodyFocus` | 로컬 상태 `expandedStage`만 변경(운동에 영향 없음) | 없음 | `phone_step_*`, v53/v55 문구 |
| B4 | 단계별 `tasks`, `phasesKeyForStage` | 없음(읽기 전용) | 없음 | `phone_step_row_*`(v55) |
| B5 | `phase()`, `phaseSecondsRemaining()`, `overtimeSeconds()`, `checked` | `jumpToPhase(i)`(누적 시간 유지), `selectedStrengthId` | 없음. 회전은 시스템 애니메이션 끔 설정 존중 | `phone_phase_row_*`(v58) |
| B6 | `phase()`, `overtimeSeconds()`, 현재 근력 종목 | 길게 누름 → `resetStage()` / `resetAll()` | 없음 | `phone_phase_*`, 초과 시간 "+mm:ss" |
| B7 | `SetRecordStore.read/previous`, `checked`, `sessionStartedAt` | `SetRecordStore.write`, `toggleChecked`, `startRest/skipRest`, 400ms 지연 `syncService`, 토스트·`Haptics.exerciseChanged` | 없음 | `set-*` 태그, 스크립트 G의 햅틱/문구 |
| B8 | 현재 근력 종목 | `showDetails(2, task)` | 없음 | `phone_current_guide` |
| B9 | `data.detail(id)`, `ExerciseGuide`, `exercisePrefs.log(id)` | 외부 `ACTION_VIEW`(YouTube 검색), 닫힐 때 다시 그림 | 없음(브라우저 없으면 토스트) | 스크립트 G의 시트 검사(v51) |
| B10 | `data.alternatives(day, slot, focus)` | `data.replaceExercise` → `refreshRoutine()` | 없음 | 종목 설정이 영구 저장됨 |
| B11 | `elapsed()`, `remaining`, `phase()`, `EnergyLedger.estimate`, `WatchKcal.fresh`, `preferences`의 `watch_heart_rate(+_at)` | `start/pause`, `refreshRoutine`, `SetRecordStore.beginSession`, `jumpTo`, `nextStage`, `syncService` | 심박은 10초 이내 값만, 워치 kcal은 같은 세션 30초 이내만 표시. 새 권한 없음 | `phone_play/prev/next`, `verify-fixed-controls.py`, v54/v58 문구 |
| B12 | `StageLog.entries`, `EnergyLedger.estimate` | `nextStage()`, 달력 화면 이동 | 없음 | 종료 흐름 테스트 |
| B13 | `CrashReporter` | `acknowledge`, 서비스 재시도 | 없음 | 복구 모드에서는 서비스 자동 시작 금지 |
| B14 | `reloadIfChanged`, `reconcile`, `recordDate` | `save()`, `WatchStatePublisher`, `WorkoutService` 시작/중지, `timer` 스냅샷, `WorkoutWidget.refresh`, 전환음 | 포그라운드 서비스 시작 실패 시 로그+토스트 | 전 테스트, 스모크 스크립트 |

권한 요약: 이전 전체에서 매니페스트 권한 추가·삭제는 없다. Health Connect 요청 흐름은 상세 화면에 없으며, 넣지 않는다.

## 3. PR 순서

잎 블록부터 옮긴다. 그래야 `ComposeView`가 항상 기존 뷰 트리의 끝에 놓이고, Compose 안에 기존 뷰를 다시 넣는 일(`AndroidView`)이 없다.

| 버전 | 범위 | 변경 파일 | 위험 | 롤백 |
| --- | --- | --- | --- | --- |
| v59 | 기반 + B1. `Activity` → `ComponentActivity`. 표시 계산을 순수 함수로 분리(`strengthSetCount`, `doneSets`, 도크 문구, `formatTime`, `movedSuffix`). `ComposeView` 호스트 도우미. 헤더를 Compose로 | `MainActivity.kt`, 신규 `detail/DetailUi.kt`·`detail/DetailFormat.kt`, 신규 단위 테스트, `verify-compose-architecture.py`(추가만), 문서 | 상위 클래스 변경으로 뒤로 가기·창 색·인셋이 달라질 수 있음 | PR 되돌리기. 저장 데이터 변경 없음 |
| v60 | B2 휴식 카드 | `MainActivity.kt`, `detail/RestCard.kt`, `PhoneFeaturesTest` | 숨김 상태에서 빈 여백(현재는 마진으로 처리) | PR 되돌리기 |
| v61 | B11 도크 + B12 다이얼로그 | `MainActivity.kt`, `detail/DetailDock.kt`, 테스트 3종, `verify-fixed-controls.py`, `verify-v36-ui.py` | 시작/일시정지/종료가 기록의 시작점. 이중 탭, 0.5초마다 재구성, 내비게이션 바 여백 | PR 되돌리기. `BLOCK_DOCK` 스위치로 기존 도크 복귀(4절) |
| v62 | B5 + B6 진행 단계 영역 | `MainActivity.kt`, `detail/ActiveStage.kt`, 테스트 | 회전 링이 UI 테스트의 유휴 대기를 막을 수 있음. 길게 눌러 초기화는 기록에 영향 | 위와 같음 |
| v63 | B7 세트 입력 + B8 진입 행 | `MainActivity.kt`, `detail/SetTable.kt`, 테스트 3종, 스크립트 G 이전 | 가장 높음. 입력 중 재구성으로 글자가 사라지거나, 저장 키·토큰이 달라지면 기록 손실 | 위와 같음. 저장 형식은 그대로라 되돌려도 기록 유지 |
| v64 | B9 안내 시트 + B10 교체 | `MainActivity.kt`, `detail/GuideSheet.kt`, 테스트, 스크립트 G(v51 검사) 이전 | `ExerciseGuide`(기존 뷰)는 `AndroidView`로 감싸야 함. 시트 최대 높이 55%, 닫기 버튼 없음, 손잡이 48dp 유지 | 위와 같음 |
| v65 | B3 + B4 아코디언. 이 시점에 v62~v63 컴포저블을 직접 포함 | `MainActivity.kt`, `detail/StageAccordion.kt`, 테스트 | `activeStageContent` 재부착 로직 제거. 펼침 상태가 단계 전환 시 달라질 수 있음 | 위와 같음 |
| v66 | B14 화면 틀. `Scaffold` 한 개로 통합, 인셋을 Compose로, `ticker` → 상세 전용 뷰모델, 싱글턴 `SessionStore` 사용, 기존 뷰 코드와 스위치 삭제 | `MainActivity.kt`, `detail/DetailScreen.kt`, `detail/DetailViewModel.kt`, `WorkoutModule.kt`, 스크립트 전부, README, CLAUDE.md의 "legacy native detail" 문구 | 인셋 이중/누락, 서비스 동기화 누락, 복구 모드 우회, 두 `SessionStore` 통합 시 경합 | PR 되돌리기. 삭제는 이 PR에서만 일어나므로 되돌리면 전부 복원 |

B13(복구 화면)은 초기화 실패 시에도 떠야 하므로 v66까지 기존 뷰로 둔다. `MainActivity`를 `NavHost` 목적지로 합치는 일은 이 계획 범위 밖이다(알림·위젯 진입점 F).

각 PR 공통 완료 조건:
- 필수 검사 5종 + PR 검사(폰 계측, 원형 워치 에뮬레이터) 통과.
- 바뀐 블록의 에뮬레이터 캡처를 `smoke-debug/features/phone-v{NN}-*.png`로 남긴다(헤더, 팝업, 스크롤 끝, 도크, 시스템 바 포함). 캡처가 없으면 시각 검증 미완료로 보고한다.
- 갤럭시 실기기 확인은 별도 항목으로 보고한다.

## 4. 컨트롤 중복 방지 방식

| 규칙 | 내용 |
| --- | --- |
| 자리 하나, 주인 하나 | 블록마다 부모 `LinearLayout`의 같은 위치에 뷰 하나만 넣는다. 그 뷰는 기존 뷰이거나 `ComposeView`이며, 둘을 함께 붙이는 코드는 만들지 않는다 |
| 스위치 한 곳 | `detail/DetailBlocks.kt`에 블록별 `const val`(예: `BLOCK_DOCK = COMPOSE`). 자리 생성 함수가 이 값으로 둘 중 하나만 만든다. 런타임 설정·사용자 토글은 두지 않는다 |
| 기존 코드 보존 기간 | 블록을 옮긴 PR에서는 기존 생성 함수를 남긴다(스위치로 복귀 가능). 삭제는 v66에서 참조 확인 후 한 번에 한다 |
| 갱신 경로 | `refreshTimer()`/`renderCurrent()`는 옮긴 블록의 뷰 필드를 건드리지 않고, 대신 `DetailUi` 상태 값 하나를 갱신한다. `lateinit` 뷰 필드는 블록 이전과 함께 접근을 막는다 |
| 태그 | Compose 블록은 같은 이름을 `testTag`로 쓴다. 한 이름이 두 트리에 동시에 존재하지 않게 한다. 테스트는 해당 PR에서 같이 바꾼다 |
| 자동 검사 | `verify-compose-architecture.py`에 추가: 스위치가 `COMPOSE`인 블록의 태그 문자열이 `MainActivity.kt`의 활성 경로와 `detail/*.kt`에 동시에 생성되지 않을 것. 계측 테스트에 추가: `phone_play` 등 핵심 컨트롤이 화면에 정확히 1개 |
| 홈과의 중복 | 홈 도크와 상세 도크는 서로 다른 액티비티라 동시에 보이지 않는다. 홈 `WorkoutDock` 컴포저블을 상세에 재사용하지 않는다(문구·버튼 규칙이 다름) |

디자인 규칙(모든 Compose 블록): 색은 `MaterialTheme.colorScheme`(`Move60ComposeTheme`), 글자·간격·모서리는 `design/tokens.json`에서 나온 값만 쓴다. 터치 대상은 48dp 이상을 유지한다(스테퍼처럼 보이는 크기만 32dp인 경우 포함). 현재 코드의 토큰 밖 숫자(글자 11·13·15·17·18·26sp, 모서리 12·24dp 등)는 옮기는 PR에서 토큰 값으로 맞추거나 토큰 추가를 따로 제안한다. 임의 값을 그대로 복사하지 않는다.

## 5. 사용자 기록 보호

| 대상 | 저장 위치 | 보호 방법 |
| --- | --- | --- |
| 세트 기록(kg/회/완료/RPE) | `SetRecordStore`(일자·세션 토큰·종목·세트) | 읽기/쓰기 호출과 인자(`token = sessionStartedAt`)를 그대로 둔다. RPE는 기존 값 유지. 저장 형식·키 변경 없음 |
| 세트 완료 체크 | `SessionStore.checked`의 `strength-{day}-{id}-{set}` | 키 문자열을 순수 함수 한 곳으로 모으고 단위 테스트로 고정 |
| 진행 상태·경과 시간 | `SessionStore` 스냅샷, `timer` 환경설정 | `syncService()`를 v66 전까지 그대로 호출. 일시정지 시 스냅샷 저장 + 위젯 갱신 유지 |
| 단계별 시간·칼로리 | `StageLog`, `EnergyLedger`, Room | UI 이전에서 쓰기 코드 변경 없음. Room 스키마 변경 없음 |
| 설정(소리, 몸무게, 컨디션, 종목 교체) | `preferences`, `ExercisePreferences`, `WorkoutData` | 키 이름 변경 없음 |

추가 안전장치:
- v63 전에 계측 테스트를 먼저 넣는다: 세트 입력 → 화면 재생성 → 값 유지, 체크 해제 → 기록은 남고 완료만 해제, 근력 단계가 아닐 때 안내 시트에서 입력 → 저장.
- 입력 칸은 포커스 중 로컬 상태를 우선하고 0.5초 갱신 값으로 덮어쓰지 않는다. 글자 변경마다 저장하는 현재 동작을 유지한다.
- 초기화 호출(`resetStage`, `resetAll`)은 지금 있는 두 곳(길게 누름 메뉴, `onResume`의 "시작 전이고 루틴이 바뀐 경우")에만 둔다. 조건식을 바꾸지 않고, 새 호출부를 만들지 않는다.
- 각 PR에서 이전 빌드 → 새 빌드 덮어쓰기 설치 후 진행 중 세션·지난 기록·달력 표시가 같은지 확인한다(에뮬레이터, 실기기는 별도 보고).
- 정리 과정에서 기록 파일·환경설정 키·Room 테이블을 지우지 않는다.

## 6. 착수 전 결정이 필요한 것

| 질문 | 제안 |
| --- | --- |
| 세트 저장 경로 차이(0절 C)를 어느 쪽에 맞출까 | 상세 화면 동작을 기준으로 리포지터리를 맞춘다. v63 전에 별도 PR |
| 길게 눌러 초기화에 확인 단계가 한 번뿐이다 | 이전 중에는 그대로 둔다. 바꾸려면 별도 변경으로 |
| 토큰 밖 글자 크기를 토큰에 추가할까 | Figma 기준 확인 후 v59에서 결정 |
