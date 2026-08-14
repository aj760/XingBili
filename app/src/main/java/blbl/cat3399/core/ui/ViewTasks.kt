package blbl.cat3399.core.ui

import android.view.View
import androidx.core.view.doOnPreDraw

/**
 * Small helpers to avoid running posted UI work after a view/fragment has already been torn down.
 *
 * The caller owns the liveness predicate (e.g. `_binding != null`, `isAdded`, `!released`).
 * This keeps the helper generic and avoids forcing every caller into the same lifecycle model.
 */
inline fun View.postIfAlive(
    crossinline isAlive: () -> Boolean,
    crossinline action: () -> Unit,
) {
    post {
        if (!isAlive()) return@post
        action()
    }
}

inline fun View.postIfAttached(crossinline action: () -> Unit) {
    postIfAlive(isAlive = { isAttachedToWindow }, action = action)
}

inline fun View.postDelayedIfAlive(
    delayMillis: Long,
    crossinline isAlive: () -> Boolean,
    crossinline action: () -> Unit,
) {
    postDelayed(
        {
            if (isAlive()) action()
        },
        delayMillis,
    )
}

inline fun View.postDelayedIfAttached(
    delayMillis: Long,
    crossinline action: () -> Unit,
) {
    postDelayedIfAlive(delayMillis = delayMillis, isAlive = { isAttachedToWindow }, action = action)
}

inline fun View.doOnPreDrawIfAlive(
    crossinline isAlive: () -> Boolean,
    crossinline action: () -> Unit,
) {
    doOnPreDraw {
        if (!isAlive()) return@doOnPreDraw
        action()
    }
}
