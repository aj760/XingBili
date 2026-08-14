package blbl.cat3399.feature.player

import android.os.SystemClock
import android.view.View
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.Player
import blbl.cat3399.core.prefs.AppPrefs
import blbl.cat3399.core.ui.popup.PopupHost
import blbl.cat3399.feature.player.engine.BlblPlayerEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal sealed interface AutoNextTarget {
    val rawTitle: String?
    val fallbackTitle: String

    data class Page(
        val index: Int,
        override val rawTitle: String?,
    ) : AutoNextTarget {
        override val fallbackTitle: String = "下一个视频"
    }

    data class Parts(
        val index: Int,
        override val rawTitle: String?,
    ) : AutoNextTarget {
        override val fallbackTitle: String = "下一个视频"
    }

    data class Recommend(
        val bvid: String,
        val cid: Long?,
        val aid: Long?,
        override val rawTitle: String?,
    ) : AutoNextTarget {
        override val fallbackTitle: String = "推荐视频"
    }
}

private fun autoNextModeAllowsNext(mode: String): Boolean {
    return when (mode) {
        AppPrefs.PLAYER_PLAYBACK_MODE_PAGE_LIST,
        AppPrefs.PLAYER_PLAYBACK_MODE_PARTS_LIST,
        AppPrefs.PLAYER_PLAYBACK_MODE_PARTS_LIST_THEN_RECOMMEND,
        AppPrefs.PLAYER_PLAYBACK_MODE_RECOMMEND,
        -> true

        else -> false
    }
}

private fun autoNextFallbackTitleForMode(mode: String): String {
    return when (mode) {
        AppPrefs.PLAYER_PLAYBACK_MODE_RECOMMEND -> "推荐视频"
        else -> "下一个视频"
    }
}

private fun PlayerActivity.hasPendingPartsAutoNextResolution(): Boolean {
    return partsListFetchJob?.isActive == true
}

private fun PlayerActivity.hasPendingRecommendAutoNextResolution(): Boolean {
    val requestBvid = currentBvid.trim()
    if (requestBvid.isBlank()) return false
    if (relatedVideosCache?.bvid == requestBvid) return false
    return relatedVideosFetchJob?.isActive == true
}

private fun PlayerActivity.shouldShowAutoNextHintFallback(mode: String): Boolean {
    val hasPendingPartsResolution = hasPendingPartsAutoNextResolution()
    val hasPendingRecommendResolution = hasPendingRecommendAutoNextResolution()
    return when (mode) {
        AppPrefs.PLAYER_PLAYBACK_MODE_PARTS_LIST -> hasPendingPartsResolution
        AppPrefs.PLAYER_PLAYBACK_MODE_PARTS_LIST_THEN_RECOMMEND ->
            hasPendingPartsResolution || hasPendingRecommendResolution
        AppPrefs.PLAYER_PLAYBACK_MODE_RECOMMEND -> hasPendingRecommendResolution
        else -> false
    }
}

internal fun PlayerActivity.isAutoNextUiBlocked(): Boolean {
    if (osdMode != PlayerActivity.OsdMode.Hidden) return true
    if (transientSeekOsdVisible) return true
    if (isSidePanelVisible()) return true
    if (isBottomCardPanelVisible() || binding.recommendScrim.visibility == View.VISIBLE) return true
    if (isCommentImageViewerVisible()) return true
    val popupHost = PopupHost.peek(this)
    if (popupHost?.hasModalView() == true) return true
    return false
}

internal fun formatAutoNextHintTitle(rawTitle: String?, fallbackTitle: String): String {
    val normalized =
        rawTitle
            .orEmpty()
            .replace(Regex("\\s+"), " ")
            .trim()
            .ifBlank { fallbackTitle }
    val codePointCount = normalized.codePointCount(0, normalized.length)
    if (codePointCount <= PlayerActivity.AUTO_NEXT_TITLE_MAX_CHARS) return normalized
    val endExclusive = normalized.offsetByCodePoints(0, PlayerActivity.AUTO_NEXT_TITLE_MAX_CHARS)
    return normalized.substring(0, endExclusive) + "..."
}

internal fun PlayerActivity.clearAutoNextPreviewState(reason: String, resetUserCancellation: Boolean) {
    val hadPending = autoNextPending != null
    val hadHint = autoNextHintVisible
    val wasCancelled = autoNextCancelledByUser
    autoNextPending = null
    dismissAutoNextHint()
    if (resetUserCancellation) autoNextCancelledByUser = false
    if (hadPending || hadHint || wasCancelled) {
        trace?.log(
            "autonext:clear_preview",
            "reason=$reason pending=${if (hadPending) 1 else 0} hint=${if (hadHint) 1 else 0} cancelled=${if (wasCancelled) 1 else 0} reset=${if (resetUserCancellation) 1 else 0}",
        )
    }
}

