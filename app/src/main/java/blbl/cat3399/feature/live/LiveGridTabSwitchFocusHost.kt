package blbl.cat3399.feature.live

import blbl.cat3399.core.ui.TabContentSwitchFocusHost

/**
 * Implemented by fragments that host a TabLayout + ViewPager2 whose pages are [LiveGridFragment].
 * Used when switching tabs from inside the content area (e.g. DPAD_LEFT/RIGHT at list edges) to
 * move focus into the newly selected page content. Target page should restore its last focused
 * card when possible, and only fallback to first card when no history exists.
 */
interface LiveGridTabSwitchFocusHost : TabContentSwitchFocusHost {
    fun requestFocusCurrentPageFirstCardFromContentSwitch(): Boolean

    override fun requestFocusCurrentPagePrimaryItemFromContentSwitch(): Boolean = requestFocusCurrentPageFirstCardFromContentSwitch()
}
