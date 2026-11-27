package com.mytvb.core.ui.focus.tv

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TvFocusMovePolicyTest {

    @Test
    fun horizontalListDoesNotConsumeDownWhenStrategyHasNoTarget() {
        assertFalse(
            TvFocusMovePolicy.shouldHandleDownAfterStrategyMiss(RecyclerView.HORIZONTAL)
        )
    }

    @Test
    fun verticalListKeepsConsumingDownAtBottomForLoadMore() {
        assertTrue(
            TvFocusMovePolicy.shouldHandleDownAfterStrategyMiss(RecyclerView.VERTICAL)
        )
    }
}
