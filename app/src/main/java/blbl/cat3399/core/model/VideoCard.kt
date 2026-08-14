package blbl.cat3399.core.model

data class VideoCard(
    val bvid: String,
    val cid: Long?,
    val aid: Long? = null,
    val epId: Long? = null,
    val business: String? = null,
    val subType: Int? = null,
    val title: String,
    val coverUrl: String,
    val durationSec: Int,
    val ownerName: String,
    val ownerFace: String?,
    val ownerMid: Long? = null,
    val view: Long?,
    val danmaku: Long?,
    val pubDate: Long?,
    val pubDateText: String?,
    val isChargingArc: Boolean = false,
    // Optional overlay text rendered on the cover (bottom-left). Used by PGC episode cards.
    val coverLeftBottomText: String? = null,
    // Optional access badge rendered on the cover (top-right). Used by PGC ("大会员"/"限免"/"付费") etc.
    val accessBadgeText: String? = null,
    // Optional season id (ssid). Used by PGC playback/reporting flows.
    val seasonId: Long? = null,
    // Optional last watched progress from listing APIs (seconds).
    val progressSec: Long? = null,
    // True when the listing API marks this item as fully watched.
    val progressFinished: Boolean = false,
    // Optional recommendation/search routing id used by web feedback/report flows.
    val trackId: String? = null,
) {
    fun stableKey(): String =
        when {
            bvid.isNotBlank() -> "bvid:$bvid"
            epId != null && epId > 0 -> "ep:$epId"
            seasonId != null && seasonId > 0 -> "sid:$seasonId"
            aid != null && aid > 0 -> "aid:$aid"
            cid != null && cid > 0 -> "cid:$cid"
            else -> "title:${title.hashCode()}"
        }
}
