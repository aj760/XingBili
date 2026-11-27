package com.mytvb.feature.cctv.vod

import android.content.Context

/**
 * 央视点播的本地播放进度。
 *
 * 官方播放器不留观看进度（每集每次打开都从片头开始），所以自己存：按
 * 「专辑 id + 集号」记录毫秒位置，下次进同一集时续播。
 * 接近片尾视为看完，直接把记录清掉（避免下次进来停在片尾）。
 */
object CctvVodProgressStore {

    private const val PREF_NAME = "cctv_vod_progress"
    private const val KEY_PREFIX = "ep_"
    private const val KEY_LAST_PREFIX = "last_"

    /** 距片尾不足这个时长就算看完。 */
    private const val NEAR_END_MS = 30_000L

    /** 前 5 秒内不记（片头/误触没必要留进度）。 */
    private const val MIN_SAVE_MS = 5_000L

    fun save(context: Context, albumId: String, episodeIndex: Int, positionMs: Long, durationMs: Long) {
        if (albumId.isBlank() || episodeIndex <= 0) return
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        val key = keyOf(albumId, episodeIndex)
        // 「最近播放到哪集」独立于分集进度记录：临近片尾的进度会被清掉，
        // 但详情页仍要靠它定位上次观看的集。
        prefs.edit().putInt(lastKeyOf(albumId), episodeIndex).apply()
        when {
            durationMs > 0 && positionMs >= durationMs - NEAR_END_MS ->
                prefs.edit().remove(key).apply()
            positionMs < MIN_SAVE_MS ->
                prefs.edit().remove(key).apply()
            else ->
                prefs.edit().putLong(key, positionMs).apply()
        }
    }

    /** 返回该集的上次播放位置（毫秒），没有记录返回 0。 */
    fun load(context: Context, albumId: String, episodeIndex: Int): Long {
        if (albumId.isBlank() || episodeIndex <= 0) return 0L
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getLong(keyOf(albumId, episodeIndex), 0L)
    }

    fun clear(context: Context, albumId: String, episodeIndex: Int) {
        if (albumId.isBlank() || episodeIndex <= 0) return
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().remove(keyOf(albumId, episodeIndex)).apply()
    }

    /** 该专辑最近一次播放的集号，没看过返回 0。 */
    fun loadLastEpisode(context: Context, albumId: String): Int {
        if (albumId.isBlank()) return 0
        val prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(lastKeyOf(albumId), 0)
    }

    private fun keyOf(albumId: String, episodeIndex: Int): String =
        "$KEY_PREFIX${albumId}_$episodeIndex"

    private fun lastKeyOf(albumId: String): String = "$KEY_LAST_PREFIX$albumId"
}
