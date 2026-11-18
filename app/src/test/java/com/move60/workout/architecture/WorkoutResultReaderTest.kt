package com.move60.workout.architecture

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class WorkoutResultReaderTest {
    private lateinit var db: WorkoutDatabase
    private var nowMs = T.ms("2026-11-15", 12)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkoutDatabase::class.java)
            .allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }

    private val reader get() = WorkoutResultReader(db) { 8 }

    @Test fun parsesTheTimerSessionId() {
        assertEquals(LocalDate.parse("2026-11-15") to 1700000000000L, reader.parse("timer-2026-11-15-d5-1700000000000"))
        assertNull(reader.parse("not-a-session"))
    }

    @Test fun missingSessionIsSavingThenFailedAfterGivingUp() = runBlocking {
        val id = "timer-2026-11-15-d5-1700000000000"
        assertEquals(ResultSaveState.SAVING, reader.read(id, giveUp = false).saveState)
        val failed = reader.read(id, giveUp = true)
        assertEquals(ResultSaveState.FAILED, failed.saveState); assertNull(failed.completion)
    }

    @Test fun storedPartialSessionReportsMeasuredValuesAndChallengeCredit() = runBlocking {
        val committer = SessionCommitter(db) { nowMs }
        val id = "timer-2026-11-15-d5-1700000000000"
        committer.challenges.create(challengeOf(start = "2026-11-15", end = "2026-11-21"))
        nowMs = T.ms("2026-11-15", 10, 30)
        committer.commit(sessionBundle(id, listOf(T.run(T.at("2026-11-15", 10), 11 * 60)), completion = SessionCompletion.PARTIAL))
        val r = reader.read(id, giveUp = false)
        assertEquals(ResultSaveState.SAVED, r.saveState)
        assertEquals(SessionCompletion.PARTIAL, r.completion)
        assertEquals(660L, r.activeSeconds); assertEquals(8, r.completedSets)
        assertEquals(true, r.challengeLine!!.contains("오늘 운동 인정"))
    }
}
