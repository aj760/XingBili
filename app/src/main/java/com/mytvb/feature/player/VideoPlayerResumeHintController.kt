package com.mytvb.feature.player

import com.mytvb.core.common.format.NumberUtils
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import com.mytvb.R
import java.util.Locale

@UnstableApi
class VideoPlayerResumeHintController(
    private val activity: AppCompatActivity,
    private val playerProvider: () -> Player?,
    private val onCancelResume: () -> Unit,
    private val onClearResumeHint: () -> Unit,
    private val onShowResumeHint: (timeText: String) -> Unit,
    private val onHideResumeHint: () -> Unit
) {

    companion object {
        // 提示窗口时长=窗口内 BACK 可从头播放的时长，也是续播 seek 的最短等待：
        // 2s 用户反馈来不及按，2.1.13 后放宽到 3s
        private const val SEEK_DELAY_MS = 3_000L
        private const val READY_TIMEOUT_MS = 30_000L
        private const val POLL_INTERVAL_MS = 50L
        private const val TRACE_THROTTLE_MS = 500L
    }

    private val handler = Handler(Looper.getMainLooper())
    private var resumeHintRunnable: Runnable? = null
    private var hideHintRunnable: Runnable? = null
    private var resumeHintStartTimeMs: Long = 0L
    private var resumeHintTargetPositionMs: Long = 0L
    private var isResumeHintCancelled: Boolean = false
    private var isResumeHintActive: Boolean = false
    private var lastTraceSignature: String? = null
    private var lastTraceTimestampMs: Long = 0L

    fun cancelResume(): Boolean {
        if (!isResumeHintActive) {
            return false
        }
        clearPendingUi(markCancelled = true)
        onCancelResume()
        playerProvider()?.seekTo(0L)
        return true
    }

    fun onHintChanged(hint: VideoPlayerViewModel.ResumeProgressHint?) {
        if (hint == null) {
            clearPendingUi(markCancelled = true)
            return
        }

        isResumeHintCancelled = false
        isResumeHintActive = true
        resumeHintTargetPositionMs = hint.targetPositionMs
        resumeHintStartTimeMs = System.currentTimeMillis()
        lastTraceSignature = null
        lastTraceTimestampMs = 0L

        val timeStr = NumberUtils.formatTimeMs(hint.targetPositionMs)
        onShowResumeHint(activity.getString(R.string.tip_play_from_history, timeStr))
        hideHintRunnable?.let(handler::removeCallbacks)
        hideHintRunnable = Runnable {
            isResumeHintActive = false
            onHideResumeHint()
        }
        handler.postDelayed(hideHintRunnable!!, SEEK_DELAY_MS)

        scheduleResumeCheck(immediate = true)
    }

    fun release() {
        clearPendingUi(markCancelled = true)
    }

    private fun scheduleResumeCheck(immediate: Boolean) {
        resumeHintRunnable?.let(handler::removeCallbacks)
        resumeHintRunnable = Runnable { checkResumeHint() }
        if (immediate) {
            handler.post(resumeHintRunnable!!)
        } else {
            handler.postDelayed(resumeHintRunnable!!, POLL_INTERVAL_MS)
        }
    }

    private fun checkResumeHint() {
        val player = playerProvider() ?: return
        if (isResumeHintCancelled) {
            return
        }

        val currentTimeMs = System.currentTimeMillis()
        val seekNotBeforeAtMs = resumeHintStartTimeMs + SEEK_DELAY_MS
        val readyDeadlineAtMs = resumeHintStartTimeMs + READY_TIMEOUT_MS

        if (currentTimeMs >= readyDeadlineAtMs) {
            onClearResumeHint()
            return
        }

        if (player.playbackState == Player.STATE_ENDED) {
            onClearResumeHint()
            return
        }

        val playerReady = player.playbackState == Player.STATE_READY
        val waitedEnough = currentTimeMs >= seekNotBeforeAtMs
        val traceSignature = "${player.playbackState}|$playerReady|$waitedEnough|${resumeHintTargetPositionMs / 1000}"
        val shouldTrace = traceSignature != lastTraceSignature ||
            currentTimeMs - lastTraceTimestampMs >= TRACE_THROTTLE_MS
        if (shouldTrace) {
            lastTraceSignature = traceSignature
            lastTraceTimestampMs = currentTimeMs
        }

        if (playerReady && waitedEnough) {
            val durationMs = player.duration.takeIf { it > 0L } ?: 0L
            val clampedTargetPositionMs = if (durationMs > 0L) {
                resumeHintTargetPositionMs.coerceIn(0L, (durationMs - 500L).coerceAtLeast(0L))
            } else {
                resumeHintTargetPositionMs
            }
            onClearResumeHint()
            // setMediaSource(source, seekPositionMs) already starts the player at the
            // resume position.  Seeking again after the toast window causes a backward
            // jump + re-buffer; when the audio CDN is fast but the video CDN is slow this
            // re-buffer introduces a persistent A/V offset.  Only seek when the player has
            // drifted far from the target (e.g. the initial start-position was not applied).
            if (kotlin.math.abs(player.currentPosition - clampedTargetPositionMs) <= SEEK_DELAY_MS + 1000L) {
                return
            }
            player.seekTo(clampedTargetPositionMs)
            return
        }

        scheduleResumeCheck(immediate = false)
    }

    private fun clearPendingUi(markCancelled: Boolean) {
        isResumeHintCancelled = markCancelled
        isResumeHintActive = false
        lastTraceSignature = null
        lastTraceTimestampMs = 0L
        resumeHintRunnable?.let(handler::removeCallbacks)
        resumeHintRunnable = null
        hideHintRunnable?.let(handler::removeCallbacks)
        hideHintRunnable = null
        onHideResumeHint()
    }

}
