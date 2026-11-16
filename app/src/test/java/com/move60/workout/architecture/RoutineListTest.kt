package com.move60.workout.architecture

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
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
class RoutineListTest {
    private lateinit var db: WorkoutDatabase
    private var clock = 1_000L
    private lateinit var service: RoutinePlanService
    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkoutDatabase::class.java).allowMainThreadQueries().build()
        service = RoutinePlanService(db) { clock }
    }
    @After fun close() { db.close() }
    private fun ex(id: String, sec: Long? = null) = RoutineExerciseInput(id, id, sec)

    @Test fun plannedLabelsNeverTurnUnknownIntoZero() {
        assertEquals("미정", formatPlanned(null))
        assertEquals("30분", formatPlanned(1_800L))
        assertEquals("2분 30초", formatPlanned(150L))
        assertEquals("45초", formatPlanned(45L))
    }

    @Test fun emptyStoreListsNothing() = runBlocking { assertTrue(service.list().isEmpty()) }

    @Test fun listsNewestFirstWithExactStoredValues() = runBlocking {
        clock = 1_000L; service.save("old", "예전 루틴", null, mapOf(StageKind.STRENGTH to listOf(ex("legPress"))))
        clock = 2_000L; service.save("new", "새 루틴", 3_600L, mapOf(
            StageKind.WARM_UP to listOf(ex("walk", 150L)), StageKind.CARDIO to listOf(ex("bike", 1_800L))))
        val items = service.list().map { it.toListItem() }
        assertEquals(listOf("새 루틴", "예전 루틴"), items.map { it.name })
        val new = items.first()
        assertEquals("목표 60분", new.targetLabel); assertEquals(2, new.exerciseCount)
        assertEquals(listOf("준비운동" to "2분 30초", "근력운동" to "미정", "유산소" to "30분", "마무리" to "미정"), new.stages)
        assertEquals("목표 시간 미정", items.last().targetLabel)
    }

    @Test fun listingDoesNotChangeStoredRoutines() = runBlocking {
        service.save("r", "A", null, mapOf(StageKind.STRENGTH to listOf(ex("legPress", 600L))))
        val before = service.load("r")
        service.list(); service.list()
        assertEquals(before, service.load("r"))
        assertEquals(1, db.routiveDao().routinePlans().size)
    }
}
