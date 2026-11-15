# ROUTIVE v2 implementation kickoff — 2026-11-15

## Source of truth
- Figma CURRENT IA, Task/State Flow, six scenarios: https://www.figma.com/design/KA37z3LKnJi0tFkzwsCt4z?node-id=1516-12183
- Strength execution prototype: https://www.figma.com/design/KA37z3LKnJi0tFkzwsCt4z?node-id=1513-438
- Notion: currently approved P-01 to P-31, D-01 to D-04, R-01 to R-03, E-01 to E-03. Older v1.2 diagrams are historical.
- ROUTIVE is the user-facing brand. Keep legacy com.move60.workout application ID until a safe migration and update-impact analysis.

## First safe code slice
The new pure Kotlin RoutinePlanningPolicy domain is not yet wired to the legacy 60-minute generator or running sessions. This preserves existing timer, app background behavior, saved activity and Wear OS integration while the data model is migrated.
- A planned exercise can have a null duration; unknown is never the same as zero.
- E-02 B: at least one exercise allows confirmation with unknown total planned time unless already-known durations exceed a stated time budget.
- Fully known durations must equal a stated target; no compulsory 60-minute target.
- Duplicated exercise identities and duration overflow are rejected.
- A cardio interval work segment has a positive duration; a recovery segment, when present, has a positive duration. Explicit absence is null with hasRecoverySegment=false, not a fake 0-second segment.
- Values in tests are arithmetic boundary examples, never automatic exercise prescriptions.
- Unit-test coverage added and JVM unit tests included in Android smoke CI.

## Subsequent implementation slices — not completed
1. Versioned RoutinePlan, CardioIntervalRound, Session, Challenge and AdjustmentHistory data contracts; migration from legacy SharedPreferences and Room.
2. Feature-flagged routine composition replacing hard-coded 60-minute split; unknown time and manual/STEADY cardio supported.
3. Numeric editing and strict saved Draft/Stored persistence: ACK-success only commit, retained Draft on failure, idempotent retry, revision conflict handling.
4. Session validity separate from challenge credit: 60 active seconds OR 1 completed strength set for valid session; challenge day when valid-session active time reaches 600 seconds, maximum one credit day.
5. UI integration for the four GNB tabs and Android/Watch4 actual-device QA.

## Validation boundary
- Changed source is in a feature branch for review, not on main.
- Running Android or Wear OS app, state restoration, existing session database migration, prototype full click E2E, and watch hardware behavior were NOT verified by adding the pure-Kotlin domain layer.
- PR must pass JVM unit tests, existing build/lint/emulator gates and peer review before merge.
## Second safe code slice — recognition rules and release CI (2026-11-15)

- Added isolated Kotlin WorkoutRecognitionPolicy and JVM tests: saved session valid at >=60 active seconds OR >=1 truly completed strength set; challenge date credited at >=600 summed active seconds from valid stored sessions, at most once per local date.
- Idempotent session/day keys prevent double credit from identical stored retries; conflicting saved snapshots fail closed. A session spanning midnight must split actual active-time intervals by local date, never wall time. Pauses, skipped sets, pending drafts and failed saves do not qualify.
- This policy evaluator is NOT connected to Room, SharedPreferences, the live session, Challenge UI or Wear OS. The name StoredWorkoutDayContribution is a contract for the future database reader, not a persistence implementation.
- PR runtime smoke checks the release comparison algorithm without demanding a version bump in every development PR. Version verification remains mandatory immediately before manual publish, and the publisher again rejects non-increasing phone/watch versions.
- Main branch pushes still run existing build/emulator gates but no longer publish updates automatically. A release requires explicit workflow_dispatch on main, passing all existing CI gates and release version checks.
- No automatic merge, production distribution, database migration or watch hardware verification occurred in this slice.

### Follow-on P0 gate

1. Versioned session/active-interval/strength-set/challenge/adjustment tables and legacy import contract, with rollback and user-data preservation tests.
2. Transactionally committed session snapshots to the evaluator (Draft -> Saved / SaveError -> Retry); same sessionId must be idempotent through process death and retries.
3. Connect flexible routine/interval plans to the existing timer under a guarded migration, then phone and Watch4 end-to-end validation.

## Third slice — Room v2 schema, safe migration and atomic session commit (2026-11-15)

Implemented in code and covered by JVM tests (real Room via Robolectric, JDK 17/21):
- `WorkoutDatabase` v1 -> v2, additive only (`RoutiveMigrations.MIGRATION_1_2`, 14 CREATE statements). Legacy
  `daily_workouts` / `workout_sets` and every SharedPreferences store are untouched. DB file name unchanged
  (`move60_workouts_room_v1.db`) so shipped installs upgrade in place. No destructive fallback.
- v1 schema baseline (`app/schemas/.../1.json`, identityHash c93bb39a...) was exported BEFORE any entity change;
  `2.json` is the Room-generated v2 contract. Migration SQL is copied from it and validated against it.
- New tables: `sessions`, `session_day_slices`, `session_segments`, `session_sets`, `session_adjustments`
  (append-only: DAO has no update/delete), `challenges`, `challenge_day_credits`, `session_drafts`, `legacy_import_ledger`.
- `SessionCommitter`: one Room transaction per finalized session; validity recomputed from `WorkoutRecognitionPolicy`
  (caller cannot assert it); identical retry = no-op, same id with different content = Conflict (stored data unchanged);
  failure rolls back everything and keeps the Draft (state FAILED) for retry; SAVING drafts interrupted by process
  death become UNKNOWN_OUTCOME and retry is safe; stale draft revisions are rejected.
- Challenge credit is derived only from committed VALID sessions (>=600 s summed per local date, max one credit per
  challenge per date), independent of ordinary-session validity.

NOT done / NOT verified in this slice:
- Nothing above is called from the running app yet: `DefaultWorkoutRepository`, `SessionStore`, the legacy timer and
  all Compose/Wear screens still use the old path. The SessionCommitter is only provided through Hilt.
- Legacy import (SharedPreferences `move60_set_records_v1`, `move60_workout_history_v1`, Room `daily_workouts`) into
  the new tables is NOT implemented; the ledger table exists but no importer writes to it yet.
- Migration was verified with Robolectric's SQLite, not on a physical device or an emulator with real v58 user data.
- Challenge lifecycle (create/abandon/expire/auto-complete), correction UI and Journey are not implemented.
