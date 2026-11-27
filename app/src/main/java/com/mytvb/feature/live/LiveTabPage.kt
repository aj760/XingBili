package com.mytvb.feature.live

import com.mytvb.core.ui.focus.TabContentFocusTarget

interface LiveTabPage : TabContentFocusTarget {
    fun scrollToTop()

    fun onTabSelected() {}

    fun onExplicitRefresh() {
        onReselected()
    }

    fun onReselected() {}
}
