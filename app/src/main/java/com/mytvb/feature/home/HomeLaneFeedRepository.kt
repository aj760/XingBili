package com.mytvb.feature.home

import android.os.SystemClock
import com.mytvb.core.common.log.AppLog
import com.mytvb.model.lane.HomeLaneSection
import com.mytvb.model.lane.HomeLanePage
import com.mytvb.repository.HomeLaneRepository
import com.mytvb.repository.cache.HomeCacheStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class HomeLaneFeedRepository(
    private val repository: HomeLaneRepository
) {

    companion object {
        private const val TAG = "HomeLaneFeedRepository"
        private const val MAX_CACHED_LANE_SECTIONS = 12
        private const val CACHE_MAX_AGE_MS = 14L * 24L * 60L * 60L * 1000L
    }

    data class CachedFeed(
        val items: List<HomeLaneSection>,
        val savedAtMs: Long,
        val schemaVersion: Int
    )

    suspend fun readCachedFeed(type: Int): CachedFeed {
        val startMs = SystemClock.elapsedRealtime()
        val cacheKey = cacheKey(type)
        AppLog.i(TAG, "APP_STARTUP lane cache read start type=$type")
        val (cached, cacheThread) = withContext(Dispatchers.IO) {
            HomeCacheStore.readCachedSections(cacheKey) to Thread.currentThread().name
        }
        val items = if (HomeCacheStore.isExpired(cached.savedAtMs, CACHE_MAX_AGE_MS)) {
            emptyList()
        } else {
            cached.items.take(MAX_CACHED_LANE_SECTIONS)
        }
        AppLog.i(
            TAG,
            "APP_STARTUP lane cache read end type=$type elapsed=${SystemClock.elapsedRealtime() - startMs}ms count=${items.size} ageMs=${HomeCacheStore.cacheAgeMs(cached.savedAtMs)} schema=${cached.schemaVersion} thread=$cacheThread"
        )
        return CachedFeed(
            items = items,
            savedAtMs = cached.savedAtMs,
            schemaVersion = cached.schemaVersion
        )
    }

    suspend fun loadNetworkPage(type: Int, cursor: Long, isRefresh: Boolean): Result<HomeLanePage> {
        val startMs = SystemClock.elapsedRealtime()
        AppLog.i(TAG, "APP_STARTUP lane network start type=$type cursor=$cursor refresh=$isRefresh")
        return repository.getHomeLanes(type = type, cursor = cursor, isRefresh = isRefresh)
            .map { page ->
                val filterStartMs = SystemClock.elapsedRealtime()
                val filtered = page.sections.filter { it.items.isNotEmpty() || it.timelineDays.isNotEmpty() }
                AppLog.i(
                    TAG,
                    "APP_STARTUP lane network end type=$type elapsed=${SystemClock.elapsedRealtime() - startMs}ms raw=${page.sections.size} filtered=${filtered.size} filter=${SystemClock.elapsedRealtime() - filterStartMs}ms hasMore=${page.hasMore}"
                )
                page.copy(sections = filtered)
            }
    }

    suspend fun writeCache(type: Int, sections: List<HomeLaneSection>) {
        withContext(Dispatchers.IO) {
            HomeCacheStore.writeSections(
                cacheKey(type),
                sections.sectionsForCache(type).take(MAX_CACHED_LANE_SECTIONS)
            )
        }
    }

    private fun cacheKey(type: Int): String {
        return "laneCacheList$type"
    }

    private fun List<HomeLaneSection>.sectionsForCache(type: Int): List<HomeLaneSection> {
        return if (type == HomeLaneRepository.TYPE_ANIMATION) {
            filter { it.timelineDays.isEmpty() }
        } else {
            this
        }
    }


}
