package blbl.cat3399.feature.custom

import android.os.Bundle
import android.os.SystemClock
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.viewpager2.adapter.FragmentStateAdapter
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.prefs.AppPrefs
import blbl.cat3399.core.ui.TabContentFocusTarget
import blbl.cat3399.core.ui.TabContentSwitchFocusHost
import blbl.cat3399.core.ui.enableDpadTabFocus
import blbl.cat3399.core.ui.findCurrentViewPagerChildFragmentAs
import blbl.cat3399.core.ui.postDelayedIfAlive
import blbl.cat3399.core.ui.postIfAlive
import blbl.cat3399.core.ui.requestFocusSelectedTab
import blbl.cat3399.databinding.FragmentCustomPageBinding
import blbl.cat3399.ui.BackPressHandler
import blbl.cat3399.ui.RefreshKeyHandler
import blbl.cat3399.ui.SidebarFocusHost
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator

class CustomPageFragment : Fragment(), TabContentSwitchFocusHost, BackPressHandler {
    private var _binding: FragmentCustomPageBinding? = null
    private val binding get() = _binding!!

    private var mediator: TabLayoutMediator? = null
    private var pageCallback: androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback? = null
    private var tabs: List<CustomPageResolvedTab> = emptyList()

    private var pendingFocusPrimaryItemFromContentSwitch: Boolean = false
    private var pendingFocusPrimaryItemFromBackToTab0: Boolean = false
    private var pendingBackToTab0RequestToken: Int = 0
    private var pendingBackToTab0AttemptsLeft: Int = 0

    private val tabReselectRefreshListener: TabLayout.OnTabSelectedListener =
        object : TabLayout.OnTabSelectedListener {
            override fun onTabSelected(tab: TabLayout.Tab) = Unit

            override fun onTabUnselected(tab: TabLayout.Tab) = Unit

