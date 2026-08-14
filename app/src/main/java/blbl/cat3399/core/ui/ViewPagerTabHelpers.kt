package blbl.cat3399.core.ui

import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import androidx.viewpager2.widget.ViewPager2
import com.google.android.material.tabs.TabLayout

fun Fragment.findCurrentViewPagerChildFragment(viewPager: ViewPager2): Fragment? {
    val adapter = viewPager.adapter as? FragmentStateAdapter
    if (adapter != null) {
        val itemId = adapter.getItemId(viewPager.currentItem)
        childFragmentManager.findFragmentByTag("f$itemId")?.let { return it }
    }
    return childFragmentManager.fragments.firstOrNull { it.isVisible }
}

inline fun <reified T> Fragment.findCurrentViewPagerChildFragmentAs(viewPager: ViewPager2): T? {
    val current = findCurrentViewPagerChildFragment(viewPager)
    if (current is T) return current
    return childFragmentManager.fragments.firstOrNull { it.isVisible && it is T } as? T
}

fun TabLayout.requestFocusSelectedTab(
    fallbackPosition: Int = 0,
    isAlive: () -> Boolean,
): Boolean {
    val tabStrip = getChildAt(0) as? ViewGroup ?: return false
    val pos = selectedTabPosition.takeIf { it >= 0 } ?: fallbackPosition
    if (pos < 0 || pos >= tabStrip.childCount) return false
    postIfAlive(isAlive = isAlive) {
        tabStrip.getChildAt(pos)?.requestFocus()
    }
    return true
}
