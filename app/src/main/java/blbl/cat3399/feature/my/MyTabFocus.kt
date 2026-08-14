package blbl.cat3399.feature.my

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import blbl.cat3399.R
import blbl.cat3399.core.ui.TabContentSwitchFocusHost
import blbl.cat3399.core.ui.postIfAttached
import com.google.android.material.tabs.TabLayout

private fun Fragment.myTabAncestors(): Sequence<Fragment> = generateSequence(parentFragment) { it.parentFragment }

fun Fragment.myTabLayout(): TabLayout? {
    return myTabAncestors()
        .mapNotNull { it.view?.findViewById<TabLayout?>(R.id.tab_layout) }
        .firstOrNull()
}

private fun Fragment.findMyTabSwitchFocusHost(): TabContentSwitchFocusHost? {
    return myTabAncestors()
        .filterIsInstance<TabContentSwitchFocusHost>()
        .firstOrNull()
}

fun Fragment.focusSelectedMyTabIfAvailable(): Boolean {
    val tabLayout = myTabLayout() ?: return false
    val tabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return false
    val pos = tabLayout.selectedTabPosition.takeIf { it >= 0 } ?: 0
    tabLayout.postIfAttached { tabStrip.getChildAt(pos)?.requestFocus() }
    return true
}

fun Fragment.switchToNextMyTabFromContentEdge(): Boolean {
    val tabLayout = myTabLayout() ?: return false
    val tabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return false
    val cur = tabLayout.selectedTabPosition.takeIf { it >= 0 } ?: 0
    val next = cur + 1
    if (next >= tabLayout.tabCount) return false
    tabLayout.getTabAt(next)?.select() ?: return false
    tabLayout.postIfAttached {
        findMyTabSwitchFocusHost()?.requestFocusCurrentPagePrimaryItemFromContentSwitch()
            ?: tabStrip.getChildAt(next)?.requestFocus()
    }
    return true
}

fun Fragment.switchToPrevMyTabFromContentEdge(): Boolean {
    val tabLayout = myTabLayout() ?: return false
    val tabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return false
    val cur = tabLayout.selectedTabPosition.takeIf { it >= 0 } ?: 0
    val prev = cur - 1
    if (prev < 0) return false
    tabLayout.getTabAt(prev)?.select() ?: return false
    tabLayout.postIfAttached {
        findMyTabSwitchFocusHost()?.requestFocusCurrentPagePrimaryItemFromContentSwitch()
            ?: tabStrip.getChildAt(prev)?.requestFocus()
    }
    return true
}
