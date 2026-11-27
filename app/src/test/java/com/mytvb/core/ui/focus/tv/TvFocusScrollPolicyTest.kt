package com.mytvb.core.ui.focus.tv

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Test

class TvFocusScrollPolicyTest {

    @Test
    fun horizontalForwardTargetKeepsNewCardAtTrailingEdge() {
        val offset = TvFocusScrollPolicy.horizontalOffsetForPendingTarget(
            position = 10,
            firstVisible = 5,
            lastVisible = 9,
            viewportWidth = 1000,
            paddingLeft = 20,
            paddingRight = 30,
            estimatedItemWidth = 180
        )

        assertEquals(790, offset)
    }

    @Test
    fun horizontalBackwardTargetUsesLeadingEdge() {
        val offset = TvFocusScrollPolicy.horizontalOffsetForPendingTarget(
            position = 4,
            firstVisible = 5,
            lastVisible = 9,
            viewportWidth = 1000,
            paddingLeft = 20,
            paddingRight = 30,
            estimatedItemWidth = 180
        )

        assertEquals(20, offset)
    }

    @Test
    fun horizontalUnknownVisibleRangeFallsBackToLeadingEdge() {
        val offset = TvFocusScrollPolicy.horizontalOffsetForPendingTarget(
            position = 10,
            firstVisible = RecyclerView.NO_POSITION,
            lastVisible = RecyclerView.NO_POSITION,
            viewportWidth = 1000,
            paddingLeft = 20,
            paddingRight = 30,
            estimatedItemWidth = 180
        )

        assertEquals(20, offset)
    }
}
