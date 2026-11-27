package com.move60.workout.architecture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.WorkoutData
import com.move60.workout.domain.CardioMetric.INCLINE
import com.move60.workout.domain.CardioMetric.LEVEL
import com.move60.workout.domain.CardioMetrics
import com.move60.workout.domain.StageKind
import com.move60.workout.domain.StageTimePlan
import java.time.LocalDate
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** docs/v71 step 3: values the user saves on the cardio stage are stored only on an explicit save, only for the machine's own values, and reach the commit. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutiveCardioStoreTest {
    private lateinit var context: Context
    private lateinit var db: WorkoutDatabase
    private var t = 1_700_000_000_000L
    private val date = LocalDate.parse("2026-11-16")
    private fun store() = RoutiveSessionStore(context, { t }, { date })
    private val plan = SavedRoutinePlan(
        "r1", "테스트 루틴", null,
        listOf(StageTimePlan(StageKind.WARM_UP, 300), StageTimePlan(StageKind.STRENGTH, 1200), StageTimePlan(StageKind.CARDIO, 900), StageTimePlan(StageKind.COOL_DOWN, null))
    )
    private val elliptical = "ELLIPTICAL"
    private val cycle = "INDOOR_CYCLE"

    @Before fun init() {
        context = ApplicationProvider.getApplicationContext()
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }

    /** A running session already on the cardio stage with [warm] seconds of warm-up and [strength] seconds of strength behind it. */
    private fun onCardio(st: RoutiveSessionStore = store(), warm: Long = 30, strength: Long = 40): RoutiveSessionStore {
        st.start(plan)
        t += warm * 1000; st.advance()
        t += strength * 1000; st.advance()
        assertEquals(StageKind.CARDIO, st.reconcile()!!.progress.kind)
        return st
    }

    @Test fun aSavedValueIsStoredWithTheStageActiveTimeAndSurvivesARestart() {
        val st = onCardio()
        t += 90_000
        assertEquals(CardioSaveResult.Saved, st.saveCardioInput(elliptical, mapOf(LEVEL to "5", INCLINE to "2")))
        val rows = store().active()!!.cardioInputs
        assertEquals(2, rows.size)
        assertEquals(setOf(0), rows.map { it.inputIndex }.toSet())
        assertEquals(mapOf("level" to "5", "incline" to "2"), rows.associate { it.metricKey to it.valueText })
        assertTrue("stage time, not session time: 90 s into the cardio stage", rows.all { it.atStageSeconds == 90 && it.stageIndex == 2 && it.equipmentKey == elliptical })
    }

    @Test fun eachSaveGetsTheNextIndexAndNothingIsEverRewritten() {
        val st = onCardio()
        t += 60_000; st.saveCardioInput(elliptical, mapOf(LEVEL to "3", INCLINE to "1"))
        t += 120_000; st.saveCardioInput(elliptical, mapOf(LEVEL to "5"))
        val rows = st.active()!!.cardioInputs
        assertEquals(listOf(0, 0, 1), rows.map { it.inputIndex })
        assertEquals("the first input is untouched", "3", rows.first { it.inputIndex == 0 && it.metricKey == "level" }.valueText)
        assertEquals(180, rows.last().atStageSeconds)
    }

    @Test fun aBlankFieldIsNotRecordedAndAValueTheMachineDoesNotHaveIsIgnored() {
        val st = onCardio()
        st.saveCardioInput(elliptical, mapOf(LEVEL to "4", INCLINE to "  "))
        assertEquals(listOf("level"), st.active()!!.cardioInputs.map { it.metricKey })
        st.saveCardioInput(cycle, mapOf(LEVEL to "7", INCLINE to "3"))
        val cycleRows = st.active()!!.cardioInputs.filter { it.equipmentKey == cycle }
        assertEquals("a cycle has no incline", listOf("level"), cycleRows.map { it.metricKey })
    }

    @Test fun refusedSavesStoreNothing() {
        val st = onCardio()
        val before = st.active()!!
        fun refused(reason: CardioSaveResult.Reason, r: CardioSaveResult) { assertEquals(CardioSaveResult.Rejected(reason), r); assertEquals(before.cardioInputs, st.active()!!.cardioInputs) }
        refused(CardioSaveResult.Reason.UNKNOWN_EQUIPMENT, st.saveCardioInput("DUMBBELL", mapOf(LEVEL to "5")))
        refused(CardioSaveResult.Reason.NO_VALUE, st.saveCardioInput(elliptical, emptyMap()))
        refused(CardioSaveResult.Reason.NO_VALUE, st.saveCardioInput(elliptical, mapOf(LEVEL to " ", INCLINE to "")))
        for (bad in listOf("abc", "-1", "NaN", "5kg", "1/2")) refused(CardioSaveResult.Reason.INVALID_VALUE, st.saveCardioInput(elliptical, mapOf(LEVEL to bad)))
        refused(CardioSaveResult.Reason.INVALID_VALUE, st.saveCardioInput(elliptical, mapOf(LEVEL to "5", INCLINE to "x")))
    }

    @Test fun unregisteredMachineDoesNotApplyCategoryCapsAndKeepsExactEnteredNumbers() {
        val st = onCardio()
        assertTrue(st.active()!!.cardioInputs.isEmpty()) // null/unrecorded, not fabricated zero
        assertEquals(CardioSaveResult.Saved, st.saveCardioInput(elliptical, mapOf(LEVEL to "26", INCLINE to "0")))
        assertEquals(CardioSaveResult.Saved, st.saveCardioInput(cycle, mapOf(LEVEL to "41")))
        val saved = st.active()!!.cardioInputs
        assertEquals(listOf("26", "0", "41"), saved.map { it.valueText })
        assertEquals(listOf("level", "incline", "level"), saved.map { it.metricKey })
        // A model-level maximum may be applied only after a verified model mapping exists.
        assertNull(CardioMetrics.maxFor(elliptical, LEVEL))
        assertNull(CardioMetrics.maxFor(cycle, LEVEL))
    }

    @Test fun onlyTheCardioStageOfASessionThatIsNotOverAcceptsAValue() {
        val st = store()
        assertEquals(CardioSaveResult.Rejected(CardioSaveResult.Reason.NO_ACTIVE_SESSION), st.saveCardioInput(elliptical, mapOf(LEVEL to "5")))
        st.start(plan)
        assertEquals("warm-up", CardioSaveResult.Rejected(CardioSaveResult.Reason.NOT_CARDIO_STAGE), st.saveCardioInput(elliptical, mapOf(LEVEL to "5")))
        t += 30_000; st.advance()
        assertEquals("strength", CardioSaveResult.Rejected(CardioSaveResult.Reason.NOT_CARDIO_STAGE), st.saveCardioInput(elliptical, mapOf(LEVEL to "5")))
        t += 30_000; st.advance(); t += 30_000; st.advance()   // now cool-down
        assertEquals("cool-down", CardioSaveResult.Rejected(CardioSaveResult.Reason.NOT_CARDIO_STAGE), st.saveCardioInput(elliptical, mapOf(LEVEL to "5")))
        st.endEarly()
        assertEquals("over", CardioSaveResult.Rejected(CardioSaveResult.Reason.NO_ACTIVE_SESSION), st.saveCardioInput(elliptical, mapOf(LEVEL to "5")))
        assertTrue(st.active()!!.cardioInputs.isEmpty())
    }

    @Test fun pausedTimeNeverCountsTowardsTheInputTime() {
        val st = onCardio()
        t += 40_000; st.pause()
        t += 600_000                                       // ten minutes paused
        st.saveCardioInput(elliptical, mapOf(LEVEL to "4")) // saving while paused is allowed; no time passes
        assertEquals(40, st.active()!!.cardioInputs.single().atStageSeconds)
    }

    @Test fun cancellingIsSimplyNotCallingSaveSoNothingChanges() {
        val st = onCardio()
        val before = st.active()!!
        t += 20_000
        assertTrue(st.reconcile()!!.cardioInputs.isEmpty())
        assertEquals(before.cardioInputs, st.active()!!.cardioInputs)
    }

    @Test fun theSavedValuesReachTheCommitAndAreStoredWithTheSession() = runBlocking {
        val st = onCardio()
        t += 70_000; st.saveCardioInput(elliptical, mapOf(LEVEL to "5", INCLINE to "2"))
        t += 60_000; st.endEarly()
        val snap = st.snapshot(sets = null)
        assertEquals(2, snap.cardioInputs.size)
        val sync = TimerSessionSync(SessionCommitter(db) { 5_000L }) { "Asia/Seoul" }
        assertTrue(sync.syncFinished(snap) is SyncOutcome.Committed)
        val stored = db.routiveDao().cardioInputs(sync.sessionId(snap))
        assertEquals(mapOf("level" to "5", "incline" to "2"), stored.associate { it.metricKey to it.valueText })
        assertTrue(stored.all { it.atActiveSeconds == 70 && it.equipmentKey == elliptical })
    }

    @Test fun aSessionStoredBeforeCardioInputsExistedStillReadsWithNone() {
        val st = onCardio()
        val prefs = context.getSharedPreferences(RoutiveSessionStore.NAME, Context.MODE_PRIVATE)
        val legacy = JSONObject(prefs.getString("active", null)!!).apply { remove("cardio"); remove("cardioV") }
        prefs.edit().putString("active", legacy.toString()).commit()
        val read = st.active()!!
        assertTrue(read.cardioInputs.isEmpty()); assertEquals(StageKind.CARDIO, read.progress.kind)
    }

    @Test fun anUnreadableEntryIsSkippedOnItsOwnAndTheSessionIsKept() {
        val st = onCardio()
        st.saveCardioInput(elliptical, mapOf(LEVEL to "5"))
        val prefs = context.getSharedPreferences(RoutiveSessionStore.NAME, Context.MODE_PRIVATE)
        val json = JSONObject(prefs.getString("active", null)!!)
        json.getJSONArray("cardio").put(JSONObject().put("i", "x"))
        prefs.edit().putString("active", json.toString()).commit()
        assertEquals(1, st.active()!!.cardioInputs.size)
    }

    @Test fun theScreenListsOnlyTheMachinesOwnValuesAndOnlyOnTheCardioStage() {
        val st = onCardio()
        t += 60_000; st.saveCardioInput(elliptical, mapOf(LEVEL to "5", INCLINE to "2"))
        t += 120_000; st.saveCardioInput(elliptical, mapOf(LEVEL to "6"))
        st.saveCardioInput(cycle, mapOf(LEVEL to "9"))
        val s = st.active()!!
        val ui = buildRoutiveUi(s, listOf(RoutineExerciseInput(WorkoutData.CARDIO_FIRST, WorkoutData.CARDIO_FIRST, 900L), RoutineExerciseInput(WorkoutData.CARDIO_SECOND, WorkoutData.CARDIO_SECOND, 900L),
            RoutineExerciseInput("bike", "실내 자전거", 900L)), emptyList(), null)
        val cycleUi = ui.exercises[0].cardio!!; val elUi = ui.exercises[1].cardio!!
        assertEquals(listOf(LEVEL), cycleUi.metrics); assertEquals(listOf(LEVEL, INCLINE), elUi.metrics)
        assertEquals(listOf("레벨 5 · 경사 2", "레벨 6"), elUi.entries.map { it.text })
        assertEquals(listOf(60, 180), elUi.entries.map { it.atStageSeconds })
        assertEquals(listOf("레벨 9"), cycleUi.entries.map { it.text })
        assertNull("an unknown machine name gets no value sheet", ui.exercises[2].cardio)
        // not the cardio stage -> nothing offered
        val onStrength = s.copy(progress = s.progress.back())
        assertEquals(StageKind.STRENGTH, onStrength.progress.kind)
        val other = buildRoutiveUi(onStrength, listOf(RoutineExerciseInput(WorkoutData.CARDIO_SECOND, WorkoutData.CARDIO_SECOND, null)), emptyList(), null)
        assertNull(other.exercises[0].cardio)
    }

    @Test fun theMachineNamesTheCardioStageUsesMapToTheEquipmentKeys() {
        assertEquals(cycle, CardioMetrics.equipmentKeyOf(WorkoutData.CARDIO_FIRST))
        assertEquals(elliptical, CardioMetrics.equipmentKeyOf(WorkoutData.CARDIO_SECOND))
        assertEquals(Equipment.INDOOR_CYCLE.name, cycle); assertEquals(Equipment.ELLIPTICAL.name, elliptical)
        assertTrue(Equipment.INDOOR_CYCLE.cardio && Equipment.ELLIPTICAL.cardio)
        assertNull(CardioMetrics.equipmentKeyOf("레그 프레스"))
    }
}
