package com.mytvb.feature.home

import com.mytvb.core.common.settings.AppSettingsDataStore
import org.koin.mp.KoinPlatform

/**
 * 首页推荐 / 热门的数据来源设置（存储值中文，永不改动，显示走 SettingLabels 三语映射）。
 * APP 端 = app.bilibili.com 接口（推荐 x/v2/feed/index 需 access_token，热门走 gRPC Popular/Index）；
 * 网页端 = api.bilibili.com web 接口。默认网页端；用户显式选 APP 端则保留其选择。
 */
object FeedSourceSetting {
    const val KEY_HOME_FEED_SOURCE = "home_feed_source"
    const val KEY_HOT_FEED_SOURCE = "hot_feed_source"
    const val VALUE_APP = "APP端"
    const val VALUE_WEB = "网页端"
    const val DEFAULT = VALUE_WEB

    val OPTIONS = arrayOf(VALUE_APP, VALUE_WEB)

    fun isAppSource(key: String): Boolean {
        val stored = runCatching {
            KoinPlatform.getKoin().get<AppSettingsDataStore>().getCachedString(key)
        }.getOrNull()
        return stored?.let { it == VALUE_APP } ?: (DEFAULT == VALUE_APP)
    }
}
