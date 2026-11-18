# 운동 상세 화면 Compose 이전 계획

계획 문서다. 이 PR에는 코드 변경이 없고, 화면·기능 개발이나 실기기 검증이 끝났다는 뜻이 아니다. 모든 내용은 정적 검토 결과이며 런타임 동작은 확인하지 않았다.

## 0. 기준 (2026-11-17 정정)

통합 개발 기준은 `main`(v58)이 아니라 Draft PR #49(`feature/routive-policy-domain-v1`, 확인한 커밋 `4636cff`)다. 처음 판은 v58만 보고 `MainActivity` 안에 새 Compose 블록과 새 저장 경로를 만드는 순서를 제안했는데, #49에는 같은 역할의 Compose 화면이 이미 있어 그대로 진행하면 중복 구현이 된다. 그 순서는 폐기하고 아래로 바꾼다.

| 항목 | v58 (`main`) | #49 |
| --- | --- | --- |
| 홈에서 운동 열기 | `MainActivity` 실행 | Compose 경로 `workout`(`WorkoutProgressScreen`) |
| 루틴 목록에서 운동 열기 | 없음 | Compose 경로 `routive_session`(`RoutiveSessionScreen`) |
| `MainActivity`에 닿는 길 | 홈, 알림, 위젯 | 레거시 세션의 알림(`WorkoutService`)과 위젯뿐 |
| 세션 저장소 | `SessionStore` | `SessionStore`(레거시 5단계) + `RoutiveSessionStore`(4단계, 별도 환경설정 파일) |
| 동시 진행 | — | 하나만. 4단계 세션 진행 중이면 레거시 시작을 거부 |
| 종료 후 기록 | `StageLog`, `EnergyLedger`, Room 일별 | 추가로 `TimerSnapshot` → `SessionCommitter`(멱등), `PendingSessionOutbox` |
| kcal 표시 | 도크·결과에 표시 | 전면 숨김(v66 결정 1). 저장 데이터는 유지 |
| 안내 시트 | 동작 순서·영상 검색 항상 표시 | `ExerciseContentApproval` 승인 종목만 |
| 버전 번호 | v58까지 | 문서가 v59~v67을 이미 사용 |

읽은 범위: v58의 `MainActivity.kt` 전체, #49의 `WorkoutProgressScreen`·`RoutiveSessionScreen` 본문, `RoutiveSessionViewModel` 세트 처리부, `WorkoutRepository` 변경분, `docs/v59-legacy-set-records.md`, `docs/v61-session-persistence-design.md`. 읽지 않은 것: `SessionStore`·`SetRecordStore` 본문, `RoutiveSetRecorder` 본문, 테스트 본문, `ROUTIVE_FIGMA_GAP_MATRIX.md`, v62~v67 정책 문서. 실행 단계에서 먼저 읽어야 한다.

## 1. 세 화면의 현재 관계 (#49)

| 화면 | 형태 | 엔진·저장소 | 진입 |
| --- | --- | --- | --- |
| `MainActivity` | 기존 뷰 | `SessionStore` 자체 인스턴스 | 레거시 알림, 위젯 |
| `WorkoutProgressScreen` | Compose | `WorkoutViewModel` → `DefaultWorkoutRepository` → `SessionStore` 싱글턴 | 홈 도크·카드 |
| `RoutiveSessionScreen` | Compose | `RoutiveSessionViewModel` → `RoutiveSessionStore` | 루틴 목록, 4단계 알림·위젯 |

`MainActivity`와 `WorkoutProgressScreen`은 같은 레거시 세션을 서로 다른 화면으로 보여준다. 사용자가 홈에서 시작하면 Compose 화면을, 알림을 누르면 기존 뷰 화면을 보게 된다. 이것이 지금 남아 있는 중복이다.

## 2. 세트 저장 경로 세 개

세 경로 모두 최종적으로 `SetRecordStore`에 쓰지만 규칙이 다르다. 이 차이를 없애기 전에는 화면 이전을 시작하지 않는다.

