# ROUTIVE AI handoff log (Claude <-> ChatGPT)

Two sections are kept strictly separate. Policy items are NOT decided by development.

## A. Development changes (Claude) — 2026-11-15, branch feature/routive-policy-domain-v1 (PR #49, Draft)
| Area | Change | Status |
| --- | --- | --- |
| Room schema | v2 additive tables (11) + `MIGRATION_1_2` + exported schemas 1.json/2.json | Implemented, JVM-tested (Robolectric) |
| Atomic save | `SessionCommitter` (commit, idempotency, conflict, draft retry, interrupted-save recovery) | Implemented, JVM-tested, NOT wired to the app |
| Time source | Sessions store UTC active intervals (`session_active_intervals`); day slices are derived per session zone (P-20) | Implemented, JVM-tested |
| Challenge lifecycle | `ChallengeRules` (pure decision) + `ChallengeService` (create / retro credit / refresh / settle / abandon) | Implemented, JVM-tested, NOT wired to the app |
| Timer bridge | `SessionStore` records real active intervals; `TimerSessionSync` commits a FINISHED timer session (idempotent id) and settles challenges on app foreground; `WorkoutService` triggers the commit when the timer finishes | Implemented, JVM-tested; NOT verified on device/emulator |
| Legacy data | `daily_workouts` kept as calendar record; `DayHistoryReader` returns legacy and session seconds separately | Implemented, JVM-tested |
| Legacy import | SharedPreferences `SetRecordStore` import | NOT approved, NOT implemented |
| Device QA | Phone / Watch4 real-device upgrade from v58 | Not verified |

## B. Approval reflection status (2026-11-15 approved policies C-20261115-01..04, D-01 B, P-21)
| Policy | Reflected in code | Where | Tests |
| --- | --- | --- | --- |
| C-01 start-day retro credit; dates before start excluded; challenge zone fixed at creation; re-attribution from UTC intervals; idempotent create | Yes | `ChallengeService.create/recomputeDate`, `ChallengeRules.secondsOnDate` | AC-01, AC-02, AC-03, AC-08 |
| C-02 target day stays ACTIVE; COMPLETE after that date closes; frozen snapshot; offline re-judgement | Yes | `ChallengeRules.decide`, `ChallengeService.settle/settleDue` | AC-05, AC-09, AC-13 |
| C-03 expiry at endDate+1 00:00 in challenge zone (DST-aware); COMPLETE wins; max one ACTIVE | Yes | `ChallengeRules.endExclusiveEpochSec/decide`, `ChallengeService.create` | AC-06, AC-07, AC-08, P-13 test |
| C-04 `daily_workouts` preserved, not converted, no credit, not summed with new sessions | Yes | no importer; `DayHistoryReader`; `recomputeDate` reads sessions only | AC-10, AC-11 |
| D-01 B below-threshold: raw segments AND permanent summary discarded; cancel keeps Draft | Yes | `SessionCommitter.commit` (`DiscardedBelowThreshold`) | AC-12 |
| P-21 ACTIVE day aggregates refresh | Yes (recompute on every stored session and `refreshActive`) | `ChallengeService` | AC-03, AC-04 |

Superseded earlier assumptions (old questions 1, 2, 3, 5): summary kept for below-threshold sessions, no retro credit,
first-600s credit freeze, and virtual `LEGACY_DAY_AGGREGATE` sessions are all removed.

