package com.move60.workout.architecture

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.domain.CardioMetric.INCLINE
import com.move60.workout.domain.CardioMetric.LEVEL
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** docs/v71 step 5: cardio value corrections are append-only history rows; the saved inputs never change. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class CardioCorrectionServiceTest {
    private lateinit var db: WorkoutDatabase
    private var nowMs = T.ms("2026-11-16", 12)
    private val dao get() = db.routiveDao()
    private val service get() = SessionCorrectionService(db) { nowMs }

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkoutDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }

    private fun input(id: String, i: Int, at: Int, m: String, v: String) =
        SessionCardioInputEntity(id, i, 0, at, "ELLIPTICAL", m, v, null, 0L)

    private suspend fun store(id: String = "c1") {
        val base = sessionBundle(id, listOf(T.run(T.at("2026-11-16", 12), 720)), sets = 1)
        val r = SessionCommitter(db) { nowMs }.commit(base.copy(cardioInputs = listOf(
            input(id, 0, 0, "level", "5"), input(id, 0, 0, "incline", "2"), input(id, 1, 300, "level", "6"))))
        assertTrue("stored: $r", r is CommitResult.Committed)
    }

    @Test fun correctionAppendsOldNewAndKeepsTheOriginalInput() = runBlocking {
        store()
        val before = dao.cardioInputs("c1")
        assertEquals(CardioCorrectionResult.Saved(1), service.applyCardio("c1", listOf(CardioValueEdit(1, LEVEL, "7"))))
        val a = dao.adjustments("c1").single()
        assertEquals(listOf("cardio:1:level", "6", "7", nowMs), listOf(a.field, a.oldValue, a.newValue, a.createdAtEpochMs))
        assertEquals("originals untouched", before, dao.cardioInputs("c1"))
    }

    @Test fun secondCorrectionChainsFromTheCurrentValueAndTheLatestWins() = runBlocking {
        store()
        service.applyCardio("c1", listOf(CardioValueEdit(1, LEVEL, "7")))
        nowMs += 1000
        service.applyCardio("c1", listOf(CardioValueEdit(1, LEVEL, "8")))
        assertEquals(listOf("6" to "7", "7" to "8"), dao.adjustments("c1").map { it.oldValue to it.newValue })
        val record = CardioStageRecords.build(0, 720, dao.cardioInputs("c1"), dao.adjustments("c1"))!!
        assertEquals(mapOf(LEVEL to "8"), record.rows.last { it.recorded }.values)
    }

    @Test fun sameValueWritesNothing_evenWrittenWithADecimalPoint() = runBlocking {
        store()
        assertEquals(CardioCorrectionResult.Unchanged, service.applyCardio("c1", listOf(CardioValueEdit(0, LEVEL, "5.0"))))
        // Decimal comma and decimal point represent the same real value, not a new correction.
        assertEquals(CardioCorrectionResult.Unchanged, service.applyCardio("c1", listOf(CardioValueEdit(0, LEVEL, "5,0"))))
        assertTrue(dao.adjustments("c1").isEmpty())
    }

    @Test fun invalidOrUnknownWritesNothingAtAll() = runBlocking {
        store()
        // second edit is invalid -> the valid first one must not be saved either
        assertEquals(CardioCorrectionResult.InvalidValue(0, "incline"),
            service.applyCardio("c1", listOf(CardioValueEdit(0, LEVEL, "9"), CardioValueEdit(0, INCLINE, "-1"))))
        assertEquals(CardioCorrectionResult.InvalidValue(0, "level"), service.applyCardio("c1", listOf(CardioValueEdit(0, LEVEL, ""))))
        assertEquals(CardioCorrectionResult.InvalidValue(0, "level"), service.applyCardio("c1", listOf(CardioValueEdit(0, LEVEL, "12a"))))
        assertEquals(CardioCorrectionResult.InvalidValue(0, "level"), service.applyCardio("c1", listOf(CardioValueEdit(0, LEVEL, "NaN"))))
        assertEquals(CardioCorrectionResult.InvalidValue(0, "level"),
            service.applyCardio("c1", listOf(CardioValueEdit(0, LEVEL, "3"), CardioValueEdit(0, LEVEL, "4"))))
        assertEquals(CardioCorrectionResult.InputNotFound(5, "level"), service.applyCardio("c1", listOf(CardioValueEdit(5, LEVEL, "3"))))
        assertEquals(CardioCorrectionResult.InputNotFound(1, "incline"), service.applyCardio("c1", listOf(CardioValueEdit(1, INCLINE, "3"))))
        assertEquals(CardioCorrectionResult.SessionNotFound, service.applyCardio("nope", listOf(CardioValueEdit(0, LEVEL, "3"))))
        assertTrue(dao.adjustments("c1").isEmpty())
    }

    @Test fun consoleModelUnknownAcceptsUserReportedValueWithoutAguessedMaxAndAppendsCorrections() = runBlocking {
        store()
        val original = dao.cardioInputs("c1")
        assertEquals(CardioCorrectionResult.Saved(1),
            service.applyCardio("c1", listOf(CardioValueEdit(0, LEVEL, "26"))))
        nowMs += 1000
        assertEquals(CardioCorrectionResult.Saved(1),
            service.applyCardio("c1", listOf(CardioValueEdit(0, LEVEL, "1,5"))))
        assertEquals(listOf("5" to "26", "26" to "1,5"),
            dao.adjustments("c1").map { it.oldValue to it.newValue })
        assertEquals("original event must remain unmodified", original, dao.cardioInputs("c1"))
        assertEquals("1,5", CardioStageRecords.build(0, 720,
            dao.cardioInputs("c1"), dao.adjustments("c1"))!!.rows.first { it.recorded }.values?.get(LEVEL))
    }

    @Test fun userConsoleCorrectionRejectsMalformedAndNonFiniteInput() {
        for (bad in listOf("", "12a", "-1", "5kg", "NaN", "Infinity", "1..5")) {
            assertNull("must reject raw invalid text $bad", CorrectionRules.cardio(bad))
        }
        assertEquals("0", CorrectionRules.cardio("0"))
        assertEquals("1,5", CorrectionRules.cardio("1,5"))
    }

    @Test fun cardioRowsDoNotAppearInTheStrengthSetHistoryOrOverlay() = runBlocking {
        store()
        service.applyCardio("c1", listOf(CardioValueEdit(0, LEVEL, "9")))
        assertTrue(service.history("c1").isEmpty()); assertTrue(service.overlay("c1").isEmpty())
    }
}
