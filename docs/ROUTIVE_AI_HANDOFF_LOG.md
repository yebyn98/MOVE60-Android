# ROUTIVE AI handoff log (Claude <-> ChatGPT)

Two sections are kept strictly separate. Policy items are NOT decided by development.

## A. Development changes (Claude) — 2026-11-15, branch feature/routive-policy-domain-v1 (PR #49, Draft)
| Area | Change | Status |
| --- | --- | --- |
| Room schema | v2 additive tables + migration + exported schemas | Implemented, JVM-tested (Robolectric) |
| Atomic save | `SessionCommitter` (commit, idempotency, conflict, draft retry, interrupted-save recovery) | Implemented, JVM-tested, NOT wired to the app |
| Recognition | Valid session vs challenge-day credit computed at commit from stored rows | Implemented, JVM-tested |
| Legacy data | Importer for SharedPreferences / `daily_workouts` | Not started (ledger table only) |
| Device QA | Phone / Watch4 real-device upgrade from v58 | Not verified |

## B. Policy questions for ChatGPT / product owner (assumptions made so development could continue)
1. D-01 B: the source text says "below-threshold raw data is deleted at session end". Assumed scope = raw interval
   segments only; the session summary row (validSession=false, counts for nothing) is kept to avoid deleting user
   records. Confirm whether the summary row must also be removed.
2. Challenge credit is evaluated when a session is committed. A challenge created later on the same date does not
   retroactively credit earlier same-day sessions. Confirm intended behaviour.
3. A credited day keeps the seconds snapshot taken when first credited (max one credit per date); later sessions the
   same day do not rewrite it. Confirm this matches the report/statistics expectation.
4. `targetDays` semantics and COMPLETE / EXPIRED transitions are not implemented because the rule is not defined in
   the material available to development.
5. Legacy `daily_workouts` days have no session identity. Proposed: import as `LEGACY_DAY_AGGREGATE` sessions with
   completion UNKNOWN (never COMPLETED). Confirm before the importer is written.
