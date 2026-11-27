package com.mytvb.feature.player

import com.mytvb.feature.player.settings.AfterPlayMode

/** Both hosts execute the same completion policy; host-specific exit stays injected. */
internal object PlaybackCompletionDispatcher {
    fun dispatch(
        plan: PlayerSessionCoordinator.ContinuationPlan,
        mode: AfterPlayMode,
        locked: Boolean = false,
        play: (ContinuationPlaybackIntent) -> Unit,
        showRecommendations: () -> Unit,
        exit: () -> Unit,
        showController: () -> Unit
    ) {
        when (plan) {
            is PlayerSessionCoordinator.ContinuationPlan.PlayIntent -> play(plan.intent)
            PlayerSessionCoordinator.ContinuationPlan.ExitPlayer ->
                if (mode != AfterPlayMode.NOTHING && !locked) showRecommendations() else exit()
            PlayerSessionCoordinator.ContinuationPlan.ShowController ->
                if (mode != AfterPlayMode.NOTHING && !locked) showRecommendations() else showController()
        }
    }
}