internal fun PlayerActivity.clearAutoNextState(reason: String, resetUserCancellation: Boolean) {
    val hadPending = autoNextPending != null
    val hadHint = autoNextHintVisible
    val wasCancelled = autoNextCancelledByUser
    val wasArmed = autoNextAfterEndedArmed
    val hadJob = autoNextAfterEndedJob != null
    autoNextPending = null
    dismissAutoNextHint()
    if (resetUserCancellation) autoNextCancelledByUser = false
    autoNextAfterEndedArmed = false
    autoNextAfterEndedToken = 0L
    autoNextAfterEndedJob?.cancel()
    autoNextAfterEndedJob = null
    if (hadPending || hadHint || wasCancelled || wasArmed || hadJob) {
        trace?.log(
            "autonext:clear",
            "reason=$reason pending=${if (hadPending) 1 else 0} hint=${if (hadHint) 1 else 0} cancelled=${if (wasCancelled) 1 else 0} armed=${if (wasArmed) 1 else 0} job=${if (hadJob) 1 else 0} reset=${if (resetUserCancellation) 1 else 0}",
        )
    }
}

internal fun PlayerActivity.cancelPendingAutoNext(reason: String, markCancelledByUser: Boolean) {
    val hadPending = autoNextPending != null
    val hadHint = autoNextHintVisible
    val wasArmed = autoNextAfterEndedArmed
    val hadJob = autoNextAfterEndedJob != null
    autoNextPending = null
    dismissAutoNextHint()
    if (markCancelledByUser) autoNextCancelledByUser = true
    autoNextAfterEndedArmed = false
    autoNextAfterEndedToken = 0L
    autoNextAfterEndedJob?.cancel()
    autoNextAfterEndedJob = null
    if (hadPending || hadHint || wasArmed || hadJob || markCancelledByUser) {
        trace?.log(
            "autonext:cancel",
            "reason=$reason pending=${if (hadPending) 1 else 0} hint=${if (hadHint) 1 else 0} armed=${if (wasArmed) 1 else 0} job=${if (hadJob) 1 else 0} user=${if (markCancelledByUser) 1 else 0}",
        )
    }
}

internal fun PlayerActivity.showAutoNextHint(target: AutoNextTarget) {
    autoNextPending = target
    autoNextHintVisible = true
    val title = formatAutoNextHintTitle(target.rawTitle, fallbackTitle = target.fallbackTitle)
    val msg = "即将播放 $title"
    autoNextHintText = msg
    // Reuse the existing bottom "seek hint" component for consistent look & feel.
    showSeekHint(msg, hold = true)
}

internal fun PlayerActivity.showAutoNextHintFallback(fallbackTitle: String) {
    autoNextPending = null
    autoNextHintVisible = true
    val title = formatAutoNextHintTitle(rawTitle = null, fallbackTitle = fallbackTitle)
    val msg = "即将播放 $title"
    autoNextHintText = msg
    showSeekHint(msg, hold = true)
}

internal fun PlayerActivity.dismissAutoNextHint() {
    if (!autoNextHintVisible) return
    val msg = autoNextHintText
    autoNextHintVisible = false
    autoNextHintText = null
    // tvSeekHint is shared across multiple features (auto-resume/skip/etc). Only hide it if we are still showing our own message.
    if (msg != null && binding.tvSeekHint.text?.toString() == msg) {
        seekHintJob?.cancel()
        binding.tvSeekHint.visibility = View.GONE
    }
}

internal fun PlayerActivity.maybeWarmUpAutoNextTarget() {
    when (resolvedPlaybackMode()) {
        AppPrefs.PLAYER_PLAYBACK_MODE_RECOMMEND,
        AppPrefs.PLAYER_PLAYBACK_MODE_PARTS_LIST_THEN_RECOMMEND,
        -> ensureRecommendCardsLoadedForAutoNext()

        else -> Unit
    }
}

internal fun PlayerActivity.resolveAutoNextTargetByPlaybackMode(preloadRecommendation: Boolean): AutoNextTarget? {
    return when (resolvedPlaybackMode()) {
        AppPrefs.PLAYER_PLAYBACK_MODE_PAGE_LIST -> resolvePageAutoNextTarget()
        AppPrefs.PLAYER_PLAYBACK_MODE_PARTS_LIST -> resolvePartsAutoNextTarget()
        AppPrefs.PLAYER_PLAYBACK_MODE_PARTS_LIST_THEN_RECOMMEND ->
            resolvePartsAutoNextTarget()
                ?: resolveRecommendedAutoNextTarget(preloadRecommendation = preloadRecommendation)
        AppPrefs.PLAYER_PLAYBACK_MODE_RECOMMEND -> resolveRecommendedAutoNextTarget(preloadRecommendation = preloadRecommendation)
        else -> null
    }
}

