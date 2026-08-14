package blbl.cat3399.core.ui

import android.view.View
import java.lang.ref.WeakReference

/**
 * A small helper to remember a focus target and restore it later (best-effort).
 *
 * Typical usage:
 * - capture() before opening a panel/overlay
 * - restoreAndClear() when closing it
 *
 * This keeps only a weak reference to avoid leaking Views across lifecycles.
 */
internal class FocusReturn {
    private var targetRef: WeakReference<View>? = null

    fun capture(view: View?) {
        targetRef = view?.let { WeakReference(it) }
    }

    fun clear() {
        targetRef = null
    }

    fun restoreAndClear(
        fallback: View? = null,
        postOnFail: Boolean = true,
    ): Boolean {
        val desired = targetRef?.get()
        targetRef = null
        return restoreNow(desired = desired, fallback = fallback, postOnFail = postOnFail)
    }

    fun restoreNow(
        desired: View?,
        fallback: View? = null,
        postOnFail: Boolean = true,
    ): Boolean {
        val candidates = ArrayList<View>(2)
        desired?.let { candidates.add(it) }
        fallback?.takeIf { it !== desired }?.let { candidates.add(it) }
        if (candidates.isEmpty()) return false

        for (c in candidates) {
            if (!c.isReturnFocusCandidate()) continue
            if (c.requestFocus()) return true
        }

        if (!postOnFail) return false

        // Last resort: try again on the next frame.
        // Avoid posting multiple runnables; only post to the first viable candidate.
        for (c in candidates) {
            if (!c.isReturnFocusCandidate()) continue
            c.post { c.requestFocus() }
            return true
        }
        return false
    }

    private fun View.isReturnFocusCandidate(): Boolean {
        if (!isAttachedToWindow) return false
        if (!isShown) return false
        if (!isEnabled) return false
        if (!isFocusable) return false
        return true
    }
}

