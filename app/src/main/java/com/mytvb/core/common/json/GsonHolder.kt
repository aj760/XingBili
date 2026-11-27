package com.mytvb.core.common.json

import com.google.gson.Gson
import com.mytvb.network.http.NetworkClientFactory

/**
 * Gson 线程安全，全局复用两个实例：
 * [DEFAULT] 等价于 Gson()；[CONFIGURED] 带宽松数字适配器与 Lazy 排除策略（网络层用）。
 */
object GsonHolder {
    val DEFAULT: Gson by lazy { Gson() }
    val CONFIGURED: Gson by lazy { NetworkClientFactory.createGson() }
}
