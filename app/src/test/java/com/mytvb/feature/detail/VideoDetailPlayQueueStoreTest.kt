package com.mytvb.feature.detail

import com.mytvb.model.video.VideoModel
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class VideoDetailPlayQueueStoreTest {

    @After
    fun tearDown() {
        VideoDetailPlayQueueStore.clearForTest()
    }

    @Test
    fun consumeReturnsQueuedVideosOnce() {
        val next = VideoModel(aid = 2L, bvid = "BV2", title = "two")
        val token = VideoDetailPlayQueueStore.enqueue(listOf(next))

        assertEquals(listOf(next), VideoDetailPlayQueueStore.consume(token))
        assertTrue(VideoDetailPlayQueueStore.consume(token).isEmpty())
    }

    @Test
    fun enqueueFiltersUnplayableVideos() {
        val token = VideoDetailPlayQueueStore.enqueue(
            listOf(
                VideoModel(title = "missing id"),
                VideoModel(aid = 3L, bvid = "BV3", title = "three")
            )
        )

        assertEquals(listOf(VideoModel(aid = 3L, bvid = "BV3", title = "three")), VideoDetailPlayQueueStore.consume(token))
    }

    @Test
    fun enqueueReturnsNullForEmptyPlayableQueue() {
        val token = VideoDetailPlayQueueStore.enqueue(listOf(VideoModel(title = "missing id")))

        assertNull(token)
    }
}
