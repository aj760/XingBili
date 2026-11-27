package com.mytvb.repository

import com.mytvb.model.series.CheckUserSeriesResult
import com.mytvb.model.series.FollowSeriesResult
import com.mytvb.model.series.RelatedRecommendResult
import com.mytvb.repository.remote.SeriesRepository as NetworkSeriesRepository

class SeriesRepository(
    private val delegate: NetworkSeriesRepository
) {
    suspend fun getSeriesDetail(seasonId: Long, epId: Long = 0) =
        delegate.getSeriesDetail(seasonId, epId)

    suspend fun checkUserFollowStatus(seasonId: Long, epId: Long = 0): Result<CheckUserSeriesResult> =
        delegate.checkUserFollowStatus(seasonId, epId)

    suspend fun followSeries(seasonId: Long): Result<FollowSeriesResult> =
        delegate.followSeries(seasonId)

    suspend fun cancelFollowSeries(seasonId: Long): Result<FollowSeriesResult> =
        delegate.cancelFollowSeries(seasonId)

    suspend fun getMyFollowingSeries(type: Int, page: Int, pageSize: Int, vmid: Long) =
        delegate.getMyFollowingSeries(type, page, pageSize, vmid)

    suspend fun getSeriesTimeline(type: Int, before: Int = 6, after: Int = 6) =
        delegate.getSeriesTimeline(type, before, after)

    suspend fun getRelatedRecommend(seasonId: Long): Result<RelatedRecommendResult> =
        delegate.getRelatedRecommend(seasonId)
}