## C. Confirmed by the user on 2026-11-15 (ChatGPT review result "Android 개발 정책 검토 결과 전달")
| Item | Decision | Code status |
| --- | --- | --- |
| Challenge start date | Creation day only; no past/future start, no SCHEDULED state. Earlier same-day sessions are retro-credited (C-01) | Implemented (`START_DATE_NOT_CREATION_DAY`), tested |
| Late-saved session after the end boundary | Record is kept; ended challenge (days, rate, state, snapshot) is never changed | Implemented, tested (`touchedChallenges` empty = record saved, challenge not credited) |
| Early end | Confirmed end is saved by real performance: >=60 s or >=1 completed set = valid; PARTIAL if not all done, COMPLETED if all done, NOT_PERFORMED if nothing performed; <60 s and 0 sets discarded (D-01 B); validity is separate from completion | Store/commit layer implemented, tested. `SessionStore.endEarly()` exists but NO screen calls it yet |
| Reset | Confirmed reset discards the current session's temporary data; stored past records untouched; cancel keeps state | `resetAll()` discards temporary intervals, tested. Confirmation dialog is UI (not built) |
| targetDays cap | `1 <= targetDays <= days(startDate..endDate inclusive)`; otherwise rejected | Implemented (`TARGET_EXCEEDS_PERIOD`), tested |
| Abandon | ACTIVE -> ABANDONED only after explicit user confirmation; snapshot keeps credited days, target, original period, rate, abandon time; sessions and calendar kept; later workouts never change it | Implemented (`abandon(id, confirmed)`; `NotConfirmed` when false), tested. Confirmation dialog is UI (not built) |

Not assumptions any more. Remaining development defaults (not policy, flagged): `abandon` snapshot stores the abandon time in
`endedAtEpochSec`; `SessionCompletion` is now COMPLETED / PARTIAL / NOT_PERFORMED (old STOPPED/UNKNOWN values removed;
no rows with those values were ever written by a shipped build).

## D. Remaining risks
- Timer bridge (new this slice): only sessions the user FINISHED (last stage completed) are committed, from intervals
  recorded by `SessionStore.reconcile`. Sessions saved by older app versions have no intervals and are skipped (never
  reconstructed). `endEarly()` is implemented and saved as PARTIAL, but no screen calls it yet; if the user starts a new
  session before the early-ended one is synced, the unsynced intervals would be lost (callers must trigger sync first).
