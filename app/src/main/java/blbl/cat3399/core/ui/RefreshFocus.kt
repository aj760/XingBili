package blbl.cat3399.core.ui

import androidx.recyclerview.widget.RecyclerView

/**
 * Helpers for "refresh" focus behavior:
 * - During refresh, we don't try to restore the exact previous card.
 * - After data is applied, content should deterministically realign to the first item (position 0).
 * - Focus is only pulled into the RecyclerView when it was already inside the RecyclerView
 *   (or focus is currently null). Otherwise we keep focus in the current container, such as sidebar.
 */
internal fun RecyclerView.requestFocusFirstItemOrSelfAfterRefresh(
    itemCount: Int,
    isAlive: () -> Boolean,
    smoothScroll: Boolean = false,
    onDone: (focusedFirstItem: Boolean) -> Unit = {},
): Boolean {
    val focused = rootView?.findFocus()
    val canMoveFocus = focused == null || FocusTreeUtils.isDescendantOf(focused, this)

    if (itemCount <= 0) {
        if (canMoveFocus) requestFocus()
        onDone(false)
        return canMoveFocus
    }

    if (!canMoveFocus) {
        if (smoothScroll) smoothScrollToPosition(0) else scrollToPosition(0)
        onDone(false)
        return true
    }

    return requestFocusAdapterPositionReliable(
        position = 0,
        smoothScroll = smoothScroll,
        isAlive = isAlive,
        onFocused = { onDone(true) },
    )
}

internal fun parkFocusForDataSetReset(vararg controllers: DpadGridController?) {
    for (c in controllers) c?.parkFocusForDataSetReset()
}

internal fun unparkFocusAfterDataSetReset(vararg controllers: DpadGridController?) {
    for (c in controllers) c?.unparkFocusAfterDataSetReset()
}
