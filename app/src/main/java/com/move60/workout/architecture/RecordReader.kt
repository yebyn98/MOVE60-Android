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
 * Only STORED values are shown. A session saved with the docs/v61 plan snapshot also keeps its routine name and the real
 * seconds of each stage; older sessions do not, and then none of those are produced. Per-exercise time and calories are not
 * stored at all, so they are never shown. Legacy `daily_workouts` seconds are returned separately and never added to session time
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
    val exerciseIds: List<String>,
    /** From the plan snapshot saved when the session started; null for sessions saved before it existed or an unreadable one. */
    val routineName: String? = null,
    /** Real seconds per stage (label to seconds) in stage order; stages with no time are absent. Empty for older sessions. */
    val stageSeconds: List<Pair<String, Long>> = emptyList()
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

/** A planned item of a stage (from the start-time plan). Its time is a PLAN, never a measured value. */
data class PlannedItem(val name: String, val plannedSeconds: Long?)

/** One stage with real time (docs/v61 D2): [actualSeconds] is measured; [plannedSeconds] null = unknown plan time. */
data class StageRecord(
    val index: Int, val kind: SegmentKind, val label: String,
    val actualSeconds: Long, val plannedSeconds: Long?, val items: List<PlannedItem>
)

data class SessionDetail(
    val date: LocalDate, val session: RecordSession, val exercises: List<ExerciseSets>,
    /** Corrections of this session, oldest first (policy 6). The originals above stay as stored. */
    val history: List<CorrectionEntry> = emptyList(),
    /** Stages that have real time, in stage order. Empty for sessions saved without stage data. */
    val stages: List<StageRecord> = emptyList()
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

    /** Stage rows (orderIndex 0..3 is the ROUTIVE_4 stage index; later indexes are reserved for intensity rounds). */
    private suspend fun stageRecords(sessionId: String, plan: RoutivePlanSnapshot.Parsed?): List<StageRecord> =
        dao.segments(sessionId).filter { it.orderIndex in 0..3 }.mapNotNull { seg ->
            val kind = runCatching { SegmentKind.valueOf(seg.kind) }.getOrNull() ?: return@mapNotNull null
            val label = RecordFormat.stageLabel(kind) ?: return@mapNotNull null
            StageRecord(
                seg.orderIndex, kind, label, seg.actualSeconds, seg.plannedSeconds,
                plan?.exercises.orEmpty().filter { it.stageIndex == seg.orderIndex }.map { PlannedItem(it.name, it.plannedSeconds) }
            )
        }.sortedBy { it.index }

    /** Stored session seconds per date of [month]. Dates without stored sessions are absent. */
    suspend fun monthSeconds(month: YearMonth): Map<LocalDate, Long> =
        dao.sessionSecondsByDate(month.atDay(1).toString(), month.atEndOfMonth().toString())
            .mapNotNull { r -> runCatching { LocalDate.parse(r.date) }.getOrNull()?.let { it to r.seconds } }.toMap()

    private suspend fun row(s: SessionEntity, date: LocalDate): RecordSession {
        val completed = dao.sets(s.sessionId).filter { it.state == SetState.COMPLETED.name }
        val plan = RoutivePlanSnapshot.parse(s.planSnapshotJson)
        return RecordSession(
            sessionId = s.sessionId,
            startedAtEpochMs = s.startedAtEpochMs,
            zoneId = s.zoneId,
            completion = runCatching { SessionCompletion.valueOf(s.completion) }.getOrNull(),
            totalActiveSeconds = s.totalActiveSeconds,
            activeSecondsOnDate = dao.sliceSeconds(s.sessionId, date.toString()) ?: 0L,
            completedSets = s.completedStrengthSets,
            exerciseIds = completed.map { it.exerciseId }.distinct().sorted(),
            routineName = plan?.routineName?.takeIf { it.isNotBlank() },
            stageSeconds = stageRecords(s.sessionId, plan).map { it.label to it.actualSeconds }
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
        return SessionDetail(
            date, row(entity, date), exercises, corrections.history(sessionId),
            stageRecords(sessionId, RoutivePlanSnapshot.parse(entity.planSnapshotJson))
        )
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

    /** `5분`, `5분 20초`, `20초`: the compact form used for stage times. */
    fun compact(seconds: Long): String {
        val s = seconds.coerceAtLeast(0L)
        val m = s / 60
        val r = s % 60
        return when {
            m == 0L -> "${r}초"
            r == 0L -> "${m}분"
            else -> "${m}분 ${r}초"
        }
    }

    /** Stage name for a stage segment; null for intensity rounds, which are not stages. */
    fun stageLabel(kind: SegmentKind): String? = when (kind) {
        SegmentKind.WARMUP -> "준비운동"
        SegmentKind.STRENGTH -> "근력운동"
        SegmentKind.CARDIO_STEADY -> "유산소"
        SegmentKind.COOLDOWN -> "마무리"
        SegmentKind.CARDIO_WORK, SegmentKind.CARDIO_RECOVERY -> null
    }

    fun completionLabel(c: SessionCompletion?): String = when (c) {
        SessionCompletion.COMPLETED -> "완료"
        SessionCompletion.PARTIAL -> "일부 수행"
        SessionCompletion.NOT_PERFORMED -> "수행 안 함"
        null -> "상태 확인 안 됨"
    }
}
