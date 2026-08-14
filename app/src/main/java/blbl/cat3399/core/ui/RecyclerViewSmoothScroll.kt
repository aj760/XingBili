package blbl.cat3399.core.ui

import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView

internal fun RecyclerView.smoothScrollToPositionStart(position: Int) {
    val lm = layoutManager as? LinearLayoutManager
    if (lm == null) {
        smoothScrollToPosition(position)
        return
    }

    val scroller =
        object : LinearSmoothScroller(context) {
            override fun getVerticalSnapPreference(): Int = SNAP_TO_START

            override fun getHorizontalSnapPreference(): Int = SNAP_TO_START
        }
    scroller.targetPosition = position
    lm.startSmoothScroll(scroller)
}

