package com.mytvb.feature.home

import android.content.Context
import com.mytvb.R
import com.mytvb.model.video.VideoModel

class HotViewModel(
    private val repository: HotFeedRepository,
    dislikeFeedback: RecommendDislikeFeedback,
    context: Context
) : BaseVideoFeedViewModel(context, dislikeFeedback) {

    companion object {
        private const val PAGE_SIZE = 24
    }

    override suspend fun fetchPage(
        page: Int,
        replace: Boolean,
        fromInitial: Boolean,
        fromRefresh: Boolean
    ): Result<FetchedPage> {
        return repository.loadNetworkPage(page = page, pageSize = PAGE_SIZE)
            .map { result ->
                FetchedPage(
                    items = filterForDisplay(result.items),
                    hasMore = result.hasMore
                )
            }
    }

    override fun errorMessage(throwable: Throwable): String {
        return throwable.message ?: appContext.getString(R.string.home_hot_load_failed)
    }

    override suspend fun writeCache(items: List<VideoModel>) {
        repository.writeCache(repository.trimCacheItems(items))
    }
}
