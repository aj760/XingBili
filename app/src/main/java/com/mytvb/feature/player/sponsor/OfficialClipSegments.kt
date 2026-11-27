package com.mytvb.feature.player.sponsor

import com.mytvb.model.player.PgcClipInfo

/**
 * 官方片头片尾（pgc playurl `clip_info_list`）→ 跳过片段转换：
 * 只认 CLIP_TYPE_OP / CLIP_TYPE_ED，过滤非法区间（end<=start），
 * 时间沿用 SponsorSegment 的秒制 List<Float> 口径。
 */
object OfficialClipSegments {

    fun toSegments(clips: List<PgcClipInfo>?): List<SponsorSegment> {
        if (clips.isNullOrEmpty()) return emptyList()
        return clips.mapNotNull { clip ->
            val category = when (clip.clipType) {
                "CLIP_TYPE_OP" -> SponsorSegment.CATEGORY_OFFICIAL_OP
                "CLIP_TYPE_ED" -> SponsorSegment.CATEGORY_OFFICIAL_ED
                else -> return@mapNotNull null
            }
            val startSec = clip.start.toFloat()
            val endSec = clip.end.toFloat()
            if (endSec <= startSec) return@mapNotNull null
            SponsorSegment(
                segment = listOf(startSec, endSec),
                UUID = "official_clip_${clip.clipType}_${clip.materialNo}",
                category = category
            )
        }
    }
}
