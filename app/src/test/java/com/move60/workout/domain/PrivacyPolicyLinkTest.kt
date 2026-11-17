package com.move60.workout.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** docs/v66 item 2: never a temporary, example or broken address; nothing configured = no link. */
class PrivacyPolicyLinkTest {
    @Test fun anOperatorGivenHttpsAddressIsUsedAsIs() {
        assertEquals("https://routive.app/privacy", PrivacyPolicyLink.validOrNull(" https://routive.app/privacy "))
    }
    @Test fun emptyPlaceholderExampleAndNonHttpsValuesAreRejected() {
        for (bad in listOf(null, "", "   ", "http://routive.app/privacy", "https://example.com/privacy", "https://www.example.org/p",
            "https://localhost/privacy", "https://10.0.0.1/privacy", "https://routive", "ftp://routive.app/p", "https://routive.app/a b",
            "not a url", "https://foo.invalid/p", "https://x.test/p", "TODO", "https:///privacy"))
            assertNull("'$bad'", PrivacyPolicyLink.validOrNull(bad))
    }
}
