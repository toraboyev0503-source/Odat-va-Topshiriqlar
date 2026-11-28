package com.memora.app.ui

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class NoteChromeScrollPolicyTest {
    @Test fun `hides after sufficient upward text movement`() {
        val policy = NoteChromeScrollPolicy(16f)
        assertEquals(true, policy.update(0))
        assertNull(policy.update(7))
        assertNull(policy.update(15))
        assertEquals(false, policy.update(17))
    }

    @Test fun `small scroll jitter does not toggle visibility`() {
        val policy = NoteChromeScrollPolicy(16f)
        policy.update(0)
        assertNull(policy.update(5))
        assertNull(policy.update(2))
        assertNull(policy.update(7))
        assertNull(policy.update(6))
    }

    @Test fun `reverse swipe reveals after threshold`() {
        val policy = NoteChromeScrollPolicy(16f)
        policy.update(0)
        assertEquals(false, policy.update(80))
        assertNull(policy.update(72))
        assertEquals(true, policy.update(63))
    }

    @Test fun `top always reveals actions`() {
        val policy = NoteChromeScrollPolicy(16f)
        policy.update(0)
        policy.update(130)
        assertEquals(true, policy.update(0))
    }
}
