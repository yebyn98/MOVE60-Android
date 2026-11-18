# v44 Figma re-sync (phone 44.0 / watch 42.0)

Figma file `KA37z3LKnJi0tFkzwsCt4z` was edited after v43, so every screen was compared again element by element
(icon, text size/weight, radius, gap, control height).

## Phone
- Home: 52dp logo mark in the top bar, weekly plan card first and always open, routine card second, dock with heart pill.
- Calendar/Settings: shared `ScreenTopBar`, 12sp bold muted section headers, 12dp card gap, single day-summary card.
- Native detail: `ic_arrow_back` / `ic_chevron_right` icons replace the "←" / "›" glyphs; 24/16/12 type scale.
- Notification/widget: 20dp `ic_logo_mark` replaces the "MOVE60" text pill; total minutes moved to the chip row.

## Watch
- Idle: date, "상체/하체 · 60분", 60:00, heart state and one filled primary "시작" button. `bodyFocus` is published as an
  additive DataMap field (older watches ignore it; no value is invented when it is missing).
- Running/finished: 12 bold stage, 10sp detail, 26 bold timer, 36dp buttons with 4dp gap, lone "종료" on finish.
- Ambient: thin dimmed ring, battery, hour, minute and date in primary; one small stage/remaining line is kept.

## Known deviations from Figma (intentional)
- Radius 14 and Figma 36/40/44dp controls are not tokens or below the 48dp rule: tokens/48dp are kept on the phone;
  the watch follows the Figma 36dp height.
- Notification chip sizes 11sp/18sp stay as in the Figma "code reference" notes.
- Not verified on a physical Galaxy Watch4 or phone; CI emulator screenshots are the only visual evidence.

## v45 follow-up (phone 45.0 only)
- Native detail dock uses Figma icons (play/pause/replay 20dp, arrows 18dp) instead of the "▶ Ⅱ ↻ ← →" glyphs.
- New CI evidence: `watch-idle.png` (wear test), `widget-workout.png` and `notification-expanded.png` (`LayoutEvidenceTest`).
- Watch code is unchanged, so `scripts/release-targets.json` is `watch: false` and only the phone is published.
  Set it back to `true` (and bump `wear/build.gradle`) in the next change that touches the watch app.

## v46 logo fix (phone 46.0 / watch 43.0)
- The Figma logo (home top bar, launcher icon, watch launcher icon) lights all 60 ticks in primary. The code lit only 45
  and drew 15 in the outline colour. `LIT` in `scripts/generate-logo-assets.py` and `LOGO_LIT_TICKS` in `Move60Logo.kt`
  are now 60; launcher vectors, the `ic_logo_mark` header icon and the in-app mark were regenerated.
- Ring, tick and dumbbell geometry already matched Figma (checked by ratio against the 110px watch icon).
- The watch launcher icon changed, so `release-targets.json` is `watch: true` again.

## v47 detail parity, cardio machines, ambient ring (phone 47.0 / watch 44.0)
- **Detail screen = Figma.** The native detail now shows only the set-rest card (above) and the current-exercise card.
  The "운동 진행 요약" card and the "오늘의 운동 구성" accordion were removed: the stage list lives on the Compose
  overview and the totals (stage, time, kcal) in the fixed dock, so the same information is no longer shown twice.
  The text chevrons "⌃/⌄" and "›/↗" of that accordion went with it. Strength sets stay inline; the exercise popup is
  opened from "자세 안내 · 대체 운동".
- **Cardio = bike + elliptical only.** Block ① is always 실내 사이클, block ② always 일립티컬 (stair climber, rowing and
  the per-day rotation are gone; `cardioOptions` was removed from `workouts.json`). Round labels restart in each block
  ("1/2회차…2/2회차", then "1/3…3/3"), so a block no longer stops at "2/5". The elliptical text is re-worded (no pedals).
  The total cardio time and the 60-minute sum are unchanged. The "오늘 컨디션 루틴" setting used to cap the stair
  climber, so it no longer changes the routine (it only records the choice).
- **Watch ambient ring.** The ambient panel had an opaque black background; the 64% x 80% safe-area box crosses the
  full-bleed ring at its four corners, which cut the ring into pieces. The background was removed and
  `ambientRingIsNotCutByThePanelAtTheSafeAreaCorners` samples the ring at those four points.
- Widget: `WorkoutWidget` is a normal home-screen widget; it is not placed automatically.
