package com.move60.workout

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import kotlin.math.ceil

/** Publishes full stage state, including a stable deadline and paused remaining time. */
object WatchStatePublisher {
    fun publishSnapshot(context: Context, snapshot: String) {
        Wearable.getDataClient(context).putDataItem(createRequest(context,snapshot).asPutDataRequest().setUrgent())
    }
    fun createRequest(context: Context, snapshot: String): PutDataMapRequest {
        val state = JSONObject(snapshot)
        val current = SessionStore(context, WorkoutData(context))
        // Enrich only the matching session. Older service callbacks must never publish an old stage.
        if (state.optString("workoutDate") != current.recordDate.toString() || state.optInt("day",-1) != current.day || state.optLong("sessionStartedAt") != current.sessionStartedAt || state.optInt("stage") != current.stage ||
            state.optLong("controlVersion") < current.controlVersion) {
            return createRequest(context, current.snapshot())
        }
        TrainingProgress.enrich(context, state, current)
        val now = System.currentTimeMillis()
        val prefs = context.getSharedPreferences("watch_publish_v26", Context.MODE_PRIVATE)
        val sequence = maxOf(now, prefs.getLong("sequence",0L)+1L)
        prefs.edit().putLong("sequence",sequence).apply()
        val index = state.optInt("stage", 0).coerceIn(0, LAST_STAGE)
        val names = listOf("준비운동", "유산소 ①", when (current.bodyFocus) { "upper" -> "상체 근력"; "lower" -> "하체 근력"; else -> "근력운동" }, "유산소 ②", "스트레칭")
        val running = state.optBoolean("running") && !state.optBoolean("finished")
        val deadline = state.optLong("deadlineAt")
        val finishedNow = state.optBoolean("finished")
        val remaining = if (running) ceil((deadline - now) / 1000.0).toInt().coerceAtLeast(0)
            else state.optInt("remaining", 0).coerceAtLeast(0)
        var detail = names[index]
        var intervalIndex = 0
        var intervalCount = 0
        var upcomingInterval = ""
        if (phasesKeyForStage(index) != null) {
            val duration = state.optJSONArray("stageSeconds")?.optInt(index, 1800) ?: 1800
            var elapsed = if (running) (duration - remaining).coerceAtLeast(0)
                else state.optInt("stageElapsedSeconds").coerceAtLeast(0)
            val phases = phasesKeyForStage(index)?.let { state.optJSONArray(it) }
            if (phases != null) for (i in 0 until phases.length()) {
                val phase = phases.optJSONObject(i) ?: continue
                if (elapsed < phase.optInt("seconds", 60) || i == phases.length() - 1) {
                    detail = phase.optString("name", names[index])
                    intervalIndex = i + 1
                    intervalCount = phases.length()
                    upcomingInterval = phases.optJSONObject(i+1)?.optString("name") ?: ""
                    break
                }
                elapsed -= phase.optInt("seconds", 60)
            }
        }
        if (index == 2) detail = state.optString("setLabel", names[index])
        val nextSet = TrainingProgress.next(current)
        val setRecord = nextSet?.let { SetRecordStore(context).read(current.day, it.task.id, it.set, date = current.recordDate, token = current.sessionStartedAt) }
        val totalSets = current.routine.stages[2].tasks.sumOf { TrainingProgress.count(it) }
        val completedSets = current.routine.stages[2].tasks.sumOf { task ->
            (1..TrainingProgress.count(task)).count { number -> "strength-"+current.day+"-"+task.id+"-"+number in current.checked }
        }
        val estimatedKcal = EnergyLedger(context).estimateSession(state)
        val request = PutDataMapRequest.create("/move60/workout")
        request.dataMap.apply {
            putInt("day",current.day)
            putString("bodyFocus",current.bodyFocus ?: "") // additive field: older watch apps ignore it
            putInt("protocol", 26)
            putLong("sequence", sequence)
            putLong("session",state.optLong("sessionStartedAt"))
            putLong("controlVersion",state.optLong("controlVersion"))
            putLong("restDeadline",state.optLong("restDeadline"))
            putInt("restPaused",state.optInt("restPaused"))
            putLong("restId",state.optLong("restId"))
            putString("exerciseId",state.optString("exerciseId"))
            putInt("setNumber",state.optInt("setNumber"))
            putString("setLabel",state.optString("setLabel"))
            putBoolean("canComplete",state.optBoolean("canComplete"))
            putBoolean("running", running)
            putBoolean("finished", state.optBoolean("finished"))
            putBoolean("hasSession", running || state.optLong("sessionStartedAt") > 0L ||
                state.optInt("activeSeconds") > 0)
            putInt("stageIndex", index)
            putString("stageName", names[index])
            putString("stage", detail) // compatibility with older watch apps
            putString("detail", detail)
            putInt("remaining", remaining)
            putLong("deadline", if (running) deadline else 0L)
            putInt("activeSeconds", current.elapsed())
            putInt("completedSets", completedSets)
            putInt("totalSets", totalSets)
            putDouble("estimatedKcal", estimatedKcal)
            putString("exerciseName", nextSet?.task?.name ?: "")
            putInt("setTotal", nextSet?.sets ?: 0)
            putInt("targetReps", nextSet?.task?.detail?.let { Regex("([0-9]+) *회").find(it)?.groupValues?.get(1)?.toIntOrNull() } ?: 0)
            putString("setWeight", setRecord?.takeIf { it.valid }?.kg ?: "")
            putInt("intervalIndex", intervalIndex)
            putInt("intervalTotal", intervalCount)
            putString("upcomingInterval", upcomingInterval)
            putInt("overtime", if (running || finishedNow) 0 else WorkoutWidgetModel.overtimeSeconds(state, index, now, false).toInt())
            putLong("updated", now)
        }
        return request
    }
}

