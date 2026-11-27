package com.mytvb.feature.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackUiCoordinatorTest {

    @Test
    fun ambientChromeShowsSlimTimelineWhenBottomProgressIsEnabledFromInitialState() {
        val coordinator = PlaybackUiCoordinator()

        coordinator.syncAmbientChrome(showBottomProgressBar = true)

        assertEquals(PlaybackUiCoordinator.ChromeState.Hidden, coordinator.chromeState)
        assertEquals(PlaybackUiCoordinator.BottomOccupant.SlimTimeline, coordinator.bottomOccupant)
        assertEquals(PlaybackUiCoordinator.HudState.Ambient, coordinator.hudState)
        assertEquals(PlaybackUiCoordinator.FocusOwner.PlayerRoot, coordinator.focusOwner)
    }

    @Test
    fun ambientChromeHidesBottomOccupantWhenBottomProgressIsDisabled() {
        val coordinator = PlaybackUiCoordinator()
        coordinator.syncAmbientChrome(showBottomProgressBar = true)

        coordinator.syncAmbientChrome(showBottomProgressBar = false)

        assertEquals(PlaybackUiCoordinator.BottomOccupant.None, coordinator.bottomOccupant)
    }
}
