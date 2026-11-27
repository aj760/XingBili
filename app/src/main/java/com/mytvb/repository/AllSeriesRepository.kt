package com.mytvb.repository

import com.mytvb.model.series.AllSeriesFilterModel
import com.mytvb.repository.remote.AllSeriesRepository as NetworkAllSeriesRepository

typealias AllSeriesPage = com.mytvb.repository.remote.AllSeriesPage

class AllSeriesRepository(
    private val delegate: NetworkAllSeriesRepository
) {
    suspend fun getAllSeries(
        type: Int,
        page: Int,
        filters: List<AllSeriesFilterModel> = emptyList()
    ) = delegate.getAllSeries(type, page, filters)
}
