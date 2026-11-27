package com.mytvb.repository.remote

import androidx.core.text.HtmlCompat
import com.mytvb.model.BaseResponse
import com.mytvb.model.search.HotWordModel
import com.mytvb.model.search.SearchAllResponseData
import com.mytvb.model.search.SearchDefaultModel
import com.mytvb.model.search.SearchItemModel
import com.mytvb.model.search.SearchResponseWrapper
import com.mytvb.model.search.SearchType
import com.mytvb.model.search.SearchVideoOrder
import com.mytvb.model.video.Owner
import com.mytvb.model.video.Stat
import com.mytvb.model.video.VideoModel
import com.mytvb.network.WbiGenerator
import com.mytvb.network.session.NetworkSessionGateway
import com.mytvb.network.response.HotWordWrapper
import com.mytvb.network.response.SearchSuggestWrapper
import retrofit2.http.QueryMap
import okhttp3.OkHttpClient
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.GET
import retrofit2.http.Query

private interface SearchApiService {
    @GET("x/web-interface/search/all/v2")
    suspend fun searchAll(
        @Query("keyword") keyword: String,
        @Query("platform") platform: String = "pc",
        @Query("highlight") highlight: Int = 1,
        @Query("page_size") pageSize: Int = 20,
        @Query("page") page: Int = 1
    ): BaseResponse<SearchAllResponseData>

    @GET("x/web-interface/wbi/search/all/v2")
    suspend fun searchAllWbi(
        @QueryMap params: Map<String, String>
    ): BaseResponse<SearchAllResponseData>

    @GET("x/web-interface/search/type")
    suspend fun searchByType(
        @Query("search_type") searchType: String,
        @Query("keyword") keyword: String,
        @Query("order") order: String,
        @Query("duration") duration: Int = 0,
        @Query("tids") tids: Int = 0,
        @Query("page") page: Int,
        @Query("page_size") pageSize: Int,
        @Query("platform") platform: String = "pc",
        @Query("highlight") highlight: Int = 0
    ): BaseResponse<SearchResponseWrapper>

    @GET("x/web-interface/wbi/search/type")
    suspend fun searchByTypeWbi(
        @QueryMap params: Map<String, String>
    ): BaseResponse<SearchResponseWrapper>

    @GET("https://s.search.bilibili.com/main/hotword")
    suspend fun getHotSearchWords(): HotWordWrapper

    @GET("x/web-interface/wbi/search/default")
    suspend fun searchDefaultWbi(
        @QueryMap params: Map<String, String>
    ): BaseResponse<SearchDefaultModel>

    @GET("https://s.search.bilibili.com/main/suggest")
    suspend fun searchSuggest(
        @Query("term") keyword: String,
        @Query("main_ver") mainVer: String = "v1"
    ): SearchSuggestWrapper
}

