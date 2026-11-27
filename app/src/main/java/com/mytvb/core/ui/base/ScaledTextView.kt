package com.mytvb.core.ui.base

import android.content.Context
import android.util.AttributeSet
import android.util.TypedValue
import android.widget.TextView
import androidx.appcompat.widget.AppCompatTextView

/**
 * 缩放感知 TextView：所有字号先乘 [UiTextScale.scale] 再落到控件。
 *
 * XML/textAppearance 在构造中读入的初始字号由 [syncFromInflation] 统一补乘
 * （由 [UiTextScaleFactory] 在 inflate 完成后调用）；代码中后续的 setTextSize
 * （含 Kotlin 的 `textSize = x` 赋值）在 setter 中拦截，内部始终记录"原始字号"，
 * 实际应用原始字号 × 系数，重复调用不会叠加放大。
 */
open class ScaledTextView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = android.R.attr.textViewStyle
) : AppCompatTextView(context, attrs, defStyleAttr) {

    /** true 时字号设置原样透传，不乘缩放系数（字幕有独立字号设置，与 UI 缩放解耦）。 */
    @Volatile
    var uiTextScaleExempt: Boolean = false

    private var rawTextSizePx = textSize

    /** XML inflate 完成后调用一次：构造里读入的初始字号补乘缩放系数。 */
    fun syncFromInflation() {
        if (uiTextScaleExempt) return
        rawTextSizePx = textSize
        applyScaled()
    }

    override fun setTextSize(size: Float) {
        if (uiTextScaleExempt) {
            super.setTextSize(size)
            return
        }
        rawTextSizePx = spToPx(size)
        applyScaled()
    }

    override fun setTextSize(unit: Int, size: Float) {
        if (uiTextScaleExempt) {
            super.setTextSize(unit, size)
            return
        }
        rawTextSizePx = if (unit == TypedValue.COMPLEX_UNIT_PX) {
            size
        } else {
            TypedValue.applyDimension(unit, size, resources.displayMetrics)
        }
        applyScaled()
    }

    private fun applyScaled() {
        // 以 px 写入绕开二次单位换算；系数为 1.0 时与原生行为一致
        super.setTextSize(TypedValue.COMPLEX_UNIT_PX, rawTextSizePx * UiTextScale.scale())
    }

    private fun spToPx(sp: Float): Float =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, sp, resources.displayMetrics)

    companion object {
        /** 对任意 TextView 打豁免标记；非 [ScaledTextView]（如未经工厂 inflate 的控件）安全无操作。 */
        fun exempt(view: TextView?) {
            (view as? ScaledTextView)?.uiTextScaleExempt = true
        }
    }
}
