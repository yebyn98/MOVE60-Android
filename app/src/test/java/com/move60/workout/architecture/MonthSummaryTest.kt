package com.move60.workout.architecture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.SetRecordStore
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate
import java.time.YearMonth

/** Calendar month line (policy 7): stored sessions count, v58 and stored time are never added on one date, no calories. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MonthSummaryTest {
    private val d2 = LocalDate.parse("2026-11-02")
    private val d9 = LocalDate.parse("2026-11-09")
    private val d20 = LocalDate.parse("2026-11-20")

    @Test fun legacyOnlyMonthKeepsItsDaysAndMinutes() {
        val r = MonthSummaryCombiner.combine(mapOf(d2 to 3540L, d9 to 600L), emptyMap())
        assertEquals(MonthSummaryUi(2, (3540L + 600L) / 60), r)
    }

    @Test fun storedOnlyMonthIsCounted() {
        assertEquals(MonthSummaryUi(1, 25), MonthSummaryCombiner.combine(emptyMap(), mapOf(d9 to 1500L)))
    }

    /** The same real workout can exist in both stores, so a shared date uses the stored seconds and never the sum. */
    @Test fun aDateInBothStoresUsesTheStoredTimeAndIsNotSummed() {
        val r = MonthSummaryCombiner.combine(mapOf(d9 to 3000L, d2 to 600L), mapOf(d9 to 1500L))
        assertEquals("2 distinct days", 2, r.trainedDays)
        assertEquals("d9 = stored 1500 s (not 4500), d2 = legacy 600 s", (1500L + 600L) / 60, r.activeMinutes)
    }

    @Test fun aStoredDayCountsEvenUnderOneMinuteAndAnEmptyMonthIsEmpty() {
        assertEquals(MonthSummaryUi(1, 0), MonthSummaryCombiner.combine(emptyMap(), mapOf(d20 to 30L)))
        assertEquals(MonthSummaryUi(0, 0), MonthSummaryCombiner.combine(emptyMap(), emptyMap()))
    }

    @Test fun readerReturnsStoredSecondsPerDateOfTheMonthOnly() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        try {
            val committer = SessionCommitter(db) { 5_000L }
            suspend fun store(id: String, date: String, seconds: Long) =
                assertTrue(committer.commit(sessionBundle(id, listOf(T.run(T.at(date, 12), seconds)))) is CommitResult.Committed)
            store("a", "2026-11-09", 600); store("b", "2026-11-09", 900); store("c", "2026-11-20", 120); store("d", "2026-12-01", 700)
            val reader = RecordReader(db) { SetRecordStore(context).completedStoredSets() }
            assertEquals(mapOf(d9 to 1500L, d20 to 120L), reader.monthSeconds(YearMonth.of(2026, 11)))
            assertEquals(mapOf(LocalDate.parse("2026-12-01") to 700L), reader.monthSeconds(YearMonth.of(2026, 12)))
            assertTrue(reader.monthSeconds(YearMonth.of(2026, 10)).isEmpty())
        } finally { db.close() }
    }
}
