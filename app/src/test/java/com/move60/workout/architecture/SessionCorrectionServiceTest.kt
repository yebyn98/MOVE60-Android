package com.move60.workout.architecture

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** Policy 6 (P-22/P-23): corrections are append-only history rows; the stored originals and challenge results never change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionCorrectionServiceTest {
    private lateinit var db: WorkoutDatabase
    private var nowMs = T.ms("2026-11-16", 12)
    private val dao get() = db.routiveDao()
    private val corrections get() = SessionCorrectionService(db) { nowMs }

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkoutDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }

    private fun set(id: String, exercise: String, n: Int, kg: String, reps: String, state: SetState = SetState.COMPLETED) =
        SessionSetEntity(id, exercise, n, state.name, kg, reps, null)

    /** A stored 12-minute session at 12:00 with the given sets (completed count must match the snapshot). */
    private suspend fun store(id: String, sets: List<SessionSetEntity>) {
        val base = sessionBundle(id, listOf(T.run(T.at("2026-11-16", 12), 720)), sets = sets.count { it.state == SetState.COMPLETED.name })
        val r = SessionCommitter(db) { nowMs }.commit(base.copy(sets = sets))
        assertTrue("$id must be stored: $r", r is CommitResult.Committed)
    }

    private val two = { id: String -> listOf(set(id, "bench", 1, "40", "10"), set(id, "bench", 2, "40", "10")) }

    @Test fun correctionsAreAppendedWithOldNewAndTimeAndShownOnTopOfTheOriginal() = runBlocking {
        store("s1", two("s1"))
        val r = corrections.apply("s1", listOf(SetCorrection("bench", 1, "42.5", "12")))
        assertEquals(CorrectionResult.Saved(2), r)

        val history = corrections.history("s1")
        assertEquals(
            listOf(
                listOf("bench", 1, "weightKg", "40", "42.5", nowMs), listOf("bench", 1, "repetitions", "10", "12", nowMs)
            ),
            history.map { listOf(it.exerciseId, it.setNumber, it.attribute, it.oldValue, it.newValue, it.createdAtEpochMs) }
        )
        assertEquals(SetValues("42.5", "12"), corrections.overlay("s1")[("bench" to 1)])
        assertNull("an uncorrected set has no overlay", corrections.overlay("s1")[("bench" to 2)])
    }

    /** The core guarantee: nothing the correction does can change the stored record. */
    @Test fun storedOriginalsNeverChange() = runBlocking {
        store("s1", two("s1"))
        val session = dao.session("s1"); val sets = dao.sets("s1"); val intervals = dao.intervals("s1"); val slices = dao.slices("s1")
        corrections.apply("s1", listOf(SetCorrection("bench", 1, "50", "8"), SetCorrection("bench", 2, "45", null)))
        corrections.apply("s1", listOf(SetCorrection("bench", 1, "55", null)))
        assertEquals(session, dao.session("s1")); assertEquals(sets, dao.sets("s1"))
        assertEquals(intervals, dao.intervals("s1")); assertEquals(slices, dao.slices("s1"))
        assertEquals("40", dao.sets("s1").first { it.setNumber == 1 }.weightKg)
        assertEquals(4, dao.adjustments("s1").size)
    }

    @Test fun aLaterCorrectionRecordsTheCurrentValueAsItsOldValueAndKeepsTheChain() = runBlocking {
        store("s1", two("s1"))
        corrections.apply("s1", listOf(SetCorrection("bench", 1, "42.5", null)))
        nowMs += 60_000
        corrections.apply("s1", listOf(SetCorrection("bench", 1, "45", null)))
        val h = corrections.history("s1")
        assertEquals(listOf("40" to "42.5", "42.5" to "45"), h.map { it.oldValue to it.newValue })
        assertTrue("saved later, so ordered later", h[1].createdAtEpochMs > h[0].createdAtEpochMs)
        assertEquals("45", corrections.overlay("s1")[("bench" to 1)]!!.kg)
        assertEquals("the original is still the stored value", "40", dao.sets("s1").first { it.setNumber == 1 }.weightKg)
    }

    /** All or nothing: one bad value, a missing set or a duplicate writes nothing at all, not even the valid edits. */
    @Test fun invalidRequestsWriteNothing() = runBlocking {
        store("s1", two("s1"))
        val bad = corrections.apply("s1", listOf(SetCorrection("bench", 1, "50", null), SetCorrection("bench", 2, null, "0")))
        assertEquals(CorrectionResult.InvalidValue("bench", 2, "repetitions"), bad)
        assertEquals(CorrectionResult.SetNotFound("legPress", 1), corrections.apply("s1", listOf(SetCorrection("bench", 1, "50", null), SetCorrection("legPress", 1, "10", null))))
        assertEquals(CorrectionResult.InvalidValue("bench", 1, "duplicate"),
            corrections.apply("s1", listOf(SetCorrection("bench", 1, "50", null), SetCorrection("bench", 1, "51", null))))
        assertEquals(CorrectionResult.SessionNotFound, corrections.apply("nope", listOf(SetCorrection("bench", 1, "50", null))))
        assertEquals("nothing was written by any of the failed requests", 0, dao.adjustments("s1").size)
    }

    @Test fun onlyRealisticNumbersAreAccepted() = runBlocking {
        store("s1", two("s1"))
        for (w in listOf("-1", "abc", "501", "", " ", "1e2", "NaN", "Infinity", "4 0", "40,5"))
            assertTrue("weight '$w' must be rejected", corrections.apply("s1", listOf(SetCorrection("bench", 1, w, null))) is CorrectionResult.InvalidValue)
        for (r in listOf("0", "101", "1.5", "", "-3", "abc"))
            assertTrue("reps '$r' must be rejected", corrections.apply("s1", listOf(SetCorrection("bench", 1, null, r))) is CorrectionResult.InvalidValue)
        assertEquals(0, dao.adjustments("s1").size)
        assertTrue(corrections.apply("s1", listOf(SetCorrection("bench", 1, "0", "100"))) is CorrectionResult.Saved)
        assertTrue(corrections.apply("s1", listOf(SetCorrection("bench", 2, "500", "1"))) is CorrectionResult.Saved)
    }

    /** Nothing is created: a skipped set and a never recorded set cannot be corrected into existence. */
    @Test fun skippedOrMissingSetsCannotBeCorrected() = runBlocking {
        store("s1", listOf(set("s1", "bench", 1, "40", "10"), set("s1", "bench", 2, "", "1", SetState.SKIPPED)))
        assertEquals(CorrectionResult.SetNotFound("bench", 2), corrections.apply("s1", listOf(SetCorrection("bench", 2, "40", "10"))))
        assertEquals(CorrectionResult.SetNotFound("bench", 3), corrections.apply("s1", listOf(SetCorrection("bench", 3, "40", "10"))))
        assertEquals(2, dao.sets("s1").size)
        assertEquals(0, dao.adjustments("s1").size)
    }

    @Test fun valuesThatAlreadyMatchWriteNothing() = runBlocking {
        store("s1", two("s1"))
        assertEquals(CorrectionResult.Unchanged, corrections.apply("s1", listOf(SetCorrection("bench", 1, "40", "10"))))
        assertEquals(CorrectionResult.Unchanged, corrections.apply("s1", listOf(SetCorrection("bench", 1, " 40 ", " 10 "))))
        assertEquals(CorrectionResult.Unchanged, corrections.apply("s1", emptyList()))
        corrections.apply("s1", listOf(SetCorrection("bench", 1, "45", null)))
        assertEquals("repeating the same correction is a no-op", CorrectionResult.Unchanged, corrections.apply("s1", listOf(SetCorrection("bench", 1, "45", null))))
        assertEquals(1, dao.adjustments("s1").size)
    }

    /** AC 3: a correction cannot change active time or challenge credit, so a closed challenge's final snapshot is untouched. */
    @Test fun correctionsLeaveChallengeResultsAndTheClosedSnapshotUntouched() = runBlocking {
        val challenges = ChallengeService(db) { nowMs }
        assertTrue(challenges.create(challengeOf(start = "2026-11-16", end = "2026-11-16", target = 1)) is ChallengeResult.Created)
        store("s1", two("s1"))
        nowMs = T.ms("2026-11-17", 0, 0, 1)
        assertEquals(1, challenges.settleDue())
        val closed = dao.challenge("c1")!!
        assertEquals("COMPLETE", closed.status); assertNotNull(closed.endSnapshotJson)
        val credit = dao.credit("c1", "2026-11-16")

        assertTrue(corrections.apply("s1", listOf(SetCorrection("bench", 1, "99", "20"))) is CorrectionResult.Saved)
        assertTrue(corrections.apply("s1", listOf(SetCorrection("bench", 1, "98", null))) is CorrectionResult.Saved)

        assertEquals("the ended challenge, with its frozen snapshot, is unchanged", closed, dao.challenge("c1"))
        assertEquals(credit, dao.credit("c1", "2026-11-16"))
        assertEquals(720L, dao.session("s1")!!.totalActiveSeconds)
    }

    @Test fun correctionFieldsRoundTripEvenWhenTheExerciseIdContainsColons() {
        val field = CorrectionField.set("machine:cycle", 3, CorrectionField.REPS)
        assertEquals(CorrectionField.Parsed("machine:cycle", 3, "repetitions"), CorrectionField.parse(field))
        assertEquals(CorrectionField.Parsed("bench", 1, "weightKg"), CorrectionField.parse(CorrectionField.set("bench", 1, CorrectionField.WEIGHT)))
        for (junk in listOf("", "garbage", "set:", "set:bench:1", "set:bench:x:weightKg", "set:bench:1:rpe", "set::1:weightKg"))
            assertNull("'$junk' was not written by this code", CorrectionField.parse(junk))
    }
}
