package blbl.cat3399.feature.player

import android.view.KeyEvent
import android.view.View
import androidx.lifecycle.lifecycleScope
import blbl.cat3399.databinding.ActivityPlayerBinding
import blbl.cat3399.databinding.IncludeVideoCommentImageViewerContentBinding
import blbl.cat3399.databinding.IncludeVideoCommentsPanelContentBinding
import blbl.cat3399.feature.video.comment.VideoCommentImageViewerController
import blbl.cat3399.feature.video.comment.VideoCommentImageViewerViews
import blbl.cat3399.feature.video.comment.VideoCommentsPanelController
import blbl.cat3399.feature.video.comment.VideoCommentsPanelViews

internal fun PlayerActivity.isSettingsPanelVisible(): Boolean = binding.settingsPanel.visibility == View.VISIBLE

internal fun PlayerActivity.isCommentsPanelVisible(): Boolean = binding.commentsPanel.visibility == View.VISIBLE

internal fun PlayerActivity.isCommentThreadVisible(): Boolean =
    videoCommentsController?.isThreadVisible() == true

internal fun PlayerActivity.isSidePanelVisible(): Boolean = isSettingsPanelVisible() || isCommentsPanelVisible()

internal fun PlayerActivity.isOverlayPanelVisible(): Boolean =
    isSidePanelVisible() || isBottomCardPanelVisible() || isSponsorSubmitPanelVisible()

internal fun ActivityPlayerBinding.videoCommentsPanelContent(): IncludeVideoCommentsPanelContentBinding =
    IncludeVideoCommentsPanelContentBinding.bind(root)

internal fun ActivityPlayerBinding.videoCommentImageViewerContent(): IncludeVideoCommentImageViewerContentBinding =
    IncludeVideoCommentImageViewerContentBinding.bind(commentImageViewer)

internal fun PlayerActivity.onOverlayPanelShown(openedFromMenuKey: Boolean) {
    when {
        openedFromMenuKey -> menuRevealedPanelSessionActive = true
        !isOverlayPanelVisible() -> menuRevealedPanelSessionActive = false
    }
}

internal fun PlayerActivity.onLastOverlayPanelDismissed(dismissTarget: PlayerActivity.PanelDismissTarget) {
    if (isOverlayPanelVisible()) return
    menuRevealedPanelSessionActive = false
    when (dismissTarget) {
        PlayerActivity.PanelDismissTarget.ResumeOsd -> setControlsVisible(true)
        PlayerActivity.PanelDismissTarget.Fullscreen -> setControlsVisible(false)
    }
}

internal fun PlayerActivity.initSidePanels() {
    val imageViews = binding.videoCommentImageViewerContent()
    val commentViews = binding.videoCommentsPanelContent()
    videoCommentImageViewerController =
        VideoCommentImageViewerController(
            views =
                VideoCommentImageViewerViews(
                    container = binding.commentImageViewer,
                    image = imageViews.ivCommentImage,
                    previous = imageViews.ivCommentImagePrev,
                    next = imageViews.ivCommentImageNext,
                ),
            currentFocusProvider = { currentFocus },
            fallbackFocusProvider = {
                when {
                    videoCommentsController?.isThreadVisible() == true -> commentViews.recyclerCommentThread
                    isCommentsPanelVisible() -> commentViews.recyclerComments
                    else -> binding.btnComments
                }
            },
        )

    videoCommentsController =
        VideoCommentsPanelController(
            context = this,
            scope = lifecycleScope,
            views =
                VideoCommentsPanelViews(
                    sortRow = commentViews.rowCommentSort,
                    sortHot = commentViews.chipCommentSortHot,
                    sortNew = commentViews.chipCommentSortNew,
                    comments = commentViews.recyclerComments,
                    thread = commentViews.recyclerCommentThread,
                    hint = commentViews.tvCommentsHint,
                ),
            oidProvider = { currentAid?.takeIf { it > 0L } },
            upMidProvider = { currentUpMid },
            imageViewer = videoCommentImageViewerController,
            isActive = { !isFinishing && !isDestroyed && isCommentsPanelVisible() },
        )
}

internal fun PlayerActivity.toggleSettingsPanel() {
    if (isSettingsPanelVisible()) {
        hideSettingsPanel()
    } else {
        showSettingsPanel()
    }
}

