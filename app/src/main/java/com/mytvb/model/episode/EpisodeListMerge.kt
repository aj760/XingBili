package com.mytvb.model.episode

/**
 * 详情页和播放器共用的正片列表拼法。
 *
 * pgc/web/season/section 的正片分区只有已更新正片；pgc/view/web/season 的 episodes
 * 会把预告集（section_type=1，角标「预告」）混在正片里。连载中官方选集是
 * 「最新正片后面接下一集预告」，看剧进度也经常停在这集；已完结不再有下一集，
 * 历史预告整季都在，混进正片行会把顺序打乱，所以只在连载中补集号刚好比最新正片大 1 的那一集。
 */
object EpisodeListMerge {

    fun mergeOngoingNextPreview(
        mainEpisodes: List<EpisodeModel>,
        seasonEpisodes: List<EpisodeModel>,
        isFinished: Boolean
    ): List<EpisodeModel> {
        if (isFinished || mainEpisodes.isEmpty()) {
            return mainEpisodes
        }
        val mainIds = mainEpisodes.map { it.id }.toSet()
        val latestEpisodeNumber = mainEpisodes.mapNotNull { it.title.toIntOrNull() }.maxOrNull()
            ?: return mainEpisodes
        val nextPreview = seasonEpisodes.firstOrNull { episode ->
            episode.id > 0L &&
                episode.id !in mainIds &&
                episode.sectionType == PREVIEW_SECTION_TYPE &&
                episode.title.toIntOrNull() == latestEpisodeNumber + 1
        } ?: return mainEpisodes
        return mainEpisodes + nextPreview
    }

    private const val PREVIEW_SECTION_TYPE = 1
}
