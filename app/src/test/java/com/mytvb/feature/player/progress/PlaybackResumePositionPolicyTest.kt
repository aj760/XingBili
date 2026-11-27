package com.mytvb.feature.player.progress

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackResumePositionPolicyTest {

    @Test
    fun picksFarthestAmongAllSources() {
        // 本地最新（12min）> 服务端（10min）> 入口快照（9min）
        val position = PlaybackResumePositionPolicy.resolveResumePositionMs(
            entryMs = 540_000L,
            localMs = 720_000L,
            serverMs = 600_000L,
            durationMs = 1_200_000L
        )
        assertEquals(720_000L, position)
    }

    @Test
    fun serverProgressBeatsStaleEntrySnapshot() {
        // 入口快照 10min 旧于 playinfo 实时 lastPlayTime 12min：取更远者防回退
        val position = PlaybackResumePositionPolicy.resolveResumePositionMs(
            entryMs = 600_000L,
            localMs = 0L,
            serverMs = 720_000L,
            durationMs = 1_200_000L
        )
        assertEquals(720_000L, position)
    }

    @Test
    fun watchedCompleteResetsToZero() {
        val position = PlaybackResumePositionPolicy.resolveResumePositionMs(
            entryMs = 1_160_000L,
            localMs = 0L,
            serverMs = 0L,
            durationMs = 1_200_000L
        )
        assertEquals(0L, position)
    }

    @Test
    fun tailClampKeepsOneSecondGuard() {
        // 短视频（<20s）时 95% 阈值晚于 1s 尾部保护，钳制分支生效
        val position = PlaybackResumePositionPolicy.resolveResumePositionMs(
            entryMs = 9_400L,
            localMs = 0L,
            serverMs = 0L,
            durationMs = 10_000L
        )
        assertEquals(9_000L, position)
    }

    @Test
    fun nearCompleteButUnderRatioKeepsPosition() {
        val position = PlaybackResumePositionPolicy.resolveResumePositionMs(
            entryMs = 1_130_000L,
            localMs = 0L,
            serverMs = 0L,
            durationMs = 1_200_000L
        )
        assertEquals(1_130_000L, position)
    }

    @Test
    fun unknownDurationKeepsCandidate() {
        val position = PlaybackResumePositionPolicy.resolveResumePositionMs(
            entryMs = 0L,
            localMs = 300_000L,
            serverMs = 0L,
            durationMs = 0L
        )
        assertEquals(300_000L, position)
    }

    @Test
    fun allZeroStartsFromBeginning() {
        val position = PlaybackResumePositionPolicy.resolveResumePositionMs(
            entryMs = 0L,
            localMs = 0L,
            serverMs = 0L,
            durationMs = 1_200_000L
        )
        assertEquals(0L, position)
    }
}
