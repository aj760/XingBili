package com.mytvb.network.cookie

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.UUID

/** One stable anonymous identity per client, never persisted or shared with login. */
class GuestCookieJar(
    private val buvid: String = UUID.randomUUID().toString().replace("-", "") + "infoc"
) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) = Unit

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        if (url.host != "bilibili.com" && !url.host.endsWith(".bilibili.com")) return emptyList()
        return listOf(Cookie.Builder().hostOnlyDomain(url.host).path("/").name("buvid3").value(buvid).build())
    }
}
