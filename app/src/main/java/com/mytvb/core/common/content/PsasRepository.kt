package com.mytvb.core.common.content

import com.mytvb.core.common.log.AppLog
import com.mytvb.network.BiliClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicReference

/**
 * 公益广告数据仓库。
 *
 * 拉取并缓存公益广告合集 BV1oW41157Z4 的分P 列表（共 34 个央视公益广告）。
 * 内存缓存，App 启动后台拉一次；失败下次重试，不阻塞主流程。
 */
object PsasRepository {

    private const val TAG = "PsasRepository"
    private const val PSAS_BVID = "BV1oW41157Z4"
    private const val VIEW_URL = "https://api.bilibili.com/x/web-interface/view?bvid=$PSAS_BVID"

    /** 单个公益广告分P。 */
    data class Episode(
        val cid: Long,
        val part: String,
        val duration: Int // 秒
    )

    private val episodesRef = AtomicReference<List<Episode>?>(null)

    /** 已缓存返回 true（可直接取随机分P）。 */
    fun isReady(): Boolean = episodesRef.get()?.isNotEmpty() == true

    /** 随机返回一个分P；未缓存返回 null。 */
    fun getRandomEpisode(): Episode? {
        val list = episodesRef.get() ?: run {
            AppLog.w(TAG, "getRandomEpisode: 列表未加载")
            return null
        }
        if (list.isEmpty()) return null
        val ep = list.random()
        AppLog.i(TAG, "getRandomEpisode: 选中 cid=${ep.cid} part=${ep.part} 总数=${list.size}")
        return ep
    }

    /** 公益广告合集的 bvid，供 PsasPlayerActivity 拉播放地址用。 */
    fun getBvid(): String = PSAS_BVID

    /**
     * 启动公益广告播放（锁死模式）。
     * @return true 表示已启动；false 表示广告列表未就绪（调用方应跳过本次触发，下个 tick 再试）。
     */
    fun launchRandomPsas(context: android.content.Context): Boolean {
        val ep = getRandomEpisode() ?: run {
            AppLog.w(TAG, "launchRandomPsas: 广告列表未就绪，跳过本次触发")
            return false
        }
        AppLog.i(TAG, "launchRandomPsas: 启动 cid=${ep.cid} part=${ep.part}")
        com.mytvb.ui.activity.PlayerActivity.start(
            context = context,
            bvid = PSAS_BVID,
            cid = ep.cid,
            psasLocked = true
        )
        return true
    }

    /**
     * 拉取并缓存分P 列表。后台调用，失败静默（下次重试）。
     * 已缓存则跳过。
     */
    suspend fun ensureLoaded(force: Boolean = false) {
        if (!force && isReady()) return
        withContext(Dispatchers.IO) {
            try {
                val json = BiliClient.getJson(VIEW_URL)
                val code = json.optInt("code", -1)
                if (code != 0) {
                    AppLog.w(TAG, "load failed code=$code msg=${json.optString("message")}")
                    return@withContext
                }
                val pages = json.optJSONObject("data")?.optJSONArray("pages") ?: run {
                    AppLog.w(TAG, "no pages in response")
                    return@withContext
                }
                val list = ArrayList<Episode>(pages.length())
                for (i in 0 until pages.length()) {
                    val p = pages.optJSONObject(i) ?: continue
                    val cid = p.optLong("cid", 0L)
                    if (cid <= 0L) continue
                    list.add(
                        Episode(
                            cid = cid,
                            part = p.optString("part", ""),
                            duration = p.optInt("duration", 0)
                        )
                    )
                }
                if (list.isNotEmpty()) {
                    episodesRef.set(list)
                    AppLog.i(TAG, "loaded ${list.size} episodes")
                }
            } catch (e: Exception) {
                AppLog.w(TAG, "load failed: ${e.message}")
            }
        }
    }
}
