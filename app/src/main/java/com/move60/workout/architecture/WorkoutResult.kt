package com.move60.workout.architecture

import java.time.LocalDate

/** Judgement of the workout (P-15 B) is separate from whether the DB write succeeded (E-03 A). */
/** NOT_STORED: nothing worth saving (D-01 B: under 1 minute and 0 sets is discarded, not an error). */
enum class ResultSaveState { SAVING, SAVED, FAILED, NOT_STORED }

data class WorkoutResult(
    val sessionId: String,
    val completion: SessionCompletion?,
    val activeSeconds: Long,
    val completedSets: Int,
    /** Null when no active challenge exists; otherwise a plain-language credit line for the session's date. */
    val challengeLine: String?,
    val saveState: ResultSaveState
)

/**
 * Builds the result of one finished/ended timer session from STORED data only. Nothing is estimated: if the session
 * row does not exist yet the result is [ResultSaveState.SAVING] (or FAILED after the caller gave up waiting).
 */
class WorkoutResultReader(
    private val db: WorkoutDatabase,
    private val completedSets: (token: Long) -> Int
) {
    /** `timer-<yyyy-MM-dd>-d<day>-<startedAtEpochMs>` as produced by [TimerSessionSync.sessionId]. */
    internal fun parse(sessionId: String): Pair<LocalDate, Long>? {
        val m = Regex("^timer-(\\d{4}-\\d{2}-\\d{2})-d\\d+-(\\d+)$").matchEntire(sessionId) ?: return null
        return runCatching { LocalDate.parse(m.groupValues[1]) to m.groupValues[2].toLong() }.getOrNull()
    }

    suspend fun read(sessionId: String, giveUp: Boolean, liveActiveSeconds: Long? = null): WorkoutResult {
        val (date, token) = parse(sessionId)
            ?: return WorkoutResult(sessionId, null, 0, 0, null, ResultSaveState.FAILED)
        val dao = db.routiveDao()
        val session = dao.session(sessionId)
            ?: run {
                val sets = completedSets(token)
                if (giveUp && liveActiveSeconds != null && liveActiveSeconds < 60 && sets == 0)
                    return WorkoutResult(sessionId, SessionCompletion.NOT_PERFORMED, liveActiveSeconds, 0, null, ResultSaveState.NOT_STORED)
                return WorkoutResult(sessionId, null, 0, sets, null,
                    if (giveUp) ResultSaveState.FAILED else ResultSaveState.SAVING)
            }
        val line = committerChallengeLine(date)
        return WorkoutResult(
            sessionId, runCatching { SessionCompletion.valueOf(session.completion) }.getOrNull(),
            session.totalActiveSeconds, completedSets(token), line, ResultSaveState.SAVED
        )
    }

    private suspend fun committerChallengeLine(date: LocalDate): String? {
        val dao = db.routiveDao()
        val active = dao.activeChallenges().firstOrNull() ?: return null
        val credit = dao.credit(active.challengeId, date.toString())
        val name = "진행 중 도전 · ${active.name}"
        return when {
            credit?.credited == true -> "$name\n오늘 운동 인정 (하루 합계 ${credit.activeSeconds / 60}분)"
            credit != null -> "$name\n오늘 합계 ${credit.activeSeconds / 60}분 · 하루 10분 이상이면 인정돼요"
            else -> "$name\n오늘은 이 도전 기간의 운동으로 집계되지 않았어요"
        }
    }
}