private fun PlayerActivity.resolvePageAutoNextTarget(): AutoNextTarget.Page? {
    val list = pageListItems
    if (list.isEmpty() || pageListIndex !in list.indices) return null
    val nextIndex = pageListIndex + 1
    val nextItem = list.getOrNull(nextIndex)
    if (nextItem == null) {
        preloadNextPageForPlaylist(PlayerVideoListKind.PAGE)
        return null
    }
    if (nextItem.bvid.isBlank() && (nextItem.aid ?: 0L) <= 0L) return null
    return AutoNextTarget.Page(index = nextIndex, rawTitle = nextItem.title)
}

private fun PlayerActivity.resolvePartsAutoNextTarget(): AutoNextTarget.Parts? {
    val list = partsListItems
    if (list.isEmpty() || partsListIndex !in list.indices) return null
    val nextIndex = partsListIndex + 1
    val nextItem = list.getOrNull(nextIndex)
    if (nextItem == null) {
        preloadNextPageForPlaylist(PlayerVideoListKind.PARTS)
        return null
    }
    if (nextItem.bvid.isBlank() && (nextItem.aid ?: 0L) <= 0L) return null
    return AutoNextTarget.Parts(index = nextIndex, rawTitle = nextItem.title)
}

private fun PlayerActivity.resolveRecommendedAutoNextTarget(preloadRecommendation: Boolean): AutoNextTarget.Recommend? {
    val requestBvid = currentBvid.trim()
    if (requestBvid.isBlank()) return null
    val cached = relatedVideosCache?.takeIf { it.bvid == requestBvid }?.items.orEmpty()
    val picked = pickRecommendedVideo(cached, excludeBvid = requestBvid)
    if (picked == null) {
        if (preloadRecommendation) ensureRecommendCardsLoadedForAutoNext()
        return null
    }
    return AutoNextTarget.Recommend(
        bvid = picked.bvid,
        cid = picked.cid?.takeIf { it > 0 },
        aid = picked.aid?.takeIf { it > 0 },
        rawTitle = picked.title.takeIf { it.isNotBlank() },
    )
}

internal fun PlayerActivity.playAutoNextTarget(target: AutoNextTarget) {
    when (target) {
        is AutoNextTarget.Page -> playPageListIndex(target.index)
        is AutoNextTarget.Parts -> playPartsListIndex(target.index)
        is AutoNextTarget.Recommend ->
            startPlayback(
                bvid = target.bvid,
                cidExtra = target.cid,
                epIdExtra = null,
                aidExtra = target.aid,
                initialTitle = target.rawTitle,
                startedFromList = PlayerVideoListKind.RECOMMEND,
            )
    }
}

internal fun PlayerActivity.armAutoNextAfterEnded(reason: String) {
    if (autoNextAfterEndedArmed) return
    autoNextAfterEndedArmed = true
    trace?.log("autonext:ended", "action=arm reason=$reason")
}

internal fun PlayerActivity.pauseAutoNextAfterEnded(reason: String) {
    val hadHint = autoNextHintVisible
    val hadPending = autoNextPending != null
    val hadJob = autoNextAfterEndedJob != null
    autoNextAfterEndedToken = 0L
    autoNextAfterEndedJob?.cancel()
    autoNextAfterEndedJob = null
    autoNextPending = null
    dismissAutoNextHint()
    if (hadHint || hadPending || hadJob) {
        trace?.log(
            "autonext:ended",
            "action=pause reason=$reason hint=${if (hadHint) 1 else 0} pending=${if (hadPending) 1 else 0} job=${if (hadJob) 1 else 0}",
        )
    }
}

