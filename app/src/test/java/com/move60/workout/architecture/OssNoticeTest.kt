package com.move60.workout.architecture

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** docs/v66 item 2: the in-app list shows only licenses a POM stated; a broken list is "unavailable", never an empty-but-complete one. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class OssNoticeTest {
    private val one = """{"scope":"s","libraries":[{"group":"g","artifact":"a","version":"1","licenses":[{"name":"Apache License, Version 2.0","url":"u"}]}]}"""

    @Test fun aLibraryWithAStatedLicenseIsListed() {
        val n = OssNoticeReader.parse(one)!!
        assertEquals(listOf(OssLibrary("g:a", "1", listOf("Apache License, Version 2.0"))), n.libraries)
    }
    @Test fun missingEmptyOrLicenselessDataIsUnavailableNotAnEmptyList() {
        assertNull(OssNoticeReader.parse("not json"))
        assertNull(OssNoticeReader.parse("""{"libraries":[]}"""))
        assertNull(OssNoticeReader.parse("""{"libraries":[{"group":"g","artifact":"a","version":"1","licenses":[]}]}"""))
    }
    @Test fun theBundledListIsReadableAndHasNoLibraryWithoutALicense() {
        val n = OssNoticeReader.load(ApplicationProvider.getApplicationContext<Context>())
        assertNotNull("assets/oss_licenses.json must be bundled (scripts/gen-oss-notice.py)", n)
        assertTrue(n!!.libraries.size >= 20)
        assertTrue(n.libraries.all { it.licenses.isNotEmpty() })
    }
}
