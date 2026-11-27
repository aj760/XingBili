package com.mytvb.network.interceptor

import com.mytvb.network.session.RequestSessionScope
import com.mytvb.network.session.RequestSessionSnapshot
import okhttp3.Interceptor
import okhttp3.Response
import java.net.ProtocolException

/** Stamp before OkHttp's bridge adds cookies; do not expose this tag as an HTTP header. */
class SessionScopeInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val original = chain.request()
        // Login/refresh/nav may deliberately replace credentials. Do not cancel
        // those flows, third-party resources, or writes with ambiguous outcomes.
        val host = original.url.host
        val path = original.url.encodedPath
        if (original.method != "GET" ||
            !(host == "bilibili.com" || host.endsWith(".bilibili.com")) ||
            host == "passport.bilibili.com" || host == "www.bilibili.com" ||
            path == "/x/web-interface/nav"
        ) return chain.proceed(original)
        val request = chain.request().newBuilder()
            .tag(RequestSessionSnapshot::class.java, RequestSessionScope.capture())
            .build()
        try {
            return chain.proceed(request)
        } catch (error: ObsoleteSessionException) {
            // The response crossed a session transition (login/logout/cookie refresh)
            // while in flight and was closed inside the network layer before its
            // stale Set-Cookie could reach the jar. Only application interceptors
            // may call proceed() more than once, so the one-shot re-issue lives
            // here under a CURRENT-generation snapshot instead of in the network
            // layer (a second proceed there crashes with IllegalStateException —
            // reproduced as a crash on scan-login when many in-flight reads go
            // stale at once). A second stale result surfaces as-is.
            val retry = request.newBuilder()
                .tag(RequestSessionSnapshot::class.java, RequestSessionScope.capture())
                .build()
            return chain.proceed(retry)
        }
    }
}

/** Thrown by [SessionScopeResponseInterceptor]; ProtocolException so OkHttp's retry layer won't re-send. */
class ObsoleteSessionException(cause: Throwable) :
    ProtocolException("Obsolete session response") {
    init {
        initCause(cause)
    }
}

/** Runs inside BridgeInterceptor, before stale Set-Cookie can mutate the current account. */
class SessionScopeResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        val snapshot = chain.request().tag(RequestSessionSnapshot::class.java)
            ?: return response
        try {
            RequestSessionScope.requireCurrent(snapshot)
            return response
        } catch (error: Exception) {
            response.close()
            // Never proceed() a second time here: network interceptors must call
            // proceed exactly once (OkHttp hard invariant). The re-issue is done by
            // SessionScopeInterceptor at the application layer.
            throw ObsoleteSessionException(error)
        }
    }
}
