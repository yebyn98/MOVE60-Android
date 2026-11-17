package com.move60.workout.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Review item 2: the live input, the plan and the store share one range, and a bad value is an input error, never a silent non-record. */
class SetInputPolicyTest {
    @Test fun theRepetitionLimitIsTheOneTheRoutinePlanAllows() {
        // RoutinePlanService.REPS_RANGE = 1..SetLimits.MAX_REPS: a plan can hold 200, so 150 and 200 must be recordable.
        assertEquals(200, SetLimits.MAX_REPS)
        for (reps in listOf("1", "12", "100", "101", "150", "200")) assertNull("$reps reps", SetInputPolicy.validate("40", reps))
    }

    @Test fun valuesOutsideTheRangeAreInputErrorsOnTheRightField() {
        for (reps in listOf("0", "201", "999", "99999999999")) assertEquals("reps $reps", SetInputError.REPS, SetInputPolicy.validate("40", reps))
        for (kg in listOf("500.1", "501", "999.9")) assertEquals("kg $kg", SetInputError.WEIGHT, SetInputPolicy.validate(kg, "10"))
        assertNull(SetInputPolicy.validate("0", "10"))
        assertNull(SetInputPolicy.validate("500", "10"))
        assertNull(SetInputPolicy.validate("42.5", "10"))
    }

    @Test fun malformedTextIsRejectedNotInterpreted() {
        for (kg in listOf("", " ", "abc", "1.2.3", "-1", "+5", "1e2", "NaN", "Infinity", "4 0", "40,5", ".5", "5.", "０")) {
            assertEquals("kg '$kg'", SetInputError.WEIGHT, SetInputPolicy.validate(kg, "10"))
        }
        for (reps in listOf("", " ", "abc", "1.5", "-3", "+3", "1e1", "1 0")) {
            assertEquals("reps '$reps'", SetInputError.REPS, SetInputPolicy.validate("40", reps))
        }
    }

    @Test fun theWeightIsCheckedBeforeTheRepetitions() {
        assertEquals(SetInputError.WEIGHT, SetInputPolicy.validate("abc", "0"))
    }

    @Test fun normalizedValuesAreWhatGetsStored() {
        assertEquals(ValidSet("40", "12"), SetInputPolicy.normalize(" 40 ", " 012 "))
        assertEquals(ValidSet("42.5", "150"), SetInputPolicy.normalize("42.5", "150"))
        assertNull(SetInputPolicy.normalize("40", "201"))
        assertNull(SetInputPolicy.normalize("x", "10"))
    }
}
