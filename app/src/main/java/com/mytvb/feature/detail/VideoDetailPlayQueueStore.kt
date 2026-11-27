package com.mytvb.feature.detail

import com.mytvb.model.video.VideoModel
import java.util.UUID

object VideoDetailPlayQueueStore {
    private const val MAX_PENDING_QUEUE_COUNT = 8
    private val pendingQueues = linkedMapOf<String, List<VideoModel>>()

    fun enqueue(playQueue: List<VideoModel>): String? {
        val queue = playQueue.filter { it.hasPlaybackIdentity }
        if (queue.isEmpty()) {
            return null
        }
        val token = UUID.randomUUID().toString()
        pendingQueues[token] = queue
        while (pendingQueues.size > MAX_PENDING_QUEUE_COUNT) {
            pendingQueues.remove(pendingQueues.keys.first())
        }
        return token
    }

    fun consume(token: String?): List<VideoModel> {
        if (token.isNullOrBlank()) {
            return emptyList()
        }
        return pendingQueues.remove(token).orEmpty()
    }

    fun clearForTest() {
        pendingQueues.clear()
    }
}
