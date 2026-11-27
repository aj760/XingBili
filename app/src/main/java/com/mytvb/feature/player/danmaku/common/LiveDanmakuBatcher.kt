package com.mytvb.feature.player.danmaku.common

import com.mytvb.model.dm.DmModel
import kotlin.math.max

/** Engine-neutral throttling and optional duplicate merging for live danmaku. */
internal class LiveDanmakuBatcher(
    private val throttleWindowMs: Long = DEFAULT_THROTTLE_WINDOW_MS,
    private val throttleMaxItems: Int = DEFAULT_THROTTLE_MAX_ITEMS,
    private val mergeWindowMs: Long = DEFAULT_MERGE_WINDOW_MS,
    private val densityWindowMs: Long = DEFAULT_DENSITY_WINDOW_MS,
) {
    companion object {
        const val DEFAULT_THROTTLE_WINDOW_MS = 100L
        const val DEFAULT_THROTTLE_MAX_ITEMS = 30
        const val DEFAULT_MERGE_WINDOW_MS = 800L
        const val DEFAULT_DENSITY_WINDOW_MS = 5_000L
    }

    private data class MergeKey(
        val content: String,
        val mode: Int,
        val color: Int,
        val colorful: Int,
        val colorfulSrc: String,
    )

    private data class PendingEntry(
        val firstItem: DmModel,
        var count: Int,
        val createdAtMs: Long,
    )

    private val pending = LinkedHashMap<MergeKey, PendingEntry>()
    private val emittedAtMs = ArrayDeque<Long>()
    private var throttleWindowStartedAtMs = Long.MIN_VALUE
    private var throttleCount = 0

    fun offer(
        item: DmModel,
        nowMs: Long,
        mergeEnabled: Boolean,
        displayCapacity: Int,
    ): List<DmModel> {
        val emitted = ArrayList<DmModel>()
        emitted.addAll(flushExpired(nowMs, displayCapacity))
        if (item.content.isBlank() || !acceptWithinThrottle(nowMs)) return emitted

        if (!mergeEnabled) {
            if (emittedAtMs.size < displayCapacity) {
                emitted.add(item)
                recordEmitted(nowMs, 1)
            }
            return emitted
        }

        val key = MergeKey(
            content = item.content.trim().lowercase(),
            mode = item.mode,
            color = item.color,
            colorful = item.colorful,
            colorfulSrc = item.colorfulSrc.trim(),
        )
        val existing = pending[key]
        if (existing != null && nowMs - existing.createdAtMs <= mergeWindowMs) {
            existing.count++
        } else {
            pending[key] = PendingEntry(item, count = 1, createdAtMs = nowMs)
        }
        return emitted
    }

    fun flushExpired(nowMs: Long, displayCapacity: Int): List<DmModel> {
        pruneEmitted(nowMs)
        if (pending.isEmpty()) return emptyList()
        val result = ArrayList<DmModel>()
        val iterator = pending.entries.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next().value
            if (nowMs - entry.createdAtMs < mergeWindowMs) continue
            appendEntry(result, entry, displayCapacity - emittedAtMs.size - result.size)
            iterator.remove()
        }
        recordEmitted(nowMs, result.size)
        return result
    }

    fun flushAll(nowMs: Long, displayCapacity: Int): List<DmModel> {
        pruneEmitted(nowMs)
        if (pending.isEmpty()) return emptyList()
        val result = ArrayList<DmModel>()
        for (entry in pending.values) {
            appendEntry(result, entry, displayCapacity - emittedAtMs.size - result.size)
        }
        pending.clear()
        recordEmitted(nowMs, result.size)
        return result
    }

    fun clear() {
        pending.clear()
        emittedAtMs.clear()
        throttleWindowStartedAtMs = Long.MIN_VALUE
        throttleCount = 0
    }

    internal fun pendingCount(): Int = pending.values.sumOf { it.count }

    fun nextFlushDelayMs(nowMs: Long): Long? {
        val earliest = pending.values.minOfOrNull { it.createdAtMs } ?: return null
        return (earliest + mergeWindowMs - nowMs).coerceAtLeast(0L)
    }

    private fun acceptWithinThrottle(nowMs: Long): Boolean {
        if (throttleWindowStartedAtMs == Long.MIN_VALUE ||
            nowMs < throttleWindowStartedAtMs ||
            nowMs - throttleWindowStartedAtMs >= throttleWindowMs
        ) {
            throttleWindowStartedAtMs = nowMs
            throttleCount = 0
        }
        if (throttleCount >= throttleMaxItems) return false
        throttleCount++
        return true
    }

    private fun appendEntry(target: MutableList<DmModel>, entry: PendingEntry, available: Int) {
        if (available <= 0) return
        target.add(if (entry.count >= 3) mergedItem(entry.firstItem, entry.count) else entry.firstItem)
    }

    private fun mergedItem(item: DmModel, count: Int): DmModel =
        item.copy(
            content = "${item.content} ×$count",
            fontSize = max(item.fontSize, 12) + 2,
        )

    private fun pruneEmitted(nowMs: Long) {
        while (emittedAtMs.isNotEmpty() && nowMs - emittedAtMs.first() > densityWindowMs) {
            emittedAtMs.removeFirst()
        }
    }

    private fun recordEmitted(nowMs: Long, count: Int) {
        repeat(count) { emittedAtMs.addLast(nowMs) }
    }
}