- The timer has two accounting paths (`SessionStore` and the service's `history_checkpoint_ms` -> `daily_workouts`);
  both still run. The new store is additive and failures are swallowed so the timer is unaffected, but the
  interplay is unverified on a real device (multi-instance SessionStore across service/activity).
- Challenge creation UI, correction UI, Compose/Wear screens and `DefaultWorkoutRepository` are not wired.
  AC-01..13 are verified at the data layer only.
- No real-device or emulator verification, and no upgrade test from real v58 user data (Robolectric SQLite only).
- Midnight settlement relies on lazy re-derivation (`settleDue`/`refreshActive`); the app must call it on start and
  foreground. Not wired yet.
- Legacy and new records can overlap on one date; they are shown separately and never merged (reconstruction unapproved).
- SetRecordStore (SharedPreferences) import is not approved; those records remain only in the old store.
- Figma FINAL screens (09/27/33/34/39/40/68) and the recommended UX copy are not implemented.
- This work is not release-ready and must not be reported as complete.

## E. 2026-11-15 Figma 전체 화면 개발 착수 (단계별)
- Phase 0: 색상 토큰을 ROUTIVE 팔레트로 이관(tokens.json v2, 앱/워치 Move60Theme·colors.xml, 로고 생성기·문서, 하드코딩 색 제거). verify 스크립트 3종 + verify-plan 통과.
- 도전 탭 1차: `RoutiveDao.endedChallenges/renameActive`, `ChallengeService.rename/active/ended`, `ChallengeViewModel`, `ChallengeScreens.kt`, 라우트 `challenge`, 대시보드 진입 아이콘. JVM 테스트 90건 통과, Gradle 게이트(lintVital·assemble·androidTest assemble) 통과.
- `ComposeChallengeTest` 추가(생성→진행→포기 확인→지난 도전). **실행·캡처는 CI 에뮬레이터 전용이며 아직 결과 없음 → 시각 검증 미완료.**
- 대조표: docs/ROUTIVE_FIGMA_GAP_MATRIX.md. 나머지 화면군은 미착수. 실기기 검증·v58 업그레이드 검증 미완료.
- 2026-11-15 19:49 사용자 전달: 도전 정책 4개 항목(시작일 입력칸, 유산소 시간 목표, 개발 기본값 승인, 탭바/도크 우선순위)은 "모두 확정, 기존 P-12·P-14·P-24·C-20261115-01과 충돌 없음". **항목별 선택값은 전달받지 못해 코드에 반영하지 않음** (현재 구현: 시작일 필드 없음, 유산소 시간 목표 없음, 운동 중 탭바 숨김, 이름 30자).
- 4탭 하단 바 추가, 상단 중복 아이콘 제거, 탭 화면 뒤로가기 제거, 도크 하단 인셋은 탭바 숨김 시에만 적용.
- 2026-11-15 20:41 ChatGPT 확정(사용자 전달): 유산소 시간 목표 A(v1 제외, 누적 인정일수만) / 도전 이름 최대 30자 승인 / 종료 도전(COMPLETE·EXPIRED·ABANDONED) '지난 도전' 보관(탈퇴 등 데이터 삭제 정책은 별도 적용) / 포기 다이얼로그 확정 문구 적용([취소][도전 포기]) / 운동 중·일시정지 중 탭바 숨김, 종료 후 복원.
  오늘 화면군: 추천 카드는 실제 루틴 계획 결과만 표시(추천 엔진 미구현 → 없는 값 임의 생성 금지, 60분 고정 표시 제거), 진행 화면은 Figma 05–07·58·59 4단계 구조로 교체(5단계 고정·유산소①② 분할 강제 안 함), 결과는 COMPLETED/PARTIAL/NOT_PERFORMED + 저장 성공은 별도 상태(P-26·28, D-01 B, E-03 A). 추천 생성 로직은 구현 완료로 보지 않음.
  시작일 입력칸(Figma 30) 항목은 답변에 없어 기존 확정 정책(생성일 고정) 유지.
- 2026-11-15 20:42 ChatGPT 확정(선택값): ① 시작일 A(입력칸 제거, 생성 당일 자동·수정 불가) ② 유산소 시간 목표 A(v1 제외, 누적 인정 운동일수만) ③-1 이름 최대 30자 ③-2 COMPLETE/EXPIRED/ABANDONED 모두 '지난 도전' 보관(회원탈퇴 등 삭제 정책은 적용) ③-3 포기 문구 "도전을 포기할까요? 포기한 도전은 지난 도전에 남고, 운동 기록은 유지돼요. 포기는 되돌릴 수 없어요. [취소][도전 포기]" ④ 운동 진행·일시정지 중 탭바 숨김, 종료 후 복원. → 현재 구현과 일치, 포기 문구·일시정지 중 탭바 숨김 반영.
- 확정: 추천 카드는 실제 루틴 계획 결과만 표시(추천 엔진 미구현 → 없는 값 임의 생성 금지, 60분 고정 표시 제거). 운동 진행은 Figma 05–07·58·59 4단계 구조로 교체(5단계 고정·유산소①② 분할 강제 안 함). 결과(08)는 P-15 B + 판정과 DB 저장 성공은 별도 상태(P-26·28, D-01 B, E-03 A). 추천 생성 로직은 구현 완료로 보지 않음.
- 운동 진행 Compose 화면(WorkoutProgressScreen) + 종료 확인 팝업(07) + 결과(08, NOT_STORED 상태 포함) 구현. 중도 종료(endEarly)가 처음으로 UI에서 호출됨. 단계 수는 현재 엔진(5단계) 기준 — 확정된 4단계 구조·유산소 ①② 분할 제거는 루틴 모델 변경이 필요해 미구현. 세트 입력 UI는 CI 캡처 미확인(유산소/준비 단계만 캡처). 실기기·알림 경로(레거시 MainActivity) 회귀 검증 미완료.


## F. 4단계 운동 엔진 전환 (ChatGPT 최종 승인, 2026-11-15)
확정: ① 준비운동→근력→유산소→마무리(유산소 1단계, 내부 구간은 단계 아님) ② 단계 시간=루틴 계획값(null→0/기본값 금지, 근력은 세트 기준·시간 강제종료 없음, 미확정 전환은 사용자 조작) ③ 유산소①② 슬라이더 신규 UI 제거(기존 데이터 보존) ④ 업데이트 당시 진행 세션은 5단계로 완료 ⑤ 운동환경·추천카드 v1 포함(가짜 추천 금지) ⑥ 심박 10초·30~240 유지, 초과 시 "수신 대기".

### 구현 상태 (이번 커밋)
- 구현 완료·JVM 검증: `domain/WorkoutStructure.kt`(StructureVersion LEGACY_5/ROUTIVE_4, 마커 없는 저장값=LEGACY_5, 단계 라벨, TransitionMode, StageTimePlan null 보존), `domain/HeartRateFreshness.kt`(휴대폰 UI 4곳 통합, 문구 "수신 대기").
- 미착수(엔진 미전환): SessionStore/MainActivity/WorkoutService/위젯/Wear의 5단계 고정 로직(STAGE_COUNT=5)은 그대로. 신규 세션이 4단계로 시작되지 않음.
### 차단 요소 (보고)
- 정책 ②의 "루틴에 저장된 계획시간" 원본(루틴 생성·저장·편집 16·17·41, 운동공간 61·62, 최초 설정 01·02)이 아직 없음. 현재 루틴은 workouts.json 고정 60분 자산. 원본 없이 4단계 엔진을 켜면 시간을 임의 부여하게 되어 정책 위반.
- Wear(`WatchActivity`)는 별도 모듈이라 도메인 규칙을 공유하지 못하고 "연결대기" 문구가 남음. 10초/30~240 판정 자체는 동일.
### 다음 순서 제안
1) 루틴 계획 저장 모델(Room 추가 마이그레이션, 단계별 nullable plannedSeconds) 2) 세션 스냅샷에 structureVersion 추가(없으면 LEGACY_5) 3) 4단계 세션 시작 경로 4) 폰·워치·알림·위젯 단계 라벨 공유 5) 01·02·61·62·16·17·41 화면.

