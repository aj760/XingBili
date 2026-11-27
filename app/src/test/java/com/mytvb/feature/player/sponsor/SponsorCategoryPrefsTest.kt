package com.mytvb.feature.player.sponsor

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SponsorCategoryPrefsTest {

    @Test
    fun defaultSelectionIsSponsorOnly() {
        // 未设置过任何开关时，默认只跳过「赞助/恰饭」，片头/片尾等其余类别默认关闭。
        val selected = SponsorCategoryPrefs.selectedCategories { null }

        assertEquals(listOf(SponsorSegment.CATEGORY_SPONSOR), selected)
    }

    @Test
    fun enablingAnOptionalCategoryAddsIt() {
        val stored = mapOf("sponsor_cat_intro" to "开")

        val selected = SponsorCategoryPrefs.selectedCategories { stored[it] }

        assertTrue(selected.contains(SponsorSegment.CATEGORY_SPONSOR))
        assertTrue(selected.contains(SponsorSegment.CATEGORY_INTRO))
        assertEquals(2, selected.size)
    }

    @Test
    fun disablingTheDefaultRemovesIt() {
        val stored = mapOf("sponsor_cat_sponsor" to "关")

        val selected = SponsorCategoryPrefs.selectedCategories { stored[it] }

        assertEquals(emptyList<String>(), selected)
    }

    @Test
    fun onlySponsorDefaultsOn() {
        val defaultOn = SponsorCategoryPrefs.OPTIONS.filter { it.defaultOn }.map { it.category }

        assertEquals(listOf(SponsorSegment.CATEGORY_SPONSOR), defaultOn)
    }

    @Test
    fun optionKeysMatchStoredNames() {
        val expected = listOf(
            "sponsor_cat_sponsor",
            "sponsor_cat_intro",
            "sponsor_cat_outro",
            "sponsor_cat_selfpromo",
            "sponsor_cat_interaction",
            "sponsor_cat_preview",
            "sponsor_cat_music_offtopic",
            "sponsor_cat_filler",
            "sponsor_cat_exclusive"
        )
        assertEquals(expected, SponsorCategoryPrefs.OPTIONS.map { it.key })
        assertEquals(expected.toSet(), SponsorCategoryPrefs.KEYS)
    }
}
