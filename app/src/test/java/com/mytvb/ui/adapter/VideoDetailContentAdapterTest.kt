package com.mytvb.ui.adapter

import com.mytvb.core.ui.focus.tv.TvDataChangeReason
import org.junit.Assert.assertEquals
import org.junit.Test

class VideoDetailContentAdapterTest {

    @Test
    fun dataChangeReasonFor_growthUsesAppendRecovery() {
        assertEquals(
            TvDataChangeReason.APPEND,
            VideoDetailContentAdapter.dataChangeReasonFor(oldCount = 20, newCount = 40)
        )
    }

    @Test
    fun dataChangeReasonFor_initialOrReplacementPreservesAnchor() {
        assertEquals(
            TvDataChangeReason.REPLACE_PRESERVE_ANCHOR,
            VideoDetailContentAdapter.dataChangeReasonFor(oldCount = 0, newCount = 20)
        )
        assertEquals(
            TvDataChangeReason.REPLACE_PRESERVE_ANCHOR,
            VideoDetailContentAdapter.dataChangeReasonFor(oldCount = 20, newCount = 20)
        )
    }
}
