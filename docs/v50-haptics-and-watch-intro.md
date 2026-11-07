# v50 haptics, exercise-change notice and watch intro (phone 50.0 / watch 46.0)

Design source: Figma v45 UX section, frame 03-2A ("근력 종목 1 완료 · 종목 2 진행 중") and the
"이동 규칙" frame, item 8 (confirmed: automatic move after the last set + tap a list row to jump).

## Changes
- **Phone:** finishing the last set of a strength exercise shows a toast ("<완료 종목> 완료 · <다음 종목>로 이동했어요")
  and vibrates twice (`Haptics.exerciseChanged`). The card already moved to the next exercise.
- **Watch vibration:** `WatchHaptics` replaces the 150 ms pulses with long pulses at maximum amplitude and alarm
  usage (rest end and exercise change: 2 x 400 ms; stage or cardio interval change: 3 pulses). The display service
  compares each new phone state with the previous one (`WatchHaptics.transition`), so the wrist buzzes with the phone.
- **Watch intro:** `WatchSplash.kt` is the same Compose sequence and timing as the phone's `Move60Splash`
  (ring sweep, 60 ticks, dumbbell pop, wordmark, fade). Skipped on tap, when the animation scale is 0, after
  recreation and while a workout is in progress.

## Not done / needs a decision
- The code still has 4 stages (준비운동, 유산소, 근력, 유산소; the watch `stageIndex` is clamped to 0..3 and shows "n/4").
  The Figma flow and `workouts.json` have 5 (스트레칭 is the 5th). Moving the app and watch to 5 stages is a separate task.
- The stage item list rows (check / spinning ring / empty ring, mm:ss) exist only in Figma; the native detail screen
  does not render them yet.
- Not verified on a physical Galaxy Watch4: vibration strength and the intro are only checkable on a real device.
