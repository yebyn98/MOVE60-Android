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

