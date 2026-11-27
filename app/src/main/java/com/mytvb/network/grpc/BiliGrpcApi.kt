package com.mytvb.network.grpc

import android.os.SystemClock
import bilibili.app.card.v1.Card
import bilibili.app.show.v1.PopularGrpc
import bilibili.app.show.v1.PopularResultReq
import com.mytvb.core.common.log.AppLog
import com.mytvb.model.video.Owner
import com.mytvb.model.video.Stat
import com.mytvb.model.video.VideoModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * B 站 app 端 gRPC 接口封装（官方 App 同源）。
 * 当前承载：热门列表 bilibili.app.show.v1.Popular/Index。
 */
object BiliGrpcApi {

    private const val TAG = "BiliGrpcApi"

    data class AppHotPage(
        val items: List<VideoModel>,
        /** 原始列表（含被过滤卡片）末项 idx，作为下次请求游标；-1=无更多 */
        val nextIdx: Long
    )

    /**
     * 官方热门列表（grpc.biliapi.net，gRPC + protobuf）。
     * 游标语义：idx 传上次响应原始列表末项的 idx（Base.idx，有跳号非连续），首屏传 0。
     */
    suspend fun popularIndex(idx: Long): AppHotPage = withContext(Dispatchers.IO) {
        val t0 = SystemClock.elapsedRealtime()
        val stub = PopularGrpc.newBlockingStub(BiliGrpcChannel.obtain())
        val reply = stub.index(
            PopularResultReq.newBuilder()
                .setIdx(idx)
                .setLoginEvent(2)
                .setSpmid("main.hot-feed.0.0")
                .build()
        )
        val rawItems = reply.itemsList
        val nextIdx = rawItems.lastOrNull()?.smallCoverV5?.base?.idx ?: -1L
        val items = rawItems.mapNotNull { card -> parseCard(card) }
        AppLog.i(
            TAG,
            "popularIndex idx=$idx raw=${rawItems.size} parsed=${items.size} nextIdx=$nextIdx net=${SystemClock.elapsedRealtime() - t0}ms"
        )
        AppHotPage(items = items, nextIdx = nextIdx)
    }

    /** 卡片 → VideoModel。只认 SMALL_COVER_V5 视频卡（官方热门主形态），其余（广告/横幅/置顶入口）丢弃。 */
    private fun parseCard(card: Card): VideoModel? {
        if (card.itemCase != Card.ItemCase.SMALL_COVER_V5) return null
        val v5 = card.smallCoverV5
        val base = v5.base
        if (base.goto != "av") return null
        val aid = base.param.toLongOrNull()
            ?: base.playerArgs.aid.takeIf { it > 0L }
            ?: return null
        // rightDesc2 实测形态："61.3万播放 · 17小时前" / "62.8万播放 · 昨天 13:43" / "145.5万播放 · 10月11日"
        val viewAndTime = v5.rightDesc2.split(" · ")
        return VideoModel(
            aid = aid,
            bvid = "",
            title = base.title,
            pic = base.cover,
            cover = base.cover,
            duration = parseDurationText(v5.coverRightText1),
            cid = base.playerArgs.cid.takeIf { it > 0L } ?: 0L,
            owner = Owner(
                mid = base.args.upId.takeIf { it > 0L } ?: v5.up.id,
                name = v5.rightDesc1.ifBlank { base.args.upName.ifBlank { v5.up.name } },
                face = v5.up.avatar.cover
            ),
            stat = Stat(
                view = parseCountText(viewAndTime.getOrNull(0))
            ),
            // 官方热门接口不下发弹幕数（手机 App 热门页同样不显示），卡片按 0 优雅隐藏
            pubDate = parseRelativeTimeText(viewAndTime.getOrNull(1)),
            trackId = base.args.trackId,
            isFollowed = v5.rcmdReasonStyle.text == "已关注",
            rcmdReason = v5.rcmdReasonStyle.text,
            typeName = base.args.tname.ifBlank { base.args.rname },
            typeId = (base.args.tid.takeIf { it > 0L } ?: base.args.rid.toLong()).toInt(),
            goto = "av"
        )
    }

    /** "12:34"/"1:02:03" 时长文本 → 秒。 */
    private fun parseDurationText(text: String): Long {
        if (text.isBlank()) return 0L
        val parts = text.split(":").mapNotNull { it.toLongOrNull() }
        return when (parts.size) {
            2 -> parts[0] * 60 + parts[1]
            3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
            else -> 0L
        }
    }

