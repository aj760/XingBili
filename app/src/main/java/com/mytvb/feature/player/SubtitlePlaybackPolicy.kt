package com.mytvb.feature.player

import com.mytvb.model.subtitle.SubtitleInfoModel
import java.net.URI
import java.security.MessageDigest

internal fun isSubtitleRequestCurrent(
    requestCid: Long,
    requestBvid: String?,
    currentCid: Long,
    currentBvid: String?
): Boolean = requestCid == currentCid && requestBvid == currentBvid

internal fun trustedPlayerInfoSubtitleTracks(
    detailTracks: List<SubtitleInfoModel>,
    playerInfoTracks: List<SubtitleInfoModel>
): List<SubtitleInfoModel> {
    if (detailTracks.isEmpty()) return orderSubtitleTracksByPreference(
        playerInfoTracks.filter { isTrustedBilibiliSubtitleUrl(it.subtitleUrl) }
    )

    val detailTrackKeys = detailTracks.map(::subtitleTrackIdentityKey).toSet()
    return orderSubtitleTracksByPreference(playerInfoTracks.filter { track ->
        isTrustedBilibiliSubtitleUrl(track.subtitleUrl) &&
            subtitleTrackIdentityKey(track) in detailTrackKeys
    })
}

internal fun isLikelyAiSubtitleTrack(track: SubtitleInfoModel): Boolean =
    track.aiStatus > 0 || track.aiType > 0 ||
        track.lanDoc.contains("ai", ignoreCase = true) ||
        track.lanDoc.contains("自动") ||
        track.lanDoc.contains("机翻") ||
        track.lanDoc.contains("机器")

internal fun isChineseSubtitleTrack(track: SubtitleInfoModel): Boolean =
    track.lan.startsWith("zh", ignoreCase = true)

internal fun isChineseAiSubtitleTrack(track: SubtitleInfoModel): Boolean =
    isChineseSubtitleTrack(track) && isLikelyAiSubtitleTrack(track)

/**
 * 自动字幕轨道选择优先级：
 * 1. 用户手动选择过的语言（lan 精确匹配，跨视频持久）；
 * 2. 中文 AI（B 站自动生成的中文字幕，如 ai-zh）；
 * 3. 中文（人工/CC，zh-CN、zh-Hans 等）；
 * 4. 列表第一个。
 */
internal fun selectPreferredSubtitleIndex(
    tracks: List<SubtitleInfoModel>,
    preferredLan: String?
): Int {
    if (tracks.isEmpty()) return -1
    if (!preferredLan.isNullOrBlank()) {
        tracks.indexOfFirst { it.lan.equals(preferredLan, ignoreCase = true) }
            .takeIf { it >= 0 }
            ?.let { return it }
    }
    tracks.indexOfFirst(::isChineseAiSubtitleTrack).takeIf { it >= 0 }?.let { return it }
    tracks.indexOfFirst(::isChineseSubtitleTrack).takeIf { it >= 0 }?.let { return it }
    return 0
}

/**
 * “自动”字幕模式下是否应加载外挂字幕（对齐 B 站官方 web/客户端与 blbl 的分级语义）：
 * - 存在人工上传/精校 CC 字幕 (type == 0)：视频本身无内嵌字幕，加载；
 * - 存在被标记为曝光的 AI 字幕 (ai_status == 1，说明视频本身无字幕、推荐展示)：加载；
 * - 全部为 AI 辅助备选 (ai_status == 2，说明画面已内嵌硬字幕)：不加载，避免双层字幕重叠；
 * - 其余情况不自动加载，用户可手动开启。
 */
internal fun shouldAutoLoadExternalSubtitle(tracks: List<SubtitleInfoModel>): Boolean {
    if (tracks.isEmpty()) return false
    if (tracks.any { it.type == 0 }) return true
    if (tracks.any { it.aiStatus == 1 }) return true
    if (tracks.all { it.aiStatus == 2 }) return false
    return false
}

/**
 * 字幕列表展示顺序（v2.1.15 起统一口径）：
 * 1. 用户设备语言的人工/CC 字幕置顶；
 * 2. AI 字幕（ai-zh 等，含 AI 中文字幕）次之；
 * 3. 其余语言保持服务端原始顺序（稳定排序不重排）。
 * 原实现「AI 全置顶 + id 降序」会把中文人工字幕压到列表底部，用户难以找到。
 */
