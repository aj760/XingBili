package com.mytvb.core.ui.focus

import androidx.recyclerview.widget.RecyclerView
import org.junit.Assert.assertEquals
import org.junit.Test

class LinearFocusStepTest {

    @Test
    fun resolveTargetPosition_movesOneStepRight() {
        assertEquals(19, LinearFocusStep.resolveTargetPosition(18, 40, 1))
    }

    @Test
    fun resolveTargetPosition_movesOneStepLeft() {
        assertEquals(17, LinearFocusStep.resolveTargetPosition(18, 40, -1))
    }

    @Test
    fun resolveTargetPosition_returnsNoPositionAtEdges() {
        assertEquals(RecyclerView.NO_POSITION, LinearFocusStep.resolveTargetPosition(0, 40, -1))
        assertEquals(RecyclerView.NO_POSITION, LinearFocusStep.resolveTargetPosition(39, 40, 1))
    }

    @Test
    fun resolveTargetPosition_rejectsInvalidInputs() {
        assertEquals(RecyclerView.NO_POSITION, LinearFocusStep.resolveTargetPosition(RecyclerView.NO_POSITION, 40, 1))
        assertEquals(RecyclerView.NO_POSITION, LinearFocusStep.resolveTargetPosition(0, 0, 1))
        assertEquals(RecyclerView.NO_POSITION, LinearFocusStep.resolveTargetPosition(18, 40, 2))
    }
}
