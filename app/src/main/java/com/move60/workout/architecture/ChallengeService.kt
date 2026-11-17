package com.move60.workout.architecture

import androidx.room.withTransaction
import com.move60.workout.domain.ActiveInterval
import com.move60.workout.domain.ChallengeOutcome
import com.move60.workout.domain.ChallengeRules
import com.move60.workout.domain.ChallengeSpec
import com.move60.workout.domain.StoredWorkoutDayContribution
import com.move60.workout.domain.WorkoutRecognitionPolicy
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import org.json.JSONArray
import org.json.JSONObject

enum class ChallengeReject { TARGET_EXCEEDS_PERIOD, INVALID_FIELD, INVALID_ZONE, INVALID_DATE, START_DATE_NOT_CREATION_DAY, ACTIVE_CHALLENGE_EXISTS }

sealed interface ChallengeResult {
    /** [creditedDays] counts dates already credited at creation, i.e. same-day retroactive credit (C-20261115-01). */
    data class Created(val challengeId: String, val creditedDays: Int) : ChallengeResult
    data class AlreadyExists(val challengeId: String) : ChallengeResult
    data class Conflict(val challengeId: String) : ChallengeResult
    data class Rejected(val reason: ChallengeReject, val detail: String) : ChallengeResult
    data class Failed(val cause: Throwable) : ChallengeResult
}

sealed interface AbandonResult {
    data class Abandoned(val challengeId: String) : AbandonResult
    data class AlreadyEnded(val status: String) : AbandonResult
    data object NotFound : AbandonResult
    /** Abandoning needs an explicit user confirmation (policy P-14); nothing was changed. */
    data object NotConfirmed : AbandonResult
}

/**
 * Challenge lifecycle on top of committed sessions (P-10..14, P-20, P-21, P-24, P-25, E-03):
 *  - day credit is RE-COMPUTED from stored valid sessions' UTC active intervals in the challenge's own fixed zone,
 *    so adding workouts or re-running after a failure/retry never double counts and always shows the latest total
 *  - at most one ACTIVE challenge (P-13); the start day must be the creation day (a past start date is NOT approved)
 *  - state comes from the single [ChallengeRules.decide]; terminal states are written once with a frozen snapshot
 *  - nothing here depends on a midnight job: [settleDue] re-derives the same answer at the next launch (AC-13)
 */
class ChallengeService(private val db: WorkoutDatabase, private val clock: () -> Long) {
    private val dao get() = db.routiveDao()

    companion object {
        const val MAX_NAME = 30

        /** A fixed, inclusive period measured from one creation-day snapshot in the challenge zone. */
        fun inclusiveEndDate(start: LocalDate, periodDays: Int): LocalDate {
            require(periodDays >= 1) { "Period must contain at least one day" }
            return start.plusDays(periodDays.toLong() - 1L)
        }
    }

    suspend fun create(requested: ChallengeEntity): ChallengeResult = try {
        db.withTransaction { createInTransaction(requested) }
    } catch (error: Throwable) {
        if (error is kotlinx.coroutines.CancellationException) throw error
        ChallengeResult.Failed(error)
    }

    suspend fun abandon(challengeId: String, confirmed: Boolean): AbandonResult = db.withTransaction {
        if (!confirmed) return@withTransaction AbandonResult.NotConfirmed
        val nowMs = clock()
        val current = dao.challenge(challengeId) ?: return@withTransaction AbandonResult.NotFound
        if (current.status == ChallengeStatus.ACTIVE.name) settle(current, nowMs / 1000L, nowMs)
        val latest = dao.challenge(challengeId)!!
        if (latest.status != ChallengeStatus.ACTIVE.name) return@withTransaction AbandonResult.AlreadyEnded(latest.status)
        val spec = latest.toSpec()
        val credited = creditedDates(latest)
        val rows = dao.finishChallenge(
            challengeId, ChallengeStatus.ABANDONED.name, nowMs,
            snapshot(latest, spec, ChallengeStatus.ABANDONED, nowMs / 1000L, credited, null)
        )
        if (rows == 1) AbandonResult.Abandoned(challengeId) else AbandonResult.AlreadyEnded(dao.challenge(challengeId)!!.status)
    }

    /** Rename an ACTIVE challenge only (1..[MAX_NAME] chars after trim). Ended challenges are never changed. */
    suspend fun rename(challengeId: String, name: String): Boolean {
        val trimmed = name.trim()
        if (trimmed.isEmpty() || trimmed.length > MAX_NAME) return false
        return db.withTransaction { dao.renameActive(challengeId, trimmed) == 1 }
    }

    suspend fun active(): ChallengeEntity? = dao.activeChallenges().firstOrNull()
    suspend fun ended(): List<ChallengeEntity> = dao.endedChallenges()
    suspend fun credits(challengeId: String): List<ChallengeDayCreditEntity> = dao.credits(challengeId)

