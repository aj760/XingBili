package blbl.cat3399.feature.video.comment

import android.view.KeyEvent
import android.view.View
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.ui.FocusReturn

internal data class VideoCommentImageViewerViews(
    val container: View,
    val image: VideoCommentImageView,
    val previous: View,
    val next: View,
)

internal class VideoCommentImageViewerController(
    private val views: VideoCommentImageViewerViews,
    private val currentFocusProvider: () -> View?,
    private val fallbackFocusProvider: () -> View?,
) {
    private val focusReturn = FocusReturn()
    private var urls: List<String> = emptyList()
    private var index: Int = 0

    init {
        views.container.visibility = View.GONE
        ImageLoader.loadInto(views.image, null)
        views.image.resetViewport()

        views.container.setOnClickListener {
            if (!isVisible()) return@setOnClickListener
            close()
        }

        views.previous.setOnClickListener {
            if (!isVisible()) return@setOnClickListener
            if (views.image.isZoomed()) return@setOnClickListener
            previous()
        }
        views.next.setOnClickListener {
            if (!isVisible()) return@setOnClickListener
            if (views.image.isZoomed()) return@setOnClickListener
            next()
        }
        views.image.onNavigatePrevious = {
            if (isVisible() && !views.image.isZoomed()) {
                previous()
            }
        }
        views.image.onNavigateNext = {
            if (isVisible() && !views.image.isZoomed()) {
                next()
            }
        }
        views.image.onBlankAreaTap = {
            if (isVisible()) {
                close()
            }
        }
        views.image.onZoomStateChanged = {
            if (isVisible()) {
                updateNavigationUi()
            }
        }
    }

    fun isVisible(): Boolean = views.container.visibility == View.VISIBLE

    fun open(urls: List<String>, startIndex: Int = 0): Boolean {
        val safeUrls = urls.map { it.trim() }.filter { it.isNotBlank() }
        if (safeUrls.isEmpty()) return false

        this.urls = safeUrls
        index = startIndex.coerceIn(0, safeUrls.lastIndex)
        focusReturn.capture(currentFocusProvider())

        views.container.visibility = View.VISIBLE
        views.container.bringToFront()
        views.container.invalidate()
        views.container.requestLayout()
        views.container.requestFocus()
        render()
        return true
    }

    fun close(restoreFocus: Boolean = true) {
        if (!isVisible()) return

        views.container.visibility = View.GONE
        ImageLoader.loadInto(views.image, null)
        views.image.resetViewport()
        urls = emptyList()
        index = 0

        if (!restoreFocus) {
            focusReturn.clear()
            return
        }

        focusReturn.restoreAndClear(fallback = fallbackFocusProvider(), postOnFail = false)
    }

    fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (!isVisible()) return false

        val keyCode = event.keyCode
        if (event.action == KeyEvent.ACTION_DOWN) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK -> {
                    close()
                    return true
                }

                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_SETTINGS,
                KeyEvent.KEYCODE_INFO,
                KeyEvent.KEYCODE_GUIDE,
                -> return true

                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                -> {
                    views.image.toggleDpadZoom()
                    return true
                }

                KeyEvent.KEYCODE_DPAD_LEFT -> {
                    if (views.image.isZoomed()) {
                        views.image.panLeft()
                    } else {
                        previous()
                    }
                    return true
                }

                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    if (views.image.isZoomed()) {
                        views.image.panRight()
                    } else {
                        next()
                    }
                    return true
                }

                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (views.image.isZoomed()) {
                        views.image.panUp()
                    }
                    return true
                }

                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (views.image.isZoomed()) {
                        views.image.panDown()
                    }
                    return true
                }
            }
        }

        if (event.action == KeyEvent.ACTION_UP) {
            when (keyCode) {
                KeyEvent.KEYCODE_BACK,
                KeyEvent.KEYCODE_MENU,
                KeyEvent.KEYCODE_SETTINGS,
                KeyEvent.KEYCODE_INFO,
                KeyEvent.KEYCODE_GUIDE,
                KeyEvent.KEYCODE_DPAD_LEFT,
                KeyEvent.KEYCODE_DPAD_RIGHT,
                KeyEvent.KEYCODE_DPAD_UP,
                KeyEvent.KEYCODE_DPAD_DOWN,
                KeyEvent.KEYCODE_DPAD_CENTER,
                KeyEvent.KEYCODE_ENTER,
                KeyEvent.KEYCODE_NUMPAD_ENTER,
                -> return true
            }
        }

        return false
    }

    private fun render() {
        val currentUrls = urls
        if (currentUrls.isEmpty()) {
            close()
            return
        }

        val idx = index.coerceIn(0, currentUrls.lastIndex)
        index = idx
        views.image.resetViewport()
        ImageLoader.loadInto(views.image, currentUrls[idx])
        updateNavigationUi()
    }

    private fun previous() {
        if (urls.size <= 1) return
        if (index <= 0) return
        index -= 1
        render()
    }

    private fun next() {
        if (urls.size <= 1) return
        if (index >= urls.lastIndex) return
        index += 1
        render()
    }

    private fun updateNavigationUi() {
        val showNavigation = urls.size > 1 && !views.image.isZoomed()
        views.previous.visibility =
            if (showNavigation && index > 0) View.VISIBLE else View.GONE
        views.next.visibility =
            if (showNavigation && index < urls.lastIndex) View.VISIBLE else View.GONE
    }
}
