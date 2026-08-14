package blbl.cat3399.feature.live

import blbl.cat3399.core.ui.TabContentFocusTarget

interface LivePageFocusTarget : TabContentFocusTarget {
    // Entering content from a focused tab item: focus first card.
    fun requestFocusFirstCardFromTab(): Boolean

    // Switching tabs from content edge: restore last focused card when possible, fallback to first card.
    fun requestFocusFirstCardFromContentSwitch(): Boolean

    // Returning to tab0 content via Back key (scheme B): always focus the first card deterministically.
    fun requestFocusFirstCardFromBackToTab0(): Boolean = requestFocusFirstCardFromContentSwitch()

    override fun requestFocusPrimaryItemFromTab(): Boolean = requestFocusFirstCardFromTab()

    override fun requestFocusPrimaryItemFromContentSwitch(): Boolean = requestFocusFirstCardFromContentSwitch()

    override fun requestFocusPrimaryItemFromBackToTab0(): Boolean = requestFocusFirstCardFromBackToTab0()
}
