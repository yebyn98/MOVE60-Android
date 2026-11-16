package com.move60.workout.architecture

import android.content.Context
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
import java.time.LocalDate

/**
 * docs/v61 slice 1: stage actuals and the start-time plan are stored with a finished session, replays stay idempotent,
 * sessions saved by an older build never turn into a Conflict, and none of this changes validity, time or credit.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionStageSegmentsTest {
    private lateinit var context: Context
    private lateinit var db: WorkoutDatabase
    private lateinit var sync: TimerSessionSync
    private val dao get() = db.routiveDao()
    private val date = LocalDate.parse("2026-11-16")
    private val planJson = """{"v":1,"routineName":"하체 루틴"}"""

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(PendingSessionOutbox.NAME, Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        sync = TimerSessionSync(SessionCommitter(db) { 5_000L }) { "Asia/Seoul" }
    }
    @After fun close() { db.close() }

    private fun fourStages(warm: Long = 300, strength: Long = 600, cardio: Long = 0, cool: Long = 120) = listOf(
        TimerStage(StageKind.WARM_UP, 300, warm), TimerStage(StageKind.STRENGTH, null, strength),
        TimerStage(StageKind.CARDIO, 900, cardio), TimerStage(StageKind.COOL_DOWN, null, cool)
    )

    private fun snap(
        stages: List<TimerStage> = fourStages(), plan: String? = planJson, start: Long = 1_000L,
        seconds: Int = stages.sumOf { it.actualSeconds }.toInt(),
        sets: List<TimerSet> = listOf(TimerSet("legPress", 1, "40", "12"))
    ) = TimerSnapshot(date, 3, start, true, seconds, listOf(100L to 100L + seconds), false, sets, stages, plan)

    private fun idOf(s: TimerSnapshot) = sync.sessionId(s)

    /** AC-S1: one row per stage with real time, order = stage index, kinds mapped, planned time kept (null stays null). */
    @Test fun stageActualsAreStoredAndZeroSecondStagesGetNoRow() = runBlocking {
        val s = snap()
        assertTrue(sync.syncFinished(s) is SyncOutcome.Committed)
        val rows = dao.segments(idOf(s))
        assertEquals(
            listOf(
                listOf(0, "WARMUP", 300L, 300L), listOf(1, "STRENGTH", null, 600L), listOf(3, "COOLDOWN", null, 120L)
            ),
            rows.map { listOf(it.orderIndex, it.kind, it.plannedSeconds, it.actualSeconds) }
        )
        assertTrue("equipment and metrics are for user-entered values only", rows.all { it.equipment == null && it.metricsJson == null })
        assertEquals(planJson, dao.session(idOf(s))!!.planSnapshotJson)
    }

    @Test fun cardioStageIsStoredAsSteadyCardioWithItsPlan() = runBlocking {
        val s = snap(stages = fourStages(cardio = 900), start = 2_000L)
        assertTrue(sync.syncFinished(s) is SyncOutcome.Committed)
        val cardio = dao.segments(idOf(s)).single { it.orderIndex == 2 }
        assertEquals("CARDIO_STEADY", cardio.kind)
        assertEquals(900L, cardio.plannedSeconds); assertEquals(900L, cardio.actualSeconds)
    }

    /** AC-S5: stages that do not add up to the measured time store no segments, but the session itself is kept. */
    @Test fun stagesThatDoNotAddUpStoreNoSegmentsButTheSessionIsKept() = runBlocking {
        val s = snap(stages = fourStages(strength = 580), seconds = 1020)   // 300+580+0+120 = 1000 != 1020
        assertTrue(sync.syncFinished(s) is SyncOutcome.Committed)
        assertEquals(0, dao.segments(idOf(s)).size)
        assertEquals(1020L, dao.session(idOf(s))!!.totalActiveSeconds)
        assertEquals(planJson, dao.session(idOf(s))!!.planSnapshotJson)
    }

    /** AC-S4 (D-01 B): below the threshold NOTHING is stored, segments and plan included. */
    @Test fun belowThresholdStoresNothingEvenWithStagesAndPlan() = runBlocking {
        val s = snap(stages = fourStages(warm = 59, strength = 0, cool = 0), sets = emptyList())
        assertTrue(sync.syncFinished(s) is SyncOutcome.Discarded)
        assertNull(dao.session(idOf(s)))
        assertEquals(0, dao.segments(idOf(s)).size)
    }

    /** AC-S6: validity, measured time and day slices are the same with or without stages and plan. */
    @Test fun validityAndMeasuredTimeDoNotDependOnStagesOrPlan() = runBlocking {
        val withStages = snap(start = 1_000L)
        val without = snap(stages = emptyList(), plan = null, start = 2_000L, seconds = 1020)
        assertTrue(sync.syncFinished(withStages) is SyncOutcome.Committed)
        assertTrue(sync.syncFinished(without) is SyncOutcome.Committed)
        val a = dao.session(idOf(withStages))!!; val b = dao.session(idOf(without))!!
        assertEquals(
            a.copy(sessionId = "x", planSnapshotJson = null, committedAtEpochMs = 0L),
            b.copy(sessionId = "x", planSnapshotJson = null, committedAtEpochMs = 0L)
        )
        assertEquals(dao.slices(idOf(withStages)).map { it.date to it.activeSecondsOnDate }, dao.slices(idOf(without)).map { it.date to it.activeSecondsOnDate })
        assertEquals(0, dao.segments(idOf(without)).size)
    }

    /** AC-S2: replays never add rows. */
    @Test fun repeatedSyncKeepsExactlyTheSameSegments() = runBlocking {
        val s = snap()
        assertTrue(sync.syncFinished(s) is SyncOutcome.Committed)
        assertTrue(sync.syncFinished(s) is SyncOutcome.AlreadyCommitted)
        assertTrue(sync.onForeground(s) is SyncOutcome.AlreadyCommitted)
        assertEquals(3, dao.segments(idOf(s)).size)
    }

    /** AC-S3 / D3: a session saved WITHOUT enrichment, replayed WITH it, is AlreadyCommitted and unchanged. */
    @Test fun sessionSavedByAnOlderBuildIsNotAConflictWhenReplayedWithStages() = runBlocking {
        val old = snap(stages = emptyList(), plan = null, seconds = 1020)
        assertTrue(sync.syncFinished(old) is SyncOutcome.Committed)
        val before = dao.session(idOf(old))
        assertTrue(sync.syncFinished(snap()) is SyncOutcome.AlreadyCommitted)
        assertEquals("stored session row must not change", before, dao.session(idOf(old)))
        assertEquals("no enrichment is applied retroactively", 0, dao.segments(idOf(old)).size)
    }

    @Test fun enrichedSessionIsNotAConflictWhenReplayedByAnOlderSnapshot() = runBlocking {
        val enriched = snap()
        assertTrue(sync.syncFinished(enriched) is SyncOutcome.Committed)
        assertTrue(sync.syncFinished(snap(stages = emptyList(), plan = null, seconds = 1020)) is SyncOutcome.AlreadyCommitted)
        assertEquals(3, dao.segments(idOf(enriched)).size)
        assertEquals(planJson, dao.session(idOf(enriched))!!.planSnapshotJson)
    }

    /** Two DIFFERENT enrichments, or a different core, are still conflicts (nothing is merged). */
    @Test fun differentStagesOrDifferentCoreAreStillConflicts() = runBlocking {
        val first = snap()
        assertTrue(sync.syncFinished(first) is SyncOutcome.Committed)
        val otherSplit = snap(stages = fourStages(warm = 200, strength = 700))
        assertEquals(SyncOutcome.Failed("conflict"), sync.syncFinished(otherSplit))
        val otherPlan = snap(plan = """{"v":1,"routineName":"다른 루틴"}""")
        assertEquals(SyncOutcome.Failed("conflict"), sync.syncFinished(otherPlan))
        val otherCore = snap(stages = fourStages(strength = 700))
        assertEquals(SyncOutcome.Failed("conflict"), sync.syncFinished(otherCore))
        assertEquals("stored data unchanged by every conflict", 3, dao.segments(idOf(first)).size)
        assertEquals(planJson, dao.session(idOf(first))!!.planSnapshotJson)
    }

    /** AC-S7: the outbox keeps stages and plan, and entries written before they existed still read and commit. */
    @Test fun outboxRoundTripsStagesAndPlan() {
        val outbox = PendingSessionOutbox(context)
        val s = snap()
        assertTrue(outbox.enqueue(s))
        assertEquals(s, outbox.pending().single())
    }

    @Test fun outboxEntryWrittenBeforeStageDataStillReadsAndCommits() = runBlocking {
        val outbox = PendingSessionOutbox(context)
        val s = snap(stages = emptyList(), plan = null, seconds = 1020)
        val oldFormat = """{"recordDate":"2026-11-16","day":3,"startedAt":1000,"finished":true,"endedEarly":false,"seconds":1020,""" +
            """"intervals":[[100,1120]],"sets":[{"id":"legPress","n":1,"kg":"40","reps":"12"}],"attempts":0}"""
        context.getSharedPreferences(PendingSessionOutbox.NAME, Context.MODE_PRIVATE).edit().putString(idOf(s), oldFormat).commit()

        val read = outbox.pending().single()
        assertTrue(read.stages.isEmpty()); assertNull(read.planSnapshotJson)
        assertTrue(sync.drain(outbox).single() is SyncOutcome.Committed)
        assertEquals(0, outbox.size())
        assertEquals(0, dao.segments(idOf(s)).size)
    }

    @Test fun outboxEntryWithAnUnknownStageKindIsKeptAndStoresNoSegments() = runBlocking {
        val outbox = PendingSessionOutbox(context)
        val s = snap(stages = emptyList(), plan = null, seconds = 1020)
        val json = """{"recordDate":"2026-11-16","day":3,"startedAt":1000,"finished":true,"endedEarly":false,"seconds":1020,""" +
            """"intervals":[[100,1120]],"sets":[{"id":"legPress","n":1,"kg":"40","reps":"12"}],""" +
            """"stages":[{"k":"NOT_A_KIND","p":null,"a":5},{"k":"WARM_UP","p":300,"a":300}],"plan":null,"attempts":0}"""
        context.getSharedPreferences(PendingSessionOutbox.NAME, Context.MODE_PRIVATE).edit().putString(idOf(s), json).commit()

        val read = outbox.pending().single()
        assertEquals("the unknown stage is dropped, the entry itself is not lost", listOf(StageKind.WARM_UP), read.stages.map { it.kind })
        assertTrue(sync.drain(outbox).single() is SyncOutcome.Committed)
        assertEquals("300 of 1020 s does not add up, so no segments", 0, dao.segments(idOf(s)).size)
        assertEquals(1020L, dao.session(idOf(s))!!.totalActiveSeconds)
    }
}