### F-2. 루틴 계획 저장 모델 (Room v3, 추가 전용)
- `routine_plans`, `routine_stage_plans` 신규(기존 테이블 무변경). `MIGRATION_2_3` + `schemas/3.json`. 단계별 `plannedSeconds`는 null 허용, 0/기본값 저장 금지.
- `RoutinePlanService.save/load`: 이름 1~30자, 공유 `RoutineDurationPolicy`로 검증(알려진 합이 목표 초과·전부 알려졌는데 불일치·0 이하 거부, null은 허용=E-02 B), 중복 ID 덮어쓰기 거부, 세션/기록 불변.
- 검증: JVM(RoutinePlanServiceTest 6, RoutiveMigrationTest v2→v3 1 + 기존 업그레이드 테스트를 v3 경로로 갱신). 실기기 업그레이드·CI는 미확인.
- 참고: 앱에서 이 서비스를 호출하는 화면(16·17·41)은 아직 없음 → 사용자에게 보이는 변화 없음.

### F-3. 세션 구조 마커 (structureVersion)
- `SessionStore.structure`(StructureVersion)를 세션 JSON에 `structureVersion`으로 저장. 마커 없음/알 수 없는 값 = LEGACY_5로 읽고 진행 상태(단계·경과·실행 중 여부·구간)를 그대로 유지(A안: 기존 세션은 5단계로 완료).
- 현재는 새 세션도 LEGACY_5로 시작(4단계 엔진 미구현). ROUTIVE_4를 쓰는 경로는 아직 없음.
- 워치/위젯으로 보내는 스냅샷에는 아직 구조 정보를 싣지 않음(다음 단계에서 라벨 통일 시 추가).
- 검증: JVM SessionStructureCompatTest 3(마커 없는 저장 세션 복원·진행 유지·재개, 알 수 없는 마커 폴백, 신규 마커 저장). 실기기·CI는 푸시 후 확인.

