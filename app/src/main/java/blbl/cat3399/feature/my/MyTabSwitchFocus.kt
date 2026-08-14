package blbl.cat3399.feature.my

import blbl.cat3399.core.ui.TabContentFocusTarget
import blbl.cat3399.core.ui.TabContentSwitchFocusHost

interface MyTabContentSwitchFocusHost : TabContentSwitchFocusHost {
    fun requestFocusCurrentPageFirstItemFromContentSwitch(): Boolean

    override fun requestFocusCurrentPagePrimaryItemFromContentSwitch(): Boolean = requestFocusCurrentPageFirstItemFromContentSwitch()
}

interface MyTabSwitchFocusTarget : TabContentFocusTarget {
    fun requestFocusFirstItemFromTabSwitch(): Boolean

    override fun requestFocusPrimaryItemFromTab(): Boolean = requestFocusFirstItemFromTabSwitch()

    override fun requestFocusPrimaryItemFromContentSwitch(): Boolean = requestFocusFirstItemFromTabSwitch()
}
