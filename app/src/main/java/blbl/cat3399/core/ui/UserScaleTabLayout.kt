package blbl.cat3399.core.ui

import android.content.Context
import android.content.SharedPreferences
import android.text.TextUtils
import android.util.AttributeSet
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.widget.TextViewCompat
import blbl.cat3399.R
import com.google.android.material.tabs.TabLayout
import kotlin.math.abs

/**
 * A TabLayout that automatically applies the user UI scale ([UiScale]) to tab label text.
 *
 * Why:
 * - Device/resolution/system-scale normalization is handled by [UiDensity] (Activity context wrap).
 * - [UiScale] is a user preference and must be applied explicitly (B plan).
 *
 * This view centralizes the scaling for ALL TabLayouts that are inflated as [UserScaleTabLayout],
 * so callers do not need to install per-page "tab text scale fixer" glue code.
 *
 * Notes:
 * - We DO NOT mutate Material TabLayout's internal label TextView, because TabView.onMeasure() can override it.
 * - Instead, we install a custom view (TextView with id android.R.id.text1) for each Tab so measurement and
 *   rendering both use the same scaled text size.
 * - For fixed-mode TabLayouts, if the scaled text cannot fit, we temporarily switch to scrollable mode so that
 *   the "tab chips" can grow with content.
 */