internal fun PlayerActivity.maybeStartAutoNextAfterEndedCountdown() {
    if (!autoNextAfterEndedArmed) return
    val engine = player ?: return
    if (engine.playbackState != Player.STATE_ENDED) {
        clearAutoNextState(reason = "ended_left_state", resetUserCancellation = false)
        return
    }
    if (autoNextCancelledByUser) {
        clearAutoNextState(reason = "ended_cancelled", resetUserCancellation = false)
        return
    }

    val mode = resolvedPlaybackMode()
    if (!autoNextModeAllowsNext(mode)) {
        clearAutoNextState(reason = "ended_mode", resetUserCancellation = false)
        return
    }

    if (isAutoNextUiBlocked()) {
        pauseAutoNextAfterEnded(reason = "ui_blocked")
        return
    }

    // Clear stale completed job reference.
    autoNextAfterEndedJob?.let { job ->
        if (!job.isActive) autoNextAfterEndedJob = null
    }
    if (autoNextAfterEndedJob != null) return

    // Proactively warm up recommendations during the countdown window.
    maybeWarmUpAutoNextTarget()

    val target = resolveAutoNextTargetByPlaybackMode(preloadRecommendation = true)
    if (target != null) {
        showAutoNextHint(target)
    } else if (shouldShowAutoNextHintFallback(mode)) {
        showAutoNextHintFallback(autoNextFallbackTitleForMode(mode))
    }

    val token = SystemClock.uptimeMillis()
    autoNextAfterEndedToken = token
    autoNextAfterEndedJob =
        lifecycleScope.launch {
            delay(PlayerActivity.AUTO_NEXT_PREVIEW_WINDOW_MS)
            if (autoNextAfterEndedToken != token) return@launch
            autoNextAfterEndedJob = null

            val engineNow = player ?: return@launch
            if (!autoNextAfterEndedArmed) return@launch
            if (engineNow.playbackState != Player.STATE_ENDED) {
                clearAutoNextState(reason = "ended_left_state", resetUserCancellation = false)
                return@launch
            }
            if (autoNextCancelledByUser) {
                clearAutoNextState(reason = "ended_cancelled", resetUserCancellation = false)
                return@launch
            }
            val modeNow = resolvedPlaybackMode()
            if (!autoNextModeAllowsNext(modeNow)) {
                clearAutoNextState(reason = "ended_mode", resetUserCancellation = false)
                return@launch
            }
            if (isAutoNextUiBlocked()) {
                pauseAutoNextAfterEnded(reason = "ui_blocked_fire")
                return@launch
            }

            val targetNow = autoNextPending ?: resolveAutoNextTargetByPlaybackMode(preloadRecommendation = false)
            // Disarm before executing: after the countdown fires, BACK should go back to normal behavior.
            autoNextAfterEndedArmed = false
            autoNextAfterEndedToken = 0L
            autoNextPending = null
            dismissAutoNextHint()
            trace?.log(
                "autonext:ended",
                "action=play mode=$modeNow target=${targetNow?.javaClass?.simpleName ?: "fallback"}",
            )
            if (targetNow != null) {
                playAutoNextTarget(targetNow)
            } else {
                playNextByPlaybackMode(userInitiated = false)
            }
        }
}

internal fun PlayerActivity.maybeUpdateAutoNext(posMs: Long, durationMs: Long) {
    val engine = player ?: return
    if (engine.playbackState == Player.STATE_ENDED) {
        // After END we defer auto-next until all OSD/panels are fully closed.
        maybeStartAutoNextAfterEndedCountdown()
        return
    }

    // If playback resumed (or user restarted), ensure any ended countdown is fully disarmed.
    if (autoNextAfterEndedArmed || autoNextAfterEndedJob != null) {
        clearAutoNextState(reason = "left_ended", resetUserCancellation = false)
    }

    if (durationMs <= 0L) {
        clearAutoNextPreviewState(reason = "no_duration", resetUserCancellation = false)
        return
    }

    val remainingMs = durationMs - posMs
    if (remainingMs > PlayerActivity.AUTO_NEXT_PREVIEW_WINDOW_MS) {
        clearAutoNextPreviewState(reason = "outside_window", resetUserCancellation = true)
        return
    }
    // At (or beyond) the end we rely on STATE_ENDED to handle the actual transition; do NOT reset
    // user cancellation here, otherwise a "BACK to cancel" can be accidentally cleared right before ENDED.
    if (remainingMs <= 0L) return

    if (isAutoNextUiBlocked()) {
        clearAutoNextPreviewState(reason = "ui_blocked", resetUserCancellation = false)
        return
    }

    // While the user is scrubbing, suppress auto-next hint to avoid distracting UI jumps.
    if (scrubbing) {
        clearAutoNextPreviewState(reason = "blocked", resetUserCancellation = false)
        return
    }

    if (autoNextCancelledByUser) return

    val target = resolveAutoNextTargetByPlaybackMode(preloadRecommendation = true)
    if (target == null) {
        clearAutoNextPreviewState(reason = "unresolved", resetUserCancellation = false)
        return
    }

    val msg = autoNextHintText
    val showing =
        autoNextHintVisible &&
            msg != null &&
            binding.tvSeekHint.visibility == View.VISIBLE &&
            binding.tvSeekHint.text?.toString() == msg
    if (target == autoNextPending && showing) return
    trace?.log("autonext:pending", "mode=${resolvedPlaybackMode()} target=${target.javaClass.simpleName}")
    showAutoNextHint(target)
}

internal fun PlayerActivity.restartCurrentPlaybackFromBeginning(
    engine: BlblPlayerEngine,
    showControls: Boolean,
    showHint: Boolean,
) {
    clearAutoNextState(reason = "restart", resetUserCancellation = true)
    engine.seekTo(0)
    engine.playWhenReady = true
    engine.play()
    if (showControls) setControlsVisible(true)
    if (showHint) showSeekHint("播放", hold = false)
}
