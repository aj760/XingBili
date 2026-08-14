package blbl.cat3399.core.ui.popup

import android.content.Context
import android.util.AttributeSet
import android.view.View
import android.widget.LinearLayout
import blbl.cat3399.R
import kotlin.math.max

internal class PopupModalLayout
    @JvmOverloads
    constructor(
        context: Context,
        attrs: AttributeSet? = null,
    ) : LinearLayout(context, attrs) {
        var maxHeightPx: Int = 0
            set(value) {
                val normalized = value.coerceAtLeast(0)
                if (field == normalized) return
                field = normalized
                requestLayout()
            }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            if (orientation != VERTICAL || maxHeightPx <= 0) {
                super.onMeasure(widthMeasureSpec, heightMeasureSpec)
                return
            }

            val contentView = findViewById<View>(R.id.content)
            var usedHeight = paddingTop + paddingBottom
            var maxWidth = paddingLeft + paddingRight
            var childState = 0

            fun measureChild(view: View, maxChildHeight: Int) {
                val lp = view.layoutParams as MarginLayoutParams
                val childWidthSpec =
                    getChildMeasureSpec(
                        widthMeasureSpec,
                        paddingLeft + paddingRight + lp.leftMargin + lp.rightMargin,
                        lp.width,
                    )
                val safeMaxChildHeight = maxChildHeight.coerceAtLeast(0)
                val childHeightSpec =
                    when {
                        lp.height >= 0 -> MeasureSpec.makeMeasureSpec(lp.height.coerceAtMost(safeMaxChildHeight), MeasureSpec.EXACTLY)
                        else -> MeasureSpec.makeMeasureSpec(safeMaxChildHeight, MeasureSpec.AT_MOST)
                    }
                view.measure(childWidthSpec, childHeightSpec)
                usedHeight += lp.topMargin + view.measuredHeight + lp.bottomMargin
                maxWidth =
                    max(
                        maxWidth,
                        paddingLeft + paddingRight + lp.leftMargin + view.measuredWidth + lp.rightMargin,
                    )
                childState = combineMeasuredStates(childState, view.measuredState)
            }

            for (i in 0 until childCount) {
                val child = getChildAt(i) ?: continue
                if (child.visibility == GONE || child === contentView) continue
                val lp = child.layoutParams as MarginLayoutParams
                val availableHeight = maxHeightPx - usedHeight - lp.topMargin - lp.bottomMargin
                measureChild(child, availableHeight)
            }

            if (contentView != null && contentView.visibility != GONE) {
                val lp = contentView.layoutParams as MarginLayoutParams
                val availableHeight = maxHeightPx - usedHeight - lp.topMargin - lp.bottomMargin
                measureChild(contentView, availableHeight)
            }

            val measuredWidth =
                resolveSizeAndState(
                    max(maxWidth, suggestedMinimumWidth),
                    widthMeasureSpec,
                    childState,
                )
            val measuredHeight =
                resolveSizeAndState(
                    max(usedHeight, suggestedMinimumHeight).coerceAtMost(maxHeightPx),
                    heightMeasureSpec,
                    childState shl MEASURED_HEIGHT_STATE_SHIFT,
                )
            setMeasuredDimension(measuredWidth, measuredHeight)
        }
    }
