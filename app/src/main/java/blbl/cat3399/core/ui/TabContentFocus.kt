package blbl.cat3399.core.ui

interface TabContentSwitchFocusHost {
    fun requestFocusCurrentPagePrimaryItemFromContentSwitch(): Boolean
}

interface TabContentFocusTarget {
    fun requestFocusPrimaryItemFromTab(): Boolean

    fun requestFocusPrimaryItemFromContentSwitch(): Boolean

    fun requestFocusPrimaryItemFromBackToTab0(): Boolean = requestFocusPrimaryItemFromContentSwitch()
}
