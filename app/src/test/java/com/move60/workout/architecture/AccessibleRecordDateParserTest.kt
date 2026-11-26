package com.move60.workout.architecture

import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Date parsing must not invent a day, change a month, or mutate stored workout records. */
class AccessibleRecordDateParserTest {
    @Test fun acceptsRealDatesInSupportedInputFormats() {
        assertEquals(LocalDate.of(2026, 11, 9), parseRoutiveDate("2026.11.09"))
        assertEquals(LocalDate.of(2026, 11, 9), parseRoutiveDate("2026. 11. 09"))
        assertEquals(LocalDate.of(2026, 11, 9), parseRoutiveDate("2026-11-9"))
        assertEquals(LocalDate.of(2026, 11, 9), parseRoutiveDate("2026/11/09"))
        assertEquals(LocalDate.of(2028, 2, 29), parseRoutiveDate("2028.02.29"))
    }

    @Test fun rejectsImpossibleDaysInsteadOfRollingIntoNextMonth() {
        for (invalid in listOf("2026.11.31", "2026.02.29", "2026.04.31",
            "2028.02.30", "2026.00.01", "2026.13.01", "2026.11.00")) {
            assertNull(invalid, parseRoutiveDate(invalid))
        }
    }

    @Test fun rejectsBlankPartialAndUnsafeNumbers() {
        for (invalid in listOf("", " ", "2026", "2026.11", "2026.11.x",
            "NaN", "Infinity", "2026.11.9abc", "-2026.11.09", "2026.11.09.10")) {
            assertNull(invalid, parseRoutiveDate(invalid))
        }
    }

    @Test fun doesNotInventPastOrFutureRangePolicy() {
        assertEquals(LocalDate.of(1998, 11, 14), parseRoutiveDate("1998.11.14"))
        assertEquals(LocalDate.of(2040, 1, 1), parseRoutiveDate("2040.01.01"))
    }
}
