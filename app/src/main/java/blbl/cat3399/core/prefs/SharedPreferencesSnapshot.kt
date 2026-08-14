package blbl.cat3399.core.prefs

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

internal object SharedPreferencesSnapshot {
    private const val KEY_TYPE = "type"
    private const val KEY_VALUE = "value"

    private const val TYPE_BOOLEAN = "boolean"
    private const val TYPE_INT = "int"
    private const val TYPE_LONG = "long"
    private const val TYPE_FLOAT = "float"
    private const val TYPE_STRING = "string"
    private const val TYPE_STRING_SET = "string_set"

    fun encode(
        prefs: SharedPreferences,
        includeKeys: Set<String>? = null,
        excludeKeys: Set<String> = emptySet(),
    ): JSONObject = encode(prefs.all.filterKeys { allowKey(it, includeKeys, excludeKeys) })

    fun encode(entries: Map<String, *>): JSONObject {
        val root = JSONObject()
        for (key in entries.keys.sorted()) {
            val value = entries[key] ?: continue
            root.put(key, encodeValue(key = key, value = value))
        }
        return root
    }

    fun replaceAll(
        prefs: SharedPreferences,
        root: JSONObject,
        includeKeys: Set<String>? = null,
        excludeKeys: Set<String> = emptySet(),
    ) {
        val decoded = decode(root)
        val editor = prefs.edit()
        for (key in prefs.all.keys) {
            if (allowKey(key, includeKeys, excludeKeys)) {
                editor.remove(key)
            }
        }
        for ((key, value) in decoded) {
            if (!allowKey(key, includeKeys, excludeKeys)) continue
            when (value) {
                is Boolean -> editor.putBoolean(key, value)
                is Int -> editor.putInt(key, value)
                is Long -> editor.putLong(key, value)
                is Float -> editor.putFloat(key, value)
                is String -> editor.putString(key, value)
                is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                else -> error("不支持导入的配置类型: key=$key type=${value.javaClass.name}")
            }
        }
        check(editor.commit()) { "保存配置失败" }
    }

    private fun decode(root: JSONObject): Map<String, Any> {
        val out = LinkedHashMap<String, Any>()
        val keys = ArrayList<String>(root.length())
        val it = root.keys()
        while (it.hasNext()) {
            keys.add(it.next())
        }
        keys.sort()
        for (key in keys) {
            val entry = root.optJSONObject(key) ?: error("配置项格式无效: $key")
            out[key] = decodeValue(key = key, entry = entry)
        }
        return out
    }

    private fun encodeValue(
        key: String,
        value: Any,
    ): JSONObject {
        return when (value) {
            is Boolean -> typedEntry(type = TYPE_BOOLEAN, value = value)
            is Int -> typedEntry(type = TYPE_INT, value = value)
            is Long -> typedEntry(type = TYPE_LONG, value = value)
            is Float -> typedEntry(type = TYPE_FLOAT, value = value)
            is String -> typedEntry(type = TYPE_STRING, value = value)
            is Set<*> ->
                typedEntry(
                    type = TYPE_STRING_SET,
                    value =
                        JSONArray().also { array ->
                            value
                                .map { it as? String ?: error("不支持导出的字符串集合类型: key=$key") }
                                .sorted()
                                .forEach(array::put)
                        },
                )
            else -> error("不支持导出的配置类型: key=$key type=${value.javaClass.name}")
        }
    }

    private fun decodeValue(
        key: String,
        entry: JSONObject,
    ): Any {
        val value = entry.opt(KEY_VALUE)
        return when (entry.optString(KEY_TYPE, "").trim()) {
            TYPE_BOOLEAN ->
                when (value) {
                    is Boolean -> value
                    is Number -> value.toInt() != 0
                    is String ->
                        when (value.trim().lowercase()) {
                            "1", "true" -> true
                            "0", "false" -> false
                            else -> error("布尔配置项无效: $key")
                        }

                    else -> error("布尔配置项无效: $key")
                }

            TYPE_INT ->
                (when (value) {
                    is Number -> value.toInt()
                    is String -> value.trim().toIntOrNull()
                    else -> null
                } ?: error("整型配置项无效: $key"))

            TYPE_LONG ->
                (when (value) {
                    is Number -> value.toLong()
                    is String -> value.trim().toLongOrNull()
                    else -> null
                } ?: error("长整型配置项无效: $key"))

            TYPE_FLOAT ->
                (when (value) {
                    is Number -> value.toFloat()
                    is String -> value.trim().toFloatOrNull()
                    else -> null
                }?.takeIf { it.isFinite() } ?: error("浮点配置项无效: $key"))

            TYPE_STRING -> value as? String ?: error("字符串配置项无效: $key")
            TYPE_STRING_SET -> {
                val array = entry.optJSONArray(KEY_VALUE) ?: error("字符串集合配置项无效: $key")
                val values = LinkedHashSet<String>(array.length())
                for (index in 0 until array.length()) {
                    values.add(array.opt(index) as? String ?: error("字符串集合配置项无效: $key"))
                }
                values
            }

            else -> error("不支持的配置项类型: key=$key type=${entry.optString(KEY_TYPE, "").trim()}")
        }
    }

    private fun typedEntry(
        type: String,
        value: Any,
    ): JSONObject {
        return JSONObject()
            .put(KEY_TYPE, type)
            .put(KEY_VALUE, value)
    }

    private fun allowKey(
        key: String,
        includeKeys: Set<String>?,
        excludeKeys: Set<String>,
    ): Boolean {
        if (key in excludeKeys) return false
        return includeKeys == null || key in includeKeys
    }
}
