package com.move60.workout.architecture

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.move60.workout.WorkoutData
import com.move60.workout.domain.ExerciseEquipmentRules
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** The rule table must match the real data: real exercise ids, real equipment keys, every exercise covered; the default space alone decides. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class ExerciseEquipmentRulesDataTest {
    private lateinit var context: Context
    private lateinit var db: WorkoutDatabase
    @Before fun open() { context = ApplicationProvider.getApplicationContext(); db = Room.inMemoryDatabaseBuilder(context, WorkoutDatabase::class.java).allowMainThreadQueries().build() }
    @After fun close() { db.close() }

    @Test fun everyRuleUsesARealExerciseAndRealEquipmentKeys() {
        val data = WorkoutData(context)
        for ((id, rule) in ExerciseEquipmentRules.RULES) {
            assertNotNull("rule for unknown exercise $id", data.detail(id))
            rule.anyOf.flatten().forEach { assertNotNull("unknown equipment key $it in $id", Equipment.fromKey(it)) }
        }
    }
    @Test fun everyExerciseOfTheDataHasARule() {
        val ids = org.json.JSONObject(context.assets.open("workouts.json").bufferedReader().use { it.readText() }).getJSONObject("exercise").keys().asSequence().toSet()
        assertEquals(ids, ExerciseEquipmentRules.RULES.keys)
    }
    @Test fun everyEquipmentKeyOfARuleCanBeRegisteredInSomeSpace() {
        val registrable = SpaceKind.values().flatMap { Equipment.choices(it) }.map { it.name }.toSet()
        for ((id, rule) in ExerciseEquipmentRules.RULES) rule.anyOf.flatten().forEach { assertTrue("$it of $id cannot be registered anywhere", it in registrable) }
    }

    @Test fun candidatesComeFromAllExercisesNotOnlyTodaysPoolAndCarryOnlyTheirOwnName() {
        val data = WorkoutData(context)
        val none = candidateInputs(data, emptySet())
        assertEquals("no equipment: only the approved body-weight ways", setOf("gluteBridge", "calfRaise", "reverseLunge", "deadBug", "birdDog", "plank", "sidePlank", "wallPushup", "chairSquat", "clamshell", "heelSlide"), none.map { it.id }.toSet())
        assertTrue("names are the app's own", none.all { it.name == data.exerciseName(it.id) && it.plannedSeconds == null && it.targetSets == null && it.targetReps == null && it.restSeconds == null })
        val withBench = candidateInputs(data, setOf("BENCH", "CABLE_MACHINE")).map { it.id }
        assertTrue("hipThrust" in withBench && "pallofPress" in withBench && "legPress" !in withBench)
    }

    private suspend fun space(id: String, kind: SpaceKind, name: String, vararg keys: String) {
        val s = SpaceService(db); s.create(id, kind, name); s.saveEquipment(id, keys.map { EquipmentChoice(it) })
    }
    @Test fun noSpaceIsNotAFailure() = runBlocking { assertEquals(CandidateSpace.NoSpace, DefaultSpaceEquipment(db).load()) }
    @Test fun onlyTheDefaultSpacesEquipmentCountsAndSpacesAreNeverMerged() = runBlocking {
        space("home", SpaceKind.HOME, "집", "DUMBBELL"); space("gym", SpaceKind.GYM, "헬스장", "LEG_PRESS", "BENCH")   // "home" was created first = default
        val ready = DefaultSpaceEquipment(db).load() as CandidateSpace.Ready
        assertEquals("집", ready.name); assertEquals(setOf("DUMBBELL"), ready.equipment)
        SpaceService(db).setDefault("gym")
        assertEquals(setOf("LEG_PRESS", "BENCH"), (DefaultSpaceEquipment(db).load() as CandidateSpace.Ready).equipment)
    }
    @Test fun aSpaceWithoutEquipmentIsReadyWithAnEmptySetNotAFailure() = runBlocking {
        space("home", SpaceKind.HOME, "집")
        assertEquals(CandidateSpace.Ready("집", "HOME", emptySet()), DefaultSpaceEquipment(db).load())
    }
    @Test fun anUnreadableSpaceIsAFailureNotAnEmptyState() = runBlocking {
        assertEquals(CandidateSpace.Failed, DefaultSpaceEquipment({ throw IllegalStateException("disk error") }, { emptyList() }).load())
        // reading the default space works but its equipment cannot be read: still a failure, never "no equipment"
        space("home", SpaceKind.HOME, "집", "DUMBBELL")
        val spaces = db.spaceDao().spaces()
        assertEquals(CandidateSpace.Failed, DefaultSpaceEquipment({ spaces }, { throw IllegalStateException("disk error") }).load())
    }
}