| 항목 | ① `MainActivity.buildSetTable` | ② `DefaultWorkoutRepository.saveSet` | ③ `RoutiveSessionViewModel.completeSet` |
| --- | --- | --- | --- |
| 쓰는 화면 | 레거시 상세, 안내 시트 | `WorkoutProgressScreen` | `RoutiveSessionScreen` |
| 입력 검증 | `Record.valid` | `Record.valid` | `SetInputPolicy`(계획·저장·정정 공통 한도) |
| 저장 시점 | 글자 입력마다 + 체크 | 완료/취소 버튼 | 세트 완료 버튼 |
| 휴식 시간 | `TrainingProgress.restDuration(task)` | 45초 고정 | 계획의 `restSeconds`, 마지막 목표 세트 뒤에는 없음 |
| 저장되는 RPE | 기존 값 유지(없으면 "5") | 항상 "5" | 항상 "5" |
| 완료 표시 | `SessionStore.checked` 키 | `checked` 키 + Room `upsertSet` | `SetRecordStore`의 완료 기록만 |
| 취소 | 어느 세트든 체크 해제 | 어느 세트든 취소 | 마지막 세트만 |
| 근력 단계가 아닐 때 | 저장함 | 무시 | 근력 단계에서만 입력 UI |
| 마지막 세트 후 | 토스트·진동·다음 종목 | 없음 | 없음 |

`SetRecordStore`는 #49의 기록 화면(`LegacySetReader`, 정책 5)과 세션 커밋(`TimerSnapshot.sets`)이 읽는 원본이다. 키 형식(`set:<날짜>:<요일>:<토큰>:<종목>:<세트>`)과 토큰 규칙은 바꾸지 않는다.

## 3. `MainActivity` 블록과 Compose 화면의 대응

| ID | 블록 (`MainActivity` 함수) | `WorkoutProgressScreen` | `RoutiveSessionScreen` |
| --- | --- | --- | --- |
| B1 | 헤더 + 요일·부위 줄 (`constructInterface`) | 상단 바 있음. 요일·부위 줄 없음 | 상단 바 있음. 루틴 이름 표시 |
| B2 | 세트 휴식 카드, +15초, 건너뛰기 | 없음 | 휴식 카드·건너뛰기 있음. +15초 없음 |
| B3 | 단계 아코디언, 전 단계 펼쳐 보기 (`renderStageAccordion`) | 진행 막대와 "다음 단계" 카드만 | 같음 |
| B4 | 예정·완료 단계 읽기 전용 행 (`buildStageRows`) | 없음 | 현재 단계 항목 카드만 |
| B5 | 진행 단계 항목 행, 탭해서 항목 이동 (`renderPhaseList`) | 현재 항목명만 | 없음(단계 건너뛰기만) |
| B6 | 항목명·RPE·남은/초과 시간·막대, 길게 눌러 초기화 (`makeTimerCard`) | 남은 시간·막대. RPE·초과·초기화 없음 | 경과·계획·초과 표시. 초기화 없음 |
| B7 | 세트 입력: 스테퍼, 지난 기록, 자동 저장 (`buildSetTable`) | 입력칸 + 완료/취소. 스테퍼·지난 기록 없음 | 입력칸 + 완료/마지막 취소. 한도 오류 표시 |
| B8·B9 | 자세 안내 시트 (`showDetails`, `showGuideSheet`) | 없음 | 없음 |
| B10 | 대체 운동 선택 (`showExerciseSwap`) | 없음 | 없음(루틴 편집은 `RoutineComposeScreen`) |
| B11 | 고정 도크: 진행 막대, 항목·시간, 심박, 이전/시작/다음 | 하단 바: 일시정지/다음, 종료/재개, 누적 시간. 이전 단계 없음 | 하단 바 + 이전 단계·건너뛰기 |
| B12 | 종료 확인, 결과 다이얼로그 | 종료 확인 → `result/{id}` 화면 | 같음 |
| B13 | 안전 복구 화면 (`showRecoveryScreen`) | 없음 | 없음 |
| B14 | 인셋, 500ms 갱신, 서비스 동기화, 전환음 | `Scaffold`, 뷰모델 1초 갱신 | `Scaffold`, `RoutiveSessionService` |

