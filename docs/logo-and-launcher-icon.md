# ROUTIVE launcher icon and first-screen intro

**Launcher icon: the approved ROUTIVE "R" symbol** (Figma file `KA37z3LKnJi0tFkzwsCt4z`, page "04 브랜드 · ROUTIVE 최종 로고",
node `977:397` "Symbol / Forest (original)"; brand guide frame `515:389`). The vector is the exact export of that node (three
paths, 320 x 300 box, unmodified): Forest `#003521` on the Ivory `#F5F1E9` plate. Figma marks the master "수정 금지", so the paths are
never edited by hand.

**In-app mark (first-screen intro, `Move60LogoMark`) is still the earlier 60-tick plate + dumbbell.** Whether the splash mark
should also become the R symbol is a design decision that was not made; only the launcher icon and the wordmark animation changed.

## Where it lives
- Launcher icon (phone + watch): adaptive icon, `mipmap-anydpi-v26/ic_launcher(.xml|_round.xml)`
  with `ic_launcher_foreground` (Forest symbol), `ic_launcher_monochrome` (same paths, one color, for the Android 13 themed icon)
  and `ic_launcher_background` (= Ivory `#F5F1E9`, `values/ic_launcher.xml`). The symbol is 48dp wide inside the 108dp canvas, well
  within the 66dp safe zone. minSdk is 26, so no legacy PNGs are needed.
- The vector files are **generated**: change `scripts/generate-logo-assets.py` and rerun it;
  `verify-compose-architecture.py` fails if committed files drift (`--check`).
- Compose mark: `ui/Move60Logo.kt` (same geometry, theme colors). First-screen intro:
  `ui/Move60Splash.kt`.

## First-screen intro
About 2.4 s: ring sweep -> ticks light up clockwise -> dumbbell pop -> the "ROUTIVE" wordmark appears slowly from left to right
(a soft edge sweeps over the letters while the word drifts up a few dp, 1.1 s with a symmetric ease-in-out, starting at 0.65 s) -> short hold -> fade out over the
dashboard. Tap skips it. It plays only on a fresh launch (`savedInstanceState == null`; rotation does
not replay it) and is skipped when the system animation scale is 0 (also keeps CI emulators, which
disable animations, deterministic). The watch plays the same sequence (`WatchSplash.kt`, same
timings and geometry) on a fresh launch when no workout is in progress.

## Not covered / follow-up
- Notification small icon (`ic_fitness`) and widget are unchanged; a tick-free simplified mark is
  needed for sizes below 32 px.
- Visual validation requires an emulator/device capture of the launcher icon and the intro; Figma
  boards and path-data renders are prototypes, not runtime captures.
