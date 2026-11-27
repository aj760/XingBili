package com.mytvb.feature.player.sponsor

import com.mytvb.core.common.log.AppLog
import kotlinx.coroutines.CancellationException

class SponsorBlockUseCase {

    companion object {
        private const val TAG = "SponsorBlockUseCase"
        private const val GRACE_PERIOD_MS = 3000L
        private const val SEEK_BACK_THRESHOLD_MS = 2000L
    }

    enum class SkipAction { AUTO_SKIP, SHOW_BUTTON, MUTE }

    data class SkipResult(
        val segment: SponsorSegment,
        val action: SkipAction
    )

    private var segments: List<SponsorSegment> = emptyList()
    private var nextSegmentIndex: Int = 0
    private val skippedIds = mutableSetOf<String>()
    private var lastPositionMs: Long = 0
    private var lastAutoSkipTime: Long = 0

    var lastError: String? = null
        private set

    suspend fun loadSegments(
        bvid: String,
        cid: Long,
        categories: List<String> = SponsorSegment.ALL_CATEGORIES
    ) {
        reset()
        try {
            val result = SponsorBlockRepository.getSegments(bvid, cid, categories)
            segments = result.segments
            lastError = result.error
            nextSegmentIndex = 0
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            AppLog.e(TAG, "加载空降片段失败: ${e.javaClass.simpleName}: ${e.message}", e)
            lastError = "空降助手加载失败"
        }
    }

    /** 装载非网络来源的已归一化片段（官方片头片尾等）：重置状态后按起点排序。 */
    fun loadSegments(rawSegments: List<SponsorSegment>) {
        reset()
        segments = rawSegments.sortedBy { it.startTimeMs }
    }

    /** 单测注入：绕过网络直接装载已归一化的片段（按 Repository 口径先排序）。 */
    internal fun loadForTest(rawSegments: List<SponsorSegment>) {
        loadSegments(rawSegments)
    }

    fun checkPosition(positionMs: Long, autoSkip: Boolean): SkipResult? {
        if (segments.isEmpty()) return null

        val isGracePeriod = System.currentTimeMillis() - lastAutoSkipTime < GRACE_PERIOD_MS

        val prevMs = lastPositionMs

        if (!isGracePeriod) {
            if (positionMs < lastPositionMs - SEEK_BACK_THRESHOLD_MS) {
                resetSkippedForSeek(positionMs)
            }
            lastPositionMs = positionMs
        } else {
            if (positionMs > lastPositionMs) {
                lastPositionMs = positionMs
            }
        }

        while (nextSegmentIndex < segments.size) {
            val candidate = segments[nextSegmentIndex]
            if (candidate.UUID in skippedIds || positionMs > candidate.endTimeMs) {
                nextSegmentIndex++
                continue
            }
            break
        }

        val segment = segments.getOrNull(nextSegmentIndex)
            ?.takeIf { positionMs in it.startTimeMs..it.endTimeMs }
            ?: return null

        // 只在向前播放跨入片段起点时自动跳过，回退进片段内不触发
        val justEntered = !isGracePeriod && prevMs <= segment.startTimeMs && positionMs > segment.startTimeMs

        val action = when {
            autoSkip && justEntered && segment.isMuteType -> {
                skippedIds.add(segment.UUID)
                nextSegmentIndex++
                // 静音不 seek、无位置 glitch，无需进入宽限期
                SkipAction.MUTE
            }
            autoSkip && justEntered -> {
                skippedIds.add(segment.UUID)
                lastAutoSkipTime = System.currentTimeMillis()
                nextSegmentIndex++
                SkipAction.AUTO_SKIP
            }
            else -> SkipAction.SHOW_BUTTON
        }

        return SkipResult(segment, action)
    }

    fun skipCurrent(): Long? {
        val segment = segments.getOrNull(nextSegmentIndex) ?: return null
        skippedIds.add(segment.UUID)
        nextSegmentIndex++
        return segment.endTimeMs
    }

    fun dismissCurrent() {
        val segment = segments.getOrNull(nextSegmentIndex) ?: return
        skippedIds.add(segment.UUID)
        nextSegmentIndex++
    }

    fun onUserSeek(positionMs: Long) {
        resetSkippedForSeek(positionMs)
        nextSegmentIndex = segments.indexOfFirst { positionMs <= it.endTimeMs }
            .takeIf { it >= 0 } ?: segments.size
        lastPositionMs = positionMs
        lastAutoSkipTime = 0
    }

    fun getSegments(): List<SponsorSegment> = segments

    fun reset() {
        segments = emptyList()
        nextSegmentIndex = 0
        skippedIds.clear()
        lastPositionMs = 0
        lastAutoSkipTime = 0
    }

    private fun resetSkippedForSeek(positionMs: Long) {
        val keep = skippedIds.filterTo(mutableSetOf()) { id ->
            segments.firstOrNull { it.UUID == id }?.let { positionMs > it.endTimeMs } ?: true
        }
        skippedIds.clear()
        skippedIds.addAll(keep)
    }
}
