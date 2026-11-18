package com.move60.workout.domain

import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Pure JVM tests of the single state-decision function and the time arithmetic (AC-05..08, AC-13). */
class ChallengeRulesTest {
    private val seoul = ZoneId.of("Asia/Seoul")
    private val ny = ZoneId.of("America/New_York")
    private fun d(s: String) = LocalDate.parse(s)
    private fun at(date: String, h: Int, m: Int, s: Int, zone: ZoneId = seoul) =
        d(date).atTime(h, m, s).atZone(zone).toEpochSecond()
    private fun spec(start: String, end: String, target: Int, zone: ZoneId = seoul) = ChallengeSpec(d(start), d(end), zone, target)

    @Test fun ac05_targetDayStaysActiveUntilMidnightThenCompletes() {
        val s = spec("2026-11-13", "2026-11-20", 3)
        val credited = setOf(d("2026-11-13"), d("2026-11-14"), d("2026-11-15"))
        assertEquals(ChallengeOutcome.ACTIVE, ChallengeRules.decide(s, credited, at("2026-11-15", 23, 59, 59)).outcome)
        val done = ChallengeRules.decide(s, credited, at("2026-11-16", 0, 0, 0))
        assertEquals(ChallengeOutcome.COMPLETE, done.outcome)
        assertEquals(at("2026-11-16", 0, 0, 0), done.endedAtEpochSec)
        assertEquals(d("2026-11-15"), done.targetReachedDate)
        assertEquals(3, done.creditedDatesCounted.size)
    }

    @Test fun ac06_completeWinsWhenTargetMetOnTheEndDate() {
        val s = spec("2026-11-13", "2026-11-15", 3)
        val credited = setOf(d("2026-11-13"), d("2026-11-14"), d("2026-11-15"))
        assertEquals(ChallengeOutcome.ACTIVE, ChallengeRules.decide(s, credited, at("2026-11-15", 23, 59, 59)).outcome)
        assertEquals(ChallengeOutcome.COMPLETE, ChallengeRules.decide(s, credited, at("2026-11-16", 0, 0, 0)).outcome)
        // Still COMPLETE (never EXPIRED) when the app only looks days later.
        assertEquals(ChallengeOutcome.COMPLETE, ChallengeRules.decide(s, credited, at("2026-11-30", 9, 0, 0)).outcome)
    }

    @Test fun ac07_missedTargetExpiresAtEndDatePlusOneMidnight() {
        val s = spec("2026-11-13", "2026-11-15", 3)
        val credited = setOf(d("2026-11-13"), d("2026-11-15"))
        assertEquals(ChallengeOutcome.ACTIVE, ChallengeRules.decide(s, credited, at("2026-11-15", 23, 59, 59)).outcome)
        val ended = ChallengeRules.decide(s, credited, at("2026-11-16", 0, 0, 0))
        assertEquals(ChallengeOutcome.EXPIRED, ended.outcome)
        assertEquals(at("2026-11-16", 0, 0, 0), ended.endedAtEpochSec)
        assertEquals(2, ended.creditedDatesCounted.size)   // earned days are preserved in the snapshot
        assertNull(ended.targetReachedDate)
    }

    @Test fun targetIsCumulativeNotConsecutiveAndOutOfRangeDaysAreIgnored() {
        val s = spec("2026-11-10", "2026-11-20", 3)
        val credited = setOf(d("2026-11-09"), d("2026-11-10"), d("2026-11-12"), d("2026-11-15"), d("2026-11-21"))
        val r = ChallengeRules.decide(s, credited, at("2026-11-16", 0, 0, 0))
        assertEquals(d("2026-11-15"), r.targetReachedDate)       // 3rd credited date inside the period
        assertEquals(ChallengeOutcome.COMPLETE, r.outcome)
    }

    @Test fun ac08_dstDaysAreRealLengthAndExpiryUsesTheChallengeZone() {
        // New York: 2026-03-08 has 23 hours, 2026-11-01 has 25 hours.
        val full23 = ActiveInterval(ChallengeRules.dayStart(d("2026-03-08"), ny), ChallengeRules.dayStart(d("2026-03-09"), ny))
        val full25 = ActiveInterval(ChallengeRules.dayStart(d("2026-11-01"), ny), ChallengeRules.dayStart(d("2026-11-02"), ny))
        assertEquals(23 * 3600L, ChallengeRules.secondsOnDate(listOf(full23), d("2026-03-08"), ny))
        assertEquals(25 * 3600L, ChallengeRules.secondsOnDate(listOf(full25), d("2026-11-01"), ny))
        // endDate = 03-08 -> expires at 03-09 00:00 EDT = 04:00Z (not 05:00Z).
        val s = spec("2026-03-06", "2026-03-08", 5, ny)
        assertEquals(Instant.parse("2026-03-09T04:00:00Z").epochSecond, ChallengeRules.endExclusiveEpochSec(s))
        assertEquals(ChallengeOutcome.ACTIVE, ChallengeRules.decide(s, emptySet(), Instant.parse("2026-03-09T03:59:59Z").epochSecond).outcome)
        assertEquals(ChallengeOutcome.EXPIRED, ChallengeRules.decide(s, emptySet(), Instant.parse("2026-03-09T04:00:00Z").epochSecond).outcome)
        // endDate = 11-01 (25h day) -> expires at 11-02 00:00 EST = 05:00Z.
        val s2 = spec("2026-10-30", "2026-11-01", 5, ny)
        assertEquals(Instant.parse("2026-11-02T05:00:00Z").epochSecond, ChallengeRules.endExclusiveEpochSec(s2))
    }

    @Test fun ac08_noSecondIsLostOrCountedTwiceAcrossDatesAndZones() {
        val iv = listOf(
            ActiveInterval(at("2026-03-07", 23, 10, 0, ny), at("2026-03-08", 3, 30, 0, ny)),   // crosses midnight AND the DST jump
            ActiveInterval(at("2026-03-08", 12, 0, 0, ny), at("2026-03-08", 12, 20, 7, ny))
        )
        val total = iv.sumOf { it.seconds }
        for (zone in listOf(ny, seoul, ZoneId.of("UTC"))) {
            assertEquals("slices must sum to the total in $zone", total, ChallengeRules.sliceByDate(iv, zone).values.sum())
        }
        // The same interval belongs to different dates in different zones: the challenge zone decides.
        val kst = ActiveInterval(at("2026-11-16", 8, 0, 0), at("2026-11-16", 8, 20, 0))
        assertEquals(1200L, ChallengeRules.secondsOnDate(listOf(kst), d("2026-11-16"), seoul))
        assertEquals(1200L, ChallengeRules.secondsOnDate(listOf(kst), d("2026-11-15"), ny))
        assertEquals(0L, ChallengeRules.secondsOnDate(listOf(kst), d("2026-11-16"), ny))
    }

    @Test fun anIntervalEndingExactlyAtMidnightDoesNotTouchTheNextDate() {
        val iv = ActiveInterval(at("2026-11-15", 23, 0, 0), at("2026-11-16", 0, 0, 0))
        assertEquals(listOf(d("2026-11-15")), ChallengeRules.datesTouched(iv, seoul))
        val over = ActiveInterval(at("2026-11-15", 23, 59, 0), at("2026-11-16", 0, 1, 0))
        assertEquals(listOf(d("2026-11-15"), d("2026-11-16")), ChallengeRules.datesTouched(over, seoul))
    }
}
