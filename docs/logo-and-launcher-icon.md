# MOVE60 logo, launcher icon and first-screen intro

**Logo: 60-tick plate + dumbbell.** An outer ring, 60 minute ticks (all 60 lit, matching the Figma logo) and a
dumbbell in the heart color. Colors are design tokens (`primary` #00E676, `outline` #303030,
`heart` #FF6B81, plate `surface` #121212). Chosen from the Figma logo exploration board.

## Where it lives
- Launcher icon (phone + watch): adaptive icon, `mipmap-anydpi-v26/ic_launcher(.xml|_round.xml)`
  with `ic_launcher_foreground` (full color), `ic_launcher_monochrome` (Android 13 themed icon) and
  `ic_launcher_background` (= `move60_surface`). minSdk is 26, so no legacy PNGs are needed.
- The vector files are **generated**: edit geometry in `scripts/generate-logo-assets.py` and rerun it;
  `verify-compose-architecture.py` fails if committed files drift (`--check`).
- Compose mark: `ui/Move60Logo.kt` (same geometry, theme colors). First-screen intro:
  `ui/Move60Splash.kt`.

## First-screen intro
About 1.7 s: ring sweep -> ticks light up clockwise -> dumbbell pop + wordmark -> fade out over the
dashboard. Tap skips it. It plays only on a fresh launch (`savedInstanceState == null`; rotation does
not replay it) and is skipped when the system animation scale is 0 (also keeps CI emulators, which
disable animations, deterministic). The watch plays the same sequence (`WatchSplash.kt`, same
timings and geometry) on a fresh launch when no workout is in progress.

## Not covered / follow-up
- Notification small icon (`ic_fitness`) and widget are unchanged; a tick-free simplified mark is
  needed for sizes below 32 px.
- Visual validation requires an emulator/device capture of the launcher icon and the intro; Figma
  boards and path-data renders are prototypes, not runtime captures.
