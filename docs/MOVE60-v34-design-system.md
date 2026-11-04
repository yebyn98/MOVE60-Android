# MOVE60 v34 — Mobile / Wear OS Design System

## Global semantic tokens
Shared by `app/.../Move60Theme.kt`, `wear/.../Move60Theme.kt` and Android XML resources (`@color/move60_*`).

| Token | Value | Usage |
| --- | --- | --- |
| Background | #000000 | All mobile/watch screens and system bars |
| Surface | #121212 | Dashboard cards, watch interactive surface, widgets |
| Elevated | #1C1C1C | Secondary buttons, raised rows |
| Brand / Primary | #00E676 | Active state, charts, progress, actions |
| High-emphasis text | #FFFFFF | Titles, high-priority metrics |
| Medium-emphasis text | #B0B0B0 | Supporting labels |
| Divider | #303030 | Card stroke, divider and progress track |

Type ramp (sp): display 30, title 24, section 20, body 16, detail 14, caption 12; native Watch text uses restricted 11–30sp fitting rules.
Space: 8 / 16 / 24 / 32dp. Card radius 20dp, controls 16dp. Touch controls 48dp whenever feasible; the wearable's circular safe area is constrained to 70.7% of diameter.
All dynamic style setters must reference `Move60Theme`; do not reintroduce screen-specific RGB literals.

## Interaction architecture
Phone: scrollable cards for weekday selection, strength-area choice, timer + progress, individual stage/sets, planned session summary, set-completion bar. Bottom workout control dock remains fixed for safety and reachability.
Wear: black background, small round safe card, priority timer/set count, arc gauge, text-light status, two major controls, ambient view with no stale seconds, and a concise Tile.
Data layer and durable logs remain intact; theme refactor must never reset session, historical sets, workout dates, or calendar records.

## Daily calorie contract
The phone displays *오늘 누적*: current Korea-calendar-day MET estimate of active workout time since the day's first start action. Pause/end freeze the value; a second workout the same day adds; tomorrow starts at 0. Historical calendar day totals remain stored. The watch session-finished estimate remains for the individual workout and must not replace the phone daily value. Both are estimates, not physiological measurements.

## Simplification rules
Remove redundant selected-body paragraph from the summary but keep upper/lower selection.
Remove previous-record import button and RPE input/labels; retain legacy stored RPE values to avoid data corruption. Preserve previous-record summary text if useful.
In settings: accessible icon-only back, sound toggle top right of notification/update card (no text label), no diagnostics button, app information with version at right, separate help card above calorie explanation.
Use dark theme for notifications and widget as well.

## Release/QA
- `python3 scripts/verify-ui-theme.py` checks fixed tokens, parity and screen adherence.
- Required phone instrumentation: 25/25. Required wearable instrumentation: 8/8, including round-safe buttons, ambient mode and ring/strength target.
- Every push must pass Android startup/notification, Wear small-circle UI and independent-device publishing tests before signing/publishing.
- If both phone and watch UI change, publish both; if only one changes in future, publish only that APK. Never claim physical Watch4 sensor/BLE/AOD verification from emulator tests.
