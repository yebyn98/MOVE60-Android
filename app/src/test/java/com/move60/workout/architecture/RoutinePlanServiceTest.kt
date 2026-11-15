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

    @Test fun nullStageTimesAreStoredAsNullNeverZero() = runBlocking {
        val r = service.save("r1", "하체 루틴", null, mapOf(StageKind.WARM_UP to 300L))
        assertTrue(r is RoutinePlanResult.Saved)
        val plan = service.load("r1")!!
        assertEquals(listOf(StageKind.WARM_UP, StageKind.STRENGTH, StageKind.CARDIO, StageKind.COOL_DOWN), plan.stages.map { it.kind })
        assertEquals(listOf<Long?>(300L, null, null, null), plan.stages.map { it.plannedSeconds })
        assertNull(plan.targetSeconds)
    }
    @Test fun fullyKnownTimesMustMatchTarget() = runBlocking {
        val times = mapOf(StageKind.WARM_UP to 300L, StageKind.STRENGTH to 1200L, StageKind.CARDIO to 1800L, StageKind.COOL_DOWN to 300L)
        assertTrue(service.save("ok", "A", 3600L, times) is RoutinePlanResult.Saved)
        val bad = service.save("bad", "B", 4000L, times) as RoutinePlanResult.Rejected
        assertEquals(PlanValidationIssue.TOTAL_TIME_MISMATCH, bad.issue)
        assertNull(service.load("bad"))
    }
    @Test fun knownPartialSumCannotExceedTarget() = runBlocking {
        val r = service.save("x", "A", 600L, mapOf(StageKind.CARDIO to 900L)) as RoutinePlanResult.Rejected
        assertEquals(PlanValidationIssue.KNOWN_TIME_EXCEEDS_TARGET, r.issue)
    }
    @Test fun zeroTimeAndBadNamesAreRejected() = runBlocking {
        assertTrue(service.save("z", "A", null, mapOf(StageKind.CARDIO to 0L)) is RoutinePlanResult.Rejected)
        assertTrue(service.save("n", "  ", null, emptyMap()) is RoutinePlanResult.Rejected)
        assertTrue(service.save("l", "가".repeat(31), null, emptyMap()) is RoutinePlanResult.Rejected)
    }
    @Test fun duplicateIdDoesNotOverwriteExistingPlan() = runBlocking {
        service.save("r1", "원본", null, mapOf(StageKind.WARM_UP to 300L))
        assertTrue(service.save("r1", "덮어쓰기", null, emptyMap()) is RoutinePlanResult.Rejected)
        assertEquals("원본", service.load("r1")!!.name)
        assertEquals(300L, service.load("r1")!!.stages.first().plannedSeconds)
    }
    @Test fun savingAPlanNeverTouchesExistingSessions() = runBlocking {
        val before = db.routiveDao().sessionSecondsOn("2026-11-15")
        service.save("r1", "A", null, emptyMap())
        assertEquals(before, db.routiveDao().sessionSecondsOn("2026-11-15"))
    }
}