internal fun orderSubtitleTracksByPreference(tracks: List<SubtitleInfoModel>): List<SubtitleInfoModel> {
    val deviceLanguage = java.util.Locale.getDefault().language.ifBlank { "zh" }
    return tracks.sortedWith(
        compareByDescending<SubtitleInfoModel> {
            !isLikelyAiSubtitleTrack(it) && it.lan.startsWith(deviceLanguage, ignoreCase = true)
        }.thenByDescending { !isLikelyAiSubtitleTrack(it) }
    )
}

internal fun normalizeBilibiliSubtitleUrl(rawUrl: String): String {
    val trimmed = rawUrl.trim()
    return when {
        trimmed.isBlank() -> ""
        trimmed.startsWith("//") -> "https:$trimmed"
        trimmed.startsWith("http://") -> "https://${trimmed.removePrefix("http://")}"
        else -> trimmed
    }
}

internal fun isTrustedBilibiliSubtitleUrl(rawUrl: String): Boolean {
    val uri = runCatching { URI(normalizeBilibiliSubtitleUrl(rawUrl)) }.getOrNull() ?: return false
    val host = uri.host?.lowercase().orEmpty()
    val trustedHost = host == "hdslb.com" || host.endsWith(".hdslb.com") ||
        host == "bilibili.com" || host.endsWith(".bilibili.com")
    return trustedHost && uri.path.orEmpty().contains("subtitle", ignoreCase = true)
}

internal fun subtitleTrackBindingKey(track: SubtitleInfoModel): String {
    val path = runCatching { URI(normalizeBilibiliSubtitleUrl(track.subtitleUrl)).path.orEmpty() }
        .getOrDefault("")
    return if (path.isBlank()) subtitleTrackIdentityKey(track) else "${subtitleTrackIdentityKey(track)}|$path"
}

internal fun subtitleTrackIdentityKey(track: SubtitleInfoModel): String {
    val identifier = track.idStr.takeIf { it.isNotBlank() }
        ?: track.id.takeIf { it > 0L }?.toString()
        ?: "no-id"
    return "$identifier|${track.lan.ifBlank { "unknown" }}"
}

internal fun refreshedSubtitleTrackFor(
    originalTrack: SubtitleInfoModel,
    refreshedTracks: List<SubtitleInfoModel>
): SubtitleInfoModel? = refreshedTracks.firstOrNull { track ->
    isTrustedBilibiliSubtitleUrl(track.subtitleUrl) &&
        subtitleTrackIdentityKey(track) == subtitleTrackIdentityKey(originalTrack)
}

internal fun shouldRetryPlayerInfoSubtitleTracks(
    detailTracks: List<SubtitleInfoModel>,
    playerInfoTracks: List<SubtitleInfoModel>
): Boolean = detailTracks.isNotEmpty() &&
    playerInfoTracks.isNotEmpty() &&
    trustedPlayerInfoSubtitleTracks(detailTracks, playerInfoTracks).isEmpty()

internal fun buildSubtitleCueCacheKey(
    bvid: String?,
    cid: Long,
    track: SubtitleInfoModel,
    normalizedUrl: String
): String {
    val urlHash = MessageDigest.getInstance("SHA-1")
        .digest(normalizedUrl.toByteArray(Charsets.UTF_8))
        .joinToString(separator = "") { "%02x".format(it) }
    return "${bvid.orEmpty()}:${cid.coerceAtLeast(0L)}:${subtitleTrackBindingKey(track)}:$urlHash"
}

internal fun shouldApplySubtitleLoadResult(
    activeToken: Long,
    resultToken: Long,
    requestCid: Long,
    requestBvid: String?,
    currentCid: Long,
    currentBvid: String?
): Boolean = activeToken == resultToken &&
    isSubtitleRequestCurrent(requestCid, requestBvid, currentCid, currentBvid)

internal fun shouldRetrySubtitleLoadWithPlayerInfo(httpCode: Int?): Boolean =
    httpCode in setOf(401, 403, 404, 410, 412)

internal fun shouldRefreshSubtitleTrack(
    track: SubtitleInfoModel,
    httpCode: Int?
): Boolean = !isTrustedBilibiliSubtitleUrl(track.subtitleUrl) ||
    shouldRetrySubtitleLoadWithPlayerInfo(httpCode)
