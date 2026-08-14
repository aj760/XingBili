package blbl.cat3399.feature.video

import blbl.cat3399.core.model.VideoCard
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class VideoCardVisibilityFilterTest {
    @Before
    fun setUp() {
        VideoCardVisibilityFilter.clearSessionState()
    }

    @After
    fun tearDown() {
        VideoCardVisibilityFilter.clearSessionState()
    }

    @Test
    fun filterVisible_should_exclude_hidden_cards() {
        val first = videoCard("BV1xx411c7mD")
        val second = videoCard("BV1xx411c7mE")

        VideoCardVisibilityFilter.hide(first.stableKey())

        val filtered = VideoCardVisibilityFilter.filterVisible(listOf(first, second))

        assertEquals(listOf(second), filtered)
    }

    @Test
    fun filterVisibleFresh_should_exclude_hidden_loaded_and_duplicate_cards() {
        val hidden = videoCard("BV1xx411c7mD")
        val loaded = videoCard("BV1xx411c7mE")
        val duplicate = videoCard("BV1xx411c7mF")
        val duplicateAgain = videoCard("BV1xx411c7mF")
        val visible = videoCard("BV1xx411c7mG")

        VideoCardVisibilityFilter.hide(hidden.stableKey())

        val filtered =
            VideoCardVisibilityFilter.filterVisibleFresh(
                cards = listOf(hidden, loaded, duplicate, duplicateAgain, visible),
                loadedStableKeys = setOf(loaded.stableKey()),
            )

        assertEquals(listOf(duplicate, visible), filtered)
    }

    private fun videoCard(bvid: String): VideoCard {
        return VideoCard(
            bvid = bvid,
            cid = null,
            title = bvid,
            coverUrl = "",
            durationSec = 0,
            ownerName = "",
            ownerFace = null,
            view = null,
            danmaku = null,
            pubDate = null,
            pubDateText = null,
        )
    }
}
