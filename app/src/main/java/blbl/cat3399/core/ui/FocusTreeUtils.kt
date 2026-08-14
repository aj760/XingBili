package blbl.cat3399.core.ui

import android.view.View

object FocusTreeUtils {
    fun isDescendantOf(view: View, ancestor: View): Boolean {
        var current: View? = view
        while (current != null) {
            if (current === ancestor) return true
            current = current.parent as? View
        }
        return false
    }
}

