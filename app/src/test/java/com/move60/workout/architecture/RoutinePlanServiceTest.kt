package com.move60.workout.architecture

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.domain.PlanValidationIssue
import com.move60.workout.domain.StageKind
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RoutinePlanServiceTest {
    private lateinit var db: WorkoutDatabase
    private lateinit var service: RoutinePlanService
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkoutDatabase::class.java)
            .allowMainThreadQueries().build()
        service = RoutinePlanService(db) { 1000L }
    }
    @After fun close() { db.close() }
    private fun ex(id: String, sec: Long? = null) = RoutineExerciseInput(id, id, sec)
    private val one = mapOf(StageKind.STRENGTH to listOf(ex("legPress")))

    @Test fun unknownExerciseTimesStayNullAndStageTimesAreUnknown() = runBlocking {
        val r = service.save("r1", "하체 루틴", null, mapOf(StageKind.WARM_UP to listOf(ex("walk", 300L)), StageKind.STRENGTH to listOf(ex("legPress"))))
        assertTrue(r is RoutinePlanResult.Saved)
        val plan = service.load("r1")!!
        assertEquals(listOf(StageKind.WARM_UP, StageKind.STRENGTH, StageKind.CARDIO, StageKind.COOL_DOWN), plan.stages.map { it.kind })
        // warm-up: every exercise known -> derived 300; strength has an unknown exercise -> null; empty stages -> null (not 0)
        assertEquals(listOf<Long?>(300L, null, null, null), plan.stages.map { it.plannedSeconds })
        assertNull(plan.exercises[StageKind.STRENGTH]!!.single().plannedSeconds)
        assertNull(plan.targetSeconds)
    }
    @Test fun stageTimeIsTheSumOfItsExercisesAndCustomValuesAreKeptExactly() = runBlocking {
        service.save("r1", "A", null, mapOf(StageKind.CARDIO to listOf(ex("bike", 1_380L), ex("elliptical", 420L)), StageKind.WARM_UP to listOf(ex("walk", 150L))))
        val plan = service.load("r1")!!
        assertEquals(1_800L, plan.stages.first { it.kind == StageKind.CARDIO }.plannedSeconds)
        assertEquals(150L, plan.stages.first { it.kind == StageKind.WARM_UP }.plannedSeconds)       // custom 2.5 min preserved, no rounding
        assertEquals(listOf<Long?>(1_380L, 420L), plan.exercises[StageKind.CARDIO]!!.map { it.plannedSeconds })
    }
    @Test fun fullyKnownTimesMustMatchTarget() = runBlocking {
        val times = mapOf(StageKind.WARM_UP to listOf(ex("walk", 300L)), StageKind.STRENGTH to listOf(ex("legPress", 1200L)),
            StageKind.CARDIO to listOf(ex("bike", 1800L)), StageKind.COOL_DOWN to listOf(ex("stretch", 300L)))
        assertTrue(service.save("ok", "A", 3600L, times) is RoutinePlanResult.Saved)
        val bad = service.save("bad", "B", 4000L, times) as RoutinePlanResult.Rejected
        assertEquals(PlanValidationIssue.TOTAL_TIME_MISMATCH, bad.issue)
        assertNull(service.load("bad"))
    }
    @Test fun knownPartialSumCannotExceedTarget() = runBlocking {
        val r = service.save("x", "A", 600L, mapOf(StageKind.CARDIO to listOf(ex("bike", 900L)))) as RoutinePlanResult.Rejected
        assertEquals(PlanValidationIssue.KNOWN_TIME_EXCEEDS_TARGET, r.issue)
    }
    @Test fun sameExerciseInTwoStagesIsCountedTwiceNotMerged() = runBlocking {
        val r = service.save("d", "A", 500L, mapOf(StageKind.WARM_UP to listOf(ex("walk", 300L)), StageKind.COOL_DOWN to listOf(ex("walk", 300L)))) as RoutinePlanResult.Rejected
        assertEquals(PlanValidationIssue.KNOWN_TIME_EXCEEDS_TARGET, r.issue)
    }
    @Test fun zeroTimeAndBadNamesAreRejected() = runBlocking {
        assertTrue(service.save("z", "A", null, mapOf(StageKind.CARDIO to listOf(ex("bike", 0L)))) is RoutinePlanResult.Rejected)
        assertTrue(service.save("n", "  ", null, one) is RoutinePlanResult.Rejected)
        assertTrue(service.save("l", "가".repeat(31), null, one) is RoutinePlanResult.Rejected)
    }
    @Test fun duplicateIdDoesNotOverwriteExistingPlan() = runBlocking {
        service.save("r1", "원본", null, mapOf(StageKind.WARM_UP to listOf(ex("walk", 300L))))
        assertTrue(service.save("r1", "덮어쓰기", null, one) is RoutinePlanResult.Rejected)
        assertEquals("원본", service.load("r1")!!.name)
        assertEquals(300L, service.load("r1")!!.stages.first().plannedSeconds)
    }
    @Test fun savingAPlanNeverTouchesExistingSessions() = runBlocking {
        val before = db.routiveDao().sessionSecondsOn("2026-11-15")
        service.save("r1", "A", null, one)
        assertEquals(before, db.routiveDao().sessionSecondsOn("2026-11-15"))
    }
    @Test fun exercisesAreStoredPerStageInOrderAndReloaded() = runBlocking {
        val items = mapOf(StageKind.WARM_UP to listOf(ex("walk", 120L), ex("mob")),
            StageKind.STRENGTH to listOf(ex("legPress")), StageKind.CARDIO to listOf(ex("bike")))
        assertTrue(service.save("r1", "하체", null, items) is RoutinePlanResult.Saved)
        val back = service.load("r1")!!.exercises
        assertEquals(listOf("walk", "mob"), back[StageKind.WARM_UP]!!.map { it.id })
        assertEquals(listOf<Long?>(120L, null), back[StageKind.WARM_UP]!!.map { it.plannedSeconds })
        assertNull(back[StageKind.COOL_DOWN])
    }
    @Test fun routineWithoutAnyExerciseCannotBeConfirmed() = runBlocking {
        assertTrue(service.save("e", "빈 루틴", null, emptyMap()) is RoutinePlanResult.Rejected)
        assertNull(service.load("e"))
    }

    @Test fun strengthTargetsAreSavedExactlyAndUnsetStaysNull() = runBlocking {
        val set = RoutineExerciseInput("legPress", "레그 프레스", null, 3, 10, 90L)
        val unset = RoutineExerciseInput("row", "로우")
        assertTrue(service.save("r1", "힘", null, mapOf(StageKind.STRENGTH to listOf(set, unset))) is RoutinePlanResult.Saved)
        val loaded = service.load("r1")!!.exercises[StageKind.STRENGTH]!!
        assertEquals(Triple(3, 10, 90L), Triple(loaded[0].targetSets, loaded[0].targetReps, loaded[0].restSeconds))
        assertEquals("no automatic rest/sets/reps is ever generated", Triple<Int?, Int?, Long?>(null, null, null),
            Triple(loaded[1].targetSets, loaded[1].targetReps, loaded[1].restSeconds))
    }

    @Test fun outOfRangeStrengthTargetsAreRejectedAndNothingIsStored() = runBlocking {
        for (bad in listOf(RoutineExerciseInput("a", "a", null, 0, 10, 60L), RoutineExerciseInput("a", "a", null, 21, 10, 60L),
            RoutineExerciseInput("a", "a", null, 3, 0, 60L), RoutineExerciseInput("a", "a", null, 3, 10, 4L), RoutineExerciseInput("a", "a", null, 3, 10, 901L))) {
            assertTrue(service.save("bad", "x", null, mapOf(StageKind.STRENGTH to listOf(bad))) is RoutinePlanResult.Rejected)
            assertNull(service.load("bad"))
        }
    }
}
