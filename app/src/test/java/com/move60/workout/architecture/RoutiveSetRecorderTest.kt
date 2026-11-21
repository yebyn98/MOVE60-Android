package com.move60.workout.architecture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.SetRecordStore
import com.move60.workout.domain.SetInputError
import com.move60.workout.domain.StageKind
import com.move60.workout.domain.StageTimePlan
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Review item 2: a set is validated BEFORE it is recorded and before the rest starts. The plan allows up to 200 repetitions, so a
 * planned 150 must be recorded (it used to be written as "not completed" because the store stopped at 100 while the rest still started).
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutiveSetRecorderTest {
    private lateinit var context: Context
    private lateinit var sets: SetRecordStore
    private var t = T.ms("2026-11-16", 12)
    private val date = LocalDate.parse("2026-11-16")
    private lateinit var store: RoutiveSessionStore
    private lateinit var recorder: RoutiveSetRecorder
    private val legPress = RoutineExerciseInput("legPress", "레그프레스", null, 3, 150, 60)

    @Before fun init() {
        context = ApplicationProvider.getApplicationContext()
        for (name in listOf(RoutiveSessionStore.NAME, "move60_set_records_v1", PendingSessionOutbox.NAME)) context.getSharedPreferences(name, 0).edit().clear().commit()
        sets = SetRecordStore(context)
        store = RoutiveSessionStore(context, { t }, { date })
        recorder = RoutiveSetRecorder(store, sets)
    }

    private fun inStrengthStage(): RoutiveSession {
        val plan = SavedRoutinePlan("r1", "루틴", null,
            listOf(StageTimePlan(StageKind.WARM_UP, 300), StageTimePlan(StageKind.STRENGTH, 1200), StageTimePlan(StageKind.CARDIO, 900), StageTimePlan(StageKind.COOL_DOWN, null)))
        assertTrue(store.start(plan) is RoutiveStart.Started)
        t += 10_000; store.advance()
        return store.active()!!.also { assertEquals(StageKind.STRENGTH, it.progress.kind) }
    }
    private fun recorded(s: RoutiveSession) = sets.completedSetsForSession(s.startedAtEpochMs)

    @Test fun aPlannedHundredFiftyRepsIsRecordedAsCompletedAndStartsTheChosenRest() {
        assertTrue("the plan can hold 150 reps", 150 in RoutinePlanService.REPS_RANGE)
        val s = inStrengthStage()
        assertNull(recorder.complete("legPress", "40", "150", legPress))
        assertEquals(listOf(SetRecordStore.CompletedSet("legPress", 1, "40", "150")), recorded(s))
        assertEquals("the user-chosen rest started", 60L, store.active()!!.rest?.totalSeconds)
    }

    @Test fun theLimitOfTheRoutinePlanIsRecordableAndOneAboveIsNot() {
        val s = inStrengthStage()
        assertNull(recorder.complete("legPress", "40", "${RoutinePlanService.REPS_RANGE.last}", null))
        assertEquals(1, recorded(s).size)
        assertEquals(SetInputError.REPS, recorder.complete("legPress", "40", "${RoutinePlanService.REPS_RANGE.last + 1}", null))
        assertEquals("the refused value was not recorded", 1, recorded(s).size)
    }

    @Test fun anInvalidValueIsAnInputErrorWithNoRecordAndNoRest() {
        val s = inStrengthStage()
        for ((kg, reps, expected) in listOf(
            Triple("abc", "10", SetInputError.WEIGHT), Triple("1.2.3", "10", SetInputError.WEIGHT), Triple("501", "10", SetInputError.WEIGHT),
            Triple("-1", "10", SetInputError.WEIGHT), Triple("40", "0", SetInputError.REPS), Triple("40", "999", SetInputError.REPS),
            Triple("40", "1.5", SetInputError.REPS), Triple("40", "abc", SetInputError.REPS)
        )) {
            assertEquals("kg='$kg' reps='$reps'", expected, recorder.complete("legPress", kg, reps, legPress))
        }
        assertTrue("nothing was recorded", recorded(s).isEmpty())
        assertNull("and no rest was started", store.active()!!.rest)
    }

    @Test fun aRefusedInputAfterASetLeavesThatSetAndItsRestUntouched() {
        val s = inStrengthStage()
        assertNull(recorder.complete("legPress", "40", "10", legPress))
        val restBefore = store.active()!!.rest
        assertEquals(SetInputError.REPS, recorder.complete("legPress", "40", "300", legPress))
        assertEquals(listOf(SetRecordStore.CompletedSet("legPress", 1, "40", "10")), recorded(s))
        assertEquals(restBefore, store.active()!!.rest)
    }

    @Test fun blankFieldsOrAnotherStageAreIgnoredWithoutAnError() {
        val s = inStrengthStage()
        assertNull(recorder.complete("legPress", "", "10", legPress)); assertNull(recorder.complete("legPress", "40", " ", legPress))
        assertTrue(recorded(s).isEmpty())
        t += 5_000; store.advance()   // cardio: no strength set can be recorded
        assertNull(recorder.complete("legPress", "40", "10", legPress))
        assertTrue(recorded(s).isEmpty())
    }

    @Test fun valuesAreStoredTrimmedAndWithoutLeadingZeros() {
        val s = inStrengthStage()
        assertNull(recorder.complete("legPress", " 42.5 ", " 012 ", null))
        assertEquals(listOf(SetRecordStore.CompletedSet("legPress", 1, "42.5", "12")), recorded(s))
    }
    @Test fun completedSetNeverOverwritesAnExplicitlySkippedEarlierSet() {
        val s = inStrengthStage()
        val skips = ExplicitSetSkipStore(context)
        assertTrue(skips.skip(s.startedAtEpochMs, "legPress", 1, 1_001L))
        val aware = RoutiveSetRecorder(store, sets, skips)
        assertNull(aware.complete("legPress", "40", "10", legPress))
        assertEquals(listOf(2), recorded(s).map { it.setNumber })
        assertEquals(listOf(1), skips.skippedForSession(s.startedAtEpochMs).map { it.setNumber })
    }

    @Test fun lastTargetSetSkippedCannotBeSilentlyCompletedWithoutUndo() {
        val s = inStrengthStage()
        val skips = ExplicitSetSkipStore(context)
        val aware = RoutiveSetRecorder(store, sets, skips)
        assertNull(aware.complete("legPress", "40", "10", legPress))
        assertNull(aware.complete("legPress", "40", "10", legPress))
        assertTrue(skips.skip(s.startedAtEpochMs, "legPress", 3, 2_000L))
        assertEquals(SetInputError.NOT_STORED, aware.complete("legPress", "40", "10", legPress))
        assertEquals(listOf(1, 2), recorded(s).map { it.setNumber })
        assertEquals(listOf(3), skips.skippedForSession(s.startedAtEpochMs).map { it.setNumber })
        assertTrue(skips.undo(s.startedAtEpochMs, "legPress", 3, 2_100L))
        assertNull(aware.complete("legPress", "40", "10", legPress))
        assertEquals(listOf(1, 2, 3), recorded(s).map { it.setNumber })
        assertTrue(skips.skippedForSession(s.startedAtEpochMs).isEmpty())
    }

}
