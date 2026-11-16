package com.move60.workout.architecture

import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/** Test-only builders. Times are real instants: `at("2026-11-15", 10, 0)` is 10:00 in the given zone. */
object T {
    const val SEOUL = "Asia/Seoul"
    fun at(date: String, hour: Int, minute: Int = 0, second: Int = 0, zone: String = SEOUL): Long =
        ZonedDateTime.of(LocalDate.parse(date), LocalTime.of(hour, minute, second), ZoneId.of(zone)).toEpochSecond()
    fun ms(date: String, hour: Int, minute: Int = 0, second: Int = 0, zone: String = SEOUL): Long = at(date, hour, minute, second, zone) * 1000L
    /** An active stretch of [seconds] starting at [startSec]. */
    fun run(startSec: Long, seconds: Long): Pair<Long, Long> = startSec to startSec + seconds
}

fun sessionBundle(
    id: String,
    intervals: List<Pair<Long, Long>>,
    zone: String = T.SEOUL,
    sets: Int = 0,
    legacyToken: Long? = null,
    segments: List<SessionSegmentEntity> = emptyList(),
    completion: SessionCompletion = SessionCompletion.COMPLETED,
    totalOverride: Long? = null
): SessionBundle {
    val rows = intervals.mapIndexed { i, (a, b) -> SessionActiveIntervalEntity(id, i, a, b) }
    val total = totalOverride ?: intervals.sumOf { it.second - it.first }
    val setRows = (1..sets).map { SessionSetEntity(id, "bench", it, SetState.COMPLETED.name, "40", "10", 5L) }
    val session = SessionEntity(
        id, legacyToken, zone, intervals.firstOrNull()?.first?.times(1000L), intervals.lastOrNull()?.second?.times(1000L),
        completion.name, total, sets, false, null, 0L
    )
    return SessionBundle(session, rows, setRows, segments)
}

fun challengeOf(
    id: String = "c1", start: String, end: String, zone: String = T.SEOUL, target: Int = 3
) = ChallengeEntity(id, "challenge $id", start, end, zone, target, ChallengeStatus.ACTIVE.name, 0L, null, null)
