package blbl.cat3399.feature.player.danmaku.model

internal class RenderSnapshot(
    var positionMs: Long = 0L,
) {
    var items: Array<DanmakuItem?> = emptyArray()
        private set

    var x: FloatArray = FloatArray(0)
        private set

    var yTop: FloatArray = FloatArray(0)
        private set

    var textWidth: FloatArray = FloatArray(0)
        private set

    var count: Int = 0
    var pendingCount: Int = 0
    var nextAtMs: Int? = null

    fun ensureCapacity(required: Int) {
        if (required <= items.size) return
        val cap = required.coerceAtLeast(items.size * 2 + 8)
        items = arrayOfNulls(cap)
        x = FloatArray(cap)
        yTop = FloatArray(cap)
        textWidth = FloatArray(cap)
    }

    fun clear() {
        for (i in 0 until count) {
            items[i] = null
        }
        count = 0
        pendingCount = 0
        nextAtMs = null
        positionMs = 0L
    }

    companion object {
        val EMPTY = RenderSnapshot()
    }
}

