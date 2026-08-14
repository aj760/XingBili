package blbl.cat3399.testutil.json

/**
 * Minimal JVM test-friendly implementation of Android's `org.json.JSONArray`.
 *
 * See [JSONObject] in the same package for rationale.
 */
class TestJSONArray {
    private val items: MutableList<Any?> = ArrayList()

    fun put(value: Any?): TestJSONArray {
        items.add(value)
        return this
    }

    fun length(): Int = items.size

    fun optJSONObject(index: Int): TestJSONObject? = items.getOrNull(index) as? TestJSONObject

    fun optJSONArray(index: Int): TestJSONArray? = items.getOrNull(index) as? TestJSONArray
}
