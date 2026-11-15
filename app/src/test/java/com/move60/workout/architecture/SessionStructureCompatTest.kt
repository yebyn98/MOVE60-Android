package com.move60.workout.architecture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.SessionStore
import com.move60.workout.WorkoutData
import com.move60.workout.domain.StructureVersion
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A session stored by the pre-4-stage build (no structureVersion) must finish in the old 5-stage structure, unchanged. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SessionStructureCompatTest {
    private lateinit var context: Context
    @Before fun init() { context = ApplicationProvider.getApplicationContext() }
    private fun store() = SessionStore(context, WorkoutData(context))

    private fun stripMarker() {
        val prefs = context.getSharedPreferences("move60_native_sessions_v8", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        val rows = prefs.all.filter { (k, v) -> k.startsWith("daily_") && v is String }
        assertTrue("fixture must find the stored session", rows.isNotEmpty())
        rows.forEach { (k, v) -> edit.putString(k, JSONObject(v as String).apply { remove("structureVersion") }.toString()) }
        edit.commit()
    }

    @Test fun newlyStartedSessionPersistsLegacyMarkerForNow() {
        val s = store(); s.start()
        assertEquals(StructureVersion.LEGACY_5, s.structure)
        assertEquals(StructureVersion.LEGACY_5, store().structure)
        val stored = context.getSharedPreferences("move60_native_sessions_v8", Context.MODE_PRIVATE).all.filter { it.key.startsWith("daily_") }.values
        assertTrue(stored.any { JSONObject(it as String).optString("structureVersion") == "LEGACY_5" })
    }

    @Test fun preUpdateRunningSessionLoadsAsLegacyAndKeepsItsProgress() {
        val s = store(); s.start()
        val t0 = System.currentTimeMillis()
        s.reconcile(t0 + 120_000L)
        val elapsed = s.accumulatedActiveSeconds; val stage = s.stage; val started = s.sessionStartedAt
        stripMarker()                                       // exactly what an old build wrote
        val reloaded = store()
        assertEquals(StructureVersion.LEGACY_5, reloaded.structure)
        assertTrue("running state must survive", reloaded.running)
        assertEquals(stage, reloaded.stage)
        assertEquals(started, reloaded.sessionStartedAt)
        assertTrue(reloaded.accumulatedActiveSeconds >= elapsed)
        reloaded.pause(); reloaded.start()                  // can still be paused/resumed in the old structure
        assertEquals(StructureVersion.LEGACY_5, store().structure)
    }

    @Test fun unknownMarkerFallsBackToLegacyInsteadOfResetting() {
        val s = store(); s.start()
        val prefs = context.getSharedPreferences("move60_native_sessions_v8", Context.MODE_PRIVATE)
        val edit = prefs.edit()
        val rows = prefs.all.filter { (k, v) -> k.startsWith("daily_") && v is String }
        assertTrue("fixture must find the stored session", rows.isNotEmpty())
        rows.forEach { (k, v) -> edit.putString(k, JSONObject(v as String).put("structureVersion", "FUTURE_9").toString()) }
        edit.commit()
        val reloaded = store()
        assertEquals(StructureVersion.LEGACY_5, reloaded.structure)
        assertTrue(reloaded.running)
    }
}