internal fun PlayerActivity.showSettingsPanel(openedFromMenuKey: Boolean = false) {
    onOverlayPanelShown(openedFromMenuKey = openedFromMenuKey)
    hideBottomCardPanel(restoreFocus = false, dismissTarget = null)
    val enteringSidePanelMode = !isSidePanelVisible()
    if (enteringSidePanelMode) {
        sidePanelFocusReturn.capture(currentFocus)
    }
    if (isCommentsPanelVisible()) {
        videoCommentsController?.clearTransientUi()
    }
    binding.commentsPanel.visibility = View.GONE
    // Make sure OSD (top/bottom bars) is visible first, so the panel height stays stable
    // even if it relies on constraints to those bars.
    setControlsVisible(true)
    binding.settingsPanel.visibility = View.VISIBLE
    showSettingsRoot(focusKey = PlayerSettingKeys.RESOLUTION)
    syncPlayerInfoPanelVisibility()
}

internal fun PlayerActivity.hideSettingsPanel(
    dismissTarget: PlayerActivity.PanelDismissTarget = PlayerActivity.PanelDismissTarget.ResumeOsd,
) {
    if (dismissTarget == PlayerActivity.PanelDismissTarget.ResumeOsd) {
        // Restore focus before hiding the panel to avoid the system picking a temporary fallback
        // (e.g. top bar back button) and causing a visible "double jump".
        sidePanelFocusReturn.restoreAndClear(fallback = binding.btnAdvanced, postOnFail = false)
    } else {
        sidePanelFocusReturn.clear()
    }
    binding.settingsPanel.visibility = View.GONE
    syncPlayerInfoPanelVisibility()
    onLastOverlayPanelDismissed(dismissTarget)
}

internal fun PlayerActivity.toggleCommentsPanel() {
    if (isCommentsPanelVisible()) {
        hideCommentsPanel()
    } else {
        showCommentsPanel()
    }
}

internal fun PlayerActivity.showCommentsPanel(openedFromMenuKey: Boolean = false) {
    onOverlayPanelShown(openedFromMenuKey = openedFromMenuKey)
    hideBottomCardPanel(restoreFocus = false, dismissTarget = null)
    val enteringSidePanelMode = !isSidePanelVisible()
    if (enteringSidePanelMode) {
        sidePanelFocusReturn.capture(currentFocus)
    }
    binding.settingsPanel.visibility = View.GONE
    setControlsVisible(true)
    binding.commentsPanel.visibility = View.VISIBLE
    videoCommentsController?.showRoot()
    videoCommentsController?.ensureLoaded()
    videoCommentsController?.focusRoot()
    syncPlayerInfoPanelVisibility()
}

internal fun PlayerActivity.hideCommentsPanel(
    dismissTarget: PlayerActivity.PanelDismissTarget = PlayerActivity.PanelDismissTarget.ResumeOsd,
) {
    videoCommentsController?.clearTransientUi()
    if (dismissTarget == PlayerActivity.PanelDismissTarget.ResumeOsd) {
        // Restore focus before hiding the panel to avoid a brief focus jump to an unrelated control.
        sidePanelFocusReturn.restoreAndClear(fallback = binding.btnComments, postOnFail = false)
    } else {
        sidePanelFocusReturn.clear()
    }
    binding.commentsPanel.visibility = View.GONE
    syncPlayerInfoPanelVisibility()
    onLastOverlayPanelDismissed(dismissTarget)
}

internal fun PlayerActivity.onSidePanelBackPressed(
    dismissTarget: PlayerActivity.PanelDismissTarget = PlayerActivity.PanelDismissTarget.ResumeOsd,
): Boolean {
    if (isCommentsPanelVisible()) {
        if (videoCommentsController?.handleBack() == true) return true
        hideCommentsPanel(dismissTarget = dismissTarget)
        return true
    }
    if (isSettingsPanelVisible()) {
        if (backFromSettingsSubmenu()) {
            return true
        }
        hideSettingsPanel(dismissTarget = dismissTarget)
        return true
    }
    return false
}

internal fun PlayerActivity.closeCommentImageViewer(restoreFocus: Boolean = true) {
    videoCommentImageViewerController?.close(restoreFocus = restoreFocus)
}

internal fun PlayerActivity.isCommentImageViewerVisible(): Boolean =
    videoCommentImageViewerController?.isVisible() == true

internal fun PlayerActivity.dispatchCommentImageViewerKey(event: KeyEvent): Boolean =
    videoCommentImageViewerController?.dispatchKeyEvent(event) == true

internal fun PlayerActivity.ensureCommentsLoaded() {
    videoCommentsController?.ensureLoaded()
}
