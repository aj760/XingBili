package com.mytvb.feature.player.danmaku.common

import java.util.IdentityHashMap

/** Counts each Bitmap owner once, regardless of how many cache layers reference it. */
internal class BitmapMemoryBudget(maxBytes: Long) {
    private val lock = Any()
    private val trackedBytes = IdentityHashMap<Any, Long>()
    private var usedBytes = 0L

    val maxBytes: Long = maxBytes.coerceAtLeast(0L)

    fun trySetBytes(owner: Any, bytes: Long): Boolean = synchronized(lock) {
        val normalized = bytes.coerceAtLeast(0L)
        val previous = trackedBytes[owner] ?: 0L
        val next = (usedBytes - previous + normalized).coerceAtLeast(0L)
        if (next > maxBytes) return@synchronized false
        if (normalized == 0L) {
            trackedBytes.remove(owner)
        } else {
            trackedBytes[owner] = normalized
        }
        usedBytes = next
        true
    }

    fun release(owner: Any): Long = synchronized(lock) {
        val released = trackedBytes.remove(owner) ?: return@synchronized 0L
        usedBytes = (usedBytes - released).coerceAtLeast(0L)
        released
    }

    fun replaceOwner(previousOwner: Any, newOwner: Any, bytes: Long): Boolean = synchronized(lock) {
        val previous = trackedBytes[previousOwner] ?: return@synchronized false
        val existing = trackedBytes[newOwner] ?: 0L
        val normalized = bytes.coerceAtLeast(0L)
        val next = (usedBytes - previous - existing + normalized).coerceAtLeast(0L)
        if (next > maxBytes) return@synchronized false
        trackedBytes.remove(previousOwner)
        if (normalized == 0L) {
            trackedBytes.remove(newOwner)
        } else {
            trackedBytes[newOwner] = normalized
        }
        usedBytes = next
        true
    }

    fun canFit(additionalBytes: Long): Boolean = synchronized(lock) {
        additionalBytes.coerceAtLeast(0L) <= maxBytes - usedBytes
    }

    fun snapshot(): Snapshot = synchronized(lock) {
        Snapshot(usedBytes = usedBytes, maxBytes = maxBytes, bitmapCount = trackedBytes.size)
    }

    internal data class Snapshot(
        val usedBytes: Long,
        val maxBytes: Long,
        val bitmapCount: Int,
    )
}

internal fun estimatedArgb8888Bytes(width: Int, height: Int): Long =
    width.coerceAtLeast(1).toLong()
        .times(height.coerceAtLeast(1).toLong())
        .coerceAtMost(Long.MAX_VALUE / 4L) * 4L

internal fun resolveDanmakuBitmapBudgetBytes(
    screenWidth: Int,
    screenHeight: Int,
    availableMemoryBytes: Long = Runtime.getRuntime().let {
        it.maxMemory() - (it.totalMemory() - it.freeMemory())
    },
): Long {
    val mb = 1024L * 1024L
    val pixels = screenWidth.coerceAtLeast(0).toLong() * screenHeight.coerceAtLeast(0).toLong()
    val baseBytes = when {
        pixels <= 1280L * 720L -> 32L * mb
        pixels <= 1920L * 1080L -> 50L * mb
        else -> 72L * mb
    }
    return if (availableMemoryBytes < 512L * mb) baseBytes / 2L else baseBytes
}

internal inline fun reclaimUntilBitmapBudgetFits(
    budget: BitmapMemoryBudget,
    requiredBytes: Long,
    evictPooled: () -> Boolean,
    evictShared: () -> Boolean,
): Boolean {
    while (!budget.canFit(requiredBytes)) {
        if (evictPooled()) continue
        if (evictShared()) continue
        return false
    }
    return true
}