### F-4. Figma 41 루틴 직접 구성 (구현 완료·CI 캡처 대기) / 번호 정정
- **번호 정정:** Figma 16=추천 루틴 상세 확인, 41=루틴 직접 구성·편집, 17=근력 종목 동작 상세(루틴 편집 아님). 이전 문서의 "16·17·41 루틴 생성·편집" 표기는 부정확.
- 구현: Room v4 `routine_exercises`(+`MIGRATION_3_4`, schemas/4.json), `RoutinePlanService.save(..., exercises)`(운동 1개 이상 필수=E-02 B, 시간 null 허용), `RoutineComposeViewModel/Screen`, 홈 카드 "루틴 직접 구성"(`dashboard_compose_routine`) → `routine_compose` 경로. 목표시간 비우면 "미정"(60분 고정 아님). 후보 종목은 앱 카탈로그(workouts.json)에서만 가져옴.
- 의도적 미구현: 16(추천 근거 문구는 추천 엔진이 없어 가짜 추천이 됨), 17의 "실제 동작 영상"(영상 자산 없음, 만들지 않음), 보유 기구 필터(61·62 미구현 → "등록된 기구가 아직 없어요"), 저장한 루틴 목록/편집/시작(4단계 엔진 미구현), 실패 시 Draft 영속(현재 화면 상태 메모리만).
- 검증: JVM 테스트(서비스·v3→v4 마이그레이션), androidTest `ComposeRoutineComposeTest`(빈 상태·선택·오류·저장 캡처) — CI 결과/캡처는 푸시 후 확인. 실기기 미검증.

### F-5. 41 루틴 구성 CI 결과 (커밋 91a7e5a, 2026-11-16)
- CI 전체 통과(JVM + 에뮬레이터 스모크). 실제 에뮬레이터 캡처 확인: `compose-routine-empty/picker/error/saved.png`(아티팩트 `move60-smoke-evidence`, run 43728034683). 실기기 미검증.
- 발견·수정: (1) 시간 오류 문구가 목록 맨 아래라 스크롤 전에는 안 보임 → 확정 버튼 위로 이동(화면 결함). (2) 기존 `PhoneFeaturesTest.movingToAnotherItemKeepsActiveTime`가 요일 의존(월·수 루틴은 유산소 1~2개 vs 테스트 ≥3 요구)이라 월요일(KST) CI에서 실패 → 요일을 화요일로 고정(검증 약화 아님).
- Figma 41과의 차이(미해결): Figma는 단계 행이 `>` 이동 행이고 시간 입력이 없는데, 구현은 단계 카드에 계획시간 입력란이 포함됨. 탭바는 Figma와 동일하게 유지. 보유 기구는 61·62 미구현으로 빈 상태.
- 남은 것: 저장 루틴 목록·편집·시작(4단계 엔진 필요), 16 추천 상세, 17 동작 안내(영상 없음), 실패 Draft 영속.

### G. ChatGPT 인수인계 Phase 1 (2026-11-16)
- **P0-1 재현 확인(코드):** `TimerSessionSync.bundleOrNull`이 `completedStrengthSets=0`·빈 세트 목록으로 저장 → 59초+완료 세트 1개가 D-01 B로 폐기될 수 있었음.
- **수정:** `SetRecordStore.completedSetsForSession(token)`(해당 운동의 완료·유효 세트만, 종목 ID·세트 번호 파싱) → `TimerSnapshot.completedSets` → `SessionSetEntity(COMPLETED)` + `completedStrengthSets`. `Move60Application.syncTimerSessionAsync`가 SetRecordStore를 전달. 중복 저장은 기존 커밋 멱등성(AlreadyCommitted)으로 보호.
- **JVM 검증(SessionSetPersistenceTest 6):** 59초+세트1=저장·유효, 59초+세트0=폐기, PARTIAL(조기 종료)도 세트 보존, 반복 동기화 시 중복 없음, 다른 운동 세트·미완료·무효 세트 제외, 실행 중 세션 스냅샷에 실제 세트 포함.
- **한계:** (1) 세트 편집은 운동 종료 전까지만 가능하다는 전제. 이전 빌드가 세트 0개로 이미 커밋한 세션과 같은 ID로 다시 커밋하면 Conflict로 보고됨(Room v2는 미출시로 판단, 실제 사용자 데이터 없음 — 출시 이력 확인 필요). (2) 에뮬레이터/실기기 end-to-end(Compose에서 세트 완료 → 종료 → 결과 화면 세트 수) 아직 미검증. (3) P1-1(저장 완료 전 새 세션 시작 시 구간 손실) 미착수.
- **41 정책 확정 반영:** 단계 시간 별도 입력 제거, 종목별 시간(null 허용·커스텀 값 그대로 보존)에서 단계 합계 산출. 한 종목이라도 시간 미정이면 단계 시간 미정. `RoutinePlanService.save(routineId, name, target, exercises)`로 시그니처 변경. 유산소 내부 구간 합산은 아직 구간 모델이 없어 종목 단위 시간만 지원.

