package com.mytvb.core.common.net

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.SystemClock
import com.mytvb.core.common.log.AppLog

/**
 * 监听系统网络从"不可用/未验证"恢复为"已验证可用"的时刻。
 *
 * 电视盒子开机自启动场景下，app 常常先于 DHCP/DNS 就绪启动，首屏请求全部
 * UnknownHostException 失败；系统网络就绪后框架不会主动通知页面重试，用户只能
 * 手动重启。此监听只在 VALIDATED 状态从无到有的跳变时回调一次（带 3s 防抖），
 * 由调用方决定触发哪些重试。
 */
object NetworkRecoveryMonitor {

    private const val TAG = "NetworkRecovery"
    private const val MIN_DISPATCH_INTERVAL_MS = 3000L

    @Volatile
    private var started = false

    @Volatile
    private var hasValidatedNetwork = false

    @Volatile
    private var lastDispatchMs = 0L

    fun start(context: Context, onRecovered: () -> Unit) {
        if (started) {
            return
        }
        val cm = context.applicationContext
            .getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        if (cm == null) {
            AppLog.w(TAG, "start: ConnectivityManager unavailable")
            return
        }
        started = true
        val request = NetworkRequest.Builder()
            .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            .build()
        cm.registerNetworkCallback(
            request,
            object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    AppLog.i(TAG, "onAvailable $network validated=$hasValidatedNetwork")
                }

                override fun onCapabilitiesChanged(
                    network: Network,
                    networkCapabilities: NetworkCapabilities
                ) {
                    if (!networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)) {
                        return
                    }
                    if (hasValidatedNetwork) {
                        return
                    }
                    hasValidatedNetwork = true
                    val now = SystemClock.elapsedRealtime()
                    if (now - lastDispatchMs < MIN_DISPATCH_INTERVAL_MS) {
                        return
                    }
                    lastDispatchMs = now
                    AppLog.i(TAG, "network recovered (validated), dispatching")
                    onRecovered()
                }

                override fun onLost(network: Network) {
                    AppLog.i(TAG, "onLost $network")
                    // 多网络场景下丢一个不代表全部失联，重查当前活动网络
                    val active = cm.activeNetwork
                    hasValidatedNetwork = active != null &&
                        cm.getNetworkCapabilities(active)
                            ?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
                }
            }
        )
        AppLog.i(TAG, "started")
    }
}
