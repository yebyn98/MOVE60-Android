# MOVE60: shared contributor instructions (Claude / ChatGPT / humans)

## Source of truth
- Read `README.md`, `design/tokens.json`, `app/src/main/assets/workouts.json`, `docs/v36-health-ui-and-consent.md`, the relevant Android sources and tests before editing.
- Main user flow: Compose overview -> legacy native detail exercise execution. Never duplicate controls or assume native detail has completed Compose migration.
- Read and write actual repository files. Do not assert a UI or runtime result based on static inspection alone.

## Code changes
1. Create a feature branch; do not commit incomplete changes to `main`.
2. Trace the data flow through callers and tests before editing. Update code, permissions, UI text, tests, and docs together.
3. Use shared theme tokens and Material3 in Compose. In native UI use `DashboardUi` and `Move60Theme`; avoid ad hoc hardcoded pixel values, clipping and edge-to-edge regressions.
4. Keep header and bottom controls clear of system bars; target at least 48dp for touch controls.
5. No fabricated measurements, health data, videos, test evidence or screenshots. Health Connect access requires explicit opt-in; do not make permission prompts automatic.
6. Protect user workout history, persisted preferences, set records, and upgrade compatibility. Never delete user records during code cleanup.
7. Delete code, assets and documents only after checking all references (including tests, resources, manifests, workflows and release scripts). Do not delete used legacy paths before the replacement is verified.
8. Never bypass or water down failing validation to make a build green. Fix the actual defect or report blockers.

## Required checks
- `python3 scripts/verify-v36-ui.py`
- `python3 scripts/verify-compose-architecture.py`
- `python3 scripts/verify-design-tokens.py`
- `node scripts/verify-plan.cjs`
- `gradle --no-daemon :app:lintVitalRelease :wear:lintVitalRelease :app:assembleDebug :wear:assembleDebug :app:assembleDebugAndroidTest :wear:assembleDebugAndroidTest`
- PR checks: phone instrumentation and round Wear OS emulator.
- Release only after all required checks and signed APK publishing pass. Physical device verification must be reported separately.

## Required visual evidence at task completion
- For EVERY UI change, capture the real screen in an emulator or device, including headers, popups, buttons, scroll state, and safe areas at the target resolution.
- Save and upload evidence in CI artifacts, e.g. `smoke-debug/features/*.png` or `smoke-debug/watch/*.png`.
- In the delivery response show the capture, or link directly to the actual screenshot artifact. If capture is unavailable, clearly say visual validation remains incomplete and DO NOT claim visual completion.
- If showing a prototype, label it as a prototype, not a runtime capture.
- Summarize changed files, tests, links, and remaining real-device checks. Do not assume a GitHub Actions run that is pending has passed.

## Health data constraints
- Health Connect records are historical samples, NOT a live on-device heart sensor.
- Check Health Connect availability, granted permissions, source attribution, sample timestamp and data freshness. Never present an old sample as current.
- Heart data is shared to Wear only after explicit opt-in. Automatic refresh may reuse consent without re-prompting.
- Samsung Health must actually export/sync samples to Health Connect. An empty result may indicate that no records are available, even when permissions are granted.
