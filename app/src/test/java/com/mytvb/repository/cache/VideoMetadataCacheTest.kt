package com.mytvb.repository.cache

import com.mytvb.model.BaseResponse
import com.mytvb.model.video.detail.VideoDetailModel
import com.mytvb.network.session.RequestSessionScope
import com.mytvb.network.session.StaleSessionException
import kotlinx.coroutines.*
import org.junit.Assert.*
import org.junit.Test

class VideoMetadataCacheTest {
    @Test fun successfulMetadataIsSharedOnlyWithinTheSameSession() = runBlocking {
        RequestSessionScope.invalidate()
        var calls = 0
        suspend fun load() = VideoMetadataCache.detail(14, "BV-success-account") {
            calls++
            BaseResponse(data = VideoDetailModel())
        }
        load(); load()
        assertEquals(1, calls)
        RequestSessionScope.invalidate()
        load()
        assertEquals(2, calls)
    }
    @Test fun failuresAreNotStoredAndAccountScopesNeverShareEntries() = runBlocking {
        RequestSessionScope.invalidate()
        var calls = 0
        suspend fun load() = VideoMetadataCache.detail(12, "BV-cache-test") {
            calls++
            BaseResponse<VideoDetailModel>(code = -404)
        }
        load(); load()
        assertEquals(2, calls)
        RequestSessionScope.invalidate()
        load()
        assertEquals(3, calls)
    }
    @Test fun switchDuringRequestPreventsOldResultFromBeingDelivered() = runBlocking {
        RequestSessionScope.invalidate()
        try {
            VideoMetadataCache.detail(13, "BV-old-account") {
                RequestSessionScope.invalidate()
                BaseResponse<VideoDetailModel>(code = -404)
            }
            fail("Old account response delivered")
        } catch (_: StaleSessionException) { }
    }
}
