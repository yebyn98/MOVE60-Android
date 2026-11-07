# v51 notification card and guide bottom sheet (phone 51.0 / watch unchanged 46.0)

Design source: Figma 03-3 flow, frame 10 ("운동 가이드 / 바텀시트"), confirmed 2026-11-08.

## Notification card (not visible in the shade before)
- Cause: `WorkoutService.updateAndSchedule` posted the custom RemoteViews card only when the preference
  `enhanced_card_enabled` was true. It defaulted to false and no code ever set it to true, so the plain
  fallback notification was the only one posted since commit e6899fd.
- Change: the card is posted by default. If posting throws, the service falls back to the plain notification for the
  rest of that process (`cardFailedInThisProcess`; a transient failure is retried after the next process start).
  After each post `card_status` records "카드 알림 게시됨" (confirmed through `NotificationManager.activeNotifications`)
  or a failure text, which the "운동 알림 진단" dialog shows.
- The card layouts use only RemoteViews-safe views (LinearLayout, TextView, ImageView, ProgressBar, Chronometer).
- CI: `scripts/smoke-android.sh` fails unless `card_status` reads "카드 알림 게시됨", and uploads the emulator shade capture.

## Guide bottom sheet
- The per-exercise guide (was an AlertDialog with a "닫기" button) is now a `BottomSheetDialog`.
- No close button. The 48dp handle row (`guide_sheet_handle`) closes it on tap; dragging down closes it too.
- Kept from the legacy detail: set record table (set entry still lives here until the native detail moves to Compose).
  The Figma sheet omits it, so this is a known deviation.

## Not verified
- Behaviour on a real Galaxy phone (OEM SystemUI rendering of the custom card, drag-to-dismiss feel).
- Emulator-only evidence comes from the CI artifacts of the PR run.
