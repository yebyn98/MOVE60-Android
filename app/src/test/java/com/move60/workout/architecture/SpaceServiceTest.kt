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

/** MY > 운동 공간 (Figma 61/62/73): nothing is seeded, writes are all-or-nothing, equipment is limited to the catalog. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpaceServiceTest {
    private lateinit var db: WorkoutDatabase
    private var now = 1_000L
    private val service get() = SpaceService(db) { now }
    private val dao get() = db.spaceDao()

    @Before fun open() {
        db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), WorkoutDatabase::class.java).allowMainThreadQueries().build()
    }
    @After fun close() { db.close() }

    @Test fun nothingExistsUntilTheUserSavesIt() = runBlocking {
        assertTrue(dao.spaces().isEmpty()); assertTrue(dao.equipment("home").isEmpty())
    }

    @Test fun theFirstSpaceBecomesTheDefaultAndSetDefaultMovesIt() = runBlocking {
        assertEquals(SpaceResult.Saved("gym1"), service.create("gym1", SpaceKind.GYM, " 우리 헬스장 "))
        assertEquals(SpaceResult.Saved("home"), service.create("home", SpaceKind.HOME, "집"))
        assertEquals(listOf("gym1" to true, "home" to false), dao.spaces().map { it.spaceId to it.isDefault })
        assertEquals("우리 헬스장", dao.space("gym1")!!.name)
        service.setDefault("home")
        assertEquals(listOf("home" to true, "gym1" to false), dao.spaces().map { it.spaceId to it.isDefault })
        assertEquals(SpaceResult.Rejected("unknown space"), service.setDefault("nope"))
    }

    @Test fun createIsIdempotentAndRejectsBadNamesDuplicatesAndTooMany() = runBlocking {
        service.create("a", SpaceKind.HOME, "집")
        assertEquals(SpaceResult.Saved("a"), service.create("a", SpaceKind.HOME, "집"))
        assertEquals(1, dao.spaces().size)
        assertTrue(service.create("b", SpaceKind.GYM, "  ") is SpaceResult.Rejected)
        assertTrue(service.create("b", SpaceKind.GYM, "가".repeat(21)) is SpaceResult.Rejected)
        assertTrue(service.create("b", SpaceKind.GYM, "집") is SpaceResult.Rejected)
        assertTrue(service.create("", SpaceKind.GYM, "x") is SpaceResult.Rejected)
        for (i in 2..SpaceService.MAX_SPACES) assertTrue(service.create("s$i", SpaceKind.GYM, "공간$i") is SpaceResult.Saved)
        assertTrue(service.create("over", SpaceKind.GYM, "초과") is SpaceResult.Rejected)
    }

    @Test fun equipmentIsReplacedAsOneSetAndCardioModelIsOptionalText() = runBlocking {
        service.create("gym1", SpaceKind.GYM, "헬스장")
        assertEquals(SpaceResult.Saved("gym1"), service.saveEquipment("gym1", listOf(
            EquipmentChoice("INDOOR_CYCLE", " 브랜드 ", "  "), EquipmentChoice("LEG_PRESS"), EquipmentChoice("LAT_PULLDOWN"))))
        val rows = dao.equipment("gym1")
        assertEquals(listOf("INDOOR_CYCLE", "LAT_PULLDOWN", "LEG_PRESS"), rows.map { it.equipmentKey })
        assertEquals("브랜드" to null, rows.first().brand to rows.first().model)
        service.saveEquipment("gym1", listOf(EquipmentChoice("ELLIPTICAL")))
        assertEquals(listOf("ELLIPTICAL"), dao.equipment("gym1").map { it.equipmentKey })
        service.saveEquipment("gym1", emptyList())
        assertTrue("clearing is allowed", dao.equipment("gym1").isEmpty())
    }

    /** Invalid input writes nothing, not even the valid entries, and keeps the previously saved set. */
    @Test fun invalidEquipmentWritesNothingAndKeepsThePreviousSet() = runBlocking {
        service.create("home", SpaceKind.HOME, "집")
        service.saveEquipment("home", listOf(EquipmentChoice("DUMBBELL")))
        for (bad in listOf(
            listOf(EquipmentChoice("YOGA_MAT"), EquipmentChoice("LEG_PRESS")),            // not offered at home
            listOf(EquipmentChoice("NOT_REAL")),
            listOf(EquipmentChoice("YOGA_MAT"), EquipmentChoice("YOGA_MAT")),
            listOf(EquipmentChoice("DUMBBELL", "x", null)),                               // model only for cardio
            listOf(EquipmentChoice("INDOOR_CYCLE", null, "가".repeat(41)))
        )) assertTrue("$bad", service.saveEquipment("home", bad) is SpaceResult.Rejected)
        assertEquals(SpaceResult.Rejected("unknown space"), service.saveEquipment("nope", emptyList()))
        assertEquals(listOf("DUMBBELL"), dao.equipment("home").map { it.equipmentKey })
    }

    @Test fun choicesKeepTheEarlierOrderAndOnlyAddEquipment() {
        // The earlier Figma checklists stay first and in order; the widened gym / home lists only append (PO decision 2026-11-17).
        assertEquals(listOf("실내 사이클", "레그프레스", "랫풀다운", "일립티컬", "케이블 머신"), Equipment.choices(SpaceKind.GYM).take(5).map { it.label })
        assertEquals(listOf("실내 사이클", "덤벨", "요가 매트", "저항 밴드", "일립티컬"), Equipment.choices(SpaceKind.HOME).take(5).map { it.label })
        assertEquals(listOf("힙쓰러스트 머신", "힙 어브덕션 머신", "레그컬 머신", "시티드 로우 머신", "체스트프레스 머신", "덤벨", "벤치", "스텝박스", "요가 매트", "저항 밴드"),
            Equipment.choices(SpaceKind.GYM).drop(5).map { it.label })
        assertEquals(listOf("벤치", "스텝박스"), Equipment.choices(SpaceKind.HOME).drop(5).map { it.label })
    }

    @Test fun everyStoredKeyOfBeforeStillResolvesAndEveryChoiceIsAKnownKey() {
        for (old in listOf("INDOOR_CYCLE", "ELLIPTICAL", "LEG_PRESS", "LAT_PULLDOWN", "CABLE_MACHINE", "DUMBBELL", "YOGA_MAT", "RESISTANCE_BAND"))
            assertNotNull(old, Equipment.fromKey(old))
        for (kind in SpaceKind.values()) assertEquals(Equipment.choices(kind).size, Equipment.choices(kind).map { it.name }.toSet().size)
    }

    @Test fun aGymCanRegisterEveryGymChoiceAndAHomeItsHomeChoices() = runBlocking {
        service.create("g", SpaceKind.GYM, "헬스장"); service.create("h", SpaceKind.HOME, "집")
        assertEquals(SpaceResult.Saved("g"), service.saveEquipment("g", Equipment.choices(SpaceKind.GYM).map { EquipmentChoice(it.name) }))
        assertEquals(Equipment.choices(SpaceKind.GYM).size, dao.equipment("g").size)
        assertEquals(SpaceResult.Saved("h"), service.saveEquipment("h", listOf(EquipmentChoice("BENCH"), EquipmentChoice("STEP_BOX"), EquipmentChoice("DUMBBELL"))))
        assertTrue("a machine is not a home choice", service.saveEquipment("h", listOf(EquipmentChoice("LEG_CURL_MACHINE"))) is SpaceResult.Rejected)
    }

    /** PO decision 2: a gym name can be edited; the rules are the same as when registering, and nothing else changes. */
    @Test fun aGymCanBeRenamedWithTheSameRulesAndKeepsItsEquipmentAndDefault() = runBlocking {
        service.create("gym1", SpaceKind.GYM, "헬스장"); service.create("gym2", SpaceKind.GYM, "다른 헬스장")
        service.saveEquipment("gym1", listOf(EquipmentChoice("LEG_PRESS"), EquipmentChoice("INDOOR_CYCLE", "브랜드", "M1")))
        val before = dao.equipment("gym1")
        assertEquals(SpaceResult.Saved("gym1"), service.rename("gym1", "  새 이름 "))
        assertEquals("새 이름", dao.space("gym1")!!.name)
        assertEquals("equipment is untouched", before, dao.equipment("gym1"))
        assertTrue("the default space does not change", dao.space("gym1")!!.isDefault)
        assertEquals("renaming to its own name is allowed", SpaceResult.Saved("gym1"), service.rename("gym1", "새 이름"))
        assertTrue("case-insensitive duplicate of another space", service.rename("gym1", "다른 헬스장") is SpaceResult.Rejected)
        for (bad in listOf("", "   ", "가".repeat(21))) assertTrue("'$bad'", service.rename("gym1", bad) is SpaceResult.Rejected)
        assertEquals("새 이름", dao.space("gym1")!!.name)
    }

    @Test fun onlyGymsAreRenamableAndAnUnknownSpaceIsRejected() = runBlocking {
        service.create("home", SpaceKind.HOME, "집 · 개인 운동 공간")
        assertEquals(SpaceResult.Rejected("only gyms can be renamed"), service.rename("home", "내 집"))
        assertEquals("집 · 개인 운동 공간", dao.space("home")!!.name)
        assertEquals(SpaceResult.Rejected("unknown space"), service.rename("nope", "이름"))
    }
}
