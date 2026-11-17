package com.move60.workout.architecture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** docs/v71 step 2: saved cardio values are stored with the session in one transaction, idempotently, and never invented. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardioInputsStorageTest {
    private lateinit var context: Context
    private lateinit var db: WorkoutDatabase
    private lateinit var committer: SessionCommitter
    private val dao get() = db.routiveDao()
    private val day = "2026-11-10"

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        committer = SessionCommitter(db) { T.ms(day, 12) }
    }
    @After fun close() { db.close() }

    private fun bundle(id: String, seconds: Long = 900, cardio: List<SessionCardioInputEntity>) =
        sessionBundle(id, listOf(T.run(T.at(day, 10), seconds))).let { it.copy(cardioInputs = cardio) }
    private fun input(id: String, index: Int, at: Int, metric: String, value: String, eq: String = "ELLIPTICAL") =
        SessionCardioInputEntity(id, index, 1, at, eq, metric, value, null, 1_000L + index)

    @Test fun savedInputsAreStoredWithTheSessionAndReadBackExactly() = runBlocking {
        val rows = listOf(input("a", 0, 0, "level", "3"), input("a", 0, 0, "incline", "2"), input("a", 1, 300, "level", "5.5"))
        assertTrue(committer.commit(bundle("a", cardio = rows)) is CommitResult.Committed)
        assertEquals(rows.sortedWith(compareBy({ it.inputIndex }, { it.metricKey })), dao.cardioInputs("a"))
    }
    @Test fun aSessionWithoutInputsStoresNoRowsAndIsNotInvented() = runBlocking {
        assertTrue(committer.commit(bundle("none", cardio = emptyList())) is CommitResult.Committed)
        assertEquals(0, dao.cardioInputs("none").size)
    }
    @Test fun anInvalidInputRejectsTheWholeBundleAndStoresNothing() = runBlocking {
        for ((name, bad) in listOf(
            "unknown metric" to input("x", 0, 0, "watts", "3"), "not a number" to input("x", 0, 0, "level", "abc"),
            "negative" to input("x", 0, 0, "level", "-1"), "after the session end" to input("x", 0, 901, "level", "3"),
            "negative index" to input("x", -1, 0, "level", "3"), "blank equipment" to input("x", 0, 0, "level", "3", eq = " "))
        ) {
            val r = committer.commit(bundle("x", cardio = listOf(bad)))
            assertTrue("$name -> $r", r is CommitResult.Rejected && r.reason == RejectReason.INVALID_CARDIO_INPUT)
            assertNull("$name: no session row", dao.session("x")); assertEquals(0, dao.cardioInputs("x").size)
        }
    }
    @Test fun aDuplicateKeyAndAForeignSessionIdAreRejected() = runBlocking {
        val dup = committer.commit(bundle("d", cardio = listOf(input("d", 0, 0, "level", "3"), input("d", 0, 10, "level", "4"))))
        assertTrue(dup is CommitResult.Rejected && dup.reason == RejectReason.DUPLICATE_CARDIO_INPUT)
        val foreign = committer.commit(bundle("f", cardio = listOf(input("other", 0, 0, "level", "3"))))
        assertTrue(foreign is CommitResult.Rejected && foreign.reason == RejectReason.CHILD_SESSION_ID_MISMATCH)
    }
    @Test fun aBelowThresholdSessionStoresNeitherTheSessionNorItsInputs() = runBlocking {
        val r = committer.commit(bundle("short", seconds = 59, cardio = listOf(input("short", 0, 0, "level", "3"))))
        assertTrue("got $r", r is CommitResult.DiscardedBelowThreshold)
        assertNull(dao.session("short")); assertEquals(0, dao.cardioInputs("short").size)
    }
    @Test fun anIdenticalRetryIsANoOpAndADifferentValueIsAConflictThatChangesNothing() = runBlocking {
        val rows = listOf(input("r", 0, 0, "level", "3"))
        assertTrue(committer.commit(bundle("r", cardio = rows)) is CommitResult.Committed)
        assertTrue(committer.commit(bundle("r", cardio = rows)) is CommitResult.AlreadyCommitted)
        assertTrue(committer.commit(bundle("r", cardio = listOf(input("r", 0, 0, "level", "9")))) is CommitResult.Conflict)
        assertEquals(rows, dao.cardioInputs("r"))
    }
    @Test fun replayingAnOlderSessionWithoutInputsNeverRemovesOrAddsRows() = runBlocking {
        val rows = listOf(input("o", 0, 0, "level", "3"))
        assertTrue(committer.commit(bundle("o", cardio = rows)) is CommitResult.Committed)
        assertTrue(committer.commit(bundle("o", cardio = emptyList())) is CommitResult.AlreadyCommitted)
        assertEquals(rows, dao.cardioInputs("o"))
        assertTrue(committer.commit(bundle("older", cardio = emptyList())) is CommitResult.Committed)
        assertTrue(committer.commit(bundle("older", cardio = listOf(input("older", 0, 0, "level", "3")))) is CommitResult.AlreadyCommitted)
        assertEquals("stored rows are never changed by a replay", 0, dao.cardioInputs("older").size)
    }

    // ---- outbox: the durable copy keeps the inputs, and an old entry (no cardio key) still reads ----
    private fun snapshot(cardio: List<TimerCardioInput>) = TimerSnapshot(
        LocalDate.parse(day), 1, 1_700_000_000_000L, true, 900, listOf(1_700_000_000L to 1_700_000_900L), cardioInputs = cardio
    )

    @Test fun theOutboxKeepsCardioInputsAndTheyCommitThroughTheNormalSyncPath() = runBlocking {
        val outbox = PendingSessionOutbox(context)
        val cardio = listOf(TimerCardioInput(0, 1, 0, "ELLIPTICAL", "level", "3", null, 5L), TimerCardioInput(0, 1, 0, "ELLIPTICAL", "incline", "2", null, 5L))
        val snap = snapshot(cardio)
        assertTrue(outbox.enqueue(snap))
        assertEquals(cardio, outbox.pending().single().cardioInputs)
        val sync = TimerSessionSync(committer) { "Asia/Seoul" }
        assertTrue(sync.drain(outbox).single() is SyncOutcome.Committed)
        assertEquals(2, dao.cardioInputs(sync.sessionId(snap)).size)
    }
    @Test fun anOutboxEntryWrittenBeforeCardioInputsExistedStillReadsWithNoInputs() {
        val outbox = PendingSessionOutbox(context)
        val snap = snapshot(emptyList())
        assertTrue(outbox.enqueue(snap))
        val prefs = context.getSharedPreferences(PendingSessionOutbox.NAME, Context.MODE_PRIVATE)
        val id = outbox.sessionId(snap)
        val legacy = JSONObject(prefs.getString(id, null)!!).apply { remove("cardio"); remove("cardioV") }
        prefs.edit().putString(id, legacy.toString()).commit()
        val read = outbox.pending().single()
        assertTrue(read.cardioInputs.isEmpty()); assertEquals(snap.accumulatedSeconds, read.accumulatedSeconds)
    }
    @Test fun anUnreadableInputDropsOnlyThatInputAndKeepsTheSession() {
        val outbox = PendingSessionOutbox(context)
        val snap = snapshot(listOf(TimerCardioInput(0, 1, 0, "ELLIPTICAL", "level", "3", null, 5L)))
        assertTrue(outbox.enqueue(snap))
        val prefs = context.getSharedPreferences(PendingSessionOutbox.NAME, Context.MODE_PRIVATE)
        val id = outbox.sessionId(snap)
        val json = JSONObject(prefs.getString(id, null)!!)
        json.getJSONArray("cardio").put(JSONObject().put("i", "x"))
        prefs.edit().putString(id, json.toString()).commit()
        val read = outbox.pending().single()
        assertEquals(1, read.cardioInputs.size)
    }
}
