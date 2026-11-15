package com.move60.workout.architecture

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionCommitterTest {
    private lateinit var db: WorkoutDatabase
    private lateinit var committer: SessionCommitter
    private var now = 1_000L
    private val dao get() = db.routiveDao()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkoutDatabase::class.java)
            .allowMainThreadQueries().build()
        committer = SessionCommitter(db) { ++now }
    }
    @After fun close() { db.close() }

    private fun session(id: String, active: Long, sets: Int = 0, legacyToken: Long? = null, completion: SessionCompletion = SessionCompletion.COMPLETED) =
        SessionEntity(id, SessionSource.LIVE.name, legacyToken, "Asia/Seoul", 0L, 1L, completion.name, active, sets, false, null, 0L)
    private fun bundle(id: String, active: Long, date: String = "2026-11-10", sets: Int = 0, legacyToken: Long? = null,
                       segments: List<SessionSegmentEntity> = emptyList()): SessionBundle {
        val setRows = (1..sets).map { SessionSetEntity(id, "bench", it, SetState.COMPLETED.name, "40", "10", 5L) }
        return SessionBundle(session(id, active, sets, legacyToken), listOf(SessionDaySliceEntity(id, date, active)), setRows, segments)
    }
    private fun segment(id: String, order: Int, secs: Long = 30L, planned: Long? = null) =
        SessionSegmentEntity(id, order, SegmentKind.CARDIO_WORK.name, planned, secs, "cycle", """{"watts":"120"}""")
    private fun challenge(id: String = "c1", start: String = "2026-11-01", end: String = "2026-11-30") =
        ChallengeEntity(id, "challenge", start, end, 10, ChallengeStatus.ACTIVE.name, 1L, null, null)

    // ---- validity & recognition ----
    @Test fun sessionValidityFollowsPolicy() = runBlocking {
        committer.commit(bundle("s59", 59))
        committer.commit(bundle("s60", 60))
        committer.commit(bundle("s59set", 59, sets = 1))
        assertEquals(false, dao.session("s59")!!.validSession)
        assertEquals(true, dao.session("s60")!!.validSession)
        assertEquals(true, dao.session("s59set")!!.validSession)
    }

    @Test fun callerCannotAssertValidity() = runBlocking {
        val b = bundle("liar", 10)
        committer.commit(b.copy(session = b.session.copy(validSession = true)))
        assertEquals(false, dao.session("liar")!!.validSession)
    }

    @Test fun rawSegmentsOfBelowThresholdSessionAreNotStoredButSummaryIs() = runBlocking {
        val result = committer.commit(bundle("short", 45, segments = listOf(segment("short", 0), segment("short", 1))))
        assertTrue(result is CommitResult.Committed)
        assertEquals(0, dao.segments("short").size)
        assertEquals(45L, dao.session("short")!!.totalActiveSeconds)
        committer.commit(bundle("long", 120, segments = listOf(segment("long", 0), segment("long", 1))))
        assertEquals(2, dao.segments("long").size)
    }

    // ---- idempotency / conflict ----
    @Test fun identicalRetryIsIdempotent() = runBlocking {
        val b = bundle("dup", 300, sets = 2)
        assertTrue(committer.commit(b) is CommitResult.Committed)
        val first = dao.session("dup")!!
        assertTrue(committer.commit(b) is CommitResult.AlreadyCommitted)
        assertTrue(committer.commit(b) is CommitResult.AlreadyCommitted)
        assertEquals(first, dao.session("dup"))
        assertEquals(1, dao.slices("dup").size)
        assertEquals(2, dao.sets("dup").size)
    }

    @Test fun sameIdDifferentContentIsConflictAndStoredDataUnchanged() = runBlocking {
        committer.commit(bundle("x", 300))
        val result = committer.commit(bundle("x", 301))
        assertTrue(result is CommitResult.Conflict)
        assertEquals(300L, dao.session("x")!!.totalActiveSeconds)
        assertEquals(300L, dao.slices("x").single().activeSecondsOnDate)
    }

    // ---- validation rejects, nothing stored ----
    @Test fun invalidBundlesAreRejectedAndNothingIsStored() = runBlocking {
        val base = bundle("bad", 100, sets = 1)
        val cases = mapOf(
            RejectReason.SLICE_SUM_MISMATCH to base.copy(slices = listOf(SessionDaySliceEntity("bad", "2026-11-10", 99))),
            RejectReason.COMPLETED_SET_COUNT_MISMATCH to base.copy(session = base.session.copy(completedStrengthSets = 2)),
            RejectReason.DUPLICATE_DAY_SLICE to base.copy(slices = listOf(SessionDaySliceEntity("bad", "2026-11-10", 50), SessionDaySliceEntity("bad", "2026-11-10", 50))),
            RejectReason.INVALID_DATE to base.copy(slices = listOf(SessionDaySliceEntity("bad", "2026-13-40", 100))),
            RejectReason.INVALID_SET to base.copy(sets = listOf(SessionSetEntity("bad", "bench", 1, SetState.COMPLETED.name, "40", "0", 5L))),
            RejectReason.DUPLICATE_SET to base.copy(sets = base.sets + base.sets),
            RejectReason.INVALID_SEGMENT to base.copy(segments = listOf(segment("bad", 0, secs = 0))),
            RejectReason.DUPLICATE_SEGMENT to base.copy(segments = listOf(segment("bad", 0), segment("bad", 0))),
            RejectReason.NEGATIVE_VALUE to base.copy(session = base.session.copy(totalActiveSeconds = -1)),
            RejectReason.BLANK_SESSION_ID to base.copy(session = base.session.copy(sessionId = " ")),
            RejectReason.INVALID_ZONE to base.copy(session = base.session.copy(zoneId = "Mars/Base")),
            RejectReason.CHILD_SESSION_ID_MISMATCH to base.copy(sets = listOf(SessionSetEntity("other", "bench", 1, SetState.COMPLETED.name, "40", "10", 5L))),
            RejectReason.LEGACY_AGGREGATE_MUST_BE_UNKNOWN to base.copy(session = base.session.copy(source = SessionSource.LEGACY_DAY_AGGREGATE.name))
        )
        for ((reason, bad) in cases) {
            val r = committer.commit(bad)
            assertTrue("$reason -> $r", r is CommitResult.Rejected && r.reason == reason)
        }
        assertNull(dao.session("bad"))
    }

    @Test fun skippedSetsAreStoredButNeverCountAsCompleted() = runBlocking {
        val b = bundle("skip", 30, sets = 0).copy(sets = listOf(SessionSetEntity("skip", "squat", 1, SetState.SKIPPED.name, "", "", null)))
        assertTrue(committer.commit(b) is CommitResult.Committed)
        assertEquals(0, dao.session("skip")!!.completedStrengthSets)
        assertEquals(false, dao.session("skip")!!.validSession)   // 30s and no COMPLETED set
    }

    // ---- challenge credit (separate rule from session validity) ----
    @Test fun challengeDayNeeds600SummedSecondsFromValidSessionsMaxOnePerDate() = runBlocking {
        dao.insertChallenge(challenge())
        assertEquals(emptyList<String>(), (committer.commit(bundle("a", 300)) as CommitResult.Committed).creditedChallenges)
        assertEquals(0, dao.credits("c1").size)
        assertEquals(listOf("c1"), (committer.commit(bundle("b", 300)) as CommitResult.Committed).creditedChallenges)
        committer.commit(bundle("c", 900))
        assertEquals(1, dao.credits("c1").size)          // never more than one day per date
        assertEquals(600L, dao.credits("c1").single().qualifyingActiveSeconds)
        committer.commit(bundle("d", 600, date = "2026-11-11"))
        assertEquals(listOf("2026-11-10", "2026-11-11"), dao.credits("c1").map { it.date })
    }

    @Test fun belowThresholdSessionsDoNotContributeToChallengeSum() = runBlocking {
        dao.insertChallenge(challenge())
        committer.commit(bundle("tiny", 59))                       // invalid (59s, no set)
        val r = committer.commit(bundle("rest", 541)) as CommitResult.Committed   // 59 + 541 = 600 only if the invalid one counted
        assertEquals(emptyList<String>(), r.creditedChallenges)
        assertEquals(0, dao.credits("c1").size)
    }

    @Test fun challengeOutsideItsPeriodOrNotActiveGetsNoCredit() = runBlocking {
        dao.insertChallenge(challenge("past", "2026-10-01", "2026-10-31"))
        dao.insertChallenge(challenge("done").copy(status = ChallengeStatus.ABANDONED.name))
        val r = committer.commit(bundle("s", 700)) as CommitResult.Committed
        assertEquals(emptyList<String>(), r.creditedChallenges)
    }

    @Test fun midnightCrossingSessionIsCreditedPerDateFromActiveSlices() = runBlocking {
        dao.insertChallenge(challenge())
        val id = "night"
        val b = SessionBundle(session(id, 1_200),
            listOf(SessionDaySliceEntity(id, "2026-11-10", 700), SessionDaySliceEntity(id, "2026-11-11", 500)))
        committer.commit(b)
        assertEquals(listOf("2026-11-10"), dao.credits("c1").map { it.date })   // 700>=600 credited; 500 not
        assertEquals(700L, dao.credits("c1").single().qualifyingActiveSeconds)
    }

    // ---- failure, draft preservation and retry ----
    @Test fun failedCommitRollsBackKeepsDraftAndRetrySucceeds() = runBlocking {
        committer.commit(bundle("first", 100, legacyToken = 777L))
        assertEquals(DraftWriteResult.Saved(1L), committer.saveDraft("d1", "SESSION", "{\"n\":1}", null))
        // Same legacyToken violates the unique index AFTER the session row is the second writer -> write fails.
        val failing = bundle("second", 200, sets = 1, legacyToken = 777L)
        val result = committer.commit(failing, draftId = "d1")
        assertTrue("got $result", result is CommitResult.Failed)
        assertNull("no partial rows", dao.session("second"))
        assertEquals(0, dao.slices("second").size)
        assertEquals(0, dao.sets("second").size)
        val kept = dao.draft("d1")!!
        assertEquals(DraftState.FAILED.name, kept.state)
        assertEquals("WRITE_FAILED", kept.lastErrorCode)
        assertEquals("{\"n\":1}", kept.payloadJson)

        val retry = committer.commit(failing.copy(session = failing.session.copy(legacyToken = 778L)), draftId = "d1")
        assertTrue("got $retry", retry is CommitResult.Committed)
        assertNull("draft removed only after ack", dao.draft("d1"))
        assertEquals(200L, dao.session("second")!!.totalActiveSeconds)
    }

    @Test fun conflictKeepsDraftAsFailed() = runBlocking {
        committer.commit(bundle("k", 300))
        committer.saveDraft("d2", "SESSION", "{}", null)
        assertTrue(committer.commit(bundle("k", 999), draftId = "d2") is CommitResult.Conflict)
        assertEquals("CONFLICT", dao.draft("d2")!!.lastErrorCode)
    }

    @Test fun interruptedSavingDraftBecomesUnknownOutcomeAndRetryIsSafe() = runBlocking {
        committer.saveDraft("d3", "SESSION", "{}", null)
        dao.setDraftState("d3", DraftState.SAVING.name, null, 5L)
        assertEquals(1, committer.recoverInterruptedSaves())
        assertEquals("UNKNOWN_OUTCOME", dao.draft("d3")!!.lastErrorCode)
        val b = bundle("recovered", 200)
        assertTrue(committer.commit(b, "d3") is CommitResult.Committed)
        // Outcome was unknown: retry of an already-stored session must not duplicate.
        committer.saveDraft("d4", "SESSION", "{}", null)
        assertTrue(committer.commit(b, "d4") is CommitResult.AlreadyCommitted)
        assertNull(dao.draft("d4"))
        assertEquals(1, dao.slices("recovered").size)
    }

    @Test fun staleDraftRevisionIsRejected() = runBlocking {
        assertEquals(DraftWriteResult.Saved(1L), committer.saveDraft("r", "K", "a", null))
        assertEquals(DraftWriteResult.Saved(2L), committer.saveDraft("r", "K", "b", 1L))
        assertEquals(DraftWriteResult.StaleRevision(2L), committer.saveDraft("r", "K", "c", 1L))
        assertEquals(DraftWriteResult.StaleRevision(2L), committer.saveDraft("r", "K", "c", null))
        assertEquals("b", dao.draft("r")!!.payloadJson)
    }

    // ---- legacy data and append-only history ----
    @Test fun committingNeverTouchesLegacyTables() = runBlocking {
        db.workoutDao().upsertDay(DailyWorkoutEntity("2026-11-04", 3540, 200.0, 9L))
        db.workoutDao().upsertSet(WorkoutSetEntity("k", "2026-11-04", 1L, "bench", 1, "40", "10", true))
        committer.commit(bundle("new", 700))
        assertEquals(3540L, db.workoutDao().day("2026-11-04")!!.activeSeconds)
        assertEquals(1, db.workoutDao().observeSetsOnceForTest(1L))
    }

    @Test fun adjustmentsAreAppendOnlyAndOrdered() = runBlocking {
        committer.commit(bundle("adj", 300))
        dao.appendAdjustment(SessionAdjustmentEntity(sessionId = "adj", createdAtEpochMs = 1, field = "activeSeconds", oldValue = "300", newValue = "280", reason = "typo"))
        dao.appendAdjustment(SessionAdjustmentEntity(sessionId = "adj", createdAtEpochMs = 2, field = "activeSeconds", oldValue = "280", newValue = "290", reason = null))
        assertEquals(listOf("280", "290"), dao.adjustments("adj").map { it.newValue })
        assertEquals(300L, dao.session("adj")!!.totalActiveSeconds)       // original snapshot kept; history is separate
    }

    private suspend fun WorkoutDao.observeSetsOnceForTest(sessionId: Long) = observeSets(sessionId).first().size
}