class SearchRepository(
    private val okHttpClient: OkHttpClient,
    private val sessionGateway: NetworkSessionGateway
) {

    private val htmlCache = object : LinkedHashMap<String, String>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, String>?): Boolean {
            return size > 64
        }
    }

    @Synchronized
    private fun parseHtml(html: String): String {
        return htmlCache.getOrPut(html) {
            HtmlCompat.fromHtml(html, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
        }
    }

    private val searchApiService: SearchApiService by lazy {
        Retrofit.Builder()
            .baseUrl("https://api.bilibili.com/")
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
            .create(SearchApiService::class.java)
    }

    suspend fun loadHotSearchWords(): Result<List<HotWordModel>> =
        runCatching {
            val response = searchApiService.getHotSearchWords()
            response.list.mapIndexed { index, item ->
                val kw = item.keyword.ifBlank { item.showName }
                HotWordModel(
                    keyword = kw,
                    showName = item.showName.ifBlank { kw },
                    pos = index + 1,
                    hotId = (index + 1).toString()
                )
            }
        }

    /**
     * 搜索框官方默认词（wbi/search/default）。仅 wbi 路径存在，无 wbi key 时直接返回
     * null（该词只做输入框占位提示，缺失无感知）。
     */
    suspend fun loadSearchDefault(): Result<SearchDefaultModel?> =
        runCatching {
            if (!hasWbiKeys()) return@runCatching null
            searchApiService.searchDefaultWbi(
                buildWbiParams(mapOf("web_location" to "333.1007"))
            ).data?.takeIf { it.showName.isNotBlank() || it.name.isNotBlank() }
        }

    suspend fun searchSuggest(keyword: String): Result<List<HotWordModel>> =
        runCatching {
            val response = searchApiService.searchSuggest(keyword)
            response.result.tag.mapNotNull { item ->
                val name = parseHtml(item.name)
                    .ifBlank { item.value }
                    .takeIf { it.isNotBlank() } ?: return@mapNotNull null
                HotWordModel.createSuggest(
                    value = item.value,
                    name = name
                )
            }
        }

    suspend fun searchAll(keyword: String): Result<SearchAllResponseData> =
        runCatching {
            val response = sessionGateway.executeWithRiskControlRetry(
                key = "search_all_$keyword",
                source = "search.searchAll"
            ) {
                if (hasWbiKeys()) {
                    searchApiService.searchAllWbi(
                        buildWbiParams(
                            mapOf(
                                "keyword" to keyword,
                                "platform" to "pc",
                                "highlight" to "1",
                                "page_size" to "20",
                                "page" to "1"
                            )
                        )
                    )
                } else {
                    searchApiService.searchAll(keyword = keyword)
                }
            }

            if (!response.isSuccess) {
                throw IllegalStateException(response.errorMessage)
            }

            response.data ?: SearchAllResponseData()
        }

    suspend fun searchByType(
        searchType: SearchType,
        keyword: String,
        page: Int,
        pageSize: Int,
        order: SearchVideoOrder = SearchVideoOrder.TotalRank
    ): Result<SearchResponseWrapper> =
        runCatching {
            val response = sessionGateway.executeWithRiskControlRetry(
                key = "search_type_${searchType.value}_$keyword",
                source = "search.searchByType"
            ) {
                if (hasWbiKeys()) {
                    searchApiService.searchByTypeWbi(
                        buildWbiParams(
                            mapOf(
                                "search_type" to searchType.value,
                                "keyword" to keyword,
                                "order" to order.orderValue,
                                "duration" to "0",
                                "tids" to "0",
                                "page" to page.toString(),
                                "page_size" to pageSize.toString(),
                                "platform" to "pc",
                                "highlight" to "0"
                            )
                        )
                    )
                } else {
                    searchApiService.searchByType(
                        searchType = searchType.value,
                        keyword = keyword,
                        order = order.orderValue,
                        page = page,
                        pageSize = pageSize
                    )
                }
            }

            if (!response.isSuccess) {
                throw IllegalStateException(response.errorMessage)
            }

            response.data ?: SearchResponseWrapper(page = page, pageSize = pageSize)
        }

    suspend fun search(
        keyword: String,
        page: Int,
        pageSize: Int,
        order: SearchVideoOrder = SearchVideoOrder.TotalRank
    ): Result<List<VideoModel>> =
        searchByType(
            searchType = SearchType.Video,
            keyword = keyword,
            page = page,
            pageSize = pageSize,
            order = order
        ).map { wrapper ->
            wrapper.result.orEmpty().map { it.toVideoModel() }
        }

    private fun SearchItemModel.toVideoModel(): VideoModel {
        return VideoModel(
            aid = aid.takeIf { it > 0 } ?: id,
            bvid = bvid,
            title = parseHtml(title),
            pic = normalizeUrl(pic.ifBlank { cover }),
            owner = Owner(
                mid = mid.toLongOrNull() ?: 0L,
                name = author.ifBlank { uname },
                face = normalizeUrl(upic)
            ),
            stat = Stat(view = play),
            duration = parseDuration(duration)
        )
    }

    private fun buildWbiParams(params: Map<String, String>): Map<String, String> {
        val (imgKey, subKey) = sessionGateway.getWbiKeys()
        return WbiGenerator.generateWbiParams(params, imgKey, subKey)
    }

    private fun hasWbiKeys(): Boolean {
        val (imgKey, subKey) = sessionGateway.getWbiKeys()
        return imgKey.isNotBlank() && subKey.isNotBlank()
    }

    private fun normalizeUrl(url: String): String {
        return when {
            url.isBlank() -> ""
            url.startsWith("http") -> url
            url.startsWith("//") -> "https:$url"
            else -> url
        }
    }

    private fun parseDuration(value: String): Long {
        if (value.isBlank()) return 0
        val parts = value.split(":").mapNotNull { it.toLongOrNull() }
        if (parts.isEmpty()) return 0
        return when (parts.size) {
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            2 -> parts[0] * 60 + parts[1]
            else -> parts[0]
        }
    }
}
