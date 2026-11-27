package com.mytvb.network.cookie

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.*
import org.junit.Test

class GuestCookieJarTest {
    @Test fun identityIsStableAndResponseCookiesAreDiscarded() {
        val jar = GuestCookieJar()
        val url = "https://api.bilibili.com/x/player".toHttpUrl()
        val first = jar.loadForRequest(url)
        jar.saveFromResponse(url, listOf(Cookie.Builder().domain(url.host).name("SESSDATA").value("secret").build()))
        assertEquals(first, jar.loadForRequest(url))
        assertEquals(listOf("buvid3"), first.map { it.name })
        assertTrue(first.single().value.endsWith("infoc"))
        assertNotEquals(first.single().value, GuestCookieJar().loadForRequest(url).single().value)
    }
    @Test fun identityNeverLeaksToThirdParty() {
        assertTrue(GuestCookieJar().loadForRequest("https://evilbilibili.com/".toHttpUrl()).isEmpty())
    }
}
