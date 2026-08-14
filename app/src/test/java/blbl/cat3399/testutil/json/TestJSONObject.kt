package blbl.cat3399.testutil.json

/**
 * Minimal JVM test-friendly implementation of Android's `org.json.JSONObject`.
 *
 * Android local unit tests run on the JVM and `android.jar` provides stubbed
 * framework classes. The real parsing logic in `core/api` uses `org.json`, so
 * tests provide this lightweight implementation to build JSON fixtures without
 * pulling extra dependencies from the network.
 */
class TestJSONObject {
    private val values: MutableMap<String, Any?> = LinkedHashMap()

    fun put(name: String, value: Any?): TestJSONObject {
        values[name] = value
        return this
    }

    fun optString(name: String): String = optString(name, "")

    fun optString(name: String, fallback: String): String {
        val v = values[name] ?: return fallback
        return when (v) {
            is String -> v
            is Number -> v.toString()
            is Boolean -> v.toString()
            else -> v.toString()
        }
    }

    fun optLong(name: String): Long = optLong(name, 0L)

    fun optLong(name: String, fallback: Long): Long {
        val v = values[name] ?: return fallback
        return when (v) {
            is Number -> v.toLong()
            is String -> v.trim().toLongOrNull() ?: fallback
            is Boolean -> if (v) 1L else 0L
            else -> fallback
        }
    }

    fun optInt(name: String): Int = optInt(name, 0)

    fun optInt(name: String, fallback: Int): Int {
        val v = values[name] ?: return fallback
        return when (v) {
            is Number -> v.toInt()
            is String -> v.trim().toIntOrNull() ?: fallback
            is Boolean -> if (v) 1 else 0
            else -> fallback
        }
    }

    fun optBoolean(name: String): Boolean = optBoolean(name, false)

    fun optBoolean(name: String, fallback: Boolean): Boolean {
        val v = values[name] ?: return fallback
        return when (v) {
            is Boolean -> v
            is Number -> v.toInt() != 0
            is String -> {
                val s = v.trim()
                when {
                    s.equals("true", ignoreCase = true) -> true
                    s.equals("false", ignoreCase = true) -> false
                    else -> s.toIntOrNull()?.let { it != 0 } ?: fallback
                }
            }
            else -> fallback
        }
    }

    fun optJSONObject(name: String): TestJSONObject? = values[name] as? TestJSONObject

    fun optJSONArray(name: String): TestJSONArray? = values[name] as? TestJSONArray
}
