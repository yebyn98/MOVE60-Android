package com.move60.workout.architecture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.domain.StageKind
import com.move60.workout.domain.StageTimePlan
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** docs/v61 D1: the plan is captured when the session STARTS and survives a restart; unknown values stay null. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutivePlanSnapshotTest {
    private lateinit var context: Context
    private var t = 1_700_000_000_000L
    private val date = LocalDate.parse("2026-11-16")
    private fun store() = RoutiveSessionStore(context, { t }, { date })

    private val routine = SavedRoutinePlan(
        "r1", "하체 루틴", null,
        listOf(StageTimePlan(StageKind.WARM_UP, 300), StageTimePlan(StageKind.STRENGTH, null), StageTimePlan(StageKind.CARDIO, 900), StageTimePlan(StageKind.COOL_DOWN, null)),
        mapOf(
            StageKind.WARM_UP to listOf(RoutineExerciseInput("walk", "제자리 걷기", 120)),
            StageKind.STRENGTH to listOf(RoutineExerciseInput("legPress", "레그프레스", null, 3, 12, 60), RoutineExerciseInput("legCurl", "레그컬"))
        )
    )

    @Before fun init() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(RoutiveSessionStore.NAME, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("move60_set_records_v1", Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun toJsonAndParseRoundTripWithoutInventingValues() {
        val parsed = RoutivePlanSnapshot.parse(RoutivePlanSnapshot.toJson(routine))!!
        assertEquals("r1", parsed.routineId); assertEquals("하체 루틴", parsed.routineName)
        assertEquals(listOf(StageKind.WARM_UP, StageKind.STRENGTH, StageKind.CARDIO, StageKind.COOL_DOWN), parsed.stages.map { it.kind })
        assertEquals("unknown planned time stays null, never 0", listOf(300L, null, 900L, null), parsed.stages.map { it.plannedSeconds })
        assertEquals(
            listOf(
                RoutivePlanSnapshot.Exercise(0, "walk", "제자리 걷기", 120L, null, null, null),
                RoutivePlanSnapshot.Exercise(1, "legPress", "레그프레스", null, 3, 12, 60L),
                RoutivePlanSnapshot.Exercise(1, "legCurl", "레그컬", null, null, null, null)
            ),
            parsed.exercises
        )
    }

    @Test fun missingUnreadableOrUnknownVersionSnapshotsParseToNull() {
        assertNull(RoutivePlanSnapshot.parse(null)); assertNull(RoutivePlanSnapshot.parse(""))
        assertNull(RoutivePlanSnapshot.parse("not json"))
        assertNull(RoutivePlanSnapshot.parse("""{"v":2,"routineId":"r","routineName":"n","stages":[],"exercises":[]}"""))
        assertNull("an unknown stage kind is not guessed", RoutivePlanSnapshot.parse(
            """{"v":1,"routineId":"r","routineName":"n","stages":[{"kind":"NOPE","plannedSeconds":null}],"exercises":[]}"""))
    }

    @Test fun startKeepsThePlanAsItWasAndItSurvivesARestart() {
        val started = (store().start(routine) as RoutiveStart.Started).session
        assertEquals(RoutivePlanSnapshot.toJson(routine), started.planSnapshotJson)
        assertEquals("a restart reads back exactly what was stored", started.planSnapshotJson, store().active()!!.planSnapshotJson)
    }

    @Test fun snapshotCarriesStageActualsThatAddUpToTheActiveTimeAndThePlan() {
        val st = store(); st.start(routine)
        t += 100_000; st.pause()
        val snap = st.snapshot(sets = null)
        assertEquals(listOf(100L, 0L, 0L, 0L), snap.stages.map { it.actualSeconds })
        assertEquals(listOf(300L, null, 900L, null), snap.stages.map { it.plannedSeconds })
        assertEquals(snap.accumulatedSeconds.toLong(), snap.stages.sumOf { it.actualSeconds })
        assertEquals("하체 루틴", RoutivePlanSnapshot.parse(snap.planSnapshotJson)!!.routineName)
    }

    @Test fun aSessionStartedBeforePlanSnapshotsExistedReadsWithoutOne() {
        store().start(routine)
        val prefs = context.getSharedPreferences(RoutiveSessionStore.NAME, Context.MODE_PRIVATE)
        val json = org.json.JSONObject(prefs.getString("active", null)!!)
        json.remove("planSnapshot")                                    // what an older build wrote
        prefs.edit().putString("active", json.toString()).commit()
        val read = store().active()!!
        assertNull(read.planSnapshotJson)
        assertEquals("the session itself is untouched", 4, read.progress.plan.size)
    }
}
