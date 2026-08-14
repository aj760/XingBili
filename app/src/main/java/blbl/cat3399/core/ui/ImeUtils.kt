package blbl.cat3399.core.ui

import android.content.Context
import android.os.SystemClock
import android.view.View
import android.view.inputmethod.InputMethodManager
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import blbl.cat3399.core.log.AppLog

private const val IME_LOG_TAG = "IME"
private val SHOW_IME_RETRY_DELAYS_MS = longArrayOf(60L, 180L)

/**
 * Best-effort soft keyboard show helper.
 *
 * Notes:
 * - Focus/layout timing can race (especially on TV/ATV devices and custom IMEs).
 * - [InputMethodManager.showSoftInput] may be ignored when called too early.
 *
 * Strategy:
 * - Ensure focus first.
 * - Try both WindowInsetsController and InputMethodManager.
 * - Retry a couple of times with small delays when IME still isn't active.
 *
 * The caller owns [isAlive] to avoid delayed retries after the view/fragment is torn down or edit mode is cancelled.
 */
fun View.showImeReliable(
    isAlive: () -> Boolean = { isAttachedToWindow },
) {
    val startedAtMs = SystemClock.uptimeMillis()
    if (isLaidOut) {
        postIfAlive(isAlive = isAlive) {
            showImeAttempt(attemptIndex = 0, startedAtMs = startedAtMs, isAlive = isAlive)
        }
    } else {
        doOnPreDrawIfAlive(isAlive = isAlive) {
            showImeAttempt(attemptIndex = 0, startedAtMs = startedAtMs, isAlive = isAlive)
        }
    }
}

fun View.hideImeReliable() {
    windowInsetsControllerCompat()?.hide(WindowInsetsCompat.Type.ime())
    val token = windowToken ?: return
    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager ?: return
    imm.hideSoftInputFromWindow(token, 0)
}

private fun View.showImeAttempt(
    attemptIndex: Int,
    startedAtMs: Long,
    isAlive: () -> Boolean,
) {
    if (!isAlive()) return

    if (!isAttachedToWindow) {
        postDelayedIfAlive(delayMillis = 16L, isAlive = isAlive) {
            showImeAttempt(attemptIndex = attemptIndex, startedAtMs = startedAtMs, isAlive = isAlive)
        }
        return
    }

    if (!hasFocus()) {
        val focused =
            if (isInTouchMode) {
                requestFocusFromTouch()
            } else {
                requestFocus()
            }
        if (!focused) requestFocus()
    }

    // WindowInsetsController is more compatible on some Android versions/IMEs.
    windowInsetsControllerCompat()?.show(WindowInsetsCompat.Type.ime())

    val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
    imm?.showSoftInput(this, InputMethodManager.SHOW_IMPLICIT)

    val isActive = imm?.isActive(this) == true
    if (isActive) return

    val delay =
        SHOW_IME_RETRY_DELAYS_MS.getOrNull(attemptIndex)
            ?: run {
                AppLog.w(
                    IME_LOG_TAG,
                    "showImeReliable failed after ${SystemClock.uptimeMillis() - startedAtMs}ms " +
                        "focused=${hasFocus()} attached=$isAttachedToWindow windowFocus=${hasWindowFocus()}",
                )
                return
            }

    postDelayedIfAlive(delayMillis = delay, isAlive = isAlive) {
        showImeAttempt(attemptIndex = attemptIndex + 1, startedAtMs = startedAtMs, isAlive = isAlive)
    }
}

@Suppress("DEPRECATION")
private fun View.windowInsetsControllerCompat() = ViewCompat.getWindowInsetsController(this)
