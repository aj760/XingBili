package com.mytvb.feature.home

import com.mytvb.core.common.log.AppLog
import com.mytvb.model.video.VideoModel
import com.mytvb.network.session.SessionStateRepository
import com.mytvb.repository.VideoRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 推荐流 dislike 反馈闭环。
 *
 * 背景：青少年模式本地过滤会把擦边/恐怖类视频从推荐列表里删掉，但 B 站推荐算法对此一无所知，
 * 会持续推送同类内容（"刷一条来一条"）。本组件在过滤后，把这些被拦视频异步反馈给 B 站 dislike 接口
 * （reasonId=1 不感兴趣），让算法从源头减少同类推送。
 *
 * 设计要点：
 * - 与展示解耦：反馈在独立协程后台跑，不阻塞过滤结果返回 UI，失败静默。
 * - 登录态守卫：未登录（无 csrf）直接跳过，与手动 dislike（VideoCardMenuDialog）行为一致。
 * - 去重节流：进程内记录已反馈的 bvid，避免同一视频重复请求；每批最多反馈 [MAX_PER_BATCH] 条，
 *   防止单次过滤几十条时打爆接口。
 */
class RecommendDislikeFeedback(
    private val videoRepository: VideoRepository,
    private val sessionGateway: SessionStateRepository
) {
    companion object {
        private const val TAG = "DislikeFeedback"
        private const val REASON_ID = 1 // 不感兴趣，与详情页 tag 命中拦截一致
        private const val MAX_PER_BATCH = 8 // 单批最多反馈条数，避免一刷几十条全打接口
        private const val MAX_SET_SIZE = 500 // 去重集合上限，防内存无限增长
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val sentBvids = LinkedHashSet<String>()

    /**
     * 对被过滤掉的视频批量反馈 dislike。可在任意线程调用，内部异步执行、立即返回。
     * 仅对有有效 bvid、且青少年模式本地拦截（非用户手动拉黑）的视频有意义——调用方传入"被过滤集"即可。
     */
    fun feedbackBlocked(videos: List<VideoModel>) {
        if (videos.isEmpty()) return
        if (!sessionGateway.isLoggedIn()) return
        val pending = videos.asSequence()
            .mapNotNull { v -> v.bvid.takeIf { it.isNotBlank() && it !in sentBvids }?.let { v } }
            .take(MAX_PER_BATCH)
            .toList()
        if (pending.isEmpty()) return
        // 先登记再请求，避免并发重复
        synchronized(sentBvids) {
            pending.forEach { sentBvids.add(it.bvid) }
            if (sentBvids.size > MAX_SET_SIZE) {
                // 淘汰最早的一批，保留近期
                val keep = sentBvids.toList().takeLast(MAX_SET_SIZE / 2)
                sentBvids.clear()
                sentBvids.addAll(keep)
            }
        }
        scope.launch {
            pending.forEach { video ->
                runCatching {
                    videoRepository.dislikeFeed(video, REASON_ID)
                }.onFailure { e ->
                    AppLog.w(TAG, "dislike failed bvid=${video.bvid} msg=${e.message}")
                }
            }
            AppLog.i(TAG, "dislike batch size=${pending.size}")
        }
    }
}
