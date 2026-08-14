package blbl.cat3399.core.ui

import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R

/**
 * Focus helper for RecyclerView item-views.
 *
 * Why this exists:
 * - A single `post { findViewHolder -> requestFocus }` is not reliable around Fragment/ViewPager2
 *   transitions: the target ViewHolder might not be bound yet, and `requestFocus()` may fail.
 * - The system may pick an arbitrary fallback focus target (e.g. sidebar/avatar) while pages are
 *   being switched, which makes the final focus position look "random".
 *
 * This helper:
 * - Scrolls to the target position once (optional smooth scroll).
 * - Retries until the ViewHolder is bound and the item-view actually accepts focus.
 * - Uses a token stored on the RecyclerView to cancel older requests automatically.
 *
 * Callers should clear their own "pending focus" flags only when [onFocused] runs.
 */
internal fun RecyclerView.requestFocusAdapterPositionReliable(
    position: Int,
    smoothScroll: Boolean,
    isAlive: () -> Boolean,
    maxAttempts: Int = 30,
    attemptDelayMs: Long = 16L,
    onFocused: () -> Unit,
): Boolean {
    val nextToken = ((getTag(R.id.tag_recycler_focus_request_token) as? Int) ?: 0) + 1
    setTag(R.id.tag_recycler_focus_request_token, nextToken)
    return requestFocusAdapterPositionReliableInternal(
        position = position,
        smoothScroll = smoothScroll,
        isAlive = isAlive,
        token = nextToken,
        attemptsLeft = maxAttempts.coerceAtLeast(0),
        attemptDelayMs = attemptDelayMs,
        scrolled = false,
        onFocused = onFocused,
    )
}

private fun RecyclerView.requestFocusAdapterPositionReliableInternal(
    position: Int,
    smoothScroll: Boolean,
    isAlive: () -> Boolean,
    token: Int,
    attemptsLeft: Int,
    attemptDelayMs: Long,
    scrolled: Boolean,
    onFocused: () -> Unit,
): Boolean {
    val currentToken = getTag(R.id.tag_recycler_focus_request_token) as? Int
    if (currentToken != token) return false
    if (!isAlive()) return false

    // Some focus requests are triggered while ViewPager2/Fragment transactions are still applying.
    // If the RecyclerView isn't attached yet, retry for a few frames.
    if (!isAttachedToWindow) {
        if (attemptsLeft <= 0) return false
        postDelayedIfAlive(
            delayMillis = attemptDelayMs,
            isAlive = { isAlive() && (getTag(R.id.tag_recycler_focus_request_token) as? Int) == token },
        ) {
            requestFocusAdapterPositionReliableInternal(
                position = position,
                smoothScroll = smoothScroll,
                isAlive = isAlive,
                token = token,
                attemptsLeft = attemptsLeft - 1,
                attemptDelayMs = attemptDelayMs,
                scrolled = scrolled,
                onFocused = onFocused,
            )
        }
        return true
    }

    val adapter =
        adapter
            ?: run {
                if (attemptsLeft <= 0) return false
                postDelayedIfAlive(
                    delayMillis = attemptDelayMs,
                    isAlive = { isAlive() && isAttachedToWindow && (getTag(R.id.tag_recycler_focus_request_token) as? Int) == token },
                ) {
                    requestFocusAdapterPositionReliableInternal(
                        position = position,
                        smoothScroll = smoothScroll,
                        isAlive = isAlive,
                        token = token,
                        attemptsLeft = attemptsLeft - 1,
                        attemptDelayMs = attemptDelayMs,
                        scrolled = scrolled,
                        onFocused = onFocused,
                    )
                }
                return true
            }
    val itemCount = adapter.itemCount
    if (position !in 0 until itemCount) return false

    val itemView = findViewHolderForAdapterPosition(position)?.itemView
    if (itemView != null && itemView.requestFocus()) {
        onFocused()
        return true
    }

    // Scroll once at the beginning of the request to bring the target into viewport.
    if (!scrolled) {
        if (smoothScroll) smoothScrollToPosition(position) else scrollToPosition(position)
    }

    if (attemptsLeft <= 0) return false
    postDelayedIfAlive(
        delayMillis = attemptDelayMs,
        isAlive = { isAlive() && isAttachedToWindow && (getTag(R.id.tag_recycler_focus_request_token) as? Int) == token },
    ) {
        requestFocusAdapterPositionReliableInternal(
            position = position,
            smoothScroll = smoothScroll,
            isAlive = isAlive,
            token = token,
            attemptsLeft = attemptsLeft - 1,
            attemptDelayMs = attemptDelayMs,
            scrolled = true,
            onFocused = onFocused,
        )
    }
    return true
}