    /** Re-derive every ACTIVE challenge from stored data (e.g. after a correction) and settle due ones. */
    suspend fun refreshActive(): Int = db.withTransaction {
        val nowMs = clock()
        var changed = 0
        for (challenge in dao.activeChallenges()) {
            val spec = challenge.toSpec()
            val today = Instant.ofEpochSecond(nowMs / 1000L).atZone(spec.zone).toLocalDate()
            var d = spec.startDate
            while (!d.isAfter(minOf(spec.endDate, today))) { recomputeDate(challenge, spec, d, nowMs); d = d.plusDays(1) }
            if (settle(challenge, nowMs / 1000L, nowMs)) changed += 1
        }
        changed
    }

    /** Settle only by time: call on app start / foreground so a closed app still ends challenges correctly. */
    suspend fun settleDue(): Int = db.withTransaction { settleAllActive(clock()) }

    // ---- used by SessionCommitter inside ITS transaction -------------------------------------------------

    internal suspend fun settleAllActive(nowMs: Long): Int {
        var changed = 0
        for (challenge in dao.activeChallenges()) if (settle(challenge, nowMs / 1000L, nowMs)) changed += 1
        return changed
    }

    /** After a valid session was stored: refresh the touched days of every still-ACTIVE challenge, then settle. */
    internal suspend fun onSessionStored(intervals: List<ActiveInterval>, nowMs: Long): List<String> {
        val touched = ArrayList<String>()
        for (challenge in dao.activeChallenges()) {
            val spec = challenge.toSpec()
            val dates = intervals.flatMap { ChallengeRules.datesTouched(it, spec.zone) }.distinct()
                .filter { !it.isBefore(spec.startDate) && !it.isAfter(spec.endDate) }
            if (dates.isEmpty()) continue
            dates.forEach { recomputeDate(challenge, spec, it, nowMs) }
            settle(challenge, nowMs / 1000L, nowMs)
            touched += challenge.challengeId
        }
        return touched
    }

    // ---- internals ----------------------------------------------------------------------------------------

    private suspend fun createInTransaction(requested: ChallengeEntity): ChallengeResult {
        // Idempotent retry first: the same request must be a no-op even on a later day.
        dao.challenge(requested.challengeId)?.let { existing ->
            return if (sameDefinition(existing, requested)) ChallengeResult.AlreadyExists(existing.challengeId)
            else ChallengeResult.Conflict(existing.challengeId)
        }
        fun reject(reason: ChallengeReject, detail: String) = ChallengeResult.Rejected(reason, detail)
        if (requested.challengeId.isBlank() || requested.name.trim().length !in 1..MAX_NAME || requested.targetDays < 1 ||
            requested.status != ChallengeStatus.ACTIVE.name || requested.endedAtEpochMs != null || requested.endSnapshotJson != null
        ) return reject(ChallengeReject.INVALID_FIELD, "id/name/target/status")
        val zone = runCatching { ZoneId.of(requested.timeZoneId) }.getOrNull() ?: return reject(ChallengeReject.INVALID_ZONE, requested.timeZoneId)
        val start = runCatching { LocalDate.parse(requested.startDate) }.getOrNull() ?: return reject(ChallengeReject.INVALID_DATE, requested.startDate)
        val end = runCatching { LocalDate.parse(requested.endDate) }.getOrNull() ?: return reject(ChallengeReject.INVALID_DATE, requested.endDate)
        if (end.isBefore(start)) return reject(ChallengeReject.INVALID_DATE, "endDate before startDate")
        // Target days are cumulative credited days, at most one per date: 1 <= target <= days in the inclusive period.
        val periodDays = ChronoUnit.DAYS.between(start, end) + 1
        if (requested.targetDays > periodDays) return reject(ChallengeReject.TARGET_EXCEEDS_PERIOD, "target=${requested.targetDays} periodDays=$periodDays")

        val nowMs = clock()
        val today = Instant.ofEpochSecond(nowMs / 1000L).atZone(zone).toLocalDate()
        // Whether a PAST start date may be chosen is not approved, so only the creation day is accepted.
        if (start != today) return reject(ChallengeReject.START_DATE_NOT_CREATION_DAY, "start=$start today=$today")

        settleAllActive(nowMs)                       // an overdue challenge must end before P-13 is checked
        if (dao.activeChallenges().isNotEmpty()) return reject(ChallengeReject.ACTIVE_CHALLENGE_EXISTS, "only one ACTIVE challenge (P-13)")

        val challenge = requested.copy(createdAtEpochMs = nowMs)
        dao.insertChallenge(challenge)
        val spec = challenge.toSpec()
        var d = spec.startDate
        while (!d.isAfter(minOf(spec.endDate, today))) { recomputeDate(challenge, spec, d, nowMs); d = d.plusDays(1) }
        settle(challenge, nowMs / 1000L, nowMs)
        return ChallengeResult.Created(challenge.challengeId, dao.credits(challenge.challengeId).count { it.credited })
    }

