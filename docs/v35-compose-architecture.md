# MOVE60 v35 — Kotlin Compose Architecture Migration

## Changed (phone only)
- New launcher `architecture.ComposeActivity`, Jetpack Compose Material 3 + Compose Navigation for Dashboard / Calendar / Settings.
- New Hilt-injected `WorkoutViewModel` / `HistoryViewModel` / `SettingsViewModel` expose `StateFlow`; transient errors use `SharedFlow`.
- `WorkoutRepository` is the single entry to legacy `SessionStore`, `WorkoutService`, current-day `EnergyLedger` and watch sync, with coroutine-safe locking.
- Room `WorkoutDatabase` stores durable day and set projections with `WorkoutDao`; current sessions and earlier records are **not** deleted or prematurely moved.
- `WorkoutHistorySyncWorker` via Hilt WorkManager writes up to 35 days of day summaries and retries on transient errors, but **does not own or run an active 60-minute workout timer**.
- User preferences remain compatible with the existing `preferences` file but are presented through injected Repository and reactive `StateFlow`.
- Original Android View-based detailed workout editor remains accessible from Compose during migration. It still handles movement swap, photos, complete editing and notification deep links. This is a **staged migration**, not a claim all legacy UI has been deleted.
- New Compose instrumentation verifies dashboard/calendar/settings Navigation and Room day upsert/Flow. All 25 original phone regressions continue alongside these tests (27 total).
- Canonical tokens from `Move60Theme` are reused in Compose `darkColorScheme`; Android 35/AGP 8.7.3 keeps a compatible older Compose BOM rather than blindly upgrading to Compose 1.12, which requires API 37/AGP 9.1.1.

## Safety / release conditions
- Phone v35 only, Watch4 binary remains at v33 with original signature/hash.
- Only `build && runtime-smoke && wear-gate` successes allow automatic publication.
- Existing `MOVE60-Updates/latest.json` phone+watch metadata must preserve watch fields unchanged on phone-only release.
- Full migration of the legacy exercise media/swap/editor is remaining work. Do not remove those screens until equivalent Compose UI and regression coverage exist.

## Current-day calorie semantics
- `오늘 누적`: Korea calendar date, starts at zero before first workout that day; adds multiple same-day workouts; pauses and finished sessions stop adding; resets to 0 on next date while historic calendar days remain preserved.
