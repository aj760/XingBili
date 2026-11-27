package com.mytvb.network.interceptor

import com.mytvb.core.common.coroutine.catchingRequest
import com.mytvb.network.session.RequestSessionScope
import com.mytvb.network.session.StaleSessionException
import okhttp3.*
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test

class NetworkBoundaryTest {
    private fun chain(request: Request, respond: (Request) -> Response): Interceptor.Chain {
        return object : Interceptor.Chain {
            private val call = OkHttpClient().newCall(request)
            override fun request() = request
            override fun proceed(request: Request) = respond(request)
            override fun call() = call
            override fun connection(): Connection? = null
            override fun connectTimeoutMillis() = 1000
            override fun readTimeoutMillis() = 1000
            override fun writeTimeoutMillis() = 1000
            override fun withConnectTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun withReadTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
            override fun withWriteTimeout(timeout: Int, unit: java.util.concurrent.TimeUnit) = this
        }
    }
    private fun response(request: Request, code: Int = 200) = Response.Builder()
        .request(request).protocol(Protocol.HTTP_1_1).code(code).message("test")
        .body("test".toResponseBody()).build()

    @Test fun staleResponsesAreClosedAndSurfaceAsCancellationAtRequestBoundary() {
        var closed = false
        val body = object : ResponseBody() {
            override fun contentType(): MediaType? = null
            override fun contentLength(): Long = 0
            override fun source(): okio.BufferedSource = okio.Buffer()
            override fun close() { closed = true }
        }
        val snapshot = RequestSessionScope.capture()
        val request = Request.Builder().url("https://api.bilibili.com/x/player")
            .tag(com.mytvb.network.session.RequestSessionSnapshot::class.java, snapshot).build()
        val chain = chain(request) {
            RequestSessionScope.invalidate()
            response(it).newBuilder().body(body).build()
        }
        try {
            catchingRequest { SessionScopeResponseInterceptor().intercept(chain) }
            fail("Old session response accepted")
        } catch (_: StaleSessionException) { }
        assertTrue(closed)
    }

    @Test fun applicationLayerReissuesOnceUnderCurrentGeneration() {
        var calls = 0
        val request = Request.Builder().url("https://api.bilibili.com/x/player").build()
        val response = SessionScopeInterceptor().intercept(chain(request) {
            calls++
            if (calls == 1) throw ObsoleteSessionException(StaleSessionException())
            response(it)
        })
        response.close()
        assertEquals(2, calls)
    }

    @Test fun secondStaleRetrySurfacesAsIs() {
        var calls = 0
        val request = Request.Builder().url("https://api.bilibili.com/x/player").build()
        try {
            SessionScopeInterceptor().intercept(chain(request) {
                calls++
                throw ObsoleteSessionException(StaleSessionException())
            })
            fail("Second stale retry accepted")
        } catch (_: ObsoleteSessionException) { }
        assertEquals(2, calls)
    }

    @Test fun loginAndWritesAreNotStampedAsReadRequests() {
        val interceptor = SessionScopeInterceptor()
        for (request in listOf(
            Request.Builder().url("https://passport.bilibili.com/qrcode").build(),
            Request.Builder().url("https://api.bilibili.com/x/action").post("".toRequestBody()).build()
        )) {
            interceptor.intercept(chain(request) {
                assertNull(it.tag(com.mytvb.network.session.RequestSessionSnapshot::class.java))
                response(it)
            }).close()
        }
    }

    @Test fun diagnosticsExcludeQueriesHeadersAndResponseBodies() {
        val messages = mutableListOf<String>()
        val request = Request.Builder().url("https://api.bilibili.com/x/player?access_key=SECRET")
            .header("Cookie", "SESSDATA=SECRET").build()
        NetworkDiagnosticsInterceptor { messages.add(it) }
            .intercept(chain(request) { response(it, 412) }).close()
        assertEquals(1, messages.size)
        assertTrue(messages.single().contains("http=412"))
        assertFalse(messages.single().contains("SECRET"))
        assertFalse(messages.single().contains("access_key"))
    }

    @Test fun explicitPageHeadersSurviveDefaultPolicy() {
        val request = Request.Builder().url("https://api.bilibili.com/x/player?bvid=BVtest")
            .header("Referer", "https://www.bilibili.com/custom/page").build()
        HeaderInterceptor({ "Chrome/120.0" }, { "zh-CN" }).intercept(chain(request) {
            assertEquals("https://www.bilibili.com/custom/page", it.header("Referer"))
            assertEquals("https://www.bilibili.com", it.header("Origin"))
            response(it)
        }).close()
    }
}
