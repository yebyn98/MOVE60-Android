# MOVE60 v36 - mobile UI clean-up / Watch4 health

## Mobile v36
- Remove VirtualMate button, app-switch instructions, remote exercise photos/playback, and daily kg·rep summary text.
- Render MOVE60 as one single-line brand, with accessible calendar and settings icon controls in Compose and native detailed exercise UI.
- Restore consistent layout, card padding and per-set completion tracking.
- Preserve workout timer, data storage, session state and Watch synchronization.

## Watch4 v34
- Preserve stable workout control and timer service.
- The workout stage long-press settings menu opens a Wear Compose + Horologist health dashboard.
- Show fresh real measured heart rate from the existing on-device HeartRateService only after BODY_SENSORS permission.
- Show historical heart rate only when synced from a paired phone with explicit read permission.
- Do not collect, display or transmit sleep or vascular load data.

## Data permission and Samsung SDK constraints
Samsung Health Data SDK runs on Android phones, not directly inside a watch app.
It is distributed as samsung-health-data-api.aar and requires Samsung partner registration with the app package name and SHA-256 signing identity. It cannot be verified on an emulator.
MOVE60 v36 intentionally limits its health-data feature to heart rate.
Until partner approval and the official AAR are provided, MOVE60 uses Samsung Health synchronization through Android Health Connect on the phone. This is explicitly a fallback, not a direct Samsung Health Data SDK integration.

User-initiated phone consent grants read-only heart-rate access. The watch receives data only via the paired Wear Data Layer and does not connect to any health-data server.
Opt-out clears the synced watch summary. The user can separately revoke Android Health Connect access in OS settings. No automatic permission popups or fake values.

## Validation
- Phone versionCode 36, Watch versionCode 34; both-device selective release.
- Phone and Watch instrumentation suites must pass with at least 27 and 9 tests respectively; tests may grow without changing a hard-coded exact count.
- scripts/verify-v36-ui.py is a mandatory CI preflight. Main may publish only after signed build, phone runtime and all Wear tests succeed.
- Pull requests must compile debug instrumentation, pass Release Lint and exercise round-screen Wear tests before merge.
- Phone emulator preflight may recover only a documented Google SDK setup ANR; MOVE60 or other crash/ANR dialogs remain hard failures.
- Actual Watch4 sensor, Samsung Health records and consent dialogs still require physical-device validation. Do not claim emulator verification covers these.
