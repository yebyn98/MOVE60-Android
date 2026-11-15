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
