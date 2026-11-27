package com.mytvb

import com.mytvb.network.NetworkManager
import com.mytvb.network.security.NetworkManagerSecurityGateway
import com.mytvb.network.session.NetworkManagerSessionGateway
import com.mytvb.repository.UserRepository
import com.mytvb.repository.VideoRepository
import com.mytvb.repository.remote.HomeLaneRepository
import com.mytvb.repository.remote.SeriesRepository as RemoteSeriesRepository
import com.mytvb.repository.remote.VideoRepository as RemoteVideoRepository
import org.junit.Ignore
import org.junit.Test

/**
 * 手动联网探针：构造真实仓库打 B 站线上 API，用于本地调试首页/推荐数据结构。
 * 依赖网络、Cookie、运行环境及 Android SystemClock，不可在 CI 纯 JVM 单元测试中运行。
 * 需要时手动去掉 @Ignore 在设备/Robolectric 环境执行。
 */
@Ignore("手动联网探针，依赖真实网络与 Android SystemClock，不应在 CI 运行")
class HomeDataProbeTest {

    @Test
    fun probeRecommendAndAnimeHomeData() {
        val sessionGateway = NetworkManagerSessionGateway()
        val securityGateway = NetworkManagerSecurityGateway()
        val videoRepository = VideoRepository(
            RemoteVideoRepository(
                NetworkManager.apiService,
                sessionGateway,
                securityGateway
            ),
            sessionGateway
        )
        runCatching {
            kotlinx.coroutines.runBlocking {
                videoRepository.getRecommendList(0, 24)
            }
        }.onSuccess { recommend ->
            println("recommend.code=${recommend.code}")
            println("recommend.message=${recommend.message}")
            println("recommend.items=${recommend.data?.items?.size ?: -1}")
            println(
                "recommend.first=${recommend.data?.items?.firstOrNull()?.let { "${it.title}|${it.bvid}|${it.aid}|${it.cid}" }}"
            )
        }.onFailure { throwable ->
            println("recommend.failure=${throwable::class.java.name}:${throwable.message}")
        }

        val homeLaneRepository = HomeLaneRepository(
            apiService = NetworkManager.apiService,
            seriesRepository = RemoteSeriesRepository(NetworkManager.apiService, sessionGateway, securityGateway),
            userRepository = UserRepository(NetworkManager.apiService, sessionGateway, securityGateway),
            sessionGateway = sessionGateway
        )
        val anime = kotlinx.coroutines.runBlocking {
            homeLaneRepository.getHomeLanes(
                type = HomeLaneRepository.TYPE_ANIMATION,
                cursor = 0,
                isRefresh = true
            ).getOrThrow()
        }
        println("anime.sections=${anime.sections.size}")
        println(
            "anime.firstSection=${anime.sections.firstOrNull()?.let { "${it.title}|items=${it.items.size}|timeline=${it.timelineDays.size}" }}"
        )
        println(
            "anime.firstItem=${anime.sections.firstOrNull { it.items.isNotEmpty() }?.items?.firstOrNull()?.let { "${it.title}|${it.cover}|${it.seasonId}|${it.oid}" }}"
        )
    }
}
