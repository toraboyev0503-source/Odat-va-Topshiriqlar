package com.memora.app.data.repository

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NoteTitlePolicyTest {
    @Test fun blankAndWhitespaceOnlyAreRejected() {
        assertFalse(NoteTitlePolicy.canPersist(""))
        assertFalse(NoteTitlePolicy.canPersist("  \t\n "))
    }

    @Test fun validTitleIsAccepted() {
        assertTrue(NoteTitlePolicy.canPersist("Yangi qayd"))
    }
}
