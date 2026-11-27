package com.mytvb.ui.activity

import com.mytvb.model.video.VideoModel
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerActivityPlayQueueTest {

    @Test
    fun buildPlayQueueReturnsItemsAfterCurrentVideo() {
        val first = VideoModel(aid = 1L, bvid = "BV1", title = "one")
        val second = VideoModel(aid = 2L, bvid = "BV2", title = "two")
        val third = VideoModel(aid = 3L, bvid = "BV3", title = "three")

        val queue = PlayerActivity.buildPlayQueue(listOf(first, second, third), second)

        assertEquals(listOf(third), queue)
    }

    @Test
    fun buildPlayQueueIsEmptyForLastItem() {
        val first = VideoModel(aid = 1L, bvid = "BV1", title = "one")
        val second = VideoModel(aid = 2L, bvid = "BV2", title = "two")

        val queue = PlayerActivity.buildPlayQueue(listOf(first, second), second)

        assertTrue(queue.isEmpty())
    }
}