            override fun onTabReselected(tab: TabLayout.Tab) {
                refreshCurrentPageFromTabReselect()
            }
        }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentCustomPageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        syncTabs(force = true)
    }

    override fun onResume() {
        super.onResume()
        syncTabs(force = false)
    }

    override fun requestFocusCurrentPagePrimaryItemFromContentSwitch(): Boolean {
        if (tabs.isEmpty()) return false
        pendingFocusPrimaryItemFromContentSwitch = true
        if (focusCurrentPagePrimaryItemFromContentSwitch()) {
            pendingFocusPrimaryItemFromContentSwitch = false
        }
        return true
    }

    override fun handleBackPressed(): Boolean {
        val b = _binding ?: return false
        if (tabs.isEmpty()) {
            return (activity as? SidebarFocusHost)?.requestFocusSidebarSelectedNav() == true
        }

        val scheme = BiliClient.prefs.mainBackFocusScheme
        if (b.tabLayout.hasFocus()) {
            return (activity as? SidebarFocusHost)?.requestFocusSidebarSelectedNav() == true
        }

        val inContent = b.viewPager.hasFocus() && !b.tabLayout.hasFocus()
        if (!inContent) return false

        val pageBackHandler = findCurrentViewPagerChildFragmentAs<BackPressHandler>(b.viewPager)
        if (pageBackHandler?.handleBackPressed() == true) return true

        return when (scheme) {
            AppPrefs.MAIN_BACK_FOCUS_SCHEME_A -> focusSelectedTab()
            AppPrefs.MAIN_BACK_FOCUS_SCHEME_B -> {
                if (b.viewPager.currentItem != 0) {
                    pendingFocusPrimaryItemFromBackToTab0 = true
                    pendingFocusPrimaryItemFromContentSwitch = false
                    pendingBackToTab0RequestToken++
                    pendingBackToTab0AttemptsLeft = 30
                    b.viewPager.setCurrentItem(0, false)
                    true
                } else {
                    (activity as? SidebarFocusHost)?.requestFocusSidebarSelectedNav() == true
                }
            }

            AppPrefs.MAIN_BACK_FOCUS_SCHEME_C -> {
                (activity as? SidebarFocusHost)?.requestFocusSidebarSelectedNav() == true
            }

            else -> focusSelectedTab()
        }
    }

    override fun onDestroyView() {
        mediator?.detach()
        mediator = null
        pageCallback?.let { binding.viewPager.unregisterOnPageChangeCallback(it) }
        pageCallback = null
        binding.tabLayout.removeOnTabSelectedListener(tabReselectRefreshListener)
        tabs = emptyList()
        _binding = null
        super.onDestroyView()
    }

    private fun syncTabs(force: Boolean) {
        val b = _binding ?: return
        val nextTabs = CustomPageTabRegistry.resolvedTabs(BiliClient.prefs.customPageConfig)
        val currentKey = tabs.getOrNull(b.viewPager.currentItem)?.stableKey
        val sameTabs = !force && tabs.map { it.stableKey } == nextTabs.map { it.stableKey }
        if (sameTabs) return

        tabs = nextTabs
        if (nextTabs.isEmpty()) {
            showEmptyState()
            return
        }

        val selectedIndex =
            currentKey?.let { key ->
                nextTabs.indexOfFirst { it.stableKey == key }.takeIf { it >= 0 }
            } ?: 0
        showTabs(nextTabs, selectedIndex)
    }

    private fun showEmptyState() {
        val b = _binding ?: return
        mediator?.detach()
        mediator = null
        pageCallback?.let { b.viewPager.unregisterOnPageChangeCallback(it) }
        pageCallback = null
        b.tabLayout.removeOnTabSelectedListener(tabReselectRefreshListener)
        b.tabLayout.visibility = View.GONE
        b.viewPager.visibility = View.GONE
        b.tvEmpty.visibility = View.VISIBLE
        b.viewPager.adapter = null
    }

    private fun showTabs(
        list: List<CustomPageResolvedTab>,
        selectedIndex: Int,
    ) {
        val b = _binding ?: return
        b.tvEmpty.visibility = View.GONE
        b.tabLayout.visibility = View.VISIBLE
        b.viewPager.visibility = View.VISIBLE

        mediator?.detach()
        mediator = null
        pageCallback?.let { b.viewPager.unregisterOnPageChangeCallback(it) }
        pageCallback = null

        b.viewPager.adapter = CustomPagePagerAdapter(this, list)
        mediator =
            TabLayoutMediator(b.tabLayout, b.viewPager) { tab, position ->
                tab.text = list.getOrNull(position)?.title ?: ""
            }.also { it.attach() }
        b.viewPager.setCurrentItem(selectedIndex.coerceIn(0, list.lastIndex), false)

        b.tabLayout.removeOnTabSelectedListener(tabReselectRefreshListener)
        b.tabLayout.addOnTabSelectedListener(tabReselectRefreshListener)

        val tabLayout = b.tabLayout
        tabLayout.postIfAlive(isAlive = { _binding === b }) {
            tabLayout.enableDpadTabFocus(selectOnFocusProvider = { BiliClient.prefs.tabSwitchFollowsFocus }) { position ->
                val title = list.getOrNull(position)?.title
                AppLog.d("CustomPage", "tab focus pos=$position title=$title t=${SystemClock.uptimeMillis()}")
            }
            val tabStrip = tabLayout.getChildAt(0) as? ViewGroup ?: return@postIfAlive
            for (i in 0 until tabStrip.childCount) {
                tabStrip.getChildAt(i).setOnKeyListener { _, keyCode, event ->
                    if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_DOWN) {
                        focusCurrentPagePrimaryItemFromTab()
                        return@setOnKeyListener true
                    }
                    false
                }
            }
        }

        pageCallback =
            object : androidx.viewpager2.widget.ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    val tab = list.getOrNull(position)
                    AppLog.d("CustomPage", "page selected pos=$position title=${tab?.title} t=${SystemClock.uptimeMillis()}")
                    if (pendingFocusPrimaryItemFromBackToTab0) {
                        maybeRequestTab0FocusFromBackToTab0()
                    } else if (pendingFocusPrimaryItemFromContentSwitch) {
                        if (focusCurrentPagePrimaryItemFromContentSwitch()) {
                            pendingFocusPrimaryItemFromContentSwitch = false
                        }
                    }
                }
            }.also { b.viewPager.registerOnPageChangeCallback(it) }
    }

    private fun refreshCurrentPageFromTabReselect(): Boolean {
        val page = findCurrentViewPagerChildFragmentAs<RefreshKeyHandler>(binding.viewPager) ?: return false
        return page.handleRefreshKey()
    }

    private fun focusCurrentPagePrimaryItemFromTab(): Boolean {
        val page = findCurrentViewPagerChildFragmentAs<TabContentFocusTarget>(binding.viewPager) ?: return false
        return page.requestFocusPrimaryItemFromTab()
    }

    private fun focusCurrentPagePrimaryItemFromContentSwitch(): Boolean {
        val page = findCurrentViewPagerChildFragmentAs<TabContentFocusTarget>(binding.viewPager) ?: return false
        return page.requestFocusPrimaryItemFromContentSwitch()
    }

    private fun maybeRequestTab0FocusFromBackToTab0(): Boolean {
        val b = _binding ?: return false
        if (!pendingFocusPrimaryItemFromBackToTab0) return false
        if (b.viewPager.currentItem != 0) return false

        val tab0 = findCurrentViewPagerChildFragmentAs<TabContentFocusTarget>(b.viewPager)
        if (tab0 != null) {
            tab0.requestFocusPrimaryItemFromBackToTab0()
            pendingFocusPrimaryItemFromBackToTab0 = false
            pendingBackToTab0AttemptsLeft = 0
            return true
        }

        if (pendingBackToTab0AttemptsLeft <= 0) return false
        val token = pendingBackToTab0RequestToken
        pendingBackToTab0AttemptsLeft--
        b.viewPager.postDelayedIfAlive(
            delayMillis = 16L,
            isAlive = { _binding === b && pendingFocusPrimaryItemFromBackToTab0 && pendingBackToTab0RequestToken == token },
        ) {
            maybeRequestTab0FocusFromBackToTab0()
        }
        return true
    }

    private fun focusSelectedTab(): Boolean {
        val b = _binding ?: return false
        return b.tabLayout.requestFocusSelectedTab(fallbackPosition = b.viewPager.currentItem) { _binding != null }
    }

    private class CustomPagePagerAdapter(
        fragment: Fragment,
        private val tabs: List<CustomPageResolvedTab>,
    ) : FragmentStateAdapter(fragment) {
        override fun getItemCount(): Int = tabs.size

        override fun createFragment(position: Int): Fragment = tabs[position].createFragment()

        override fun getItemId(position: Int): Long = tabs[position].stableKey.hashCode().toLong()

        override fun containsItem(itemId: Long): Boolean = tabs.any { it.stableKey.hashCode().toLong() == itemId }
    }

    companion object {
        fun newInstance() = CustomPageFragment()
    }
}
