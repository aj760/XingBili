package com.mytvb.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchItemAdapterTest {

    @Test
    fun plan_appendedPageUsesRangeInsert() {
        val plan = SearchItemUpdatePlanner.plan(
            oldKeys = listOf("aid:1", "aid:2"),
            newKeys = listOf("aid:1", "aid:2", "aid:3", "aid:4")
        )

        assertEquals(SearchItemUpdatePlan.Append(positionStart = 2, itemCount = 2), plan)
    }

    @Test
    fun plan_changedPrefixFallsBackToReplace() {
        val plan = SearchItemUpdatePlanner.plan(
            oldKeys = listOf("aid:1", "aid:2"),
            newKeys = listOf("aid:1", "aid:20", "aid:3")
        )

        assertEquals(SearchItemUpdatePlan.Replace, plan)
    }
}
