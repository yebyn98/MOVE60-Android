package com.move60.workout.architecture

import android.content.Context
import com.google.android.gms.wearable.PutDataMapRequest
import com.google.android.gms.wearable.Wearable
import com.move60.workout.SessionStore
import com.move60.workout.SetRecordStore
import com.move60.workout.WorkoutData
import kotlin.math.ceil

/**
 * Publishes the ROUTIVE_4 session to the watch on the same data path as the legacy five-stage protocol, with additive
 * keys (structure, stageCount, plannedKnown, stage elapsed). The phone stays the only authority; the watch derives its
 * clock from [deadline]/[stageElapsedAt] so no per-second traffic is needed. Nothing here is invented: unknown plan
 * times are published as "unknown", not as a number, and no heart rate or kcal is produced.
 */
object RoutiveWatchSync {
    const val PROTOCOL = 27
    private var lastSignature: String? = null

    /** The ROUTIVE_4 session the watch should show: it exists and is not older than the last legacy session. */
    fun current(context: Context): RoutiveSession? {
        val session = try { RoutiveSessionStore(context).active() } catch (_: Exception) { null } ?: return null
        val legacyStartedAt = try { SessionStore(context, WorkoutData(context)).sessionStartedAt } catch (_: Exception) { 0L }
        return session.takeIf { it.startedAtEpochMs >= legacyStartedAt }
    }

    fun createRequest(context: Context, s: RoutiveSession, now: Long = System.currentTimeMillis()): PutDataMapRequest {
        val p = s.progress
        val prefs = context.getSharedPreferences("watch_publish_v26", Context.MODE_PRIVATE)
        val sequence = maxOf(now, prefs.getLong("sequence", 0L) + 1L)
        prefs.edit().putLong("sequence", sequence).apply()
        val planned = p.plannedSeconds()
        val elapsed = p.elapsedSeconds[p.stageIndex]
        val running = s.running && !s.over
        val completedSets = try { SetRecordStore(context).completedSetsForSession(s.startedAtEpochMs).size } catch (_: Exception) { 0 }
        val rest = s.rest?.takeIf { !it.done && !s.over }
        val request = PutDataMapRequest.create("/move60/workout")
        request.dataMap.apply {
            putInt("protocol", PROTOCOL)
            putString("structure", "ROUTIVE_4")
            putInt("stageCount", p.plan.size)
            putLong("sequence", sequence)
            putLong("session", s.startedAtEpochMs)
            putLong("controlVersion", 0L)
            putInt("day", s.day)
            putBoolean("running", running)
            putBoolean("finished", s.over)
            putBoolean("hasSession", true)
            putInt("stageIndex", p.stageIndex)
            putString("stageName", p.kind.listTitle())
            val detail = if (s.over) (if (p.endedEarly) "운동 종료" else "운동 완료") else ""
            putString("stage", detail); putString("detail", detail)
            putBoolean("plannedKnown", planned != null)
            // A past deadline is kept as is: the watch derives "+mm:ss" overtime from it while running.
            putInt("remaining", if (planned != null) (planned - elapsed).coerceAtLeast(0L).toInt() else 0)
            putLong("deadline", if (running && planned != null) now + (planned - elapsed) * 1000L else 0L)
            putInt("overtime", if (planned != null && !running) (elapsed - planned).coerceAtLeast(0L).toInt() else 0)
            putInt("stageElapsed", elapsed.toInt()); putLong("stageElapsedAt", now)
            putInt("activeSeconds", p.totalElapsedSeconds.toInt())
            putInt("completedSets", completedSets); putInt("totalSets", 0)
            putLong("restDeadline", if (running && rest != null) now + rest.remainingSeconds * 1000L else 0L)
            putInt("restPaused", if (!running && rest != null) rest.remainingSeconds.toInt() else 0)
            putLong("restId", if (rest != null) s.startedAtEpochMs xor (completedSets.toLong() shl 40) else 0L)
            putDouble("estimatedKcal", 0.0)
            putLong("updated", now)
        }
        return request
    }

    /** Publishes only when something the watch shows has changed; never throws. Returns true when the shown state changed (widgets redraw too). */
    fun publishIfChanged(context: Context, s: RoutiveSession): Boolean {
        val p = s.progress
        val sets = try { SetRecordStore(context).completedSetsForSession(s.startedAtEpochMs).size } catch (_: Exception) { 0 }
        val signature = listOf(s.startedAtEpochMs, p.stageIndex, s.running, s.over, p.finished, p.endedEarly, p.plannedSeconds(),
            s.rest?.exerciseId, s.rest?.done, s.rest?.totalSeconds, sets).joinToString("|")
        if (signature == lastSignature) return false
        lastSignature = signature
        try {
            Wearable.getDataClient(context).putDataItem(createRequest(context, s).asPutDataRequest().setUrgent())
        } catch (_: Exception) { /* no watch / Play services: the phone session is unaffected, the next change publishes again */ }
        return true
    }
}

/** Watch buttons for a ROUTIVE_4 session. The command must name this session and its current stage, otherwise it is stale. */
object RoutiveWatchCommands {
    /** null = there is no current ROUTIVE_4 session (the legacy processor handles the command); otherwise accepted or not. */
    fun handle(context: Context, request: org.json.JSONObject): Boolean? {
        if (RoutiveWatchSync.current(context) == null) return null
        val store = RoutiveSessionStore(context)
        val s = store.reconcile() ?: return null
        if (s.over) return false
        if (request.optLong("session") != s.startedAtEpochMs || request.optInt("stage", -1) != s.progress.stageIndex) return false
        return when (request.optString("op")) {
            "pause" -> if (s.running) { store.pause(); true } else false
            "start" -> if (!s.running) { store.resume(); RoutiveSessionService.ensure(context); true } else false
            "next" -> {
                // The last stage completed on the watch: save through the existing path now (the phone screen may be closed).
                if (store.advance()?.over == true) (context.applicationContext as? com.move60.workout.Move60Application)?.syncTimerSessionAsync(settle = true)
                true
            }
            "rest_skip" -> if (s.rest != null) { store.skipRest(); true } else false
            else -> false   // "+15 s rest", "complete" and the like do not exist for ROUTIVE_4
        }
    }
}
