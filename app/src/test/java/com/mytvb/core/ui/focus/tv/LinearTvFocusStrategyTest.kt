package com.mytvb.core.ui.focus.tv

import android.view.View
import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LinearTvFocusStrategyTest {

    @Test
    fun horizontalMovesWithLeftAndRight() {
        val strategy = LinearTvFocusStrategy(RecyclerView.HORIZONTAL)
        val anchor = strategy.anchorFor(position = 2, stableKey = "2", offsetTop = 0)

        assertEquals(1, strategy.nextPosition(anchor, View.FOCUS_LEFT, 5))
        assertEquals(3, strategy.nextPosition(anchor, View.FOCUS_RIGHT, 5))
        assertNull(strategy.nextPosition(anchor, View.FOCUS_UP, 5))
        assertNull(strategy.nextPosition(anchor, View.FOCUS_DOWN, 5))
    }

    @Test
    fun verticalKeepsExistingUpAndDownBehavior() {
        val strategy = LinearTvFocusStrategy()
        val anchor = strategy.anchorFor(position = 2, stableKey = "2", offsetTop = 0)

        assertEquals(1, strategy.nextPosition(anchor, View.FOCUS_UP, 5))
        assertEquals(3, strategy.nextPosition(anchor, View.FOCUS_DOWN, 5))
        assertNull(strategy.nextPosition(anchor, View.FOCUS_LEFT, 5))
        assertNull(strategy.nextPosition(anchor, View.FOCUS_RIGHT, 5))
    }
}
