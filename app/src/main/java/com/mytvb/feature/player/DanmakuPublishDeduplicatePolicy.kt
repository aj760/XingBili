package com.mytvb.feature.player

import com.mytvb.model.dm.DmModel

internal data class DistinctDanmakuBatch(
    val items: List<DmModel>,
    val identityKeys: List<String>,
)

internal fun List<DmModel>.distinctRegularDanmaku(): List<DmModel> {
    if (size < 2) return sortedBy { it.progress }
    val seen = HashSet<String>(size)
    return asSequence()
        .filter { seen.add(it.danmakuIdentityKey()) }
        .sortedBy { it.progress }
        .toList()
}

internal fun List<DmModel>.distinctRegularDanmakuBatch(): DistinctDanmakuBatch {
    if (isEmpty()) return DistinctDanmakuBatch(emptyList(), emptyList())
    val seen = HashSet<String>(size)
    val keyed = ArrayList<Pair<DmModel, String>>(size)
    for (item in this) {
        val key = item.danmakuIdentityKey()
        if (seen.add(key)) keyed.add(item to key)
    }
    keyed.sortBy { it.first.progress }
    val items = ArrayList<DmModel>(keyed.size)
    val identityKeys = ArrayList<String>(keyed.size)
    for ((item, key) in keyed) {
        items.add(item)
        identityKeys.add(key)
    }
    return DistinctDanmakuBatch(items, identityKeys)
}

internal fun DmModel.danmakuIdentityKey(): String {
    if (id > 0L) return "id:$id"
    val normalizedIdStr = idStr.trim()
    if (normalizedIdStr.isNotEmpty()) return "id:$normalizedIdStr"
    return listOf(
        "fallback",
        progress,
        mode,
        color,
        colorful,
        colorfulSrc.trim(),
        fontSize,
        pool,
        attr,
        midHash.trim(),
        ctime,
        action.trim(),
        animation.trim(),
        content.trim()
    ).joinToString(separator = "|")
}
