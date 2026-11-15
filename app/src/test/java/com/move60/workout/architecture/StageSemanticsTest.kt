package com.move60.workout.architecture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.SessionStore
import com.move60.workout.WorkoutData
import com.move60.workout.domain.StageKind
import com.move60.workout.domain.StructureVersion
import com.move60.workout.domain.WorkoutStructures
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** P0-2: stage meaning follows the session's structure; a structure this engine cannot run is never advanced or rewritten. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class StageSemanticsTest {
    private lateinit var context: Context
    private val prefsName = "move60_native_sessions_v8"
    @Before fun init() { context = ApplicationProvider.getApplicationContext() }
    private fun store() = SessionStore(context, WorkoutData(context))
    private fun rows() = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE).all.filter { it.key.startsWith("daily_") }

    @Test fun strengthAndLastStageDifferPerStructure() {
        assertEquals(2, WorkoutStructures.strengthIndex(StructureVersion.LEGACY_5))
        assertEquals(1, WorkoutStructures.strengthIndex(StructureVersion.ROUTIVE_4))
        assertEquals(4, WorkoutStructures.lastIndex(StructureVersion.LEGACY_5))
        assertEquals(3, WorkoutStructures.lastIndex(StructureVersion.ROUTIVE_4))
        assertEquals(StageKind.CARDIO, WorkoutStructures.kindAt(StructureVersion.ROUTIVE_4, 2))   // 5-stage code would call index 2 strength
        assertEquals(StageKind.CARDIO, WorkoutStructures.kindAt(StructureVersion.LEGACY_5, 3))
        assertNull(WorkoutStructures.kindAt(StructureVersion.ROUTIVE_4, 4))
    }

    @Test fun legacySessionKeepsItsExistingStageMeaning() {
        val s = store()
        assertTrue(s.engineSupported)
        assertEquals(4, s.lastStageIndex)
        val kinds = (0..4).map { s.jumpTo(it); s.kindOf(it) }
        assertEquals(listOf(StageKind.WARM_UP, StageKind.CARDIO, StageKind.STRENGTH, StageKind.CARDIO, StageKind.COOL_DOWN), kinds)
        s.jumpTo(2); assertTrue(s.isStrengthStage)
        s.jumpTo(3); assertFalse(s.isStrengthStage)
    }

    @Test fun newerStructureSlotIsReadAsItsOwnStagesAndNeverAdvancedByThisEngine() {
        val s = store(); s.start(); s.pause()
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        rows().forEach { (k, v) -> edit.putString(k, JSONObject(v as String).put("structureVersion", "ROUTIVE_4").put("stage", 1).toString()) }
        edit.commit()
        val before = rows()

        val r = store()
        assertEquals(StructureVersion.ROUTIVE_4, r.structure)
        assertEquals(1, r.stage)
        assertTrue("index 1 is strength in ROUTIVE_4", r.isStrengthStage)
        assertEquals(3, r.lastStageIndex)
        assertFalse(r.engineSupported)

        r.nextStage(); r.jumpTo(2); r.jumpTo(0); r.start()
        assertEquals("slot must be neither advanced nor rewritten", before, rows())
        val again = store()
        assertEquals(1, again.stage); assertEquals(StructureVersion.ROUTIVE_4, again.structure); assertFalse(again.running)
    }

    @Test fun finishedNewerStructureSlotCanStillBeReplacedByANewLegacySession() {
        val s = store(); s.start(); s.pause()
        val prefs = context.getSharedPreferences(prefsName, Context.MODE_PRIVATE)
        val edit = prefs.edit()
        rows().forEach { (k, v) -> edit.putString(k, JSONObject(v as String).put("structureVersion", "ROUTIVE_4").put("finished", true).toString()) }
        edit.commit()
        val r = store()
        r.start()                                            // finished session is archived, then a fresh one starts
        assertEquals(StructureVersion.LEGACY_5, r.structure)
        assertTrue(r.running)
    }
}
