package blbl.cat3399.feature.video

import blbl.cat3399.core.model.VideoCard

object VideoCardVisibilityFilter {
    private val hiddenStableKeys = HashSet<String>()

    fun filterVisible(cards: List<VideoCard>): List<VideoCard> {
        if (cards.isEmpty()) return emptyList()
        val hidden = hiddenSnapshot()
        if (hidden.isEmpty()) return cards
        return cards.filter { card -> !hidden.contains(card.stableKey()) }
    }

    fun filterVisibleFresh(
        cards: List<VideoCard>,
        loadedStableKeys: Set<String>,
    ): List<VideoCard> {
        val hidden = hiddenSnapshot()
        val seen = HashSet<String>(cards.size)
        return cards.filter { card ->
            val stableKey = card.stableKey()
            if (hidden.contains(stableKey)) return@filter false
            if (loadedStableKeys.contains(stableKey)) return@filter false
            if (!seen.add(stableKey)) return@filter false
            true
        }
    }

    fun hide(stableKey: String) {
        if (stableKey.isBlank()) return
        synchronized(hiddenStableKeys) {
            hiddenStableKeys.add(stableKey)
        }
    }

    internal fun clearSessionState() {
        synchronized(hiddenStableKeys) {
            hiddenStableKeys.clear()
        }
    }

    private fun hiddenSnapshot(): Set<String> {
        synchronized(hiddenStableKeys) {
            if (hiddenStableKeys.isEmpty()) return emptySet()
            return HashSet(hiddenStableKeys)
        }
    }
}
