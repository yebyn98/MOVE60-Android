package com.move60.workout.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * One measured ACTIVE stretch of a session in UTC epoch seconds (pauses are not part of it).
 * Date attribution always starts from these, never from another zone's per-day slices (P-20, C-20261115-01).
 */
data class ActiveInterval(val startEpochSec: Long, val endEpochSec: Long) {
    init { require(endEpochSec > startEpochSec) { "Active interval must have positive length" } }
    val seconds: Long get() = endEpochSec - startEpochSec
}

/** Fixed-at-creation challenge definition. [endDate] is INCLUSIVE. [zone] never follows the device (P-25). */
data class ChallengeSpec(val startDate: LocalDate, val endDate: LocalDate, val zone: ZoneId, val targetDays: Int) {
    init {
        require(targetDays >= 1) { "targetDays must be at least 1" }
        require(!endDate.isBefore(startDate)) { "endDate before startDate" }
    }
}

enum class ChallengeOutcome { ACTIVE, COMPLETE, EXPIRED }

/**
 * Result of the single decision function.
 * [creditedDatesCounted] are the credited dates that were already CLOSED (fully in the past) at [endedAtEpochSec],
 * which is what the terminal snapshot must report (P-24).
 */
data class ChallengeDecision(
    val outcome: ChallengeOutcome,
    val endedAtEpochSec: Long?,
    val targetReachedDate: LocalDate?,
    val creditedDatesCounted: List<LocalDate>
)

object ChallengeRules {
    /** Start of [date] in [zone] as UTC epoch seconds. DST-aware: a day may be 23, 24 or 25 hours long. */
    fun dayStart(date: LocalDate, zone: ZoneId): Long = date.atStartOfDay(zone).toEpochSecond()

    /** Instant the challenge stops being ACTIVE: endDate + 1 day at 00:00 in the challenge's own zone (C-20261115-03). */
    fun endExclusiveEpochSec(spec: ChallengeSpec): Long = dayStart(spec.endDate.plusDays(1), spec.zone)

    /** Seconds of [intervals] that fall inside local date [date] of [zone]; nothing is lost or counted twice. */
    fun secondsOnDate(intervals: List<ActiveInterval>, date: LocalDate, zone: ZoneId): Long {
        val windowStart = dayStart(date, zone)
        val windowEnd = dayStart(date.plusDays(1), zone)
        var total = 0L
        for (i in intervals) {
            val overlap = minOf(i.endEpochSec, windowEnd) - maxOf(i.startEpochSec, windowStart)
            if (overlap > 0L) total = Math.addExact(total, overlap)
        }
        return total
    }

    /** Local dates of [zone] that [interval] touches, in order (a midnight crossing yields two or more). */
    fun datesTouched(interval: ActiveInterval, zone: ZoneId): List<LocalDate> {
        val first = Instant.ofEpochSecond(interval.startEpochSec).atZone(zone).toLocalDate()
        val last = Instant.ofEpochSecond(interval.endEpochSec - 1).atZone(zone).toLocalDate()
        val out = ArrayList<LocalDate>()
        var d = first
        while (!d.isAfter(last)) { out += d; d = d.plusDays(1) }
        return out
    }

    /** Splits the whole session by local date of [zone] (P-20). The slices always sum to the interval total. */
    fun sliceByDate(intervals: List<ActiveInterval>, zone: ZoneId): Map<LocalDate, Long> {
        val out = sortedMapOf<LocalDate, Long>()
        for (i in intervals) for (d in datesTouched(i, zone)) {
            val s = secondsOnDate(listOf(i), d, zone)
            if (s > 0L) out[d] = Math.addExact(out[d] ?: 0L, s)
        }
        return out
    }

    /**
     * THE single state decision (C-20261115-02/03). Pure; callable at any time, so an app that was closed
     * over midnight re-derives the same answer on its next launch (C-02.5, AC-13).
     *
     * - ACTIVE while the day on which the cumulative target was reached is still open.
     * - COMPLETE once that day has closed (next 00:00 in the challenge zone), without user confirmation.
     * - At endDate+1 00:00: COMPLETE if the target was met, otherwise EXPIRED. COMPLETE wins an overlap.
     * [creditedDates] are dates already judged >= 600 s by [WorkoutRecognitionPolicy]; targetDays is cumulative.
     */
    fun decide(spec: ChallengeSpec, creditedDates: Set<LocalDate>, nowEpochSec: Long): ChallengeDecision {
        val inRange = creditedDates.filter { !it.isBefore(spec.startDate) && !it.isAfter(spec.endDate) }.sorted()
        val reached = if (inRange.size >= spec.targetDays) inRange[spec.targetDays - 1] else null
        val endExclusive = endExclusiveEpochSec(spec)
        fun counted(endedAt: Long) = inRange.filter { dayStart(it.plusDays(1), spec.zone) <= endedAt }
        if (reached != null) {
            val closesAt = dayStart(reached.plusDays(1), spec.zone)
            if (nowEpochSec >= closesAt) {
                return ChallengeDecision(ChallengeOutcome.COMPLETE, closesAt, reached, counted(closesAt))
            }
        }
        if (nowEpochSec >= endExclusive) {
            return ChallengeDecision(ChallengeOutcome.EXPIRED, endExclusive, null, counted(endExclusive))
        }
        return ChallengeDecision(ChallengeOutcome.ACTIVE, null, reached, emptyList())
    }
}