    /** Rebuild one challenge date from stored VALID sessions' UTC intervals, in the challenge's fixed zone. */
    private suspend fun recomputeDate(challenge: ChallengeEntity, spec: ChallengeSpec, date: LocalDate, nowMs: Long) {
        val windowStart = ChallengeRules.dayStart(date, spec.zone)
        val windowEnd = ChallengeRules.dayStart(date.plusDays(1), spec.zone)
        val contributions = dao.validIntervalsOverlapping(windowStart, windowEnd).groupBy { it.sessionId }.mapNotNull { (id, rows) ->
            val seconds = ChallengeRules.secondsOnDate(rows.map { ActiveInterval(it.startEpochSec, it.endEpochSec) }, date, spec.zone)
            if (seconds <= 0L) null
            else StoredWorkoutDayContribution(id, date, seconds, rows.first().totalActiveSeconds, rows.first().completedStrengthSets)
        }
        if (contributions.isEmpty()) {
            if (dao.credit(challenge.challengeId, date.toString()) != null) {
                dao.upsertCredit(ChallengeDayCreditEntity(challenge.challengeId, date.toString(), 0L, 0, false, nowMs))
            }
            return
        }
        val decision = WorkoutRecognitionPolicy.evaluateChallengeDay(date, contributions)
        dao.upsertCredit(
            ChallengeDayCreditEntity(challenge.challengeId, date.toString(), decision.qualifyingActiveSeconds,
                decision.validSessionCount, decision.credited, nowMs)
        )
    }

    private suspend fun creditedDates(challenge: ChallengeEntity): Set<LocalDate> =
        dao.credits(challenge.challengeId).filter { it.credited }.map { LocalDate.parse(it.date) }.toSet()

    /** Applies [ChallengeRules.decide]; writes a terminal state exactly once. Returns true if it ended now. */
    private suspend fun settle(challenge: ChallengeEntity, nowSec: Long, nowMs: Long): Boolean {
        if (challenge.status != ChallengeStatus.ACTIVE.name) return false
        val spec = challenge.toSpec()
        val decision = ChallengeRules.decide(spec, creditedDates(challenge), nowSec)
        if (decision.outcome == ChallengeOutcome.ACTIVE) return false
        val status = if (decision.outcome == ChallengeOutcome.COMPLETE) ChallengeStatus.COMPLETE else ChallengeStatus.EXPIRED
        val endedAt = decision.endedAtEpochSec!!
        val rows = dao.finishChallenge(
            challenge.challengeId, status.name, endedAt * 1000L,
            snapshot(challenge, spec, status, endedAt, decision.creditedDatesCounted.toSet(), decision.targetReachedDate)
        )
        return rows == 1
    }

    /** Frozen terminal snapshot (P-24): goal, period, zone, credited days and seconds, rate, end state and time. */
    private suspend fun snapshot(
        challenge: ChallengeEntity, spec: ChallengeSpec, status: ChallengeStatus, endedAtSec: Long,
        credited: Set<LocalDate>, targetReached: LocalDate?
    ): String {
        val seconds = dao.credits(challenge.challengeId).filter { it.credited && LocalDate.parse(it.date) in credited }
        val days = JSONArray()
        seconds.forEach { days.put(JSONObject().put("date", it.date).put("activeSeconds", it.activeSeconds)) }
        return JSONObject()
            .put("challengeId", challenge.challengeId).put("name", challenge.name).put("status", status.name)
            .put("startDate", challenge.startDate).put("endDate", challenge.endDate).put("timeZoneId", challenge.timeZoneId)
            .put("targetDays", challenge.targetDays).put("creditedDays", credited.size)
            .put("achievementRate", minOf(1.0, credited.size.toDouble() / spec.targetDays))
            .put("targetReachedDate", targetReached?.toString() ?: JSONObject.NULL)
            .put("endedAtEpochSec", endedAtSec).put("days", days)
            .toString()
    }

    private fun sameDefinition(a: ChallengeEntity, b: ChallengeEntity) =
        a.name == b.name && a.startDate == b.startDate && a.endDate == b.endDate &&
            a.timeZoneId == b.timeZoneId && a.targetDays == b.targetDays

    private fun ChallengeEntity.toSpec() =
        ChallengeSpec(LocalDate.parse(startDate), LocalDate.parse(endDate), ZoneId.of(timeZoneId), targetDays)
}