    /** "61.3万播放"/"583.4万观看"/"9999播放" 计数文本 → 数值（后缀播放/观看两种形态都兼容）。 */
    private fun parseCountText(text: String?): Long {
        val t = text?.trim()?.removeSuffix("播放")?.removeSuffix("观看") ?: return 0L
        return when {
            t.endsWith("万") -> ((t.removeSuffix("万").toDoubleOrNull() ?: 0.0) * 10_000).toLong()
            t.endsWith("亿") -> ((t.removeSuffix("亿").toDoubleOrNull() ?: 0.0) * 100_000_000).toLong()
            t.isBlank() -> 0L
            else -> t.toLongOrNull() ?: 0L
        }
    }

    /**
     * 官方相对时间文本 → 秒级时间戳。实测形态：
     * "17小时前" / "3天前" / "5分钟前" / "昨天 13:43" / "10月11日"（跨年后为完整日期）。
     */
    private fun parseRelativeTimeText(text: String?): Long {
        if (text.isNullOrBlank()) return 0L
        val now = System.currentTimeMillis() / 1000
        val calendar = java.util.Calendar.getInstance()
        relativeRegex.find(text)?.let { m ->
            val (num, unit) = m.destructured
            val n = num.toLongOrNull() ?: return 0L
            return when (unit) {
                "秒" -> now - n
                "分钟" -> now - n * 60
                "小时" -> now - n * 3600
                "天" -> now - n * 86400
                "周" -> now - n * 7 * 86400
                "月" -> now - n * 30 * 86400
                "年" -> now - n * 365 * 86400
                else -> 0L
            }
        }
        if (text.startsWith("昨天")) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -1)
            val (h, min) = parseTodayClock(text.substringAfter("昨天").trim()) ?: return calendar.timeInMillis / 1000
            calendar.set(java.util.Calendar.HOUR_OF_DAY, h)
            calendar.set(java.util.Calendar.MINUTE, min)
            return calendar.timeInMillis / 1000
        }
        if (text.startsWith("前天")) {
            calendar.add(java.util.Calendar.DAY_OF_YEAR, -2)
            val (h, min) = parseTodayClock(text.substringAfter("前天").trim()) ?: return calendar.timeInMillis / 1000
            calendar.set(java.util.Calendar.HOUR_OF_DAY, h)
            calendar.set(java.util.Calendar.MINUTE, min)
            return calendar.timeInMillis / 1000
        }
        // "10月11日"（当年）；跨年场景官方会下发完整日期文本，此处兜当年
        monthDayRegex.find(text)?.let { m ->
            val month = m.groupValues[1].toIntOrNull() ?: return 0L
            val day = m.groupValues[2].toIntOrNull() ?: return 0L
            calendar.set(java.util.Calendar.MONTH, month - 1)
            calendar.set(java.util.Calendar.DAY_OF_MONTH, day)
            // 官方只精确到日：未来日期说明是去年（如 12月31日 在 1 月看）
            if (calendar.timeInMillis / 1000 > now) {
                calendar.add(java.util.Calendar.YEAR, -1)
            }
            return calendar.timeInMillis / 1000
        }
        // 完整日期 "2025-10-11" 兜底
        fullDateRegex.find(text)?.let { m ->
            val y = m.groupValues[1].toIntOrNull() ?: return 0L
            val mo = m.groupValues[2].toIntOrNull() ?: return 0L
            val d = m.groupValues[3].toIntOrNull() ?: return 0L
            calendar.set(y, mo - 1, d, 0, 0, 0)
            return calendar.timeInMillis / 1000
        }
        return 0L
    }

    private fun parseTodayClock(text: String): Pair<Int, Int>? {
        val m = clockRegex.find(text) ?: return null
        val h = m.groupValues[1].toIntOrNull() ?: return null
        val min = m.groupValues[2].toIntOrNull() ?: return null
        return h to min
    }

    private val relativeRegex = Regex("^(\\d+)\\s*(秒|分钟|小时|天|周|月|年)前$")
    private val monthDayRegex = Regex("^(\\d{1,2})月(\\d{1,2})日?$")
    private val fullDateRegex = Regex("^(\\d{4})-(\\d{1,2})-(\\d{1,2})")
    private val clockRegex = Regex("^(\\d{1,2}):(\\d{2})$")
}
