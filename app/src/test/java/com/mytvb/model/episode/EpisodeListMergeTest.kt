package com.mytvb.model.episode

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeListMergeTest {

    @Test
    fun ongoingSeries_appendsOnlyTheNextPreview() {
        val main = listOf(episode(id = 1, title = "18"), episode(id = 2, title = "19"))
        val season = main + listOf(
            episode(id = 9, title = "20", sectionType = 1),
            episode(id = 10, title = "21", sectionType = 1)
        )

        val merged = EpisodeListMerge.mergeOngoingNextPreview(
            mainEpisodes = main,
            seasonEpisodes = season,
            isFinished = false
        )

        assertEquals(listOf("18", "19", "20"), merged.map { it.title })
    }

    @Test
    fun finishedSeries_keepsHistoryPreviewsOutOfTheMainRow() {
        val main = listOf(episode(id = 1, title = "1"), episode(id = 2, title = "11"))
        val season = main + episode(id = 9, title = "12", sectionType = 1)

        val merged = EpisodeListMerge.mergeOngoingNextPreview(
            mainEpisodes = main,
            seasonEpisodes = season,
            isFinished = true
        )

        assertEquals(listOf("1", "11"), merged.map { it.title })
    }

    @Test
    fun nonNumericTitle_isLeftUntouched() {
        val main = listOf(episode(id = 1, title = "总集篇"))
        val season = main + episode(id = 9, title = "2", sectionType = 1)

        val merged = EpisodeListMerge.mergeOngoingNextPreview(
            mainEpisodes = main,
            seasonEpisodes = season,
            isFinished = false
        )

        assertEquals(listOf("总集篇"), merged.map { it.title })
    }

    private fun episode(id: Long, title: String, sectionType: Int = 0): EpisodeModel {
        return EpisodeModel(id = id, title = title, sectionType = sectionType)
    }
}
