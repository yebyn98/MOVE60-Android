# ROUTIVE AI handoff log (Claude <-> ChatGPT)

Two sections are kept strictly separate. Policy items are NOT decided by development.

## A. Development changes (Claude) — 2026-11-15, branch feature/routive-policy-domain-v1 (PR #49, Draft)
| Area | Change | Status |
| --- | --- | --- |
| Room schema | v2 additive tables (11) + `MIGRATION_1_2` + exported schemas 1.json/2.json | Implemented, JVM-tested (Robolectric) |
| Atomic save | `SessionCommitter` (commit, idempotency, conflict, draft retry, interrupted-save recovery) | Implemented, JVM-tested, NOT wired to the app |
| Time source | Sessions store UTC active intervals (`session_active_intervals`); day slices are derived per session zone (P-20) | Implemented, JVM-tested |
| Challenge lifecycle | `ChallengeRules` (pure decision) + `ChallengeService` (create / retro credit / refresh / settle / abandon) | Implemented, JVM-tested, NOT wired to the app |
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

## C. Assumptions made by development (NOT policy; confirm or reject)
1. Challenge creation is limited to `startDate == creation date in the challenge zone` (`START_DATE_NOT_CREATION_DAY`).
   Past/future start dates are an unapproved exception and are rejected.
2. A session saved after a challenge has already ended never changes it (the challenge is settled before the insert).
   Late-saved-session credit is an unapproved exception.
3. `abandon` is minimal: terminal ABANDONED with a snapshot of days credited so far.
4. No rule exists for `targetDays` larger than the number of days in the period; it is currently accepted.
5. The correction path (P-22/P-23: user edits of stored evidence) is not built. Re-derivation (`refreshActive`) and
   additional sessions are tested; edits are simulated with raw SQL in tests.

## D. Remaining risks
- Nothing above is called from the running app: timer, `DefaultWorkoutRepository`, Compose/Wear screens, and the
  start/end-session UI still use the old path. AC-01..13 are verified at the data layer only.
- No real-device or emulator verification, and no upgrade test from real v58 user data (Robolectric SQLite only).
- Midnight settlement relies on lazy re-derivation (`settleDue`/`refreshActive`); the app must call it on start and
  foreground. Not wired yet.
- Legacy and new records can overlap on one date; they are shown separately and never merged (reconstruction unapproved).
- SetRecordStore (SharedPreferences) import is not approved; those records remain only in the old store.
- Figma FINAL screens (09/27/33/34/39/40/68) and the recommended UX copy are not implemented.
- This work is not release-ready and must not be reported as complete.
