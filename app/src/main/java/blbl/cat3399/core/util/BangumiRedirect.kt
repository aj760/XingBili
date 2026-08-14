package blbl.cat3399.core.util

internal data class BangumiRedirect(
    val seasonId: Long?,
    val epId: Long?,
    val url: String,
)

private val BANGUMI_EP_ID_REGEX = Regex("/bangumi/play/ep(\\d+)")
private val BANGUMI_SEASON_ID_REGEX = Regex("/bangumi/play/ss(\\d+)")

internal fun parseBangumiRedirectUrl(rawUrl: String?): BangumiRedirect? {
    val url = rawUrl?.trim().orEmpty()
    if (url.isBlank()) return null

    val epId =
        BANGUMI_EP_ID_REGEX.find(url)?.groupValues?.getOrNull(1)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }
    val seasonId =
        BANGUMI_SEASON_ID_REGEX.find(url)?.groupValues?.getOrNull(1)
            ?.toLongOrNull()
            ?.takeIf { it > 0L }

    if (epId == null && seasonId == null) return null
    return BangumiRedirect(seasonId = seasonId, epId = epId, url = url)
}

