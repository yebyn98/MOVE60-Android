package com.move60.workout

import android.content.Context

/**
 * Watch-measured workout calories (opt-in on the watch). Total = active + basal energy of the watch exercise session.
 * Stored only as the latest sample with its measurement time and session; shown only while fresh and for the same
 * workout, and never added to the MET-based daily estimate.
 */
object WatchKcal {
    const val MAX_AGE_MS = 30_000L
    private const val PREFS = "preferences"
    data class Sample(val session: Long, val totalKcal: Double, val measuredAt: Long)

    /** "kcal:<session>:<total>:<measuredAt>" -> sample, or null when malformed, replayed or implausible. */
    fun parse(command: String, now: Long): Sample? {
        val f = command.split(':')
        if (f.size != 4 || f[0] != "kcal") return null
        val session = f[1].toLongOrNull() ?: return null
        val total = f[2].toDoubleOrNull() ?: return null
        val at = f[3].toLongOrNull() ?: return null
        if (session <= 0L || !total.isFinite() || total < 0.0 || total > 5_000.0) return null
        if (at > now + 2_000L || now - at > MAX_AGE_MS) return null
        return Sample(session, total, at)
    }

    fun save(context: Context, sample: Sample) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        // Older replays of the same session cannot overwrite a newer sample.
        if (prefs.getLong("watch_kcal_session", 0L) == sample.session &&
            sample.measuredAt < prefs.getLong("watch_kcal_at", 0L)) return
        prefs.edit().putLong("watch_kcal_session", sample.session)
            .putString("watch_kcal_total", sample.totalKcal.toString())
            .putLong("watch_kcal_at", sample.measuredAt).apply()
    }

    /** The latest sample for this workout session if it is still fresh, otherwise null (never a stale value). */
    fun fresh(context: Context, session: Long, now: Long = System.currentTimeMillis()): Sample? {
        if (session <= 0L) return null
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        if (prefs.getLong("watch_kcal_session", 0L) != session) return null
        val at = prefs.getLong("watch_kcal_at", 0L)
        val total = prefs.getString("watch_kcal_total", null)?.toDoubleOrNull() ?: return null
        if (now - at !in 0L..MAX_AGE_MS) return null
        return Sample(session, total, at)
    }
}