### H. P1-1 저장 확정 전 세션 교체 보호 (2026-11-16, 구현 완료·CI 확인 대기)
- **재현(코드 추적):** 종료/조기 종료한 세션은 Room 커밋이 확정되기 전까지 단일 일자 슬롯(`SessionStore`)에만 있음. 커밋은 비동기(`syncTimerSessionAsync`)이고 실패하면 재시도는 "현재 슬롯"만 읽음 → 그 전에 `start()`(새 운동), `resetAll()`, `selectBodyFocus`, `setDay`, 날짜 전환이 슬롯을 덮어쓰거나 다른 슬롯으로 옮기면 이전 구간·세트가 유실될 수 있었음.
- **수정:** `PendingSessionOutbox`(SharedPreferences `move60_pending_sessions_v1`)에 종료/조기 종료된 세션의 실제 구간·완료 세트를 슬롯 교체 **직전에** `commit()`으로 복사. `Move60Application.syncTimerSessionAsync`가 현재 슬롯 동기화 뒤 `drain`으로 재시도. 멱등 커밋 결과가 Committed/AlreadyCommitted/DiscardedBelowThreshold일 때만 항목 삭제, Failed/Rejected/Conflict는 시도 횟수·오류를 기록하고 보관(원본 유일 사본이므로 삭제 금지).
- **의도적으로 보관하지 않는 것:** 진행 중(미종료) 세션의 초기화(P-28 A, 사용자 폐기). 기존 Room·SharedPreferences 데이터는 변환·삭제하지 않음(보관함은 신규 파일, 기존 슬롯 형식 무변경).
- **검증:** JVM `PendingSessionOutboxTest` 8(새 시작·초기화 시 보관, 진행 중 초기화는 보관 안 함, Conflict·검증 불가 항목은 보관 유지·저장 데이터 불변, 이미 저장된 세션 정리, 60초 미만 폐기 후 정리, 중복 enqueue가 재시도 정보 유지). androidTest 2건 추가: 세트 완료→조기 종료→결과 화면 "1세트"·"저장됨"(캡처 `compose-workout-result-with-set.png`), 저장 전 새 운동 시작→이전 세션이 Room에 저장되고 보관함이 비워짐(UI 없이 실제 SessionStore·Room·SharedPreferences를 에뮬레이터에서 구동: 앱을 열면 포그라운드 동기화가 먼저 커밋해 상황 자체가 사라지기 때문). 첫 CI 시도에서 이 테스트는 이 앱 동작(종료된 세션이 있으면 개요가 결과 화면으로 이동) 때문에 실패했고 테스트 설계를 수정함. 로컬 SDK가 없어 Gradle 실행은 CI에서만 확인. 실기기 미검증.
- **남은 위험:** (1) 보관함은 Conflict(같은 ID, 다른 내용)를 자동 해소하지 않음 → 이전 빌드가 세트 0개로 커밋한 세션이 있으면 영구 보관됨(사용자 데이터 보존 우선, 해소 정책은 별도 결정 필요). (2) 앱 데이터 삭제 시 보관함도 함께 사라짐(OS 동작). (3) 결과 화면은 현재 세션 기준이며 보관함에서 나중에 저장된 세션의 결과 진입 경로는 아직 없음.
- **CI 결과(커밋 477b599, 2026-11-16):** JVM 테스트·에뮬레이터 스모크(76개) 모두 통과(run 43737527846/43737527847). 실제 에뮬레이터 캡처 확인: `compose-workout-result-with-set.png`(4초 + 완료 세트 1 → "운동을 일부 마쳤어요 / 저장됨 / 0분 4초 / 1세트", P0-1 end-to-end 확인). 실기기 미검증.
- **CI 반복에서 발견한 사항:** (1) 앱을 열면 종료된 세션이 있을 때 개요가 결과 화면으로 이동하고 포그라운드 동기화가 먼저 커밋함(제품 동작, 의도됨) → 보관함 시나리오는 UI 없이 검증. (2) 이미 저장된 종료 세션도 초기화/새 시작 시 보관함에 들어갔다가 다음 동기화에서 AlreadyCommitted로 정리됨(무해한 중복 처리). (3) 기존 androidTest `ensureIdleOverview`가 앞 테스트의 종료 세션 때문에 결과 화면에 머무를 수 있어 뒤로 가기 처리와 실패 시 화면 캡처를 추가(검증 약화 아님).

