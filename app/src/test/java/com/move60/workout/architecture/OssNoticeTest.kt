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
    @Test fun theTransitiveAuditIsExplicitlyUnverifiedForRelease() {
        val bundled = OssNoticeReader.load(ApplicationProvider.getApplicationContext<Context>())
        assertNotNull(bundled)
        val notice = bundled!!
        assertEquals(188, notice.auditedCount)
        assertEquals(188, notice.libraries.size)
        assertEquals(0, notice.unresolvedCount)
        assertFalse("POM license labels and legal license/NOTICE distribution are different", notice.legalNoticesVerified)
    }

    @Test fun offlineSpdxReferenceTextsAreReadableButAreNotLegalClearance() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        for (id in OssLicenseReference.knownIds) {
            val license = OssLicenseReference.read(context, id)
            assertNotNull("Missing offline SPDX reference $id", license)
            assertTrue("Reference $id is unexpectedly short", license!!.length > 700)
        }
        assertTrue(OssLicenseReference.read(context, "Apache-2.0")!!.contains("TERMS AND CONDITIONS"))
        assertTrue(OssLicenseReference.read(context, "MIT")!!.contains("Permission is hereby granted"))
        assertTrue(OssLicenseReference.read(context, "BSD-3-Clause")!!.contains("Redistributions in binary form"))
        assertNull("Unknown license IDs must not access arbitrary assets", OssLicenseReference.read(context, "../workouts"))
        assertNull(OssLicenseReference.read(context, "Android Software Development Kit License"))
        assertFalse("Standard text != per-module notices/copyright", OssNoticeReader.load(context)!!.legalNoticesVerified)
    }

    @Test fun theBundledListIsReadableAndHasNoLibraryWithoutALicense() {
        val n = OssNoticeReader.load(ApplicationProvider.getApplicationContext<Context>())
        assertNotNull("assets/oss_licenses.json must be bundled (scripts/gen-oss-notice.py)", n)
        assertTrue(n!!.libraries.size >= 20)
        assertTrue(n.libraries.all { it.licenses.isNotEmpty() })
    }
}
