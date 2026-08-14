package blbl.cat3399.core.ui.focus

import android.content.Context
import android.graphics.drawable.GradientDrawable
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import blbl.cat3399.R
import blbl.cat3399.core.net.BiliClient
import java.util.WeakHashMap

/**
 * TV D-Pad focus hint overlay.
 *
 * When a [View] attached via [attach] gains focus, after a small delay a small rounded bubble
 * appears beneath it (or above, if it sits near the bottom of the screen) showing the supplied
 * text. Useful for icon-only buttons whose function is not obvious without a label.
 *
 * Reads `BiliClient.prefs.focusHintEnabled` on every show attempt; toggling that flag in
 * Settings takes effect immediately without re-attaching anything.
 */
class FocusHintOverlay private constructor(
    private val activity: ComponentActivity,
) {
    private val mainHandler = Handler(Looper.getMainLooper())
    private val attached = HashMap<View, CharSequence>()
    private var container: FrameLayout? = null
    private var bubble: TextView? = null
    private var pendingShow: Runnable? = null

    private val focusListener = View.OnFocusChangeListener { v, hasFocus ->
        cancelPending()
        if (hasFocus) {
            val text = attached[v] ?: return@OnFocusChangeListener
            scheduleShow(v, text)
        } else {
            dismiss()
        }
    }

    fun attach(view: View, text: CharSequence) {
        attached[view] = text
        // Replace, don't append — keeps behavior predictable if called twice.
        view.onFocusChangeListener = focusListener
    }

    fun attachAll(map: Map<View, CharSequence>) {
        map.forEach { (v, t) -> attach(v, t) }
    }

    private fun scheduleShow(target: View, text: CharSequence) {
        if (!isEnabled()) return
        val r = Runnable { showNow(target, text) }
        pendingShow = r
        mainHandler.postDelayed(r, SHOW_DELAY_MS)
    }

    private fun cancelPending() {
        pendingShow?.let { mainHandler.removeCallbacks(it) }
        pendingShow = null
    }

    private fun isEnabled(): Boolean =
        runCatching { BiliClient.prefs.focusHintEnabled }.getOrDefault(true)

    private fun showNow(target: View, text: CharSequence) {
        if (!isEnabled()) return
        // If the anchor view isn't focused anymore (or has gone away) by the time the delay
        // elapses, drop the show — common when user mashes the D-Pad.
        if (!target.hasWindowFocus() || !target.isFocused) return
        val (c, t) = ensureUi()
        container = c
        bubble = t
        t.text = text
        positionBubble(target, t)
        c.visibility = View.VISIBLE
    }

    fun dismiss() {
        cancelPending()
        container?.visibility = View.GONE
    }

    private fun ensureUi(): Pair<FrameLayout, TextView> {
        val existingContainer = container
        val existingBubble = bubble
        if (existingContainer != null && existingBubble != null) {
            return existingContainer to existingBubble
        }

        val parent = activity.window?.decorView as? ViewGroup
            ?: (activity.findViewById<ViewGroup>(android.R.id.content))
            ?: return FrameLayout(activity) to TextView(activity)

        val frame = FrameLayout(activity).apply {
            isFocusable = false
            isClickable = false
            visibility = View.GONE
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val bg = GradientDrawable().apply {
            cornerRadius = dp(frame.context, RADIUS_DP).toFloat()
            setColor(readThemeColor(frame.context, R.attr.focusHintBackground))
        }
        val textColor = readThemeColor(activity, R.attr.focusHintText)

        val tv = TextView(activity).apply {
            textSize = TEXT_SIZE_SP
            setTextColor(textColor)
            includeFontPadding = false
            setPadding(
                dp(activity, PAD_H_DP),
                dp(activity, PAD_V_DP),
                dp(activity, PAD_H_DP),
                dp(activity, PAD_V_DP),
            )
            background = bg
            // Subtle shadow so white text stays legible over busy video frames.
            setShadowLayer(8f, 0f, 2f, 0x99000000.toInt())
            importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        }

        val tvLp = FrameLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            android.view.Gravity.TOP or android.view.Gravity.START,
        )
        frame.addView(tv, tvLp)

        parent.addView(
            frame,
            FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
                android.view.Gravity.TOP or android.view.Gravity.START,
            ),
        )

        container = frame
        bubble = tv
        return frame to tv
    }

    private fun positionBubble(target: View, tv: TextView) {
        val root = activity.window?.decorView ?: return
        // Force a measure pass so we know how wide the bubble will be after `setText`.
        tv.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
        )
        val bubbleW = tv.measuredWidth
        val bubbleH = tv.measuredHeight

        val targetLoc = IntArray(2)
        target.getLocationInWindow(targetLoc)
        val rootLoc = IntArray(2)
        root.getLocationInWindow(rootLoc)
        val targetScreenX = targetLoc[0] - rootLoc[0]
        val targetScreenY = targetLoc[1] - rootLoc[1]
        val targetW = target.width
        val targetH = target.height

        val rootW = root.width
        val rootH = root.height
        val edgeGap = dp(activity, SCREEN_EDGE_GAP_DP)

        val centerX = targetScreenX + targetW / 2
        var left = centerX - bubbleW / 2
        left = left.coerceIn(edgeGap, (rootW - bubbleW - edgeGap).coerceAtLeast(edgeGap))

        // Prefer below the target; if it would overflow the bottom, flip above the target.
        val desiredBelowY = targetScreenY + targetH + dp(activity, MARGIN_V_DP)
        val top = if (desiredBelowY + bubbleH <= rootH - edgeGap) {
            desiredBelowY
        } else {
            (targetScreenY - bubbleH - dp(activity, MARGIN_V_DP))
                .coerceAtLeast(edgeGap)
        }

        (tv.layoutParams as FrameLayout.LayoutParams).apply {
            gravity = android.view.Gravity.TOP or android.view.Gravity.START
            leftMargin = left
            topMargin = top
            width = bubbleW
            height = bubbleH
        }
        tv.requestLayout()
    }

    private fun readThemeColor(context: Context, attrId: Int): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attrId, tv, true)) {
            when {
                tv.resourceId != 0 -> ContextCompat.getColor(context, tv.resourceId)
                tv.type >= TypedValue.TYPE_FIRST_COLOR_INT && tv.type <= TypedValue.TYPE_LAST_COLOR_INT -> tv.data
                else -> Color_FALLBACK
            }
        } else Color_FALLBACK
    }

    private fun dp(context: Context, v: Float): Int =
        TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, context.resources.displayMetrics)
            .toInt()

    companion object {
        private const val SHOW_DELAY_MS = 250L
        private const val RADIUS_DP = 6f
        private const val TEXT_SIZE_SP = 14f
        private const val PAD_H_DP = 12f
        private const val PAD_V_DP = 6f
        private const val MARGIN_V_DP = 8f
        private const val SCREEN_EDGE_GAP_DP = 16f
        private val Color_FALLBACK = 0xFF202125.toInt() // dark surface fallback

        private val instances = WeakHashMap<ComponentActivity, FocusHintOverlay>()

        @JvmStatic
        fun from(activity: ComponentActivity): FocusHintOverlay {
            return instances.getOrPut(activity) { FocusHintOverlay(activity) }
        }
    }
}
