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
    private var nowMs = T.ms("2026-11-10", 12)
    private val dao get() = db.routiveDao()
    private val day = "2026-11-10"
    private fun start(h: Int = 10, m: Int = 0) = T.at(day, h, m)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkoutDatabase::class.java)
            .allowMainThreadQueries().build()
        committer = SessionCommitter(db) { nowMs }
    }
    @After fun close() { db.close() }

    private fun bundle(id: String, seconds: Long, sets: Int = 0, legacyToken: Long? = null, segments: List<SessionSegmentEntity> = emptyList()) =
        sessionBundle(id, listOf(T.run(start(), seconds)), sets = sets, legacyToken = legacyToken, segments = segments)
    private fun segment(id: String, order: Int, secs: Long = 30L) =
        SessionSegmentEntity(id, order, SegmentKind.CARDIO_WORK.name, null, secs, "cycle", """{"watts":"120"}""")

    // ---- validity & D-01 B (AC-12) ----
    @Test fun validityFollowsPolicyAndBelowThresholdIsDiscardedNotStored() = runBlocking {
        assertTrue(committer.commit(bundle("s59", 59)) is CommitResult.DiscardedBelowThreshold)
        assertTrue(committer.commit(bundle("s60", 60)) is CommitResult.Committed)
        assertTrue(committer.commit(bundle("s59set", 59, sets = 1)) is CommitResult.Committed)
        assertNull("no permanent summary for a below-threshold session", dao.session("s59"))
        assertEquals(0, dao.intervals("s59").size)
        assertEquals(true, dao.session("s60")!!.validSession)
        assertEquals(true, dao.session("s59set")!!.validSession)
    }

    @Test fun ac12_finalizingA59sSessionDiscardsEverythingButCancelKeepsTheDraft() = runBlocking {
        val short = bundle("short", 59, segments = listOf(segment("short", 0), segment("short", 1)))
        committer.saveDraft("end-confirmed", "SESSION", "{\"a\":1}", null)
        committer.saveDraft("end-cancelled", "SESSION", "{\"a\":2}", null)

        // Cancel: the app never calls commit -> the temporary data stays.
        assertEquals("{\"a\":2}", dao.draft("end-cancelled")!!.payloadJson)

        // Confirm: raw segments AND the permanent summary are discarded, the Draft is consumed.
        val result = committer.commit(short, draftId = "end-confirmed")
        assertTrue("got $result", result is CommitResult.DiscardedBelowThreshold)
        assertNull(dao.session("short")); assertEquals(0, dao.segments("short").size); assertEquals(0, dao.slices("short").size)
        assertNull(dao.draft("end-confirmed"))
        assertEquals("cancel path untouched", "{\"a\":2}", dao.draft("end-cancelled")!!.payloadJson)
        assertEquals(0, dao.sessionSecondsOn(day))        // nothing shows on the ordinary calendar either
    }

    @Test fun callerCannotAssertValidity() = runBlocking {
        val b = bundle("liar", 10)
        val result = committer.commit(b.copy(session = b.session.copy(validSession = true)))
        assertTrue(result is CommitResult.DiscardedBelowThreshold)
        assertNull(dao.session("liar"))
    }

    @Test fun rawSegmentsAreStoredForValidSessions() = runBlocking {
        committer.commit(bundle("long", 120, segments = listOf(segment("long", 0), segment("long", 1))))
        assertEquals(2, dao.segments("long").size)
    }

    // ---- derived day slices (P-20) ----
    @Test fun midnightCrossingSessionIsSlicedFromRealIntervals() = runBlocking {
        val b = sessionBundle("night", listOf(T.run(T.at("2026-11-10", 23, 50), 1200)))   // 23:50 -> 00:10 KST
        committer.commit(b)
        assertEquals(listOf("2026-11-10" to 600L, "2026-11-11" to 600L), dao.slices("night").map { it.date to it.activeSecondsOnDate })
    }

    @Test fun pausesAreNotCounted() = runBlocking {
        val b = sessionBundle("paused", listOf(T.run(start(), 300), T.run(start(10, 30), 300)))   // 25-minute pause in between
        committer.commit(b)
        assertEquals(600L, dao.session("paused")!!.totalActiveSeconds)
        assertEquals(2, dao.intervals("paused").size)
    }

    // ---- idempotency / conflict ----
    @Test fun identicalRetryIsIdempotent() = runBlocking {
        val b = bundle("dup", 300, sets = 2)
        assertTrue(committer.commit(b) is CommitResult.Committed)
        val first = dao.session("dup")!!
        assertTrue(committer.commit(b) is CommitResult.AlreadyCommitted)
        assertTrue(committer.commit(b) is CommitResult.AlreadyCommitted)
        assertEquals(first, dao.session("dup"))
        assertEquals(1, dao.slices("dup").size); assertEquals(2, dao.sets("dup").size); assertEquals(1, dao.intervals("dup").size)
    }

    @Test fun sameIdDifferentContentIsConflictAndStoredDataUnchanged() = runBlocking {
        committer.commit(bundle("x", 300))
        assertTrue(committer.commit(bundle("x", 301)) is CommitResult.Conflict)
        assertEquals(300L, dao.session("x")!!.totalActiveSeconds)
        assertEquals(300L, dao.slices("x").single().activeSecondsOnDate)
    }

    // ---- validation rejects, nothing stored ----
    @Test fun invalidBundlesAreRejectedAndNothingIsStored() = runBlocking {
        val base = bundle("bad", 100, sets = 1)
        val iv = base.intervals.single()
        val cases = mapOf(
            RejectReason.INTERVAL_SUM_MISMATCH to base.copy(session = base.session.copy(totalActiveSeconds = 99)),
            RejectReason.INVALID_INTERVAL to base.copy(intervals = listOf(iv.copy(endEpochSec = iv.startEpochSec))),
            RejectReason.INTERVAL_OVERLAP to base.copy(
                intervals = listOf(iv, iv.copy(orderIndex = 1, startEpochSec = iv.startEpochSec + 10, endEpochSec = iv.startEpochSec + 110)),
                session = base.session.copy(totalActiveSeconds = 200)),
            RejectReason.DUPLICATE_INTERVAL to base.copy(intervals = listOf(iv, iv), session = base.session.copy(totalActiveSeconds = 200)),
            RejectReason.COMPLETED_SET_COUNT_MISMATCH to base.copy(session = base.session.copy(completedStrengthSets = 2)),
            RejectReason.INVALID_SET to base.copy(sets = listOf(SessionSetEntity("bad", "bench", 1, SetState.COMPLETED.name, "40", "0", 5L))),
            RejectReason.DUPLICATE_SET to base.copy(sets = base.sets + base.sets),
            RejectReason.INVALID_SEGMENT to base.copy(segments = listOf(segment("bad", 0, secs = 0))),
            RejectReason.DUPLICATE_SEGMENT to base.copy(segments = listOf(segment("bad", 0), segment("bad", 0))),
            RejectReason.NEGATIVE_VALUE to base.copy(session = base.session.copy(totalActiveSeconds = -1)),
            RejectReason.BLANK_SESSION_ID to base.copy(session = base.session.copy(sessionId = " ")),
            RejectReason.INVALID_ZONE to base.copy(session = base.session.copy(zoneId = "Mars/Base")),
            RejectReason.CHILD_SESSION_ID_MISMATCH to base.copy(sets = listOf(SessionSetEntity("other", "bench", 1, SetState.COMPLETED.name, "40", "10", 5L)))
        )
        for ((reason, bad) in cases) {
            val r = committer.commit(bad)
            assertTrue("$reason -> $r", r is CommitResult.Rejected && r.reason == reason)
        }
        assertNull(dao.session("bad"))
    }

    @Test fun skippedSetsAreStoredButNeverCountAsCompleted() = runBlocking {
        val b = bundle("skip", 90).let { it.copy(sets = listOf(SessionSetEntity("skip", "squat", 1, SetState.SKIPPED.name, "", "", null))) }
        assertTrue(committer.commit(b) is CommitResult.Committed)
        assertEquals(0, dao.session("skip")!!.completedStrengthSets)
        assertEquals(SetState.SKIPPED.name, dao.sets("skip").single().state)
    }

    // ---- failure, draft preservation and retry (E-03) ----
    @Test fun failedCommitRollsBackKeepsDraftAndRetrySucceeds() = runBlocking {
        committer.commit(bundle("first", 100, legacyToken = 777L))
        assertEquals(DraftWriteResult.Saved(1L), committer.saveDraft("d1", "SESSION", "{\"n\":1}", null))
        val failing = sessionBundle("second", listOf(T.run(T.at(day, 14), 200)), sets = 1, legacyToken = 777L)
        val result = committer.commit(failing, draftId = "d1")
        assertTrue("got $result", result is CommitResult.Failed)
        assertNull("no partial rows", dao.session("second"))
        assertEquals(0, dao.intervals("second").size); assertEquals(0, dao.slices("second").size); assertEquals(0, dao.sets("second").size)
        val kept = dao.draft("d1")!!
        assertEquals(DraftState.FAILED.name, kept.state); assertEquals("WRITE_FAILED", kept.lastErrorCode); assertEquals("{\"n\":1}", kept.payloadJson)

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
        assertEquals(1, db.workoutDao().observeSets(1L).first().size)
    }

    @Test fun adjustmentsAreAppendOnlyAndOrdered() = runBlocking {
        committer.commit(bundle("adj", 300))
        dao.appendAdjustment(SessionAdjustmentEntity(sessionId = "adj", createdAtEpochMs = 1, field = "activeSeconds", oldValue = "300", newValue = "280", reason = "typo"))
        dao.appendAdjustment(SessionAdjustmentEntity(sessionId = "adj", createdAtEpochMs = 2, field = "activeSeconds", oldValue = "280", newValue = "290", reason = null))
        assertEquals(listOf("280", "290"), dao.adjustments("adj").map { it.newValue })
        assertEquals(300L, dao.session("adj")!!.totalActiveSeconds)
    }
}
