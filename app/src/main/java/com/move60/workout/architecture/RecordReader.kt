package com.move60.workout.architecture

import com.move60.workout.SetRecordStore
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.util.Locale

/**
 * Read model of the ROUTIVE v2 record screens (Figma 11 calendar, 26 day record, 27 session detail).
 *
 * Only STORED values are shown. The committed session keeps start/end, completion, active seconds, active intervals and
 * completed sets; it does not keep a routine name, per-stage or per-exercise time, or calories, so none of those are
 * produced here. Legacy `daily_workouts` seconds are returned separately and never added to session time
 * (C-20261115-04), and nothing here writes to the database.
 */
data class RecordSession(
    val sessionId: String,
    val startedAtEpochMs: Long?,
    val zoneId: String,
    val completion: SessionCompletion?,
    val totalActiveSeconds: Long,
    /** Seconds of this session that fall on the viewed date (a session can span midnight). */
    val activeSecondsOnDate: Long,
    val completedSets: Int,
    /** Distinct exercises with at least one completed set, in id order. */
    val exerciseIds: List<String>
) {
    val startClock: String? get() = RecordFormat.clock(startedAtEpochMs, zoneId)
}

data class DayRecord(
    val date: LocalDate,
    val sessions: List<RecordSession>,
    /** Stored session seconds on [date]. Excludes the legacy total below. */
    val sessionSeconds: Long,
    /** Legacy v58 day total, or null when none. Shown on its own, never merged with [sessionSeconds]. */
    val legacyActiveSeconds: Long?,
    /** v58 completed sets that cannot be tied to a stored session (policy 5). */
    val unlinkedLegacy: List<LegacySetGroup>
) {
    val isEmpty: Boolean get() = sessions.isEmpty() && legacyActiveSeconds == null && unlinkedLegacy.isEmpty()
}

data class ExerciseSets(val exerciseId: String, val sets: List<ShownSet>)

data class SessionDetail(
    val date: LocalDate, val session: RecordSession, val exercises: List<ExerciseSets>,
    /** Corrections of this session, oldest first (policy 6). The originals above stay as stored. */
    val history: List<CorrectionEntry> = emptyList()
)

class RecordReader(
    private val db: WorkoutDatabase,
    legacySource: () -> List<SetRecordStore.StoredSet>
) {
    private val dao get() = db.routiveDao()
    private val legacy = LegacySetReader(db, legacySource)

    /** Dates of [month] that have stored session time. */
    suspend fun sessionDates(month: YearMonth): Set<LocalDate> =
        dao.sessionDatesBetween(month.atDay(1).toString(), month.atEndOfMonth().toString())
            .mapNotNull { runCatching { LocalDate.parse(it) }.getOrNull() }.toSet()

    private suspend fun row(s: SessionEntity, date: LocalDate): RecordSession {
        val completed = dao.sets(s.sessionId).filter { it.state == SetState.COMPLETED.name }
        return RecordSession(
            sessionId = s.sessionId,
            startedAtEpochMs = s.startedAtEpochMs,
            zoneId = s.zoneId,
            completion = runCatching { SessionCompletion.valueOf(s.completion) }.getOrNull(),
            totalActiveSeconds = s.totalActiveSeconds,
            activeSecondsOnDate = dao.sliceSeconds(s.sessionId, date.toString()) ?: 0L,
            completedSets = s.completedStrengthSets,
            exerciseIds = completed.map { it.exerciseId }.distinct().sorted()
        )
    }

    suspend fun day(date: LocalDate): DayRecord {
        val sessions = dao.sessionsOn(date.toString()).map { row(it, date) }
        val legacyTotal = db.workoutDao().day(date.toString())?.activeSeconds?.takeIf { it > 0L }
        return DayRecord(date, sessions, dao.sessionSecondsOn(date.toString()), legacyTotal, legacy.forDate(date).unlinked)
    }

    /** Null when no stored session has this id. Completed sets: stored ones plus v58 values not already stored (policy 5). */
    suspend fun session(sessionId: String): SessionDetail? {
        val entity = dao.session(sessionId) ?: return null
        val date = dao.slices(sessionId).firstOrNull()?.date?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return null
        val shown = legacy.forDate(date).groups.firstOrNull { it.sessionId == sessionId }?.sets
            ?: dao.sets(sessionId).filter { it.state == SetState.COMPLETED.name }
                .map { ShownSet(it.exerciseId, it.setNumber, it.weightKg, it.repetitions, SetOrigin.STORED) }
        // Corrections are shown on top of the untouched stored original; v58 values come from SharedPreferences and are not corrected here.
        val corrections = SessionCorrectionService(db)
        val overlay = corrections.overlay(sessionId)
        val corrected = shown.map { s ->
            val o = if (s.origin == SetOrigin.STORED) overlay[s.exerciseId to s.setNumber] else null
            if (o == null || (o.kg == null && o.reps == null)) s
            else s.copy(
                kg = o.kg ?: s.kg, reps = o.reps ?: s.reps,
                originalKg = if (o.kg != null) s.kg else null, originalReps = if (o.reps != null) s.reps else null
            )
        }
        val exercises = corrected.groupBy { it.exerciseId }.toSortedMap()
            .map { (id, sets) -> ExerciseSets(id, sets.sortedWith(compareBy({ it.setNumber }, { it.origin }))) }
        return SessionDetail(date, row(entity, date), exercises, corrections.history(sessionId))
    }
}

object RecordFormat {
    /** `45분 20초`, `15분 00초`, `60분 20초`. */
    fun duration(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        return "${s / 60}분 ${"%02d".format(Locale.KOREA, s % 60)}초"
    }

    /** `20:35` in the session's own zone, or null when the start or zone is not known. */
    fun clock(epochMs: Long?, zoneId: String): String? {
        if (epochMs == null) return null
        return runCatching {
            val t = Instant.ofEpochMilli(epochMs).atZone(ZoneId.of(zoneId))
            "%02d:%02d".format(Locale.KOREA, t.hour, t.minute)
        }.getOrNull()
    }

    /**
     * Set values exactly as stored. Equal sets: `20kg × 12회 × 3세트`; different sets:
     * `40kg × 12회 · 45kg × 12회 (2세트)`; one set: `40kg × 10회 (1세트)`.
     */
    fun setSummary(sets: List<ShownSet>): String {
        if (sets.isEmpty()) return ""
        val each = sets.map { "${it.kg}kg × ${it.reps}회" }
        return when {
            sets.size == 1 -> "${each.single()} (1세트)"
            each.distinct().size == 1 -> "${each.first()} × ${sets.size}세트"
            else -> "${each.joinToString(" · ")} (${sets.size}세트)"
        }
    }

    fun completionLabel(c: SessionCompletion?): String = when (c) {
        SessionCompletion.COMPLETED -> "완료"
        SessionCompletion.PARTIAL -> "일부 수행"
        SessionCompletion.NOT_PERFORMED -> "수행 안 함"
        null -> "상태 확인 안 됨"
    }
}
