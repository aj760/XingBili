package com.mytvb.network.interceptor

import com.mytvb.core.common.log.AppLog
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException

/** No URL query, header values, response body or exception message enters logs. */
class NetworkDiagnosticsInterceptor(
    private val report: (String) -> Unit = { AppLog.w("NetworkDiag", it) }
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val endpoint = "${request.method} ${request.url.host}${request.url.encodedPath}"
        val started = System.nanoTime()
        return try {
            chain.proceed(request).also { response ->
                if (response.code >= 500 || response.code == 429 || response.code == 412) {
                    report("$endpoint http=${response.code} elapsedMs=${(System.nanoTime() - started) / 1_000_000}")
                }
            }
        } catch (error: IOException) {
            if (!chain.call().isCanceled() && error.cause !is java.util.concurrent.CancellationException) {
                report("$endpoint failure=${error.javaClass.simpleName}")
            }
            throw error
        }
    }
}
