package com.mytvb.feature.player.sponsor

/**
 * av 号 → BV 号本地换算（B 站公开的查表算法）。
 * 番剧（PGC）season 接口未登录时 episodes 常不返回 bvid，但 aid 一定有；
 * 空降助手 API 的 videoID 需要 bvid，用 aid 本地换算兜底，省一次网络请求。
 */
internal object AvToBv {

    private const val BV_XOR = 177451812L
    private const val BV_ADD = 8728348608L
    private val BV_TABLE = "fZodR9XQDSUm21yCkr6zBqiveYah8bt4xsWpHnJE7jL5VG3guMTKNPAwcF"
    private val BV_POS = intArrayOf(11, 10, 3, 8, 4, 6)

    /** 旧查表算法的编码上界 58^6 = 38_068_692_544，aid 超出后换算结果必错（新号段是另一套算法）。 */
    private const val MAX_ENCLOSABLE_AID = 38_068_692_544L

    fun convert(aid: Long): String? {
        if (aid <= 0L) return null
        if (aid >= MAX_ENCLOSABLE_AID) return null
        val x = (aid xor BV_XOR) + BV_ADD
        val out = "BV1  4 1 7  ".toCharArray()
        var pow = 1L
        for (i in 0 until 6) {
            out[BV_POS[i]] = BV_TABLE[((x / pow) % 58L).toInt()]
            pow *= 58L
        }
        return String(out)
    }
}
