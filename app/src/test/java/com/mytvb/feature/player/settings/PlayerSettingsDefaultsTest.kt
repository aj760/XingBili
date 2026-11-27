package com.mytvb.feature.player.settings

import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerSettingsDefaultsTest {

    @Test
    fun sponsorBlockIsOnByDefault() {
        assertTrue(PlayerSettings().sponsorBlockEnabled)
    }

    @Test
    fun skipOfficialOpEdIsOnByDefault() {
        assertTrue(PlayerSettings().skipOfficialOpEd)
    }
}
