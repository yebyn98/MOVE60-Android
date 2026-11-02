package com.move60.watch

import android.content.Context
import com.google.android.gms.wearable.DataMap
import org.json.JSONObject
import kotlin.math.ceil

data class WatchWorkoutState(
    val running: Boolean = false, val finished: Boolean = false,
    val hasSession: Boolean = false, val stageIndex: Int = 0,
    val stageName: String = "준비운동", val detail: String = "",
    val remaining: Int = 300, val deadline: Long = 0L
) {
    val stageLabel: String get() = "${stageIndex + 1}/4 · $stageName"
    val ongoing: Boolean get() = hasSession && !finished
    fun secondsRemaining(now: Long = System.currentTimeMillis()): Int =
        if (running) ceil((deadline - now) / 1000.0).toInt().coerceAtLeast(0) else remaining
    fun save(context: Context) {
        val json = JSONObject().put("running", running).put("finished", finished)
            .put("hasSession", hasSession).put("stageIndex", stageIndex).put("stageName", stageName)
            .put("detail", detail).put("remaining", remaining).put("deadline", deadline)
        context.getSharedPreferences("watch_workout", Context.MODE_PRIVATE).edit()
            .putString("state", json.toString()).apply()
    }
    companion object {
        fun fromData(data: DataMap) = WatchWorkoutState(
            data.getBoolean("running"), data.getBoolean("finished"),
            data.getBoolean("hasSession") || data.getBoolean("running"),
            data.getInt("stageIndex").coerceIn(0, 3),
            data.getString("stageName") ?: data.getString("stage") ?: "준비운동",
            data.getString("detail") ?: data.getString("stage") ?: "",
            data.getInt("remaining", 300).coerceAtLeast(0), data.getLong("deadline"))
        fun load(context: Context): WatchWorkoutState = try {
            val j = JSONObject(context.getSharedPreferences("watch_workout", Context.MODE_PRIVATE)
                .getString("state", "{}") ?: "{}")
            WatchWorkoutState(j.optBoolean("running"), j.optBoolean("finished"),
                j.optBoolean("hasSession"), j.optInt("stageIndex").coerceIn(0, 3),
                j.optString("stageName", "준비운동"), j.optString("detail"),
                j.optInt("remaining", 300).coerceAtLeast(0), j.optLong("deadline"))
        } catch (_: Exception) { WatchWorkoutState() }
    }
}
