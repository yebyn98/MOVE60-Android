package com.move60.workout.domain

import java.time.LocalDate

/**
 * Committed, date-attributed active-time contribution from a finalized session.
 * Construct only after a confirmed database write; NEVER count Draft/pending/failed edits.
 * Total session activity and completed sets determine session validity, while the per-day
 * contribution determines challenge-day credit. Pauses never count toward active time.
 *
 * For midnight crossing, emit one slice per date based on actual active-time intervals,
 * not elapsed wall-clock time. The (sessionId, date) pair is an idempotency key.
 */
data class StoredWorkoutDayContribution(
    val sessionId: String,
    val date: LocalDate,
    val activeSecondsOnDate: Long,
    val sessionActiveSeconds: Long,
    val completedStrengthSets: Int
)

data class ChallengeDayDecision(
    val date: LocalDate,
    val qualifyingActiveSeconds: Long,
    val validSessionCount: Int
) {
    val credited: Boolean get() = qualifyingActiveSeconds >= WorkoutRecognitionPolicy.CHALLENGE_DAY_MIN_SECONDS
    val creditedDays: Int get() = if (credited) 1 else 0
}

/**
 * Ordinary session: >=60 active seconds OR >=1 completed (not skipped) strength set.
 * Challenge date: >=600 active seconds from valid, already stored sessions, max one day.
 * This standalone evaluator does not persist, migrate or integrate the legacy app.
 */
object WorkoutRecognitionPolicy {
    const val SESSION_MIN_SECONDS = 60L
    const val CHALLENGE_DAY_MIN_SECONDS = 600L

    fun validSession(activeSeconds: Long, completedStrengthSets: Int): Boolean {
        require(activeSeconds >= 0L) { "Active time cannot be negative" }
        require(completedStrengthSets >= 0) { "Completed set count cannot be negative" }
        return activeSeconds >= SESSION_MIN_SECONDS || completedStrengthSets >= 1
    }

    fun evaluateChallengeDay(
        date: LocalDate,
        contributions: List<StoredWorkoutDayContribution>
    ): ChallengeDayDecision {
        val unique = linkedMapOf<String, StoredWorkoutDayContribution>()
        for (record in contributions) {
            if (record.date != date) continue
            require(record.sessionId.isNotBlank()) { "Stored session needs a stable ID" }
            require(record.activeSecondsOnDate >= 0L) { "Daily active time cannot be negative" }
            require(record.activeSecondsOnDate <= record.sessionActiveSeconds) {
                "Daily contribution cannot exceed saved session active time"
            }
            validSession(record.sessionActiveSeconds, record.completedStrengthSets)
            val previous = unique[record.sessionId]
            require(previous == null || previous == record) {
                "Conflicting persisted snapshots for session/day: " + record.sessionId
            }
            unique[record.sessionId] = record
        }

        var qualifyingSeconds = 0L
        var count = 0
        for (record in unique.values) {
            if (!validSession(record.sessionActiveSeconds, record.completedStrengthSets)) continue
            qualifyingSeconds = try {
                Math.addExact(qualifyingSeconds, record.activeSecondsOnDate)
            } catch (_: ArithmeticException) {
                throw IllegalArgumentException("Challenge day active-time sum overflow")
            }
            count += 1
        }
        return ChallengeDayDecision(date, qualifyingSeconds, count)
    }
}
