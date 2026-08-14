package blbl.cat3399.core.ui

object GridSpanPolicy {
    private const val MIN_SPAN = 1
    private const val MAX_SPAN = 6

    fun fixedSpanCountForWidthDp(widthDp: Float, overrideSpanCount: Int): Int {
        if (overrideSpanCount > 0) return overrideSpanCount.coerceIn(MIN_SPAN, MAX_SPAN)
        return when {
            widthDp >= 1100f -> 4
            widthDp >= 800f -> 3
            else -> 2
        }
    }

    fun dynamicSpanCountForWidthDp(widthDp: Float, dynamicOverrideSpanCount: Int, globalOverrideSpanCount: Int): Int {
        if (dynamicOverrideSpanCount > 0) return dynamicOverrideSpanCount.coerceIn(MIN_SPAN, MAX_SPAN)
        return fixedSpanCountForWidthDp(widthDp = widthDp, overrideSpanCount = globalOverrideSpanCount)
    }

    fun autoSpanCountForWidthDp(
        widthDp: Float,
        overrideSpanCount: Int,
        uiScale: Float,
        minCardWidthDp: Float = 210f,
        minSpan: Int = 2,
        maxSpan: Int = MAX_SPAN,
    ): Int {
        if (overrideSpanCount > 0) return overrideSpanCount.coerceIn(MIN_SPAN, MAX_SPAN)
        val minWidthDp = minCardWidthDp * uiScale
        val raw = (widthDp / minWidthDp).toInt()
        return raw.coerceIn(minSpan, maxSpan)
    }
}

