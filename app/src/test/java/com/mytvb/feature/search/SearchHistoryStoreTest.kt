package com.mytvb.feature.search

import org.junit.Assert.assertEquals
import org.junit.Test

class SearchHistoryStoreTest {

    @Test
    fun save_insertsNewestSearchAtTop() {
        val updated = SearchHistoryPlanner.save(
            keyword = " gamma ",
            existing = listOf("alpha", "beta")
        )

        assertEquals(listOf("gamma", "alpha", "beta"), updated)
    }

    @Test
    fun save_movesDuplicateSearchToTop() {
        val updated = SearchHistoryPlanner.save(
            keyword = "beta",
            existing = listOf("alpha", "beta", "gamma")
        )

        assertEquals(listOf("beta", "alpha", "gamma"), updated)
    }

    @Test
    fun save_trimsEntryCountFromOldestEnd() {
        val existing = (1..10).map { "k$it" }

        val updated = SearchHistoryPlanner.save(
            keyword = "new",
            existing = existing
        )

        assertEquals(listOf("new") + (1..9).map { "k$it" }, updated)
    }

    @Test
    fun save_trimsTotalLengthFromOldestEnd() {
        val updated = SearchHistoryPlanner.save(
            keyword = "N".repeat(30),
            existing = listOf(
                "A".repeat(30),
                "B".repeat(30),
                "C".repeat(30)
            )
        )

        assertEquals(
            listOf("N".repeat(30), "A".repeat(30), "B".repeat(30)),
            updated
        )
    }
}
