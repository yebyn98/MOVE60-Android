package com.move60.workout.ui

import org.junit.Assert.*
import org.junit.Test

class Move60ToastStateTest {
    @Test fun showReplacesTheMessageAndDismissClearsIt() {
        val t = Move60ToastState()
        assertNull(t.message)
        t.show("a"); t.show("b")
        assertEquals("b", t.message)
        t.dismiss(); assertNull(t.message)
    }

    @Test fun theSameTextShownAgainIsANewShowSoTheTimerRestarts() {
        val t = Move60ToastState()
        t.show("운동한 기록이 없습니다."); val first = t.token
        t.show("운동한 기록이 없습니다.")
        assertNotEquals(first, t.token)
    }
}
