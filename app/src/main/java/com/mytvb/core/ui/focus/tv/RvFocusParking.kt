package com.mytvb.core.ui.focus.tv

import android.os.Build
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView 焦点停泊（park focus）的共享状态原语。
 *
 * 停泊场景：布局/刷新期间持焦 child 可能被 detach，临时把焦点收拢到 RV 自身，
 * 防止框架把焦点落到意外位置；布局完成后再恢复到目标 child。
 * 本类只负责「停泊覆盖值」的保存与恢复，不负责焦点请求本身（时机与重试由调用方编排）：
 * - descendantFocusability → FOCUS_BEFORE_DESCENDANTS（先收拢再交给 child）
 * - isFocusable → true（可选：RV 平时不可聚焦时需要，否则 requestFocus 必然失败）
 * - defaultFocusHighlightEnabled → false（停泊期间不画 RV 级焦点框，API O 以上）
 *
 * 注意 [clearParkOverrides] 刻意不恢复高亮：停泊解除时焦点往往还在 RV 上，
 * 高亮需等焦点真正落到 child（或离开 RV）后再恢复，由调用方调
 * [restoreDefaultFocusHighlightIfNeeded]。
 *
 * 此前 TvListFocusController 与 RecyclerViewLoadMoreFocusController 各持一份
 * 保存/恢复代码，行为易漂移，收敛至此。
 */
internal class RvFocusParking(
    private val recyclerView: RecyclerView,
    private val overrideRecyclerFocusable: Boolean
) {

    private var parkedDescendantFocusability: Int? = null
    private var parkedRecyclerFocusable: Boolean? = null
    private var parkedDefaultFocusHighlightEnabled: Boolean? = null

    /** 保存并应用停泊覆盖值；重复调用幂等（首次保存的原值才是恢复依据）。 */
    fun applyParkOverrides() {
        if (parkedDescendantFocusability == null) {
            parkedDescendantFocusability = recyclerView.descendantFocusability
            recyclerView.descendantFocusability = ViewGroup.FOCUS_BEFORE_DESCENDANTS
        }
        if (overrideRecyclerFocusable && parkedRecyclerFocusable == null) {
            parkedRecyclerFocusable = recyclerView.isFocusable
            recyclerView.isFocusable = true
        }
        suppressDefaultFocusHighlight()
    }

    /** 恢复 descendantFocusability / isFocusable；高亮恢复独立调用。 */
    fun clearParkOverrides() {
        parkedDescendantFocusability?.let { original ->
            recyclerView.descendantFocusability = original
            parkedDescendantFocusability = null
        }
        parkedRecyclerFocusable?.let { original ->
            recyclerView.isFocusable = original
            parkedRecyclerFocusable = null
        }
    }

    /** 保存并在开启时关闭默认焦点高亮；首次调用保存原值。 */
    fun suppressDefaultFocusHighlight() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        if (parkedDefaultFocusHighlightEnabled == null) {
            parkedDefaultFocusHighlightEnabled = recyclerView.defaultFocusHighlightEnabled
        }
        if (recyclerView.defaultFocusHighlightEnabled) {
            recyclerView.defaultFocusHighlightEnabled = false
        }
    }

    fun restoreDefaultFocusHighlightIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val original = parkedDefaultFocusHighlightEnabled ?: return
        recyclerView.defaultFocusHighlightEnabled = original
        parkedDefaultFocusHighlightEnabled = null
    }
}