### I. P0-2 5단계 가정 제거 1차 (2026-11-16, 구현 완료·CI 확인 대기)
- **점검 결과(코드):** 세션 단계 의미를 인덱스 리터럴로 판단하던 곳 — 근력 `stage == 2`(MainActivity 7곳, TrainingProgress 2, SessionStore.startRest, WorkoutRepository 4, WorkoutProgressScreen 2, WorkoutService, WorkoutWidgetModel `stage == 1 || 3`/`== 2`), 마지막 단계 `LAST_STAGE`(SessionStore.nextStage/jumpTo/load, MainActivity 2, WorkoutRepository). 4단계에서는 index 2가 유산소이므로 그대로 두면 유산소 단계에서 세트 입력·휴식 로직이 켜짐.
- **수정:** `WorkoutStructures.kindAt/strengthIndex/lastIndex` 추가. `SessionStore.kindOf/isStrengthStage/lastStageIndex/engineSupported`로 구조 기반 판단. UI 상태에 `strengthStage` 추가. 워치 스냅샷·위젯 상태 JSON에 `structure` 필드 추가(서비스 알림·위젯은 종류 기반으로 판단, 마커 없으면 LEGACY_5). 5단계 루틴 **데이터** 색인은 `LEGACY_STRENGTH_DATA_INDEX`로 명명(세션 단계 의미와 분리).
- **가드:** 엔진이 실행할 수 없는 구조(ROUTIVE_4 표식 슬롯)는 `start/nextStage/jumpTo`가 무시하고 슬롯을 읽기만 함(덮어쓰기·진행 없음, 데이터 보존). 종료된 슬롯은 기존대로 보관 후 새 LEGACY_5 세션으로 교체 가능. 현재 빌드는 ROUTIVE_4를 생성하지 않으므로 도달 경로는 다운그레이드/신규 빌드 기록뿐.
- **검증 스크립트 변경(약화 아님):** `verify-compose-architecture.py`의 `0..LAST_STAGE`/`stage >= LAST_STAGE` 문자열 확인을 구조 기반 `0..lastStageIndex`/`stage >= lastStageIndex` + `isStrengthStage`/`engineSupported` 존재 확인으로 교체하고, 앱 소스에 `stage == 2`/`!= 2`/`== LAST_STAGE` 리터럴이 남으면 실패하는 확인을 **추가**함(HistoryViewModel 제외).
- **테스트:** JVM `StageSemanticsTest` 4(구조별 근력/마지막 인덱스, 레거시 단계 의미 유지, ROUTIVE_4 슬롯은 4단계 의미로 읽히고 진행·재작성되지 않음, 종료된 ROUTIVE_4 슬롯 교체 가능). 로컬 SDK 없음 → Gradle은 CI에서만 확인. 실기기 미검증.
- **남은 5단계 가정(Phase 3·4):** (1) `HistoryViewModel`의 `entry.stage == 2` — StageLog 행에 구조 정보가 없어 레거시 기록으로만 해석 가능, 4단계 기록 도입 시 StageLog에 structure 필요. (2) `WorkoutService/WidgetModel`의 `phasesKeyForStage`, 단계 이름 배열, `stageSeconds` — 5단계 루틴 데이터 키(phases0/phases/phases3/phases4) 기반. (3) `WatchStatePublisher`·Wear의 `STAGE_COUNT = 5`, `LAST_STAGE`, 타일 진행률, "연결대기". (4) `WorkoutData`의 5단계 루틴 모델·유산소①② 슬라이더(`setCardioSplit`, RangeSlider). 모두 ROUTIVE_4 루틴/엔진 구현과 함께 교체 필요.