class UserScaleTabLayout @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = com.google.android.material.R.attr.tabStyle,
) : TabLayout(context, attrs, defStyleAttr) {
    private val prefs: SharedPreferences by lazy(LazyThreadSafetyMode.NONE) {
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    private val tabTextAppearanceRes: Int

    private val baseTabMode: Int

    private var pendingEnforce: Boolean = false

    private val prefListener =
        SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key == null || key == PREF_KEY_UI_SCALE_FACTOR) {
                scheduleEnforce()
            }
        }

    init {
        val a =
            context.obtainStyledAttributes(
                attrs,
                com.google.android.material.R.styleable.TabLayout,
                defStyleAttr,
                0,
            )
        tabTextAppearanceRes =
            try {
                a.getResourceId(
                    com.google.android.material.R.styleable.TabLayout_tabTextAppearance,
                    R.style.TextAppearance_Blbl_Tab,
                )
            } finally {
                a.recycle()
            }
        baseTabMode = tabMode
    }

    override fun newTab(): Tab {
        // Install the custom view eagerly so that FIRST MEASURE already uses the final scaled text size.
        // This avoids "first frame small / switch tab then fixes" and avoids measure-vs-draw truncation.
        val tab = super.newTab()
        ensureUserScaleCustomView(tab, requestLayoutIfChanged = false)
        return tab
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        prefs.registerOnSharedPreferenceChangeListener(prefListener)
        scheduleEnforce()
    }

    override fun onDetachedFromWindow() {
        prefs.unregisterOnSharedPreferenceChangeListener(prefListener)
        super.onDetachedFromWindow()
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
        // Keep our custom tab views updated BEFORE the next measurement pass is committed.
        // This is cheap (tab count is small), and ensures scrollable-mode tabs are measured with the scaled size.
        val textChanged = updateUserScaleTabTextViews(requestLayoutIfChanged = false)
        val modeChanged = maybeAdjustTabModeForTextFit()
        if (textChanged || modeChanged) {
            super.onMeasure(widthMeasureSpec, heightMeasureSpec)
            maybeAdjustTabModeForTextFit()
            updateUserScaleTabTextViews(requestLayoutIfChanged = false)
        }
    }

    private fun scheduleEnforce() {
        if (!isAttachedToWindow) return
        if (pendingEnforce) return
        pendingEnforce = true
        requestLayout()
        doOnPreDrawIfAlive(isAlive = { isAttachedToWindow }) {
            pendingEnforce = false
            // Install custom views (if needed) and update their scaled sizes.
            val installed = ensureAllTabsHaveUserScaleCustomView(requestLayoutIfChanged = false)
            val changed = updateUserScaleTabTextViews(requestLayoutIfChanged = false)
            val modeChanged = maybeAdjustTabModeForTextFit()
            if (installed || changed || modeChanged) {
                requestLayout()
                invalidate()
            }
        }
        invalidate()
    }

    private fun ensureAllTabsHaveUserScaleCustomView(requestLayoutIfChanged: Boolean): Boolean {
        var changed = false
        for (i in 0 until tabCount) {
            val tab = getTabAt(i) ?: continue
            changed = ensureUserScaleCustomView(tab, requestLayoutIfChanged = false) || changed
        }
        if (changed && requestLayoutIfChanged) {
            requestLayout()
            invalidate()
        }
        return changed
    }

    private fun ensureUserScaleCustomView(tab: Tab, requestLayoutIfChanged: Boolean): Boolean {
        val existing = tab.customView
        if (existing == null) {
            val tv = createUserScaleTabTextView()
            tab.setCustomView(tv)
            if (requestLayoutIfChanged) {
                requestLayout()
                invalidate()
            }
            return true
        }

        // Respect call sites that set their own custom view (rare). We only manage our own views.
        val isOurs = (existing.getTag(R.id.tag_user_scale_tab_custom_view) as? Boolean) == true
        if (!isOurs) return false

        // Existing custom view is ours; update it in-place.
        val changed = updateUserScaleTabTextView(existing)
        if (changed && requestLayoutIfChanged) {
            requestLayout()
            invalidate()
        }
        return changed
    }

    private fun updateUserScaleTabTextViews(requestLayoutIfChanged: Boolean): Boolean {
        val scale = UiScale.factor(context).takeIf { it.isFinite() && it > 0f } ?: 1.0f
        val basePx = resolveBaseTextSizePx(tabTextAppearanceRes)
        if (basePx <= 0f) return false
        val expectedPx = context.uiScaler(scale).scaledPxF(basePx, minPx = 1f)

        var changed = false
        for (i in 0 until tabCount) {
            val tab = getTabAt(i) ?: continue
            val custom = tab.customView ?: continue
            val isOurs = (custom.getTag(R.id.tag_user_scale_tab_custom_view) as? Boolean) == true
            if (!isOurs) continue
            changed = updateUserScaleTabTextView(custom, expectedPx = expectedPx) || changed
        }

        if (changed && requestLayoutIfChanged) {
            requestLayout()
            invalidate()
        }
        return changed
    }

    private fun updateUserScaleTabTextView(view: android.view.View, expectedPx: Float? = null): Boolean {
        val tv = view as? TextView ?: return false
        val expected =
            expectedPx
                ?: run {
                    val scale = UiScale.factor(context).takeIf { it.isFinite() && it > 0f } ?: 1.0f
                    val basePx = resolveBaseTextSizePx(tabTextAppearanceRes)
                    if (basePx <= 0f) return false
                    context.uiScaler(scale).scaledPxF(basePx, minPx = 1f)
                }

        var changed = false

        // Keep style consistent with TabLayout defaults.
        if (tv.id != android.R.id.text1) {
            tv.id = android.R.id.text1
            changed = true
        }
        if (tv.layoutParams !is LinearLayout.LayoutParams) {
            tv.layoutParams =
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                )
            changed = true
        }
        if (tv.gravity != Gravity.CENTER) {
            tv.gravity = Gravity.CENTER
            changed = true
        }
        if (tv.maxLines != 1) {
            tv.maxLines = 1
            changed = true
        }
        tv.setHorizontallyScrolling(false)
        if (tv.ellipsize != TextUtils.TruncateAt.END) {
            tv.ellipsize = TextUtils.TruncateAt.END
            changed = true
        }

        if (!tv.isDuplicateParentStateEnabled) {
            tv.isDuplicateParentStateEnabled = true
            changed = true
        }

        // Ensure our px sizing is not overridden by auto-size.
        if (TextViewCompat.getAutoSizeTextType(tv) != TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE) {
            TextViewCompat.setAutoSizeTextTypeWithDefaults(tv, TextViewCompat.AUTO_SIZE_TEXT_TYPE_NONE)
            changed = true
        }

        // Apply TextAppearance only once (or when style changes), otherwise it will reset textSize each pass.
        val appliedAppearance = tv.getTag(R.id.tag_user_scale_tab_text_appearance_res) as? Int
        if (appliedAppearance != tabTextAppearanceRes) {
            TextViewCompat.setTextAppearance(tv, tabTextAppearanceRes)
            tv.setTag(R.id.tag_user_scale_tab_text_appearance_res, tabTextAppearanceRes)
            changed = true
        }

        val colors = tabTextColors
        if (colors != null && tv.textColors != colors) {
            tv.setTextColor(colors)
            changed = true
        }

        if (abs(tv.textSize - expected) > 0.01f) {
            tv.setTextSize(TypedValue.COMPLEX_UNIT_PX, expected)
            changed = true
        }

        return changed
    }

    private fun createUserScaleTabTextView(): TextView {
        val tv = TextView(context)
        tv.setTag(R.id.tag_user_scale_tab_custom_view, true)
        updateUserScaleTabTextView(tv)
        return tv
    }

    private fun maybeAdjustTabModeForTextFit(): Boolean {
        // Only auto-switch for layouts that are authored as FIXED; keep explicit SCROLLABLE tabs stable.
        if (baseTabMode != MODE_FIXED) return false

        val count = tabCount
        if (count <= 1) {
            if (tabMode != MODE_FIXED) {
                tabMode = MODE_FIXED
                return true
            }
            return false
        }

        val widthPx = measuredWidth - paddingLeft - paddingRight
        if (widthPx <= 0) return false

        val tabStrip = getChildAt(0) as? ViewGroup ?: return false
        if (tabStrip.childCount <= 0) return false

        val widthPerTab = widthPx.toFloat() / count.toFloat()
        val shouldScroll =
            (0 until minOf(count, tabStrip.childCount))
                .any { index ->
                    val tab = getTabAt(index) ?: return@any false
                    val tabView = tabStrip.getChildAt(index) ?: return@any false
                    val custom = tab.customView as? TextView ?: return@any false
                    val isOurs = (custom.getTag(R.id.tag_user_scale_tab_custom_view) as? Boolean) == true
                    if (!isOurs) return@any false

                    val text = tab.text?.toString().orEmpty()
                    if (text.isEmpty()) return@any false
                    val textWidth = custom.paint.measureText(text)
                    val required =
                        textWidth +
                            custom.compoundPaddingLeft + custom.compoundPaddingRight +
                            tabView.paddingLeft + tabView.paddingRight
                    required > widthPerTab
                }

        val desired = if (shouldScroll) MODE_SCROLLABLE else MODE_FIXED
        if (tabMode == desired) return false
        tabMode = desired
        return true
    }

    private fun resolveBaseTextSizePx(textAppearanceRes: Int): Float {
        val a = context.obtainStyledAttributes(textAppearanceRes, intArrayOf(android.R.attr.textSize))
        return try {
            a.getDimension(0, 0f)
        } finally {
            a.recycle()
        }
    }

    private companion object {
        private const val PREFS_NAME = "blbl_prefs"
        private const val PREF_KEY_UI_SCALE_FACTOR = "ui_scale_factor"
    }
}
