package com.mytvb.core.ui.focus.tv

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvFocusParkingPolicyTest {

    @Test
    fun parksWhenTargetCardIsNotAttachedAndFocusIsStillInList() {
        assertTrue(
            TvFocusParkingPolicy.shouldParkFocusForPendingTarget(
                hasAttachedFocusableTarget = false,
                focusIsOutsideList = false
            )
        )
    }

    @Test
    fun doesNotParkWhenTargetCardCanReceiveFocusImmediately() {
        assertFalse(
            TvFocusParkingPolicy.shouldParkFocusForPendingTarget(
                hasAttachedFocusableTarget = true,
                focusIsOutsideList = false
            )
        )
    }

    @Test
    fun doesNotStealFocusThatAlreadyMovedOutsideTheList() {
        assertFalse(
            TvFocusParkingPolicy.shouldParkFocusForPendingTarget(
                hasAttachedFocusableTarget = false,
                focusIsOutsideList = true
            )
        )
    }
}
