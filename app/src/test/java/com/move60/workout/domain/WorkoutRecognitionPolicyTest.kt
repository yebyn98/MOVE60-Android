package com.move60.workout.domain

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WorkoutRecognitionPolicyTest {
    private val today = LocalDate.of(2026, 11, 15)
    private val tomorrow = today.plusDays(1)

    private fun saved(
        id: String, dateSeconds: Long, sets: Int = 0,
        date: LocalDate = today, sessionSeconds: Long = dateSeconds
    ) = StoredWorkoutDayContribution(id, date, dateSeconds, sessionSeconds, sets)

    @Test fun sessionThresholdAndCompletedSetAreIndependent() {
        assertFalse(WorkoutRecognitionPolicy.validSession(59, 0))
        assertTrue(WorkoutRecognitionPolicy.validSession(60, 0))
        assertTrue(WorkoutRecognitionPolicy.validSession(0, 1))
        assertFalse(WorkoutRecognitionPolicy.validSession(0, 0))
    }

    @Test fun multipleSessionsReachExactlyTenMinutes() {
        val tooShort = WorkoutRecognitionPolicy.evaluateChallengeDay(
            today, listOf(saved("a", 299), saved("b", 300))
        )
        assertEquals(599L, tooShort.qualifyingActiveSeconds)
        assertEquals(0, tooShort.creditedDays)
        val enough = WorkoutRecognitionPolicy.evaluateChallengeDay(
            today, listOf(saved("a", 300), saved("b", 300))
        )
        assertEquals(600L, enough.qualifyingActiveSeconds)
        assertEquals(2, enough.validSessionCount)
        assertEquals(1, enough.creditedDays)
    }

    @Test fun aLongSessionNeverCreditsMoreThanOneDay() {
        val result = WorkoutRecognitionPolicy.evaluateChallengeDay(today, listOf(saved("a", 1800)))
        assertEquals(1, result.creditedDays)
    }

    @Test fun invalidSessionNeverInflatesChallengeTime() {
        val result = WorkoutRecognitionPolicy.evaluateChallengeDay(
            today, listOf(saved("invalid", 59), saved("valid", 541))
        )
        assertEquals(541L, result.qualifyingActiveSeconds)
        assertFalse(result.credited)
        assertEquals(1, result.validSessionCount)
    }

    @Test fun completedSetCanValidateSessionButNotZeroSecondChallengeCredit() {
        val result = WorkoutRecognitionPolicy.evaluateChallengeDay(today, listOf(saved("set", 0, sets = 1)))
        assertEquals(1, result.validSessionCount)
        assertEquals(0L, result.qualifyingActiveSeconds)
        assertFalse(result.credited)
    }

    @Test fun identicalRetryDoesNotDoubleCredit() {
        val record = saved("session-1", 600)
        val result = WorkoutRecognitionPolicy.evaluateChallengeDay(today, listOf(record, record))
        assertEquals(600L, result.qualifyingActiveSeconds)
        assertEquals(1, result.validSessionCount)
        assertEquals(1, result.creditedDays)
    }

    @Test(expected = IllegalArgumentException::class)
    fun conflictingRetryIsRejectedRatherThanSilentlyDoubleCounted() {
        WorkoutRecognitionPolicy.evaluateChallengeDay(
            today, listOf(saved("same", 400), saved("same", 600))
        )
    }

    @Test fun oneOvernightSessionUsesLocalDayActiveSlices() {
        val first = saved("overnight", 590, date = today, sessionSeconds = 700)
        val second = saved("overnight", 110, date = tomorrow, sessionSeconds = 700)
        val contributions = listOf(first, second)
        assertEquals(590L, WorkoutRecognitionPolicy.evaluateChallengeDay(today, contributions).qualifyingActiveSeconds)
        assertEquals(110L, WorkoutRecognitionPolicy.evaluateChallengeDay(tomorrow, contributions).qualifyingActiveSeconds)
        assertFalse(WorkoutRecognitionPolicy.evaluateChallengeDay(today, contributions).credited)
        assertFalse(WorkoutRecognitionPolicy.evaluateChallengeDay(tomorrow, contributions).credited)
    }

    @Test(expected = IllegalArgumentException::class)
    fun dayContributionCannotExceedTotalSessionTime() {
        WorkoutRecognitionPolicy.evaluateChallengeDay(
            today, listOf(saved("bad", 601, sessionSeconds = 600))
        )
    }

    @Test(expected = IllegalArgumentException::class)
    fun negativeTimeIsRejected() {
        WorkoutRecognitionPolicy.validSession(-1, 1)
    }

    @Test(expected = IllegalArgumentException::class)
    fun emptyStableIdIsRejected() {
        WorkoutRecognitionPolicy.evaluateChallengeDay(today, listOf(saved("", 600)))
    }

    @Test(expected = IllegalArgumentException::class)
    fun summationOverflowIsRejected() {
        WorkoutRecognitionPolicy.evaluateChallengeDay(
            today, listOf(saved("max", Long.MAX_VALUE), saved("extra", 60))
        )
    }
}