### J. Phase 3 1단계: ROUTIVE_4 순수 진행 모델 (2026-11-16, 구현 완료·CI 확인 대기, 엔진 연결 미착수)
- **범위:** `domain/FourStageProgress`(Android·시계 무의존). 저장 루틴의 단계 계획 시간(`StageTimePlan`)으로 4단계(준비운동→근력→유산소→마무리) 진행 상태를 계산. **아직 `SessionStore`/화면/서비스에 연결하지 않았고, 새 세션은 여전히 LEGACY_5로 시작함.** 사용자 눈에 보이는 변화 없음.
- **정책 반영:** 계획 시간 null은 0·기본값이 되지 않고 남은 시간도 null("미정"); 근력은 세트 완료 기준이며 계획 시간이 지나도 강제 종료 안 함; 비근력 단계는 계획 시간 도달 시 "도달"(PLAN_REACHED) 표시만, 시간 미정이면 수동(MANUAL); **어떤 전환도 자동 아님**(다음·건너뛰기·이전·조기 종료는 모두 사용자 동작); 단계별 실제 경과 시간은 이전/건너뛰기 후에도 보존; 유산소 내부 구간은 단계를 만들지 않음; 4단계 형태가 아닌 계획(5단계·순서 불일치)은 거부.
- **내가 둔 가정(ChatGPT 확인 필요):** (1) 마지막 단계(마무리)를 건너뛰면 완료가 아니라 **조기 종료(PARTIAL)**로 처리. (2) 건너뛴 단계로 "이전"으로 돌아가면 건너뜀 표시 해제. (3) 근력 세트가 모두 끝나도 단계는 자동으로 넘어가지 않고 사용자가 "다음"을 눌러야 함(기존 "카운트다운으로 자동 진행 금지" 원칙 유지). 확정 정책과 충돌하는 부분이 있으면 알려주세요.
- **테스트:** JVM `FourStageProgressTest` 9. Gradle은 CI에서만 확인.
- **다음(2단계):** 세션 시작 경로(저장 루틴 선택 → ROUTIVE_4 슬롯 생성, `engineSupported` 확장), 근력 세트·휴식, 배경/프로세스 복구, 화면의 4단계 표시와 ①② 슬라이더 제거, 알림·위젯·워치 동기화. 루틴이 없을 때의 시작 화면은 ChatGPT 정책 검토 필요.

### K. 저장한 루틴 목록 (2026-11-16, 구현 완료·CI 확인 대기)
- **범위:** 홈의 "루틴 직접 구성" 카드 안 "저장한 루틴 보기"(48dp) → `routine_list`. 읽기 전용 목록(이름, 목표 시간 또는 "목표 시간 미정", 운동 수, 4단계별 계획 시간). 시간 null은 "미정", 150초는 "2분 30초"(반올림 없음). 빈 상태·불러오기 실패(저장 루틴은 그대로라는 안내 + 다시 시도). 목록 조회는 데이터를 변경하지 않음(삭제·수정 없음). 읽지 못한 행은 건너뛸 뿐 삭제하지 않음.
- **하지 않은 것:** 루틴 선택→운동 시작, 편집, 삭제, 추천. 시작은 Phase 3 2단계(4단계 세션 생성)와 함께.
- **테스트:** JVM `RoutineListTest` 4, 에뮬레이터 `ComposeRoutineListTest`(Room에 실제 저장 후 화면에서 정확한 값·미정 확인, 캡처 `compose-routine-list.png`). 빈 상태·실패 상태는 화면 캡처로 확인하지 못함(에뮬레이터 DB에 다른 테스트의 루틴이 남을 수 있어 빈 상태를 재현하지 않음).
- **CI 결과(커밋 5b9aeb4):** JVM·에뮬레이터 스모크(77개) 통과(run 43780083297/43780083305). 실제 에뮬레이터 캡처 확인: `compose-routine-list.png`(저장 루틴 2개, 2분 30초·30분·미정 정확 표시, 하단 탭 유지, 하단 "루틴 만들기" 버튼). 실기기 미검증. CI 반복에서 고친 것은 모두 테스트 쪽(정책에 맞지 않는 픽스처로 저장이 거절됨, 잘못된 import, 중복 노드 단언)이며 앱 코드 결함은 아님.
