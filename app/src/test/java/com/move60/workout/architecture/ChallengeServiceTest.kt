package com.move60.workout.architecture

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.Instant

/** Approved-policy acceptance criteria AC-01..AC-13 (C-20261115-01..04, D-01 B) against a real Room DB. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ChallengeServiceTest {
    private lateinit var db: WorkoutDatabase
    private lateinit var committer: SessionCommitter
    private lateinit var service: ChallengeService
    private var nowMs = T.ms("2026-11-15", 12)
    private val dao get() = db.routiveDao()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkoutDatabase::class.java)
            .allowMainThreadQueries().build()
        committer = SessionCommitter(db) { nowMs }
        service = ChallengeService(db) { nowMs }
    }
    @After fun close() { db.close() }

    private fun at(date: String, h: Int, m: Int = 0, s: Int = 0) { nowMs = T.ms(date, h, m, s) }
    private suspend fun store(id: String, date: String, h: Int, minutes: Long, zone: String = T.SEOUL) =
        committer.commit(sessionBundle(id, listOf(T.run(T.at(date, h, 0, 0, zone), minutes * 60)), zone = zone))
    private suspend fun status(id: String = "c1") = dao.challenge(id)!!.status
    private fun snapshot(c: ChallengeEntity) = JSONObject(c.endSnapshotJson!!)

    @Test fun challengePeriodHasInclusiveEndAndCannotCreateZeroDayPeriod() {
        val d = java.time.LocalDate.parse("2026-11-17")
        assertEquals(d, ChallengeService.inclusiveEndDate(d, 1))
        assertEquals(java.time.LocalDate.parse("2026-11-23"), ChallengeService.inclusiveEndDate(d, 7))
        val dateInSeoul = Instant.parse("2026-11-17T15:30:00Z").atZone(java.time.ZoneId.of("Asia/Seoul")).toLocalDate()
        val dateInUtc = Instant.parse("2026-11-17T15:30:00Z").atZone(java.time.ZoneId.of("UTC")).toLocalDate()
        assertEquals(java.time.LocalDate.parse("2026-11-18"), dateInSeoul)
        assertEquals(java.time.LocalDate.parse("2026-11-17"), dateInUtc)
        assertEquals(java.time.LocalDate.parse("2026-11-24"), ChallengeService.inclusiveEndDate(dateInSeoul, 7))
        try {
            ChallengeService.inclusiveEndDate(d, 0)
            org.junit.Assert.fail("zero-day challenge must be rejected")
        } catch (_: IllegalArgumentException) {
            // expected
        }
    }

    @Test fun ac01_validSessionEarlierOnStartDayIsCreditedRetroactively() = runBlocking {
        at("2026-11-15", 9, 30); assertTrue(store("morning", "2026-11-15", 9, 12) is CommitResult.Committed)
        at("2026-11-15", 14)
        val r = service.create(challengeOf(start = "2026-11-15", end = "2026-11-21"))
        assertEquals(ChallengeResult.Created("c1", 1), r)
        assertEquals(720L, dao.credit("c1", "2026-11-15")!!.activeSeconds)
        assertEquals(true, dao.credit("c1", "2026-11-15")!!.credited)
        assertEquals(ChallengeResult.AlreadyExists("c1"), service.create(challengeOf(start = "2026-11-15", end = "2026-11-21")))   // idempotent
        assertEquals(1, dao.credits("c1").size)
    }

    @Test fun ac02_workoutBeforeTheStartDateIsNeverCredited() = runBlocking {
        at("2026-11-14", 20); store("yesterday", "2026-11-14", 19, 20)
        at("2026-11-15", 9)
        assertEquals(ChallengeResult.Created("c1", 0), service.create(challengeOf(start = "2026-11-15", end = "2026-11-21")))
        assertNull(dao.credit("c1", "2026-11-14"))
        assertTrue(dao.credits("c1").none { it.credited })
    }

    @Test fun ac03_fivePlusFiveMinutesWithSaveRetryIsOneDayOf600sWithoutDuplicates() = runBlocking {
        at("2026-11-15", 8); service.create(challengeOf(start = "2026-11-15", end = "2026-11-21"))
        at("2026-11-15", 9, 10); store("a", "2026-11-15", 9, 5)
        assertEquals(false, dao.credit("c1", "2026-11-15")!!.credited)       // 300s alone is not a day
        at("2026-11-15", 18, 10); store("b", "2026-11-15", 18, 5)
        assertTrue(committer.commit(sessionBundle("b", listOf(T.run(T.at("2026-11-15", 18), 300)))) is CommitResult.AlreadyCommitted)
        val c = dao.credit("c1", "2026-11-15")!!
        assertEquals(600L, c.activeSeconds); assertEquals(true, c.credited); assertEquals(2, c.validSessionCount)
        assertEquals(1, dao.credits("c1").count { it.credited })
    }

    @Test fun ac04_extraWorkoutAndCorrectionUpdateTheActiveDayButStillCountOneDay() = runBlocking {
        at("2026-11-15", 8); service.create(challengeOf(start = "2026-11-15", end = "2026-11-21"))
        at("2026-11-15", 9, 30); store("s1", "2026-11-15", 9, 12)
        at("2026-11-15", 20, 30); store("s2", "2026-11-15", 20, 20)
        assertEquals(720L + 1200L, dao.credit("c1", "2026-11-15")!!.activeSeconds)
        assertEquals(1, dao.credits("c1").count { it.credited })
        // Correction of stored evidence while ACTIVE: re-derivation refreshes the day (P-21).
        db.openHelper.writableDatabase.execSQL("UPDATE session_active_intervals SET endEpochSec = startEpochSec + 300 WHERE sessionId='s2'")
        assertEquals(0, service.refreshActive())   // returns number settled, not refreshed
        assertEquals(720L + 300L, dao.credit("c1", "2026-11-15")!!.activeSeconds)
        assertEquals(true, dao.credit("c1", "2026-11-15")!!.credited)
    }

    private suspend fun threeDays(end: String) {
        at("2026-11-13", 8); service.create(challengeOf(start = "2026-11-13", end = end, target = 3))
        for (d in listOf("2026-11-13", "2026-11-14", "2026-11-15")) { at(d, 10, 30); store("s-$d", d, 10, 15) }
    }

    @Test fun ac05_targetDayStaysActiveUntilItClosesThenCompletesWithFrozenSnapshot() = runBlocking {
        threeDays("2026-11-20")
        at("2026-11-15", 23, 59, 59); service.settleDue(); assertEquals("ACTIVE", status())
        // Totals keep updating on the target day itself.
        at("2026-11-15", 23, 59, 59); store("late", "2026-11-15", 22, 10)
        at("2026-11-16", 0, 0, 0); assertEquals(1, service.settleDue())
        val c = dao.challenge("c1")!!
        assertEquals("COMPLETE", c.status); assertEquals(T.ms("2026-11-16", 0), c.endedAtEpochMs)
        val snap = snapshot(c)
        assertEquals(3, snap.getInt("creditedDays")); assertEquals("2026-11-15", snap.getString("targetReachedDate"))
        assertEquals(900L + 600L, snap.getJSONArray("days").let { a -> (0 until a.length()).map { a.getJSONObject(it) }.first { it.getString("date") == "2026-11-15" }.getLong("activeSeconds") })
    }

    @Test fun ac06_completeBeatsExpiredWhenTheTargetDayIsTheEndDate() = runBlocking {
        threeDays("2026-11-15")
        at("2026-11-15", 23, 59, 59); service.settleDue(); assertEquals("ACTIVE", status())
        at("2026-11-16", 0, 0, 0); service.settleDue(); assertEquals("COMPLETE", status())
        at("2026-11-30", 9); service.settleDue(); assertEquals("COMPLETE", status())
    }

    @Test fun ac07_missedTargetExpiresAtEndDatePlusOneMidnightKeepingEarnedDays() = runBlocking {
        at("2026-11-13", 8); service.create(challengeOf(start = "2026-11-13", end = "2026-11-15", target = 3))
        for (d in listOf("2026-11-13", "2026-11-15")) { at(d, 10, 30); store("s-$d", d, 10, 15) }
        at("2026-11-15", 23, 59, 59); service.settleDue(); assertEquals("ACTIVE", status())
        at("2026-11-16", 0, 0, 0); service.settleDue()
        val c = dao.challenge("c1")!!
        assertEquals("EXPIRED", c.status); assertEquals(T.ms("2026-11-16", 0), c.endedAtEpochMs)
        assertEquals(2, snapshot(c).getInt("creditedDays"))
    }

    @Test fun ac08_challengeZoneDecidesTheDateNotTheSessionOrDeviceZone() = runBlocking {
        val ny = "America/New_York"
        nowMs = T.ms("2026-11-15", 9, zone = ny)
        service.create(challengeOf(start = "2026-11-15", end = "2026-11-21", zone = ny))
        // Recorded in Seoul at 11-16 08:00 KST == 11-15 18:00 New York.
        nowMs = T.ms("2026-11-15", 19, zone = ny)
        assertTrue(store("kst", "2026-11-16", 8, 20, zone = T.SEOUL) is CommitResult.Committed)
        assertEquals(1200L, dao.credit("c1", "2026-11-15")!!.activeSeconds)
        assertNull(dao.credit("c1", "2026-11-16"))
        assertEquals("2026-11-16", dao.slices("kst").single().date)   // session-zone slice is display only
    }

    @Test fun ac08_dstFallBackDayKeepsEverySecond() = runBlocking {
        val ny = "America/New_York"
        nowMs = T.ms("2026-11-01", 3, zone = ny)       // after the 25-hour day's repeated hour
        service.create(challengeOf(start = "2026-11-01", end = "2026-11-03", zone = ny))
        val s = Instant.parse("2026-11-01T04:50:00Z").epochSecond; val e = Instant.parse("2026-11-01T06:10:00Z").epochSecond  // 00:50 EDT -> 01:10 EST
        assertTrue(committer.commit(sessionBundle("dst", listOf(s to e), zone = ny)) is CommitResult.Committed)
        assertEquals(4800L, dao.credit("c1", "2026-11-01")!!.activeSeconds)
    }

    @Test fun ac09_terminalSnapshotSurvivesLaterCorrectionsAndLateSessions() = runBlocking {
        threeDays("2026-11-15"); at("2026-11-16", 0, 0, 0); service.settleDue()
        val frozen = dao.challenge("c1")!!
        db.openHelper.writableDatabase.execSQL("DELETE FROM session_active_intervals WHERE sessionId='s-2026-11-14'")
        at("2026-11-17", 10); store("late", "2026-11-15", 23, 30)     // saved after the boundary
        assertEquals(0, service.refreshActive()); assertEquals(0, service.settleDue())
        assertEquals(frozen, dao.challenge("c1"))
        assertEquals(frozen.endSnapshotJson, dao.challenge("c1")!!.endSnapshotJson)
    }

    @Test fun ac10_legacyDailyWorkoutStaysOnTheCalendarButNeverBecomesASessionOrCredit() = runBlocking {
        db.workoutDao().upsertDay(DailyWorkoutEntity("2026-11-15", 1200, 90.0, 1L))
        at("2026-11-15", 9)
        assertEquals(ChallengeResult.Created("c1", 0), service.create(challengeOf(start = "2026-11-15", end = "2026-11-21")))
        assertTrue(dao.credits("c1").none { it.credited })
        val h = DayHistoryReader(db).day("2026-11-15")
        assertEquals(1200L, h.legacyActiveSeconds); assertEquals(0L, h.sessionActiveSeconds)
        assertEquals(0, db.query("SELECT COUNT(*) FROM sessions", null).use { it.moveToFirst(); it.getInt(0) })
        assertEquals(1200L, db.workoutDao().day("2026-11-15")!!.activeSeconds)
    }

    @Test fun ac11_legacyAndNewSessionOnTheSameDateStaySeparateAndAreNeverSummed() = runBlocking {
        db.workoutDao().upsertDay(DailyWorkoutEntity("2026-11-15", 1200, 90.0, 1L))
        at("2026-11-15", 8); service.create(challengeOf(start = "2026-11-15", end = "2026-11-21"))
        at("2026-11-15", 11); store("new", "2026-11-15", 10, 11)
        val h = DayHistoryReader(db).day("2026-11-15")
        assertEquals(1200L, h.legacyActiveSeconds); assertEquals(660L, h.sessionActiveSeconds)
        assertEquals(660L, dao.credit("c1", "2026-11-15")!!.activeSeconds)      // not 1860
        assertEquals(1200L, db.workoutDao().day("2026-11-15")!!.activeSeconds)  // legacy untouched
    }

    @Test fun ac13_offlinePastMidnightSettlesOnceWithTheEffectiveBoundaryAndRerunIsIdempotent() = runBlocking {
        at("2026-11-15", 8); service.create(challengeOf(start = "2026-11-15", end = "2026-11-21", target = 1))
        at("2026-11-15", 10, 30); store("s", "2026-11-15", 10, 15)
        assertEquals("ACTIVE", status())
        at("2026-11-18", 7)                         // app was closed over midnight
        assertEquals(1, service.settleDue())
        val first = dao.challenge("c1")!!
        assertEquals("COMPLETE", first.status); assertEquals(T.ms("2026-11-16", 0), first.endedAtEpochMs)
        assertEquals(0, service.settleDue()); assertEquals(0, service.settleDue())
        assertEquals(first, dao.challenge("c1"))
    }

    @Test fun ac13_failedSaveNeverChangesChallengeAndRetryAppliesOnce() = runBlocking {
        at("2026-11-15", 8); service.create(challengeOf(start = "2026-11-15", end = "2026-11-21"))
        at("2026-11-15", 10, 30)
        committer.commit(sessionBundle("dupToken", listOf(T.run(T.at("2026-11-15", 10), 900)), legacyToken = 5L))
        val before = dao.credit("c1", "2026-11-15")
        val failing = sessionBundle("other", listOf(T.run(T.at("2026-11-15", 15), 900)), legacyToken = 5L)
        assertTrue(committer.commit(failing) is CommitResult.Failed)
        assertEquals(before, dao.credit("c1", "2026-11-15"))
        val ok = failing.copy(session = failing.session.copy(legacyToken = 6L))
        assertTrue(committer.commit(ok) is CommitResult.Committed)
        assertTrue(committer.commit(ok) is CommitResult.AlreadyCommitted)
        assertEquals(1800L, dao.credit("c1", "2026-11-15")!!.activeSeconds)
    }

    // ---- guards implied by the approved policy (P-13, unapproved exceptions stay rejected) ----
    @Test fun onlyOneActiveChallengeAtATime() = runBlocking {
        service.create(challengeOf(id = "c1", start = "2026-11-15", end = "2026-11-21"))
        val r = service.create(challengeOf(id = "c2", start = "2026-11-15", end = "2026-11-22"))
        assertTrue(r is ChallengeResult.Rejected && r.reason == ChallengeReject.ACTIVE_CHALLENGE_EXISTS)
        assertNull(dao.challenge("c2"))
    }

    @Test fun pastOrFutureStartDatesAreNotApprovedAndAreRejected() = runBlocking {
        for (start in listOf("2026-11-14", "2026-11-16")) {
            val r = service.create(challengeOf(id = "x$start", start = start, end = "2026-11-30"))
            assertTrue("$start -> $r", r is ChallengeResult.Rejected && r.reason == ChallengeReject.START_DATE_NOT_CREATION_DAY)
        }
        assertTrue(dao.activeChallenges().isEmpty())
    }

    @Test fun invalidDefinitionsAreRejected() = runBlocking {
        val bad = listOf(
            challengeOf(id = "z", start = "2026-11-15", end = "2026-11-21", zone = "Mars/Base") to ChallengeReject.INVALID_ZONE,
            challengeOf(id = "z", start = "2026-11-15", end = "2026-11-14") to ChallengeReject.INVALID_DATE,
            challengeOf(id = "z", start = "2026-11-15", end = "2026-11-21", target = 0) to ChallengeReject.INVALID_FIELD,
            challengeOf(id = "z", start = "2026-11-15", end = "2026-11-21", name = " ".repeat(3)) to ChallengeReject.INVALID_FIELD,
            challengeOf(id = "z", start = "2026-11-15", end = "2026-11-21", name = "x".repeat(ChallengeService.MAX_NAME + 1)) to ChallengeReject.INVALID_FIELD
        )
        for ((c, reason) in bad) { val r = service.create(c); assertTrue("$reason -> $r", r is ChallengeResult.Rejected && r.reason == reason) }
    }

    @Test fun abandonIsTerminalAndKeepsEarnedDays() = runBlocking {
        at("2026-11-15", 9); service.create(challengeOf(start = "2026-11-15", end = "2026-11-21"))
        at("2026-11-15", 10, 30); store("s", "2026-11-15", 10, 15)
        assertEquals(AbandonResult.Abandoned("c1"), service.abandon("c1", confirmed = true))
        assertEquals("ABANDONED", status())
        assertEquals(1, snapshot(dao.challenge("c1")!!).getInt("creditedDays"))
        assertTrue(service.abandon("c1", confirmed = true) is AbandonResult.AlreadyEnded)
        assertEquals(AbandonResult.NotFound, service.abandon("nope", confirmed = true))
    }

    // ---- confirmed 2026-11-15: target-day cap, abandon snapshot, late save, PARTIAL credit ----
    @Test fun targetDaysMustFitTheInclusivePeriod() = runBlocking {
        val cases = listOf(                                   // (start, end, target, allowed)
            Triple("2026-11-15" to "2026-11-17", 3, true), Triple("2026-11-15" to "2026-11-17", 4, false),
            Triple("2026-11-15" to "2026-11-15", 1, true), Triple("2026-11-15" to "2026-11-15", 2, false)
        )
        for ((i, c) in cases.withIndex()) {
            val (period, target, allowed) = c
            val r = service.create(challengeOf(id = "t$i", start = period.first, end = period.second, target = target))
            if (allowed) { assertTrue("$c -> $r", r is ChallengeResult.Created); service.abandon("t$i", confirmed = true) }
            else assertTrue("$c -> $r", r is ChallengeResult.Rejected && r.reason == ChallengeReject.TARGET_EXCEEDS_PERIOD)
        }
        assertNull(dao.challenge("t1")); assertNull(dao.challenge("t3"))
    }

    @Test fun abandonNeedsConfirmationAndNothingChangesWithout() = runBlocking {
        service.create(challengeOf(start = "2026-11-15", end = "2026-11-21"))
        assertEquals(AbandonResult.NotConfirmed, service.abandon("c1", confirmed = false))
        assertEquals("ACTIVE", status())
    }

    @Test fun abandonSnapshotKeepsProgressAndLaterWorkoutNeverChangesIt() = runBlocking {
        db.workoutDao().upsertDay(DailyWorkoutEntity("2026-11-15", 1200, 90.0, 1L))
        at("2026-11-15", 8); service.create(challengeOf(start = "2026-11-15", end = "2026-11-21", target = 3))
        at("2026-11-15", 10, 30); store("before", "2026-11-15", 10, 15)
        at("2026-11-15", 21); assertEquals(AbandonResult.Abandoned("c1"), service.abandon("c1", confirmed = true))
        val ended = dao.challenge("c1")!!
        val snap = snapshot(ended)
        assertEquals("ABANDONED", ended.status); assertEquals(T.ms("2026-11-15", 21), ended.endedAtEpochMs)
        assertEquals(1, snap.getInt("creditedDays")); assertEquals(3, snap.getInt("targetDays"))
        assertEquals("2026-11-15", snap.getString("startDate")); assertEquals("2026-11-21", snap.getString("endDate"))
        assertEquals(1.0 / 3.0, snap.getDouble("achievementRate"), 1e-9); assertEquals(T.at("2026-11-15", 21), snap.getLong("endedAtEpochSec"))
        // Past records are kept and later workouts do not touch the ended challenge.
        assertEquals(1200L, db.workoutDao().day("2026-11-15")!!.activeSeconds); assertNotNullSession("before")
        at("2026-11-16", 10); val later = store("after", "2026-11-16", 9, 30)
        assertTrue(later is CommitResult.Committed && later.touchedChallenges.isEmpty())
        assertEquals(ended, dao.challenge("c1")); assertNotNullSession("after")
    }

    private suspend fun assertNotNullSession(id: String) = assertTrue("session $id kept", dao.session(id) != null)

    @Test fun lateSavedSessionIsKeptAsARecordButDoesNotTouchTheEndedChallenge() = runBlocking {
        threeDays("2026-11-15"); at("2026-11-16", 0, 0, 0); service.settleDue()
        val frozen = dao.challenge("c1")!!
        at("2026-11-17", 9)
        val r = store("late", "2026-11-15", 22, 20)          // performed before the boundary, first saved after it
        assertTrue("got $r", r is CommitResult.Committed && r.touchedChallenges.isEmpty())
        assertEquals(1200L, dao.session("late")!!.totalActiveSeconds)
        assertEquals(frozen, dao.challenge("c1")); assertEquals("COMPLETE", status())
    }

    @Test fun partialSessionCountsForTheChallengeWhenItIsValid() = runBlocking {
        at("2026-11-15", 8); service.create(challengeOf(start = "2026-11-15", end = "2026-11-21"))
        at("2026-11-15", 10, 30)
        committer.commit(sessionBundle("partial", listOf(T.run(T.at("2026-11-15", 10), 11 * 60)), completion = SessionCompletion.PARTIAL))
        assertEquals(true, dao.credit("c1", "2026-11-15")!!.credited)
        assertEquals(660L, dao.credit("c1", "2026-11-15")!!.activeSeconds)
    }

    @Test fun renameWorksOnlyWhileActiveAndValidatesLength() = runBlocking {
        at("2026-11-15", 8); service.create(challengeOf(start = "2026-11-15", end = "2026-11-21"))
        assertTrue(service.rename("c1", "  새 이름  "))
        assertEquals("새 이름", dao.challenge("c1")!!.name)
        assertEquals(false, service.rename("c1", "   "))
        assertEquals(false, service.rename("c1", "a".repeat(ChallengeService.MAX_NAME + 1)))
        assertEquals("새 이름", dao.challenge("c1")!!.name)
        service.abandon("c1", confirmed = true)
        assertEquals(false, service.rename("c1", "종료 후 변경"))      // ended snapshot stays frozen
        assertEquals("새 이름", dao.challenge("c1")!!.name)
    }

    @Test fun activeAndEndedListsSeparateTheChallenges() = runBlocking {
        at("2026-11-15", 8); service.create(challengeOf(id = "old", start = "2026-11-15", end = "2026-11-21"))
        assertEquals("old", service.active()!!.challengeId); assertTrue(service.ended().isEmpty())
        at("2026-11-15", 9); service.abandon("old", confirmed = true)
        assertNull(service.active()); assertEquals(listOf("old"), service.ended().map { it.challengeId })
        assertEquals("ABANDONED", service.ended().single().status)
        assertEquals(ChallengeResult.Created("new", 0), service.create(challengeOf(id = "new", start = "2026-11-15", end = "2026-11-21")))
        assertEquals("new", service.active()!!.challengeId)
    }
}
