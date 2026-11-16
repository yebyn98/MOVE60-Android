package com.move60.workout.architecture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.WorkoutHistory
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** MY tab "누적 운동한 날": v58 dates and stored-session dates count once each, never added together on one date. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MyGrowthTest {
    private lateinit var context: Context
    private lateinit var db: WorkoutDatabase
    private fun d(s: String) = LocalDate.parse(s)

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("move60_workout_history_v1", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }

    @Test fun aDateInBothSourcesCountsOnce() {
        assertEquals(3, MyGrowth.cumulativeDays(setOf(d("2026-11-02"), d("2026-11-09")), setOf(d("2026-11-09"), d("2026-11-10"))))
        assertEquals(0, MyGrowth.cumulativeDays(emptySet(), emptySet()))
    }

    @Test fun legacyDatesNeedAtLeastOneRecordedMinuteAndIgnoreForeignKeys() {
        val history = WorkoutHistory(context)
        history.addActiveSeconds(d("2026-11-02"), 3540)
        history.addActiveSeconds(d("2026-11-03"), 59)     // under one minute: not a trained day (same rule as the calendar)
        context.getSharedPreferences("move60_workout_history_v1", Context.MODE_PRIVATE).edit().putLong("not-a-date", 9999).commit()
        assertEquals(setOf(d("2026-11-02")), history.trainedDates())
    }

    @Test fun storedSessionDatesAreListedOnceEach() = runBlocking {
        val a = sessionBundle("s1", listOf(T.run(T.at("2026-11-09", 20, 35), 600)))
        val b = sessionBundle("s2", listOf(T.run(T.at("2026-11-09", 22, 0), 600)))
        assertTrue(SessionCommitter(db) { T.ms("2026-11-16", 12) }.commit(a) is CommitResult.Committed)
        assertTrue(SessionCommitter(db) { T.ms("2026-11-16", 12) }.commit(b) is CommitResult.Committed)
        assertEquals(listOf("2026-11-09"), db.routiveDao().allSessionDates())
    }
}
