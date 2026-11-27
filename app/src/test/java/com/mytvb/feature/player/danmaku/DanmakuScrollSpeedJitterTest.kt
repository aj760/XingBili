package com.mytvb.feature.player.danmaku

import com.mytvb.feature.player.danmaku.model.DanmakuItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 滚动速度扰动因子（等宽文本退场去相关）的行为回归：
 * - 确定性：同一条弹幕（同 dmid/文本/时间）多次计算结果一致
 * - 取值域：始终落在 1 ± SCROLL_SPEED_JITTER 内
 * - 离散度：满屏同文本不同 dmid 的场景下，因子应显著散开（覆盖上下两侧），
 *   否则退场相关性仍在，"整批进出场"极限环会重新形成
 */
class DanmakuScrollSpeedJitterTest {

    private fun item(dmid: Long?, text: String = "中", timeMs: Int = 5_000) = DanmakuItem(
        data = Danmaku(
            timeMs = timeMs,
            mode = 1,
            text = text,
            color = 0xFFFFFF,
            fontSize = 25,
            weight = 0,
            dmid = dmid,
        )
    )

    @Test
    fun factorIsDeterministicForSameItem() {
        val a = scrollSpeedJitterFactor(item(dmid = 2_105_888_639_862_780_672L))
        val b = scrollSpeedJitterFactor(item(dmid = 2_105_888_639_862_780_672L))
        assertEquals(a, b, 0f)
    }

    @Test
    fun factorStaysWithinJitterBand() {
        repeat(500) { i ->
            val f = scrollSpeedJitterFactor(item(dmid = 1L + i * 7919))
            assertTrue("factor=$f 越界", f in (1f - SCROLL_SPEED_JITTER)..(1f + SCROLL_SPEED_JITTER))
        }
    }

    @Test
    fun identicalTextItemsSpreadAcrossTheBand() {
        // 满屏"中"场景：文本相同、时间相近，只有 dmid 不同
        val factors = (0 until 300).map { scrollSpeedJitterFactor(item(dmid = 2_000_000_000_000L + it)) }
        assertTrue("扰动未覆盖快速侧 min=${factors.min()}", factors.min() < 1f - SCROLL_SPEED_JITTER * 0.8f)
        assertTrue("扰动未覆盖慢速侧 max=${factors.max()}", factors.max() > 1f + SCROLL_SPEED_JITTER * 0.8f)
    }
}
