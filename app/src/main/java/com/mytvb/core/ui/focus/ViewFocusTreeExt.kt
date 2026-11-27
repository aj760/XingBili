package com.mytvb.core.ui.focus

import android.view.View
import androidx.recyclerview.widget.RecyclerView

/**
 * 视图子树判断的共享实现。
 *
 * 此前 `isDescendantOf` 在 11 个类里各有一份私有拷贝（焦点控制器、各列表页），
 * 行为本应一致却易漂移，收敛到此。
 */
fun View?.isDescendantOf(ancestor: View?): Boolean {
    if (this == null || ancestor == null) {
        return false
    }
    var current: View? = this
    while (current != null) {
        if (current === ancestor) return true
        current = current.parent as? View
    }
    return false
}

/**
 * 当前真实焦点是否落在该 RecyclerView 内。
 *
 * 用途：焦点恢复成功的判据——`requestFocus()` 返回值不可靠（touch mode /
 * view 未 attach / 转场动画期间内部失败也返回 true），必须以真实落点为准。
 * 与 `TvListFocusController.hasFocusInList` 语义相同，供没有 controller 的页面复用。
 */
fun RecyclerView.hasFocusInChildren(): Boolean {
    return rootView?.findFocus().isDescendantOf(this)
}
