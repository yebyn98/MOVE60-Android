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

/** 최초 운동 설정: shown only to a user with no history and no space; nothing is chosen for the user. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class FirstRunTest {
    private lateinit var context: Context
    private lateinit var db: WorkoutDatabase
    private lateinit var store: FirstRunStore
    private lateinit var history: WorkoutHistory

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(FirstRunStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
        context.getSharedPreferences("move60_workout_history_v1", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        store = FirstRunStore(context); history = WorkoutHistory(context)
    }
    @After fun close() { db.close() }

    private val gate get() = FirstRunGate(db, history, store)

    @Test fun policyTruthTable() {
        assertTrue(FirstRunPolicy.needsSetup(done = false, hasHistory = false, hasSpace = false))
        assertFalse(FirstRunPolicy.needsSetup(done = true, hasHistory = false, hasSpace = false))
        assertFalse("an existing user is never forced", FirstRunPolicy.needsSetup(done = false, hasHistory = true, hasSpace = false))
        assertFalse(FirstRunPolicy.needsSetup(done = false, hasHistory = false, hasSpace = true))
        assertTrue("forced (test/debug) always shows it", FirstRunPolicy.needsSetup(done = true, hasHistory = true, hasSpace = true, forced = true))
    }

    @Test fun aCleanInstallNeedsTheSetupAndNothingIsPreselected() = runBlocking {
        assertTrue(gate.needsSetup(forced = false))
        assertNull("the purpose is never chosen for the user", store.purpose)
        assertFalse(store.done)
    }

    @Test fun v58HistoryAStoredSessionOrASpaceMeansAnExistingUser() = runBlocking {
        history.addActiveSeconds(LocalDate.parse("2026-11-02"), 3540)
        assertFalse("v58 history", gate.needsSetup(false))
        context.getSharedPreferences("move60_workout_history_v1", Context.MODE_PRIVATE).edit().clear().commit()
        assertTrue(gate.needsSetup(false))

        assertTrue(SessionCommitter(db) { T.ms("2026-11-16", 12) }.commit(sessionBundle("s1", listOf(T.run(T.at("2026-11-09", 20, 35), 600)))) is CommitResult.Committed)
        assertFalse("a stored session", gate.needsSetup(false))
    }

    @Test fun aRegisteredSpaceOrADoneFlagMeansNoSetup() = runBlocking {
        assertTrue(gate.needsSetup(false))
        SpaceService(db).create("home", SpaceKind.HOME, "집")
        assertFalse(gate.needsSetup(false))
        db.close()
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        assertTrue("fresh db again", FirstRunGate(db, history, store).needsSetup(false))
        store.markDone()
        assertFalse(FirstRunGate(db, history, store).needsSetup(false))
        assertTrue("forced still shows it", FirstRunGate(db, history, store).needsSetup(true))
    }

    @Test fun purposeRoundTripsAndAnUnknownStoredKeyReadsAsNotChosen() {
        store.savePurpose(WorkoutPurpose.STRENGTH)
        assertEquals(WorkoutPurpose.STRENGTH, FirstRunStore(context).purpose)
        context.getSharedPreferences(FirstRunStore.FILE, Context.MODE_PRIVATE).edit().putString(FirstRunStore.KEY_PURPOSE, "LOSE_WEIGHT_FAST").commit()
        assertNull(FirstRunStore(context).purpose)
    }

    @Test fun thePurposeChoicesAreTheFourFigmaOptionsInOrder() {
        assertEquals(listOf("체중 관리", "근력 향상", "체력 향상", "운동 습관"), WorkoutPurpose.entries.map { it.label })
        assertEquals("내 페이스로 시작하는 꾸준한 변화", WorkoutPurpose.WEIGHT_MANAGEMENT.supporting)
    }
}
