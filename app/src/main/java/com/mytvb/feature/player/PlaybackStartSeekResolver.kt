package com.mytvb.feature.player

data class PlaybackStartSeekResolution(
    val positionMs: Long,
    val nearEndReset: Boolean
)

object PlaybackStartSeekResolver {
    private const val NEAR_END_WINDOW_MS = 5_000L
    private const val TAIL_CLAMP_MS = 500L

    fun resolve(
        requestedSeekMs: Long,
        durationMs: Long,
        reuseSameSource: Boolean
    ): PlaybackStartSeekResolution {
        val requested = requestedSeekMs.coerceAtLeast(0L)
        if (!reuseSameSource || requested <= 0L || durationMs <= 0L) {
            return PlaybackStartSeekResolution(positionMs = requested, nearEndReset = false)
        }
        if (durationMs - requested <= NEAR_END_WINDOW_MS) {
            return PlaybackStartSeekResolution(positionMs = 0L, nearEndReset = true)
        }
        return PlaybackStartSeekResolution(
            positionMs = requested.coerceAtMost((durationMs - TAIL_CLAMP_MS).coerceAtLeast(0L)),
            nearEndReset = false
        )
    }
}
