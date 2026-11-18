# v43 UX / Figma sync (phone 43.0; watch 41.0 = version bump only)

Source of truth: Figma file `KA37z3LKnJi0tFkzwsCt4z`, page "Phone" (v43 frames). The watch was not changed in this release;
its build number is raised only because the release gate requires every selected target to exceed the published version.
Screens are verified from CI emulator captures only; physical phone / Galaxy Watch4 checks remain open.

1. **Bottom dock = today's status.** The former "운동 현황" card is gone. The dock's top edge is a 4dp 60-minute progress bar
   (`dock_progress` in Compose, `phone_dock_progress` in the native detail). The routine card header shows
   "근력 세트 x/y · 단계 n/4". Dock buttons are 48dp high. Compose home and native detail use the same dock content.
2. **Calendar.** Tapping a day without a record shows the toast "운동한 기록이 없습니다." and "기록 없음" under the grid.
   Tapping a recorded day shows, below the grid, a summary card (date, body-part chip, total minutes, estimated kcal) and one card per
   stage with measured time and its share of the day's estimated kcal. Per-stage data is stored in `move60_workout_stages_v1`
   (`StageLog`) from v43 on; older days show only the day total plus a note, never an invented breakdown. Existing history,
   energy ledger and set records are untouched. The old "최근 운동 기록" list was replaced by this day detail.
3. **Settings (grouped list).** Groups 운동 / 내 정보 / 앱.
   - 시간 비율: inline chips 20–40분 (no popup); the summary line shows the resulting split.
   - 알림: turning the switch on opens the notification diagnostics (permission request first on Android 13+).
   - 앱 버전: the row height is fixed; "업데이트" replaces the version text only when a newer build is published
     (`UpdateManager.peekAvailability`, silent: no dialog, no download, no stored state). Offline or unknown shows the plain version.
   - Removed: 건강 데이터 연동 entry and the help card (owner decision from the Figma review: the integration was unreliable).
     `HealthConsentActivity` stays in the manifest and its test, but nothing in the phone UI opens it any more, and nothing requests
     Health Connect permissions automatically. **Open follow-up (watch, not touched here):** the watch hint
     "휴대폰 MOVE60 설정 > 건강 데이터 연동에서 동의" now points to an entry that no longer exists.
4. **Smaller buttons.** Set −/+ steppers look 32dp but keep a 48dp touch target (inset background); the set-complete button is 48dp.
