package com.mytvb.feature.player.danmaku

import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 复现 CacheManager.sharedCacheStore 的 LRU 用法：
 * accessOrder=true 的 LinkedHashMap 子类 + removeEldestEntry 上限 256。
 * 断言：插入 2000 个 key 后 map 大小必须被卡在 ~256，否则"预算只进不出"。
 */
class SharedCacheLruBehaviorTest {

    private companion object {
        const val MAX_SHARED_CACHE = 256
    }

    @Test
    fun lruEvictionKeepsTableBounded() {
        val store = object : java.util.LinkedHashMap<Long, String>(128, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, String>?): Boolean {
                if (size <= MAX_SHARED_CACHE) return false
                return true
            }
        }
        repeat(2000) { store[it.toLong()] = "x$it" }
        assertTrue("map size=${store.size} 未被 LRU 卡住", store.size <= MAX_SHARED_CACHE + 1)
    }
}
