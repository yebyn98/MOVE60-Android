package com.move60.watch

import android.content.Context
import com.google.android.gms.wearable.DataMap
import org.json.JSONObject
import kotlin.math.ceil

data class WatchWorkoutState(
    val running: Boolean = false, val finished: Boolean = false,
    val hasSession: Boolean = false, val stageIndex: Int = 0,
    val stageName: String = "준비운동", val detail: String = "",
    val remaining: Int = 300, val deadline: Long = 0L,
    val protocol: Int = 0, val sequence: Long = 0L, val session: Long = 0L,
    val controlVersion: Long = 0L, val restDeadline: Long = 0L,
    val restPaused: Int = 0, val restId: Long = 0L,
    val exerciseId: String = "", val setNumber: Int = 0,
    val setLabel: String = "", val canComplete: Boolean = false, val day: Int = 1
) {
    val stageLabel: String get() = (stageIndex+1).toString()+"/4 · "+stageName
    val ongoing: Boolean get() = hasSession && !finished
    fun secondsRemaining(now: Long = System.currentTimeMillis()): Int =
        if (running) ceil((deadline - now) / 1000.0).toInt().coerceAtLeast(0) else remaining
    fun restSeconds(now: Long = System.currentTimeMillis()): Int =
        if (restDeadline > 0L) ceil((restDeadline-now)/1000.0).toInt().coerceAtLeast(0) else restPaused
    fun save(context: Context): Boolean {
        val prefs = context.getSharedPreferences("watch_workout", Context.MODE_PRIVATE)
        val old = try { JSONObject(prefs.getString("state","{}")!!) } catch (_: Exception) { JSONObject() }
        val oldSequence = old.optLong("sequence")
        if (sequence < oldSequence) return false
        val json = JSONObject().put("running",running).put("finished",finished).put("hasSession",hasSession)
            .put("stageIndex",stageIndex).put("stageName",stageName).put("detail",detail)
            .put("remaining",remaining).put("deadline",deadline).put("protocol",protocol).put("sequence",sequence)
            .put("session",session).put("controlVersion",controlVersion).put("restDeadline",restDeadline)
            .put("restPaused",restPaused).put("restId",restId).put("exerciseId",exerciseId)
            .put("day",day).put("setNumber",setNumber).put("setLabel",setLabel).put("canComplete",canComplete)
        prefs.edit().putString("state",json.toString()).apply()
        if (old.optInt("stageIndex") != stageIndex || old.optString("setLabel") != setLabel ||
            old.optBoolean("running") != running || old.optBoolean("finished") != finished || old.optLong("restId") != restId)
            androidx.wear.tiles.TileService.getUpdater(context).requestUpdate(WorkoutTileService::class.java)
        return true
    }
    companion object {
        fun fromData(d: DataMap) = WatchWorkoutState(
            running=d.getBoolean("running"),finished=d.getBoolean("finished"),
            hasSession=d.getBoolean("hasSession") || d.getBoolean("running"),
            stageIndex=d.getInt("stageIndex").coerceIn(0,3),
            stageName=d.getString("stageName") ?: d.getString("stage") ?: "준비운동",
            detail=d.getString("detail") ?: d.getString("stage") ?: "",
            remaining=d.getInt("remaining",300).coerceAtLeast(0),deadline=d.getLong("deadline"),
            protocol=d.getInt("protocol"),sequence=d.getLong("sequence"),session=d.getLong("session"),
            controlVersion=d.getLong("controlVersion"),restDeadline=d.getLong("restDeadline"),
            restPaused=d.getInt("restPaused"),restId=d.getLong("restId"),
            exerciseId=d.getString("exerciseId") ?: "",setNumber=d.getInt("setNumber"),
            setLabel=d.getString("setLabel") ?: "",canComplete=d.getBoolean("canComplete"),day=d.getInt("day",1))
        fun load(context: Context): WatchWorkoutState = try {
            val j = JSONObject(context.getSharedPreferences("watch_workout", Context.MODE_PRIVATE).getString("state","{}")!!)
            WatchWorkoutState(
                running=j.optBoolean("running"),finished=j.optBoolean("finished"),hasSession=j.optBoolean("hasSession"),
                stageIndex=j.optInt("stageIndex").coerceIn(0,3),stageName=j.optString("stageName","준비운동"),
                detail=j.optString("detail"),remaining=j.optInt("remaining",300).coerceAtLeast(0),deadline=j.optLong("deadline"),
                protocol=j.optInt("protocol"),sequence=j.optLong("sequence"),session=j.optLong("session"),
                controlVersion=j.optLong("controlVersion"),restDeadline=j.optLong("restDeadline"),
                restPaused=j.optInt("restPaused"),restId=j.optLong("restId"),exerciseId=j.optString("exerciseId"),
                setNumber=j.optInt("setNumber"),setLabel=j.optString("setLabel"),canComplete=j.optBoolean("canComplete"),day=j.optInt("day",1))
        } catch (_: Exception) { WatchWorkoutState() }
    }
}
