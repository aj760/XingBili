package blbl.cat3399.feature.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View

class HoldToTriggerController(
    private val view: View,
    private val progressHost: HoldProgressHost,
    private val durationMs: Long,
    private val onLongTrigger: () -> Unit,
) {
    private var holdAnimator: ValueAnimator? = null
    private var holdTriggered: Boolean = false

    fun install() {
        view.setOnTouchListener { touchedView, event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    touchedView.isPressed = true
                    startHoldGesture()
                    true
                }

                MotionEvent.ACTION_MOVE -> {
                    val inside =
                        event.x >= 0f &&
                            event.x < touchedView.width &&
                            event.y >= 0f &&
                            event.y < touchedView.height
                    touchedView.isPressed = inside
                    if (!inside) cancel(resetTriggered = false)
                    true
                }

                MotionEvent.ACTION_UP -> {
                    val triggered = holdTriggered
                    val inside =
                        event.x >= 0f &&
                            event.x < touchedView.width &&
                            event.y >= 0f &&
                            event.y < touchedView.height
                    touchedView.isPressed = false
                    cancel(resetTriggered = false)
                    if (!triggered && inside && touchedView.isEnabled) touchedView.performClick()
                    holdTriggered = false
                    true
                }

                MotionEvent.ACTION_CANCEL -> {
                    touchedView.isPressed = false
                    cancel(resetTriggered = true)
                    true
                }

                else -> false
            }
        }

        view.setOnKeyListener { keyView, keyCode, event ->
            if (!isHoldTriggerConfirmKey(keyCode)) return@setOnKeyListener false
            when (event.action) {
                KeyEvent.ACTION_DOWN -> {
                    if (event.repeatCount == 0) {
                        keyView.isPressed = true
                        startHoldGesture()
                    }
                    true
                }

                KeyEvent.ACTION_UP -> {
                    val triggered = holdTriggered
                    keyView.isPressed = false
                    cancel(resetTriggered = false)
                    if (!triggered && keyView.isEnabled) keyView.performClick()
                    holdTriggered = false
                    true
                }

                else -> false
            }
        }

        view.setOnFocusChangeListener { focusedView, hasFocus ->
            if (hasFocus) return@setOnFocusChangeListener
            focusedView.isPressed = false
            cancel(resetTriggered = true)
        }
    }

    fun cancel(resetTriggered: Boolean = true) {
        holdAnimator?.cancel()
        holdAnimator = null
        progressHost.holdProgress = 0f
        if (resetTriggered) holdTriggered = false
    }

    private fun startHoldGesture() {
        if (!view.isEnabled || view.visibility != View.VISIBLE) return
        holdTriggered = false
        holdAnimator?.cancel()
        progressHost.holdProgress = 0f

        val animator =
            ValueAnimator.ofFloat(0f, 1f).apply {
                duration = durationMs
                addUpdateListener { valueAnimator ->
                    progressHost.holdProgress = (valueAnimator.animatedValue as? Float) ?: 0f
                }
                addListener(
                    object : AnimatorListenerAdapter() {
                        private var cancelled = false

                        override fun onAnimationCancel(animation: Animator) {
                            cancelled = true
                        }

                        override fun onAnimationEnd(animation: Animator) {
                            if (holdAnimator === animation) holdAnimator = null
                            if (cancelled) return
                            holdTriggered = true
                            progressHost.holdProgress = 1f
                            onLongTrigger()
                        }
                    },
                )
            }
        holdAnimator = animator
        animator.start()
    }
}

private fun isHoldTriggerConfirmKey(keyCode: Int): Boolean {
    return when (keyCode) {
        KeyEvent.KEYCODE_DPAD_CENTER,
        KeyEvent.KEYCODE_ENTER,
        KeyEvent.KEYCODE_NUMPAD_ENTER,
        KeyEvent.KEYCODE_SPACE,
        KeyEvent.KEYCODE_BUTTON_A,
        -> true

        else -> false
    }
}
