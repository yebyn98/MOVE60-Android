package com.move60.workout.architecture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** PO decision 1: one non-blocking notice, only for users who already have records, never twice. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class KcalNoticeTest {
    private lateinit var context: Context
    @Before fun clear() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences(KcalNoticeStore.FILE, Context.MODE_PRIVATE).edit().clear().commit()
    }

    @Test fun shownOnlyToAnExistingUserWhoHasNotSeenIt() {
        assertTrue(KcalNoticePolicy.show(seen = false, existingUser = true))
        assertFalse("a new user has nothing to be told about", KcalNoticePolicy.show(seen = false, existingUser = false))
        assertFalse("never twice", KcalNoticePolicy.show(seen = true, existingUser = true))
    }

    @Test fun markingItSeenSurvivesARestart() {
        assertFalse(KcalNoticeStore(context).seen)
        KcalNoticeStore(context).markSeen()
        assertTrue("a new store instance (new process) still sees it", KcalNoticeStore(context).seen)
    }

    @Test fun theTextDoesNotMentionAnyNumberOrPlaceholder() {
        assertFalse(KcalNoticePolicy.TITLE.contains("0") || KcalNoticePolicy.BODY.contains("0"))
        assertTrue(KcalNoticePolicy.BODY.contains("보존"))
    }
}
