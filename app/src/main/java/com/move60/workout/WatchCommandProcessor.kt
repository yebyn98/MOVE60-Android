package com.move60.workout

import android.content.Context
import org.json.JSONObject

/** Retries cannot advance twice or modify a newer phone session. Run on the main looper. */
object WatchCommandProcessor {
    fun apply(context: Context, request: JSONObject, now: Long = System.currentTimeMillis()): Boolean {
        val prefs = context.getSharedPreferences("watch_commands_v26", Context.MODE_PRIVATE)
        val id = request.optString("id")
        val at = request.optLong("at")
        if (id.isBlank() || id.length > 100 || at > now+2_000L || now-at !in -2_000L..15_000L) return false
        if (prefs.getStringSet("handled",emptySet())!!.contains(id)) return false
        val store = SessionStore(context, WorkoutData(context))
        if (request.optInt("day",-1) != store.day || request.optLong("session",-1L) != store.sessionStartedAt || request.optLong("version",-1L) != store.controlVersion || request.optInt("stage",-1) != store.stage) return false
        val accepted = when (request.optString("op")) {
            "start" -> if (!store.running) { store.start(); SetRecordStore(context).beginSession(store.day,store.sessionStartedAt); true } else false
            "pause" -> if (store.running) { store.pause(); true } else false
            "next" -> if (!store.finished) { store.nextStage(); true } else false
            "complete" -> TrainingProgress.complete(context,store,request.optString("exercise"),request.optInt("set"))
            "rest_add" -> if (store.restId > 0L) { store.adjustRest(15); true } else false
            "rest_sub" -> if (store.restId > 0L) { store.adjustRest(-15); true } else false
            "rest_skip" -> if (store.restId > 0L) { store.skipRest(); true } else false
            else -> false
        }
        if (accepted) prefs.edit().putStringSet("handled",(prefs.getStringSet("handled",emptySet())!!.toList().takeLast(63)+id).toSet()).commit()
        return accepted
    }
}
