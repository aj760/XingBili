package com.mytvb.network.interceptor

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class RequestHeaderPolicyTest {
    @Test fun pagePathNeverEntersOrigin() {
        val result = resolvePageHeaders("https://api.bilibili.com/x/space/web-space/foo?mid=123".toHttpUrl())
        assertEquals("https://space.bilibili.com/123", result.referer)
        assertEquals("https://space.bilibili.com", result.origin)
    }
    @Test fun endpointsUseTheirOwnPages() {
        val cases = mapOf(
            "https://api.live.bilibili.com/room/v1/Room?room_id=12" to "https://live.bilibili.com/12",
            "https://api.bilibili.com/x/polymer/web-dynamic/v1/feed/all" to "https://t.bilibili.com/",
            "https://api.bilibili.com/x/v3/fav/resource/list?media_id=23" to "https://www.bilibili.com/list/ml23",
            "https://api.bilibili.com/x/player/playurl?bvid=BV123" to "https://www.bilibili.com/video/BV123",
            "https://api.bilibili.com/x/player/wbi/playurl?bvid=BV123" to "https://www.bilibili.com"
        )
        cases.forEach { (url, referer) -> assertEquals(referer, resolvePageHeaders(url.toHttpUrl()).referer) }
    }
    @Test fun lookalikeHostsAreNotBilibili() {
        assertFalse(isBilibiliHost("evilbilibili.com"))
        assertFalse(isBilibiliHost("bilibili.com.example.org"))
        assertTrue(isBilibiliHost("api.bilibili.com"))
    }
}
