package com.mytvb.network.session

import org.junit.Assert.*
import org.junit.Test

class SessionScopeTrackerTest {
    @Test fun identicalCredentialsKeepScopeButChangesDoNot() {
        val tracker = SessionScopeTracker()
        val a = tracker.capture(listOf("account-a", "vip"))
        assertEquals(a, tracker.capture(listOf("account-a", "vip")))
        assertNotEquals(a, tracker.capture(listOf("account-a", "no-vip")))
        assertNotEquals(a, tracker.capture(listOf("account-b", "vip")))
    }

    @Test fun logoutAndLoginToSameAccountDoesNotReviveOldScope() {
        val tracker = SessionScopeTracker()
        val first = tracker.capture(listOf("a"))
        tracker.invalidate()
        assertNotEquals(first, tracker.capture(listOf("a")))
    }
}
