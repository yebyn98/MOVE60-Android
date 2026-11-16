package com.move60.workout.architecture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.SetRecordStore
import com.move60.workout.domain.StageKind
import com.move60.workout.domain.StageTimePlan
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/**
 * Slice 2 (docs/v61 D5): a session saved with the start-time plan and stage actuals shows its routine name and stage times in
 * the day list and the detail; a session saved without them reads exactly as before. Read end to end: written through the
 * real commit path, read through [RecordReader].
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordStageReadTest {
    private lateinit var context: Context
    private lateinit var db: WorkoutDatabase
    private lateinit var sync: TimerSessionSync
    private lateinit var reader: RecordReader
    private val date = LocalDate.parse("2026-11-16")

    private val routine = SavedRoutinePlan(
        "r1", "하체 루틴", null,
        listOf(StageTimePlan(StageKind.WARM_UP, 300), StageTimePlan(StageKind.STRENGTH, null), StageTimePlan(StageKind.CARDIO, 900), StageTimePlan(StageKind.COOL_DOWN, null)),
        mapOf(
            StageKind.WARM_UP to listOf(RoutineExerciseInput("walk", "제자리 걷기", 120), RoutineExerciseInput("mobility", "관절 가동성")),
            StageKind.STRENGTH to listOf(RoutineExerciseInput("legPress", "레그프레스", null, 3, 12, 60)),
            StageKind.CARDIO to listOf(RoutineExerciseInput("cycle", "실내 사이클", 900))
        )
    )

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("move60_set_records_v1", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        sync = TimerSessionSync(SessionCommitter(db) { 5_000L }) { "Asia/Seoul" }
        reader = RecordReader(db) { SetRecordStore(context).completedStoredSets() }
    }
    @After fun close() { db.close() }

    private fun stages(warm: Long = 300, strength: Long = 600, cardio: Long = 0, cool: Long = 120) = listOf(
        TimerStage(StageKind.WARM_UP, 300, warm), TimerStage(StageKind.STRENGTH, null, strength),
        TimerStage(StageKind.CARDIO, 900, cardio), TimerStage(StageKind.COOL_DOWN, null, cool)
    )

    /** Real epoch seconds on [date] (12:00 Seoul), so the session lands on that date's day slice. */
    private fun snap(stages: List<TimerStage>, plan: String?, start: Long): TimerSnapshot {
        val total = stages.sumOf { it.actualSeconds }
        val from = T.at("2026-11-16", 12)
        return TimerSnapshot(date, 1, start, true, total.toInt(), listOf(from to from + total), false,
            listOf(TimerSet("legPress", 1, "40", "12")), stages, plan)
    }

    private suspend fun commit(s: TimerSnapshot): String {
        assertTrue(sync.syncFinished(s) is SyncOutcome.Committed)
        return sync.sessionId(s)
    }

    @Test fun dayListShowsTheRoutineNameAndStageTimesInStageOrderWithoutZeroSecondStages() = runBlocking {
        commit(snap(stages(), RoutivePlanSnapshot.toJson(routine), 111L))
        val s = reader.day(date).sessions.single()
        assertEquals("하체 루틴", s.routineName)
        assertEquals(listOf("준비운동" to 300L, "근력운동" to 600L, "마무리" to 120L), s.stageSeconds)
    }

    @Test fun detailHasThePlannedItemsOfEachStageLabelledAsPlanAndTheRealTime() = runBlocking {
        val id = commit(snap(stages(cardio = 900), RoutivePlanSnapshot.toJson(routine), 112L))
        val detail = reader.session(id)!!
        assertEquals(listOf(0, 1, 2, 3), detail.stages.map { it.index })
        assertEquals(listOf(SegmentKind.WARMUP, SegmentKind.STRENGTH, SegmentKind.CARDIO_STEADY, SegmentKind.COOLDOWN), detail.stages.map { it.kind })
        val warm = detail.stages[0]
        assertEquals(300L, warm.actualSeconds); assertEquals(300L, warm.plannedSeconds)
        assertEquals("unknown item time stays null", listOf(PlannedItem("제자리 걷기", 120L), PlannedItem("관절 가동성", null)), warm.items)
        val cardio = detail.stages[2]
        assertEquals(listOf(PlannedItem("실내 사이클", 900L)), cardio.items)
        assertNull("strength plan time was unknown", detail.stages[1].plannedSeconds)
        assertTrue("cool-down had no planned items", detail.stages[3].items.isEmpty())
    }

    /** A session saved before this existed (no plan, no segments) reads as it always did. */
    @Test fun sessionWithoutStageDataHasNoRoutineNameStagesOrStageLine() = runBlocking {
        val id = commit(snap(stages(), null, 113L).copy(stages = emptyList()))
        val s = reader.day(date).sessions.single { it.sessionId == id }
        assertNull(s.routineName); assertTrue(s.stageSeconds.isEmpty())
        val detail = reader.session(id)!!
        assertTrue(detail.stages.isEmpty())
        assertEquals("the stored sets are still shown", listOf("legPress"), detail.exercises.map { it.exerciseId })
    }

    /** An unreadable plan must not hide the real stage times. */
    @Test fun anUnreadablePlanStillShowsTheStageTimesWithoutNamesOrItems() = runBlocking {
        val id = commit(snap(stages(), "this is not json", 114L))
        val s = reader.day(date).sessions.single()
        assertNull(s.routineName)
        assertEquals(3, s.stageSeconds.size)
        assertTrue(reader.session(id)!!.stages.all { it.items.isEmpty() })
    }

    /** Intensity rounds (later feature) and unknown kinds are not stages and never appear as one. */
    @Test fun intensityRoundsAreNotShownAsStages() = runBlocking {
        val id = commit(snap(stages(), RoutivePlanSnapshot.toJson(routine), 115L))
        assertEquals(3, db.routiveDao().segments(id).size)
        assertNull(RecordFormat.stageLabel(SegmentKind.CARDIO_WORK)); assertNull(RecordFormat.stageLabel(SegmentKind.CARDIO_RECOVERY))
        assertEquals(listOf("준비운동", "근력운동", "유산소", "마무리"), listOf(SegmentKind.WARMUP, SegmentKind.STRENGTH, SegmentKind.CARDIO_STEADY, SegmentKind.COOLDOWN).map { RecordFormat.stageLabel(it) })
    }

    @Test fun compactTimeReadsAsMinutesAndSeconds() {
        assertEquals("5분", RecordFormat.compact(300)); assertEquals("5분 20초", RecordFormat.compact(320))
        assertEquals("20초", RecordFormat.compact(20)); assertEquals("0초", RecordFormat.compact(0)); assertEquals("0초", RecordFormat.compact(-4))
        assertEquals("60분 20초", RecordFormat.compact(3620))
    }
}
