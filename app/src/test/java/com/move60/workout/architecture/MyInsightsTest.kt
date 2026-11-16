package com.move60.workout.architecture

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDate

/** MY tab trend, milestones and the numbers behind them (Figma 10, 68): real records only, never added twice, never invented. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MyInsightsTest {
    private lateinit var db: WorkoutDatabase
    private var nowMs = T.ms("2026-11-16", 12)
    private fun d(s: String) = LocalDate.parse(s)

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkoutDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }

    @Test fun weeksAreMondayBasedOldestFirstAndEndWithTheCurrentWeek() {
        // 2026-11-16 is a Monday.
        assertEquals(d("2026-11-16"), MyInsights.weekStart(d("2026-11-22")))
        assertEquals(d("2026-11-16"), MyInsights.weekStart(d("2026-11-16")))
        val w = MyInsights.weekly(d("2026-11-18"), 4, emptyMap(), emptyMap())
        assertEquals(listOf("2026-10-26", "2026-11-02", "2026-11-09", "2026-11-16"), w.map { it.weekStart.toString() })
        assertTrue("no records means 0 days and 0 minutes per week", w.all { it.days == 0 && it.minutes == 0L })
    }

    @Test fun aDateWithBothSourcesCountsOnceWithTheStoredSecondsAndLegacyOnlyDatesKeepTheirs() {
        val legacy = mapOf(d("2026-11-09") to 3000L, d("2026-11-10") to 600L)
        val stored = mapOf(d("2026-11-09") to 1200L, d("2026-11-17") to 300L)
        val w = MyInsights.weekly(d("2026-11-18"), 2, legacy, stored)
        assertEquals(WeekPoint(d("2026-11-09"), 2, (1200L + 600L) / 60), w[0])   // 11-09 stored wins, 11-10 legacy
        assertEquals(WeekPoint(d("2026-11-16"), 1, 5L), w[1])
    }

    @Test fun milestonesFollowTheFigmaThresholdsAndOmitWhatCouldNotBeRead() {
        fun achieved(days: Int?, challenges: Int?) = MyInsights.milestones(days, challenges).filter { it.achieved }.map { it.id }
        assertEquals(emptyList<String>(), achieved(0, 0))
        assertEquals(listOf("first"), achieved(1, 0))
        assertEquals(listOf("first", "days20"), achieved(20, 0))
        assertEquals(listOf("first", "challenge1"), achieved(19, 1))
        assertEquals(listOf("challenge1"), MyInsights.milestones(null, 2).map { it.id })
        assertEquals(listOf("first", "days20"), MyInsights.milestones(3, null).map { it.id })
    }

    @Test fun labelsReadFromThisWeekBackwards() {
        assertEquals(listOf("3주 전", "2주 전", "지난 주", "이번 주"), (0..3).map { MyInsights.weekLabel(it, 4) })
        assertEquals(listOf("지난 주", "이번 주"), (0..1).map { MyInsights.weekLabel(it, 2) })
    }

    @Test fun counterQueriesCountStoredSessionsCompletedChallengesAndFindTheLatestSession() = runBlocking {
        val dao = db.routiveDao()
        assertEquals(0, dao.sessionCount()); assertEquals(0, dao.completedChallengeCount()); assertNull(dao.latestSessionId())
        val committer = SessionCommitter(db) { nowMs }
        assertTrue(committer.commit(sessionBundle("old", listOf(T.run(T.at("2026-11-09", 20, 35), 600)))) is CommitResult.Committed)
        assertTrue(committer.commit(sessionBundle("new", listOf(T.run(T.at("2026-11-16", 12), 720)))) is CommitResult.Committed)
        assertEquals(2, dao.sessionCount())
        assertEquals("the session that started last", "new", dao.latestSessionId())

        val challenges = ChallengeService(db) { nowMs }
        assertTrue(challenges.create(challengeOf(start = "2026-11-16", end = "2026-11-16", target = 1)) is ChallengeResult.Created)
        assertEquals("an ACTIVE challenge is not completed", 0, dao.completedChallengeCount())
        nowMs = T.ms("2026-11-17", 0, 0, 1)
        assertEquals(1, challenges.settleDue())
        assertEquals(1, dao.completedChallengeCount())
    }
}
