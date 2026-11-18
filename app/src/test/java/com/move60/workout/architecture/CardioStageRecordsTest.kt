package com.move60.workout.architecture

import com.move60.workout.domain.CardioMetric.INCLINE
import com.move60.workout.domain.CardioMetric.LEVEL
import org.junit.Assert.*
import org.junit.Test

/** docs/v71 step 4: the record-detail read model (read-only; originals are never touched). */
class CardioStageRecordsTest {
    private val min = 60
    private fun input(sid: String, i: Int, atMin: Int, eq: String, m: String, v: String, stage: Int = 2) =
        SessionCardioInputEntity(sid, i, stage, atMin * min, eq, m, v, null, 0L)
    private fun adj(id: Long, field: String, new: String?) = SessionAdjustmentEntity(id, "s", 0L, field, null, new, null)

    @Test fun noInputsMeansNoRecord_soTheScreenKeepsSayingNotRecorded() {
        assertNull(CardioStageRecords.build(2, 900, emptyList(), emptyList()))
        // inputs of another stage are ignored
        assertNull(CardioStageRecords.build(2, 900, listOf(input("s", 0, 0, "INDOOR_CYCLE", "level", "3", stage = 1)), emptyList()))
    }

    @Test fun lateFirstInputKeepsTheStartNotRecorded_andNothingIsBackFilled() {
        val r = CardioStageRecords.build(2, 15 * min, listOf(input("s", 0, 5, "INDOOR_CYCLE", "level", "5")), emptyList())!!
        assertFalse(r.rows[0].recorded); assertNull(r.rows[0].values); assertEquals(5 * min, r.rows[0].seconds)
        assertEquals(mapOf(LEVEL to "5"), r.rows[1].values); assertEquals(10 * min, r.rows[1].seconds)
    }

    @Test fun ellipticalInputIsOneIntervalWithLevelAndIncline() {
        val r = CardioStageRecords.build(2, 10 * min, listOf(
            input("s", 0, 0, "ELLIPTICAL", "level", "6"), input("s", 0, 0, "ELLIPTICAL", "incline", "3")), emptyList())!!
        assertEquals(1, r.rows.size); assertEquals(mapOf(LEVEL to "6", INCLINE to "3"), r.rows[0].values)
        assertFalse(r.multipleEquipment)
    }

    @Test fun latestCorrectionWins_originalRowsAreUnchanged() {
        val inputs = listOf(input("s", 0, 0, "INDOOR_CYCLE", "level", "3"))
        val r = CardioStageRecords.build(2, 5 * min, inputs, listOf(
            adj(1, "cardio:0:level", "4"), adj(2, "cardio:0:level", "5"), adj(3, "set:legPress:1:kg", "99"), adj(4, "cardio:9:level", "1")))!!
        assertEquals(mapOf(LEVEL to "5"), r.rows.single().values)
        assertEquals("3", inputs.single().valueText)
    }

    @Test fun twoMachinesAreFlagged() {
        val r = CardioStageRecords.build(2, 10 * min, listOf(
            input("s", 0, 0, "INDOOR_CYCLE", "level", "3"), input("s", 1, 5, "ELLIPTICAL", "level", "6")), emptyList())!!
        assertTrue(r.multipleEquipment); assertEquals(listOf("INDOOR_CYCLE", "ELLIPTICAL"), r.rows.map { it.equipmentKey })
    }

    @Test fun cardioFieldRoundTripAndRejection() {
        assertEquals("cardio:2:incline", CorrectionField.cardio(2, "incline"))
        assertEquals(2, CorrectionField.parseCardio("cardio:2:incline")!!.inputIndex)
        assertNull(CorrectionField.parseCardio("cardio:x:level")); assertNull(CorrectionField.parseCardio("cardio:1:speed"))
        assertNull(CorrectionField.parseCardio("set:legPress:1:kg")); assertNull(CorrectionField.parseCardio("cardio:1:level:x"))
    }

    @Test fun formatting() {
        assertEquals("05:00–12:30", RecordFormat.cardioRange(300, 750))
        assertEquals("레벨 6 · 경사 3", RecordFormat.cardioValues(mapOf(INCLINE to "3", LEVEL to "6")))
    }
}
