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
