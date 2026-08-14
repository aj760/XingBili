package blbl.cat3399.core.prefs

import org.json.JSONArray
import org.json.JSONObject

data class CustomPageConfig(
    val enabled: Boolean,
    val tabs: List<CustomPageTabConfig>,
)

data class CustomPageTabConfig(
    val sourceType: String,
    val sourceKey: String? = null,
) {
    fun stableKey(): String = buildStableKey(sourceType = sourceType, sourceKey = sourceKey)

    companion object {
        fun buildStableKey(
            sourceType: String,
            sourceKey: String?,
        ): String {
            val type = sourceType.trim().lowercase()
            val key = sourceKey?.trim()?.takeIf { it.isNotBlank() }
            return if (key == null) type else "$type:$key"
        }
    }
}

object CustomPageConfigStore {
    private const val JSON_VERSION = 1
    private const val KEY_VERSION = "v"
    private const val KEY_ENABLED = "enabled"
    private const val KEY_TABS = "tabs"
    private const val KEY_SOURCE_TYPE = "type"
    private const val KEY_SOURCE_KEY = "key"

    fun parse(raw: String?): CustomPageConfig {
        val text = raw?.trim().orEmpty()
        if (text.isBlank()) return empty()
        val root = runCatching { JSONObject(text) }.getOrNull() ?: return empty()
        if (root.optInt(KEY_VERSION, JSON_VERSION) != JSON_VERSION) return empty()

        val enabled = root.optBoolean(KEY_ENABLED, false)
        val tabsArray = root.optJSONArray(KEY_TABS) ?: JSONArray()
        val tabs = ArrayList<CustomPageTabConfig>(tabsArray.length().coerceAtLeast(0))
        for (index in 0 until tabsArray.length()) {
            val item = tabsArray.optJSONObject(index) ?: continue
            val sourceType = item.optString(KEY_SOURCE_TYPE, "").trim().lowercase()
            if (sourceType.isBlank()) continue
            val sourceKey = item.optString(KEY_SOURCE_KEY, "").trim().takeIf { it.isNotBlank() }
            tabs.add(CustomPageTabConfig(sourceType = sourceType, sourceKey = sourceKey))
        }
        return normalize(CustomPageConfig(enabled = enabled, tabs = tabs))
    }

    fun serialize(config: CustomPageConfig): String {
        val normalized = normalize(config)
        val tabs = JSONArray()
        normalized.tabs.forEach { tab ->
            tabs.put(
                JSONObject()
                    .put(KEY_SOURCE_TYPE, tab.sourceType)
                    .put(KEY_SOURCE_KEY, tab.sourceKey),
            )
        }
        return JSONObject()
            .put(KEY_VERSION, JSON_VERSION)
            .put(KEY_ENABLED, normalized.enabled)
            .put(KEY_TABS, tabs)
            .toString()
    }

    fun normalize(config: CustomPageConfig): CustomPageConfig {
        if (config.tabs.isEmpty()) return CustomPageConfig(enabled = config.enabled, tabs = emptyList())
        val seen = HashSet<String>(config.tabs.size * 2)
        val normalizedTabs = ArrayList<CustomPageTabConfig>(config.tabs.size)
        config.tabs.forEach { tab ->
            val sourceType = tab.sourceType.trim().lowercase()
            if (sourceType.isBlank()) return@forEach
            val sourceKey = tab.sourceKey?.trim()?.takeIf { it.isNotBlank() }
            val normalizedTab = CustomPageTabConfig(sourceType = sourceType, sourceKey = sourceKey)
            if (seen.add(normalizedTab.stableKey())) {
                normalizedTabs.add(normalizedTab)
            }
        }
        return CustomPageConfig(enabled = config.enabled, tabs = normalizedTabs)
    }

    fun empty(): CustomPageConfig = CustomPageConfig(enabled = false, tabs = emptyList())
}
