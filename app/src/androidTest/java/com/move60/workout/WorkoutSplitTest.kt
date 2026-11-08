package com.move60.workout

import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class WorkoutSplitTest {
    private val context get() = InstrumentationRegistry.getInstrumentation().targetContext

    @Before fun clean() {
        context.getSharedPreferences("move60_duration_v1", android.content.Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun validSplitsAreFiveMinuteStepsThatLeaveStrengthTenMinutes() {
        assertTrue(WorkoutData.isValidSplit(15, 20))
        assertTrue(WorkoutData.isValidSplit(5, 35))
        assertFalse("first not a 5-minute step", WorkoutData.isValidSplit(12, 20))
        assertFalse("cardio below 5", WorkoutData.isValidSplit(0, 20))
        assertFalse("strength below 10", WorkoutData.isValidSplit(25, 20))
    }

    @Test fun upgradeFromSingleCardioTotalKeepsTheOldSplit() {
        context.getSharedPreferences("move60_duration_v1", android.content.Context.MODE_PRIVATE)
            .edit().putInt("cardio_minutes", 35).commit()
        val data = WorkoutData(context)
        assertEquals(35, data.cardioMinutes())
        assertEquals(20, data.cardioFirstMinutes()) // old rule: half rounded to 5
        assertEquals(15, data.cardioSecondMinutes())
        assertEquals(15, data.strengthMinutes())
    }

    @Test fun individualSplitIsPersisted() {
        val data = WorkoutData(context)
        data.setCardioSplit(15, 20)
        val again = WorkoutData(context)
        assertEquals(15, again.cardioFirstMinutes())
        assertEquals(20, again.cardioSecondMinutes())
        assertEquals(15, again.strengthMinutes())
    }

    @Test fun phaseKeysCoverCardioAndStretchStagesOnly() {
        assertEquals(listOf(null, "phases", null, "phases3", "phases4"), (0..4).map { phasesKeyForStage(it) })
        assertEquals(5, STAGE_COUNT)
    }
}
