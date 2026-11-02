package com.move60.workout

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import org.json.JSONObject
import kotlin.math.ceil

/** Publishes full stage state, including a stable deadline and paused remaining time. */
object WatchStatePublisher {
    fun publishSnapshot(context: Context, snapshot: String) {
        val state = JSONObject(snapshot)
        val now = System.currentTimeMillis()
        val index = state.optInt("stage", 0).coerceIn(0, 3)
        val names = listOf("준비운동", "유산소", "근력운동", "마무리 스트레칭")
        val running = state.optBoolean("running") && !state.optBoolean("finished")
        val deadline = state.optLong("deadlineAt")
        val remaining = if (running) ceil((deadline - now) / 1000.0).toInt().coerceAtLeast(0)
            else state.optInt("remaining", 0).coerceAtLeast(0)
        var detail = names[index]
        if (index == 1) {
            val duration = state.optJSONArray("stageSeconds")?.optInt(index, 1800) ?: 1800
            var elapsed = if (running) (duration - remaining).coerceAtLeast(0)
                else state.optInt("stageElapsedSeconds").coerceAtLeast(0)
            val phases = state.optJSONArray("phases")
            if (phases != null) for (i in 0 until phases.length()) {
                val phase = phases.optJSONObject(i) ?: continue
                if (elapsed < phase.optInt("seconds", 60) || i == phases.length() - 1) {
                    detail = phase.optString("name", names[index])
                    break
                }
                elapsed -= phase.optInt("seconds", 60)
            }
        }
        val request = PutDataMapRequest.create("/move60/workout")
        request.dataMap.apply {
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
            putLong("updated", now)
        }
        Wearable.getDataClient(context).putDataItem(request.asPutDataRequest().setUrgent())
    }
}