"없음"은 화면 본문에서 해당 UI를 찾지 못했다는 뜻이다. Figma에서 의도적으로 뺀 것인지는 `ROUTIVE_FIGMA_GAP_MATRIX.md`와 대조해야 알 수 있다.

블록별 데이터·콜백·권한(레거시 기준, #49에서도 유효):

| 블록 | 읽기 | 쓰기·콜백 | 권한·건강 |
| --- | --- | --- | --- |
| B2 | `restSeconds()` | `adjustRest(15)`, `skipRest()` | 없음 |
| B5 | `phase()`, `phaseSecondsRemaining()`, `overtimeSeconds()` | `jumpToPhase(i)`(누적 시간 유지) | 회전 애니메이션은 시스템 설정 존중 |
| B6 | 현재 항목 | `resetStage()`, `resetAll()` — 기록에 영향 | 없음 |
| B7 | `SetRecordStore.read/previous` | `write`, `toggleChecked`, `startRest/skipRest`, 400ms 지연 동기화, 토스트·`Haptics.exerciseChanged` | 없음 |
| B9 | `data.detail`, `ExerciseGuide`, `ExerciseContentApproval` | 외부 영상 검색(승인 종목만) | 없음 |
| B10 | `data.alternatives` | `data.replaceExercise` → `refreshRoutine()` (영구 저장) | 없음 |
| B11 | `elapsed()`, `preferences`의 `watch_heart_rate(+_at)` | `start/pause`, `beginSession`, `jumpTo`, `nextStage` | 심박은 신선한 값만(`HeartRateFreshness`). 새 권한 없음 |
| B14 | `reloadIfChanged`, `reconcile` | `WatchStatePublisher`, `WorkoutService`, `timer` 스냅샷, 위젯 갱신 | 복구 모드에서는 서비스 자동 시작 금지 |

검사 의존: `verify-compose-architecture.py`가 `MainActivity.kt` 안의 `Haptics.exerciseChanged(this)`, `이동했어요`, `BottomSheetDialog`, `guide_sheet_handle`, 손잡이 닫기 코드를 검사한다(#49에서도 같음). 태그 참조 수는 v58 기준 `PhoneFeaturesTest` 75, `TrainingSyncTest` 13, `BodyFocusTest` 11이며 #49 기준으로 다시 세야 한다.

## 4. 수정된 순서

목표는 유지한다: 레거시 상세를 Compose로 옮기고 `MainActivity`의 뷰 코드를 없앤다. 방법이 바뀐다. `MainActivity` 안에 새 컴포저블을 짓지 않고, 이미 있는 `WorkoutProgressScreen`에 빠진 기능을 채운 뒤 진입점을 옮긴다.

버전 번호는 붙이지 않았다(#49가 v67까지 사용). 번호는 착수할 때 정한다. 모든 단계는 #49 브랜치 위에서 진행한다.

| 단계 | 범위 | 변경 파일(예상) | 위험 | 롤백 |
| --- | --- | --- | --- | --- |
| P0 | 대조 확정. 3절 표를 `ROUTIVE_FIGMA_GAP_MATRIX.md`·v62~v67 결정과 맞춰 "옮길 것 / 의도적으로 뺀 것" 확정. 레거시 5단계 화면의 수명(유지 기간, 4단계로 대체 시점)을 PO가 결정 | 문서만 | 없음 | — |
| P1 | 세트 경로 통일. ①②의 검증을 `SetInputPolicy`로, 저장을 한 곳으로 모은다(③의 `RoutiveSetRecorder`는 `RoutiveSessionStore`에 묶여 있으므로 엔진과 무관한 공용 기록기로 일반화). 휴식 시간·RPE 유지·취소 범위는 2절 차이마다 결정 후 한 규칙으로 | `WorkoutRepository.kt`, `MainActivity.kt`, `RoutiveSetRecorder.kt`, 테스트 | 가장 높음. 저장 규칙이 바뀌면 기존 기록 표시·커밋 멱등 판정(`sameContent`)에 영향 | PR 되돌리기. 키·토큰 형식 불변이라 기록 유지 |
| P2 | `WorkoutProgressScreen` 기능 채우기. P0에서 "옮길 것"으로 확정된 블록을 한 PR에 하나씩: 휴식 카드 → 이전 단계 → 항목 행·항목 이동 → 세트 스테퍼·지난 기록 → 안내 시트·대체 운동 | `WorkoutProgressScreen.kt`, `WorkoutViewModel.kt`, `WorkoutRepository.kt`, Compose 테스트, 검사 스크립트 | 뷰모델 1초 갱신으로 타이머 표시가 튈 수 있음. 입력 중 재구성으로 글자 손실 | PR 되돌리기. `MainActivity`는 그대로라 알림 경로는 영향 없음 |
| P3 | 진입점 이전. 레거시 알림·위젯이 `ComposeActivity`의 `workout` 경로를 열게 한다(4단계 세션이 이미 쓰는 방식) | `WorkoutService.kt`, `WorkoutWidget.kt`, `ComposeActivity.kt`, 테스트 | 알림에서 열 때 인트로·첫 실행 설정 화면이 끼어들 수 있음. 작업 스택 | PR 되돌리기. 인텐트 대상만 바뀜 |
| P4 | 복구 화면(B13)을 `ComposeActivity` 쪽으로 옮기거나 `MainActivity`를 복구 전용으로 축소 | `MainActivity.kt`, `ComposeActivity.kt` | 초기화 실패 시에도 떠야 함 | PR 되돌리기 |
| P5 | `MainActivity` 뷰 코드 삭제. 참조 전체 확인 후 검사 스크립트의 `MainActivity.kt` 문자열 검사를 새 위치로 옮김. README·CLAUDE.md의 "legacy native detail" 문구 갱신 | `MainActivity.kt`, 스크립트, 테스트, 문서 | 삭제 후 누락 기능 발견 | PR 되돌리기(삭제는 이 PR에만 있음) |

각 단계 공통 완료 조건: 필수 검사 + PR 검사(폰 계측, 원형 워치) 통과, 바뀐 화면의 에뮬레이터 캡처를 `smoke-debug/features/`에 보관, 실기기 확인은 별도 보고. 캡처가 없으면 시각 검증 미완료로 보고한다.

## 5. 컨트롤 중복 방지

| 규칙 | 내용 |
| --- | --- |
| 새 화면 금지 | 상세용 컴포저블 패키지를 새로 만들지 않는다. 기능은 `WorkoutProgressScreen`(레거시 엔진) 또는 `RoutiveSessionScreen`(4단계)에만 추가한다 |
| 공용 조각 | 두 Compose 화면에 같은 UI(휴식 카드, 세트 입력)가 필요하면 한 컴포저블을 공유한다. 화면마다 따로 복사하지 않는다 |
| 한 세션, 한 화면 | P3 전까지는 레거시 세션이 두 화면에 보인다. 이 기간에 `MainActivity`에는 기능을 추가하지 않는다(수정은 결함 수정만) |
| 한 화면, 한 컨트롤 | `MainActivity` 안에 `ComposeView`를 끼워 넣지 않는다. 기존 뷰와 Compose가 한 화면에 섞이지 않으므로 같은 버튼이 둘 생길 수 없다 |
| 진입점 전환은 한 번에 | P3에서 알림과 위젯을 같은 PR로 옮긴다. 한쪽만 옮겨 두 화면이 번갈아 뜨는 상태를 만들지 않는다 |
| 동시 세션 | 레거시와 4단계가 동시에 진행되지 않는 현재 가드를 유지한다 |
| 자동 검사 | 계측 테스트: 레거시 세션 진행 중 화면에 시작/일시정지 컨트롤이 정확히 1개. P3 이후 스크립트: `WorkoutService`·`WorkoutWidget`에 `MainActivity::class` 참조 없음 |

디자인 규칙: 색은 `MaterialTheme.colorScheme`(`Move60ComposeTheme`), 글자·간격·모서리는 `design/tokens.json` 값만 쓴다(#49에서 토큰이 바뀌었으므로 그 판 기준). 터치 대상 48dp 이상. 인셋은 `Scaffold`와 `navigationBarsPadding()`으로 처리하고 시스템 바를 침범하지 않는다. `MainActivity`의 토큰 밖 숫자(글자 11·13·15·17·18·26sp 등)는 옮기지 않고 토큰 값에 맞춘다.

## 6. 사용자 기록 보호

| 대상 | 저장 위치 | 보호 방법 |
| --- | --- | --- |
| 세트 기록 | `SetRecordStore`(환경설정) | 키·토큰 형식 불변. 원본을 삭제·변환하지 않는다(정책 5와 같음). 미완료·임시 입력을 완료로 바꾸지 않는다 |
| 완료 체크 | `SessionStore.checked` | 키 문자열 `strength-{day}-{id}-{set}` 불변. P1에서 한 함수로 모으고 테스트로 고정 |
| 커밋된 세션 | Room `sessions`·`session_sets`·`session_segments` | 스키마·마이그레이션 변경 없음. 커밋은 `SessionCommitter` 한 곳만. P1 후에도 같은 세션 재커밋이 `AlreadyCommitted`인지 테스트 |
| 미전송 세션 | `PendingSessionOutbox` | 형식 변경 없음. 기존 항목이 계속 읽히는지 테스트 |
| 단계 시간·에너지 | `StageLog`, `EnergyLedger` | UI 이전에서 쓰기 코드 변경 없음. kcal은 숨김 상태 유지, 저장 데이터는 그대로 |
| 설정·종목 교체 | `preferences`, `ExercisePreferences`, `WorkoutData` | 키 이름 불변 |

추가 안전장치:
- P1 전에 계측 테스트를 먼저 넣는다: 세 경로 각각에서 세트 입력 → 프로세스 재시작 → 값 유지, 완료 취소 → 기록은 남고 완료만 해제, 종료 후 기록 화면에 같은 값.
- 초기화(`resetStage`, `resetAll`)는 현재 호출부(길게 누름 메뉴, `onResume`의 시작 전 루틴 변경)에만 둔다. Compose 화면으로 옮길지는 P0에서 결정하고, 옮기더라도 조건식을 바꾸지 않는다.
- 각 단계에서 이전 빌드 → 새 빌드 덮어쓰기 설치 후 진행 중 세션·지난 기록·달력 표시가 같은지 확인한다.
- 정리 과정에서 기록 파일·환경설정 키·Room 테이블을 지우지 않는다.

## 7. 착수 전 결정이 필요한 것

| # | 질문 | 제안 |
| --- | --- | --- |
| 1 | 레거시 5단계 진행 화면을 계속 둘 것인가, 4단계(`RoutiveSessionScreen`)로 흡수할 것인가 | 흡수가 확정이면 P2를 최소화하고(누락 기능은 4단계 화면에만) P3에서 바로 진입점을 옮긴다 |
| 2 | 세트 규칙 차이(휴식 시간, RPE 유지, 임의 세트 취소, 근력 단계 밖 입력)를 어느 쪽에 맞출 것인가 | 검증·취소는 ③(`SetInputPolicy`), 휴식은 종목별 값 유지, 기존 RPE는 덮어쓰지 않음 |
| 3 | 3절에서 Compose 화면에 없는 기능 중 무엇을 옮길 것인가 | Figma 대조 후 결정. 안내 시트와 항목 이동은 사용자 기능 손실이므로 옮기는 쪽 |
| 4 | 이 문서의 파일명 | #49에 `docs/v59-legacy-set-records.md`가 있어 번호가 겹친다. #49에 합칠 때 `docs/detail-compose-migration-plan.md`로 바꾸기를 제안 |

## 8. 이 PR의 CI 상태

`runtime-smoke`는 버전 검사에서 실패한다(폰 58·워치 52가 배포 버전과 같음). 문서만 바꾼 PR이라 버전을 올리지 않았다. 이 문서는 `main`에 따로 병합하기보다 #49 기준 작업에 포함시키는 것이 맞다.
