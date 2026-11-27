package com.mytvb.feature.player

import com.mytvb.feature.player.settings.AfterPlayMode
import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackCompletionDispatcherTest {
    private fun dispatch(
        plan: PlayerSessionCoordinator.ContinuationPlan,
        mode: AfterPlayMode,
        locked: Boolean = false
    ): String {
        var action = ""
        PlaybackCompletionDispatcher.dispatch(plan, mode, locked,
            play = { action = "play" },
            showRecommendations = { action = "recommendations" },
            exit = { action = "exit" },
            showController = { action = "controller" })
        return action
    }
    @Test fun nothingModePreservesExitAndControllerBehavior() {
        assertEquals("exit", dispatch(PlayerSessionCoordinator.ContinuationPlan.ExitPlayer, AfterPlayMode.NOTHING))
        assertEquals("controller", dispatch(PlayerSessionCoordinator.ContinuationPlan.ShowController, AfterPlayMode.NOTHING))
    }
    @Test fun autoplayWithoutNextFallsBackToRecommendationsUnlessLocked() {
        val mode = AfterPlayMode.entries.first { it != AfterPlayMode.NOTHING }
        assertEquals("recommendations", dispatch(PlayerSessionCoordinator.ContinuationPlan.ExitPlayer, mode))
        assertEquals("exit", dispatch(PlayerSessionCoordinator.ContinuationPlan.ExitPlayer, mode, true))
        assertEquals("controller", dispatch(PlayerSessionCoordinator.ContinuationPlan.ShowController, mode, true))
    }
}
