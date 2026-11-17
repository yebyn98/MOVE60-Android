package com.move60.workout.domain

import com.move60.workout.domain.CardioMetric.INCLINE
import com.move60.workout.domain.CardioMetric.LEVEL
import org.junit.Assert.*
import org.junit.Test

/** docs/v71, policy P-08 cases A-E and AC-P08-01/02/04/05, plus the 2026-11-17 PO input decisions. */
class CardioIntervalsTest {
    private val min = 60
    private fun input(i: Int, atMin: Int, vararg v: Pair<CardioMetric, String>) = CardioInput(i, atMin * min, mapOf(*v))
    private val fifteen = CardioWindow(0, 15 * min)

    @Test fun caseA_threeInputsFromTheStartMakeThreeFiveMinuteIntervals() {
        val s = CardioIntervals.compute(fifteen, listOf(input(0, 0, LEVEL to "3"), input(1, 5, LEVEL to "5"), input(2, 10, LEVEL to "7")))
        assertEquals(listOf("3", "5", "7"), s.intervals.map { it.values!![LEVEL] })
        assertTrue(s.intervals.all { it.seconds == 5 * min && it.recorded })
        assertEquals(15 * min, s.recordedSeconds); assertEquals(0, s.unrecordedSeconds)
        assertEquals(listOf(0, 1, 2), s.intervals.map { it.inputIndex })
    }
    @Test fun caseB_theFirstInputIsLateSoTheFirstFiveMinutesStayNotRecorded() {
        val s = CardioIntervals.compute(fifteen, listOf(input(0, 5, LEVEL to "5"), input(1, 10, LEVEL to "7")))
        val first = s.intervals.first()
        assertFalse(first.recorded); assertNull(first.values); assertNull(first.inputIndex); assertEquals(5 * min, first.seconds)
        assertEquals(10 * min, s.recordedSeconds); assertEquals(5 * min, s.unrecordedSeconds)
        assertNull("never back-filled with the later value", s.intervals.first().values)
    }
    @Test fun caseC_aCancelledInputCreatesNoEventSoNothingChanges() {
        val before = CardioIntervals.compute(fifteen, listOf(input(0, 0, LEVEL to "3")))
        assertEquals(1, before.intervals.size); assertEquals(15 * min, before.recordedSeconds)
    }
    @Test fun noInputAtAllIsOneNotRecordedInterval() {
        val s = CardioIntervals.compute(fifteen, emptyList())
        assertEquals(1, s.intervals.size); assertFalse(s.intervals[0].recorded); assertEquals(15 * min, s.unrecordedSeconds)
    }
    @Test fun caseD_activeSecondsAlreadyExcludeThePause_soTotalsEqualTheActiveWindow() {
        // 5 + pause(not counted) + 5 + 5 of active time: the clock the inputs carry never advances during the pause.
        val s = CardioIntervals.compute(fifteen, listOf(input(0, 0, LEVEL to "3"), input(1, 5, LEVEL to "5")))
        assertEquals(fifteen.seconds, s.totalSeconds)
    }
    @Test fun caseE_aCorrectionChangesWhatIsReadButNeverTheOriginalInput() {
        val originals = listOf(input(0, 0, LEVEL to "3"), input(1, 5, LEVEL to "5"))
        val corrected = CardioIntervals.effective(originals, listOf(CardioCorrection(1, LEVEL, "6", 10L)))
        assertEquals("6", corrected[1].values[LEVEL]); assertEquals("3", corrected[0].values[LEVEL])
        assertEquals("the original stays", "5", originals[1].values[LEVEL])
        assertEquals("the time cannot be corrected", originals[1].atActiveSeconds, corrected[1].atActiveSeconds)
    }
    @Test fun theLatestCorrectionWinsPerInputAndMetric() {
        val originals = listOf(input(0, 0, LEVEL to "3", INCLINE to "2"))
        val fixed = CardioIntervals.effective(originals, listOf(
            CardioCorrection(0, LEVEL, "4", 1L), CardioCorrection(0, LEVEL, "5", 2L), CardioCorrection(0, INCLINE, "1", 1L)))
        assertEquals(mapOf(LEVEL to "5", INCLINE to "1"), fixed[0].values)
    }
    @Test fun aCorrectionForAMetricTheInputNeverHadIsIgnored() {
        val fixed = CardioIntervals.effective(listOf(input(0, 0, LEVEL to "3")), listOf(CardioCorrection(0, INCLINE, "9", 1L)))
        assertEquals(mapOf(LEVEL to "3"), fixed[0].values)
    }
    @Test fun aMetricThatTheInputOmittedIsNotCarriedForward() {
        val s = CardioIntervals.compute(fifteen, listOf(input(0, 0, LEVEL to "3", INCLINE to "2"), input(1, 5, LEVEL to "4")))
        assertNull(s.intervals[1].values!![INCLINE])
    }
    @Test fun inputsOutsideTheWindowAreIgnoredAndTheLaterOneWinsAtTheSameSecond() {
        val window = CardioWindow(10 * min, 20 * min)
        val s = CardioIntervals.compute(window, listOf(input(0, 5, LEVEL to "1"), input(1, 12, LEVEL to "2"), input(2, 12, LEVEL to "3"), input(3, 25, LEVEL to "9")))
        assertEquals(2, s.intervals.size); assertFalse(s.intervals[0].recorded); assertEquals("3", s.intervals[1].values!![LEVEL])
        assertEquals(window.seconds, s.totalSeconds)
    }
    @Test fun anEmptyWindowHasNoIntervals() { assertEquals(0, CardioIntervals.compute(CardioWindow(60, 60), listOf(input(0, 1, LEVEL to "1"))).intervals.size) }

    @Test fun theSheetAsksOnlyForTheMetricsTheEquipmentHas() {
        assertEquals(listOf(LEVEL), CardioMetrics.forEquipment("INDOOR_CYCLE"))
        assertEquals(listOf(LEVEL, INCLINE), CardioMetrics.forEquipment("ELLIPTICAL"))
        assertTrue(CardioMetrics.forEquipment("DUMBBELL").isEmpty())
    }
    @Test fun typedValuesMustBeFiniteNonNegativeNumbers() {
        assertEquals(5.0, CardioMetrics.parse(" 5 ")!!, 0.0); assertEquals(7.5, CardioMetrics.parse("7,5")!!, 0.0); assertEquals(0.0, CardioMetrics.parse("0")!!, 0.0)
        for (bad in listOf("", " ", "abc", "-1", "NaN", "Infinity", "5kg")) assertNull("'$bad'", CardioMetrics.parse(bad))
    }
    @Test fun invalidConstructionIsRejected() {
        assertThrows(IllegalArgumentException::class.java) { CardioInput(-1, 0, emptyMap()) }
        assertThrows(IllegalArgumentException::class.java) { CardioWindow(10, 5) }
    }
}
