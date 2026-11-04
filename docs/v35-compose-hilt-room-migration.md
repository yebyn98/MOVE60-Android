# MOVE60 v35 — Kotlin / Jetpack architecture migration (phone only)

## Implemented scope
- Launcher: `ComposeHomeActivity` with Material 3 and Compose Navigation routes `today`, `history` and `settings`.
- ViewModel: `ComposeHomeViewModel` supplies immutable Compose UI states through `StateFlow`, one-time navigation and messages through `SharedFlow`, and uses `viewModelScope` / `repeatOnLifecycle(STARTED)`.
- Data: injected `WorkoutDashboardRepository` mirrors only dated active time and estimated daily kcal into `Room` `DailyWorkoutEntity`/`DailyWorkoutDao`. Existing `WorkoutService`, `SessionStore`, `SetRecordStore`, `WorkoutHistory`, and `EnergyLedger` remain the source of truth. **Never replace or migrate-delete** their preferences.
- Hilt: `@HiltAndroidApp`, a typed DI module and an `@HiltViewModel`. The new UI never constructs repositories or session stores directly.
- Background: `WorkoutHistorySyncWorker` with an injected `CoroutineWorker` asynchronously refreshes recent calendar cache every 12 hours. **WorkManager does not run the live workout clock.** Existing foreground service maintains active workout time/notification and phone–Watch4 sync.
- Full workout editing, time controls, stage transitions, and advanced settings still open the original native Activity until an explicitly tested Compose replacement exists.
- The previously used real-black, dark-card, electric-green palette is reused directly from `Move60Theme`.
- Compose launcher opens first; the existing MainActivity stays accessible for workout controls and old 25 regression tests.

## Data semantics
- Korean calendar date `Asia/Seoul`.
- Today kcal is obtained via legacy `EnergyLedger.estimate(date, pendingSnapshot)` and refreshed on screen STARTED; it is not lifetime or individual-session kcal.
- Same-day sessions accumulate; next date is separate. Pauses / termination are handled by the existing foreground service's checkpoint logic.
- Room rows are re-created from known legacy records; caching errors never authorize a reset of original records.

## Why incremental
A whole-app immediate swap would remove carefully validated native ForegroundService and Wear OS interoperability from the production app. This first slice deliberately modernizes the UI entry, navigation, reactive read model, DI and background cache while keeping the working exercise engine. Do not claim **every workout screen** is Compose until the set editor, timer and transitions are ported and tested.

## Release
- Phone versionCode 35, Wear versionCode remains 33.
- Only phone=true / watch=false in `scripts/release-targets.json`.
- Required `ComposeFoundationTest` in addition to the 25 established phone tests: all **26** tests must pass, plus Wear's unchanged mandatory UI gate.
- The normal `build`, `runtime-smoke`, `wear-gate`, `publish` checks all remain hard release dependencies.
