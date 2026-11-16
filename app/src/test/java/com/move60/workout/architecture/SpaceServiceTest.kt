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

    @Test fun choicesFollowTheFigmaChecklists() {
        assertEquals(listOf("실내 사이클", "레그프레스", "랫풀다운", "일립티컬", "케이블 머신"), Equipment.choices(SpaceKind.GYM).map { it.label })
        assertEquals(listOf("실내 사이클", "덤벨", "요가 매트", "저항 밴드", "일립티컬"), Equipment.choices(SpaceKind.HOME).map { it.label })
    }
}
