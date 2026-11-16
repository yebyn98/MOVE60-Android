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

/** Session detail (Figma 27) shows the latest corrected value with the stored original beside it, plus the history. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RecordCorrectionReadTest {
    private lateinit var context: Context
    private lateinit var db: WorkoutDatabase
    private lateinit var store: SetRecordStore
    private lateinit var reader: RecordReader
    private var nowMs = T.ms("2026-11-16", 12)
    private val nov9 = LocalDate.parse("2026-11-09")
    private val id = "timer-2026-11-09-d1-111"
    private val service get() = SessionCorrectionService(db) { nowMs }

    @Before fun open() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("move60_set_records_v1", Context.MODE_PRIVATE).edit().clear().commit()
        db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build()
        store = SetRecordStore(context)
        reader = RecordReader(db) { store.completedStoredSets() }
    }
    @After fun close() { db.close() }

    private suspend fun commit(sets: List<SessionSetEntity>) {
        val base = sessionBundle(id, listOf(T.run(T.at("2026-11-09", 20, 35), 600)), sets = sets.size)
        assertTrue(SessionCommitter(db) { nowMs }.commit(base.copy(sets = sets)) is CommitResult.Committed)
    }
    private fun set(exercise: String, n: Int, kg: String, reps: String) = SessionSetEntity(id, exercise, n, SetState.COMPLETED.name, kg, reps, null)

    @Test fun detailShowsTheCorrectedValueBesideTheStoredOriginalAndTheHistory() = runBlocking {
        commit(listOf(set("legPress", 1, "40", "12"), set("legPress", 2, "45", "12")))
        service.apply(id, listOf(SetCorrection("legPress", 1, "42.5", null)))

        val detail = reader.session(id)!!
        val sets = detail.exercises.single().sets
        assertEquals(listOf("42.5", "45"), sets.map { it.kg })
        assertEquals("the stored original is kept beside the corrected value", "40", sets[0].originalKg)
        assertNull(sets[0].originalReps); assertNull(sets[1].originalKg)
        assertEquals("42.5kg × 12회 · 45kg × 12회 (2세트)", RecordFormat.setSummary(sets))
        assertEquals(listOf("weightKg"), detail.history.map { it.attribute })
        assertEquals("40" to "42.5", detail.history.single().oldValue to detail.history.single().newValue)
        assertEquals("the session time is not affected", 600L, detail.session.totalActiveSeconds)
    }

    @Test fun anUncorrectedSessionHasNoHistoryAndNoOriginalMarkers() = runBlocking {
        commit(listOf(set("legPress", 1, "40", "12")))
        val detail = reader.session(id)!!
        assertTrue(detail.history.isEmpty())
        assertTrue(detail.exercises.single().sets.all { it.originalKg == null && it.originalReps == null })
    }

    /** v58 values live in SharedPreferences, are not session rows, and are therefore never corrected or changed here. */
    @Test fun legacyOnlyValuesCannotBeCorrectedAndStayAsSaved() = runBlocking {
        commit(listOf(set("chestPress", 1, "40", "10")))
        store.write(1, "chestPress", 2, SetRecordStore.Record("50", "8", "5", true), nov9, token = 111L)   // legacy only, same workout
        val before = context.getSharedPreferences("move60_set_records_v1", Context.MODE_PRIVATE).all.toSortedMap()

        assertEquals(CorrectionResult.SetNotFound("chestPress", 2), service.apply(id, listOf(SetCorrection("chestPress", 2, "60", null))))

        val shown = reader.session(id)!!.exercises.single().sets
        assertEquals(listOf(SetOrigin.STORED, SetOrigin.LEGACY), shown.map { it.origin })
        assertEquals("50", shown[1].kg)
        assertEquals(before, context.getSharedPreferences("move60_set_records_v1", Context.MODE_PRIVATE).all.toSortedMap())
    }
}
