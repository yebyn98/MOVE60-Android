# v42 UX flow (phone 42.0 / watch 40.0)

Five usability changes. Screens are verified from CI emulator captures only; Galaxy Watch4 and physical phone checks remain open.

1. **Watch finish screen**: one result. Large time, "오늘 기록 저장됨", a single "종료" button. "새 운동" is not on the watch; the phone dock offers "새 운동 시작". Heart row shows the session average, or "워치 착용 시 심박이 기록돼요" when no sample exists. Rest button label is "건너뛰기".
2. **Heart empty state**: states the cause and next action instead of "심박 기록 없음". No heart values are generated.
3. **Calendar**: workout days are dotted by body part (상체 = primary, 하체 = onSurface, 부위 미기록 = secondary) with a legend. A habit card shows this week's trained days (goal 5) and month progress (goal 20). Goals are constants (`WorkoutHistory.WEEKLY_GOAL_DAYS`, `MONTHLY_GOAL_DAYS`), not user settings yet.
   - Body part is recorded per date in `move60_workout_focus_v1` when active seconds are logged. Days recorded before v42 show as "부위 미기록". Existing history is untouched.
4. **Home hierarchy**: today's routine + start button first, status next, weekly plan card last and collapsible (open when no body part is chosen yet).
5. **Set input**: per set a block with [−][value][+] steppers (kg ±2.5, reps ±1) and a full-width 56dp complete button. The EditText/CheckBox tags and stored `SetRecordStore` records are unchanged, so typing a value still works and old records load.
