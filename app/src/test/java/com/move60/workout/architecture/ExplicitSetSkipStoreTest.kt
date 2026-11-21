package com.move60.workout.architecture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.SetRecordStore
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** CLOSE-DEC-01 / SKIP-01..06: the user must explicitly choose a set to skip. No completed record is rewritten. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExplicitSetSkipStoreTest {
    private lateinit var context: Context
    private lateinit var ledger: ExplicitSetSkipStore
    private lateinit var completed: SetRecordStore

    @Before fun prepare() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(ExplicitSetSkipStore.NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("move60_set_records_v1", Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences(PendingSessionOutbox.NAME, Context.MODE_PRIVATE).edit().clear().commit()
        ledger = ExplicitSetSkipStore(context)
        completed = SetRecordStore(context)
    }

    @Test fun explicitSkipIsIdempotentAndSurvivesReloadWithoutCreatingACompletedSet() {
        assertTrue(ledger.skippedForSession(101L).isEmpty())
        assertTrue(ledger.skip(101L, "legPress", 3, 1_000L))
        assertTrue(ledger.skip(101L, "legPress", 3, 2_000L))
        assertEquals(listOf("SKIPPED"), ledger.history(101L, "legPress", 3))
        assertEquals(listOf(ExplicitSetSkipStore.SkippedSet("legPress", 3, 1_000L)),
            ExplicitSetSkipStore(context).skippedForSession(101L))
        assertTrue(completed.completedSetsForSession(101L).isEmpty())
        assertTrue(ledger.skippedForSession(202L).isEmpty())
    }

    @Test fun undoRestoresUndecidedButKeepsAnAppendOnlyHistory() {
        assertTrue(ledger.skip(111L, "legPress", 3, 1_000L))
        assertTrue(ledger.undo(111L, "legPress", 3, 1_500L))
        assertTrue(ledger.skippedForSession(111L).isEmpty())
        assertEquals(listOf("SKIPPED", "UNDECIDED"), ledger.history(111L, "legPress", 3))
        assertFalse("undo again is not another decision", ledger.undo(111L, "legPress", 3))
        assertTrue(ledger.skip(111L, "legPress", 3, 2_000L))
        assertEquals(listOf("SKIPPED", "UNDECIDED", "SKIPPED"), ledger.history(111L, "legPress", 3))
        assertEquals(2_000L, ExplicitSetSkipStore(context).skippedForSession(111L).single().decidedAtEpochMs)
    }

    @Test fun completedSetCanNeverBeSkippedOrOverwrittenAndInvalidTokenIsRefused() {
        completed.write(3, "legPress", 2, SetRecordStore.Record("40", "12", "5", true),
            LocalDate.parse("2026-11-21"), 121L)
        assertFalse(ledger.skip(121L, "legPress", 2))
        assertFalse(ledger.skip(0L, "legPress", 3))
        assertFalse(ledger.skip(121L, "", 3))
        assertFalse(ledger.skip(121L, "legPress", 0))
        assertEquals("40", completed.completedSetsForSession(121L).single().kg)
        assertTrue(ledger.skippedForSession(121L).isEmpty())
    }

    @Test fun sessionOutboxAndRoomPreserveSkippedWithoutCountingItAsCompleted() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        try {
            val session = TimerSnapshot(
                recordDate = LocalDate.parse("2026-11-21"), day = 6, startedAtEpochMs = 5_001L,
                finished = true, accumulatedSeconds = 120, intervals = listOf(100L to 220L),
                completedSets = listOf(TimerSet("legPress", 1, "40", "12"), TimerSet("legPress", 2, "40", "12")),
                skippedSets = listOf(TimerSkippedSet("legPress", 3, 5_101L))
            )
            val outbox = PendingSessionOutbox(context)
            assertTrue(outbox.enqueue(session))
            assertEquals(session.skippedSets, PendingSessionOutbox(context).pending().single().skippedSets)
            val sync = TimerSessionSync(SessionCommitter(db) { 10_000L }) { "Asia/Seoul" }
            assertTrue(sync.drain(outbox).single() is SyncOutcome.Committed)
            val room = db.routiveDao().session(sync.sessionId(session))!!
            assertEquals(2, room.completedStrengthSets)
            assertEquals(3, db.routiveDao().sets(room.sessionId).size)
            assertEquals(listOf("COMPLETED", "COMPLETED", "SKIPPED"),
                db.routiveDao().sets(room.sessionId).sortedBy { it.setNumber }.map { it.state })
            assertTrue(sync.syncFinished(session) is SyncOutcome.AlreadyCommitted)
            assertEquals(3, db.routiveDao().sets(room.sessionId).size)
        } finally { db.close() }
    }

    @Test fun duplicateSkippedAndCompletedSetIsRejectedRatherThanLosingTheOriginal() {
        val snapshot = TimerSnapshot(
            LocalDate.parse("2026-11-21"), 6, 5_002L, true, 120, listOf(100L to 220L),
            completedSets = listOf(TimerSet("legPress", 1, "40", "12")),
            skippedSets = listOf(TimerSkippedSet("legPress", 1, 5_102L))
        )
        val db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        try {
            val sync = TimerSessionSync(SessionCommitter(db) { 10_000L }) { "Asia/Seoul" }
            val bundle = sync.bundleOrNull(snapshot)!!
            assertEquals(RejectReason.DUPLICATE_SET, SessionBundleValidator.validate(bundle)?.reason)
        } finally { db.close() }
    }

    @Test fun undecidedSetsAreNotInventedAsSkippedRows() {
        val snapshot = TimerSnapshot(LocalDate.parse("2026-11-21"), 6, 5_003L, true, 120,
            listOf(100L to 220L), completedSets = listOf(TimerSet("legPress", 1, "40", "12")))
        assertTrue(snapshot.skippedSets.isEmpty())
        val db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        try {
            val bundle = TimerSessionSync(SessionCommitter(db) { 10_000L }) { "Asia/Seoul" }.bundleOrNull(snapshot)!!
            assertEquals(1, bundle.sets.size)
            assertTrue(bundle.sets.none { it.state == SetState.SKIPPED.name })
        } finally { db.close() }
    }
}
