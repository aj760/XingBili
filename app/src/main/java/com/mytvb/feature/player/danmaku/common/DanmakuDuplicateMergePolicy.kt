package com.mytvb.feature.player.danmaku.common

import com.mytvb.model.dm.DmModel

internal object DanmakuDuplicateMergePolicy {
    private const val DEFAULT_WINDOW_MS = 2_000
    private const val MERGE_DISPLAY_THRESHOLD = 3
    private const val MERGE_TEXT_SIZE_BONUS = 2

    fun merge(
        items: List<DmModel>,
        windowMs: Int = DEFAULT_WINDOW_MS
    ): List<DmModel> {
        if (items.size < 2 || windowMs <= 0) return items
        val result = ArrayList<DmModel>(items.size)
        val buckets = LinkedHashMap<MergeKey, MutableList<DmModel>>()
        // 调用方（两个引擎的 preprocess）均已保证按 progress 升序；O(n) 守卫只兜底
        // 意外的乱序输入，避免每次全量 sortedBy 的 O(n log n) + 数组分配（2 万条时数 ms）。
        val sortedInput = if (isSortedByProgress(items)) items else items.sortedBy { it.progress }
        for (item in sortedInput) {
            flushExpired(
                buckets = buckets,
                expireBeforeMs = item.progress - windowMs,
                result = result
            )
            if (!item.canMergeDuplicate()) {
                result += item
                continue
            }
            val key = item.mergeKey()
            buckets.getOrPut(key) { ArrayList() }.add(item)
        }
        flushAll(buckets, result)
        return result.sortedBy { it.progress }
    }

    /**
     * 仅当新批次是时间尾追加，且不会与旧批次尾部形成重复合并组时，旧预处理结果才可保留。
     * 返回 false 只表示需要全量重建，允许保守误判，不能漏掉真实跨批冲突。
     */
    fun canAppendWithoutRebuildingExisting(
        existingSorted: List<DmModel>,
        incomingSorted: List<DmModel>,
        mergeDuplicate: Boolean,
        windowMs: Int = DEFAULT_WINDOW_MS
    ): Boolean {
        if (existingSorted.isEmpty() || incomingSorted.isEmpty()) return false
        if (existingSorted.last().progress > incomingSorted.first().progress) return false
        if (!mergeDuplicate || windowMs <= 0) return true

        val tailStartMs = incomingSorted.first().progress - windowMs
        var low = 0
        var high = existingSorted.size
        while (low < high) {
            val middle = (low + high) ushr 1
            if (existingSorted[middle].progress < tailStartMs) low = middle + 1 else high = middle
        }
        if (low >= existingSorted.size) return true
        val existingTailKeys = HashSet<MergeKey>(existingSorted.size - low)
        for (index in low until existingSorted.size) {
            val item = existingSorted[index]
            if (item.canMergeDuplicate()) existingTailKeys += item.mergeKey()
        }
        return incomingSorted.none { item ->
            item.canMergeDuplicate() && item.mergeKey() in existingTailKeys
        }
    }

    private fun flushExpired(
        buckets: LinkedHashMap<MergeKey, MutableList<DmModel>>,
        expireBeforeMs: Int,
        result: MutableList<DmModel>
    ) {
        val iterator = buckets.iterator()
        while (iterator.hasNext()) {
            val entry = iterator.next()
            val bucket = entry.value
            if (bucket.isEmpty() || bucket.first().progress >= expireBeforeMs) {
                continue
            }
            result += bucket.toMergedItem()
            iterator.remove()
        }
    }

    private fun flushAll(
        buckets: LinkedHashMap<MergeKey, MutableList<DmModel>>,
        result: MutableList<DmModel>
    ) {
        for (bucket in buckets.values) {
            if (bucket.isNotEmpty()) {
                result += bucket.toMergedItem()
            }
        }
        buckets.clear()
    }

    private fun List<DmModel>.toMergedItem(): DmModel {
        if (size <= 1) return first()
        val first = first()
        val mergedCount = size
        val maxWeightItem = maxByOrNull { it.weight } ?: first
        val maxAiFlagItem = maxByOrNull { it.aiFlagScore } ?: first
        val representativeStyle = maxWithOrNull(
            compareBy<DmModel> { it.weight }
                .thenBy { it.aiFlagScore }
                .thenBy { it.fontSize }
        ) ?: first
        return first.copy(
            id = first.id.takeIf { it > 0L } ?: minOfProgressId(),
            color = representativeStyle.color,
            colorful = representativeStyle.colorful,
            colorfulSrc = representativeStyle.colorfulSrc,
            colorfulStyle = representativeStyle.colorfulStyle,
            content = if (mergedCount >= MERGE_DISPLAY_THRESHOLD) {
                "${first.content.trim()} ×$mergedCount"
            } else {
                first.content
            },
            fontSize = if (mergedCount >= MERGE_DISPLAY_THRESHOLD) {
                maxOf(first.fontSize, maxOf { it.fontSize }) + MERGE_TEXT_SIZE_BONUS
            } else {
                maxOf { it.fontSize }
            },
            weight = maxWeightItem.weight,
            attr = first.attr or fold(0) { acc, item -> acc or item.attr },
            aiFlagScore = maxAiFlagItem.aiFlagScore
        )
    }

    private fun List<DmModel>.minOfProgressId(): Long =
        minOf { it.progress }.toLong().coerceAtLeast(1L)

    private fun isSortedByProgress(items: List<DmModel>): Boolean {
        for (index in 1 until items.size) {
            if (items[index - 1].progress > items[index].progress) return false
        }
        return true
    }

    private fun DmModel.canMergeDuplicate(): Boolean {
        val normalized = content.trim()
        if (normalized.isBlank()) return false
        if (mode == DanmakuProtocolMode.ADVANCED || mode == DanmakuProtocolMode.SCRIPT) return false
        if (normalized.contains("def text", ignoreCase = true)) return false
        return true
    }

    private fun DmModel.mergeKey(): MergeKey =
        MergeKey(
            content = content.trim().lowercase(),
            mode = mode,
            color = color,
            colorful = colorful,
            colorfulSrc = colorfulSrc.trim()
        )

    private data class MergeKey(
        val content: String,
        val mode: Int,
        val color: Int,
        val colorful: Int,
        val colorfulSrc: String
    )
}
