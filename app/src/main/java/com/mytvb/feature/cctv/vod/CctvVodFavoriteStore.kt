package com.mytvb.feature.cctv.vod

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * 央视点播的本地收藏。
 *
 * 收藏列表要直接展示封面/主演，且接口没有「按 id 查单条」的入口，所以整条专辑
 * 快照落盘；同时记下收藏时所属的分类 code，供片库页的分类 tab 在收藏模式下过滤。
 * JSON 解析失败的单条直接跳过，不让脏数据拖垮整个列表。
 */
object CctvVodFavoriteStore {

    private const val PREF_NAME = "cctv_vod_favorites"
    private const val KEY_ITEMS = "items"

    data class Entry(
        val album: CctvAlbum,
        val categoryCode: String,
        val favoritedAt: Long
    )

    /** 收藏列表（按收藏时间倒序）；[categoryCode] 非空时只返回该分类。 */
    fun list(context: Context, categoryCode: String? = null): List<Entry> =
        readAll(context)
            .filter { categoryCode == null || it.categoryCode == categoryCode }
            .sortedByDescending { it.favoritedAt }

    fun isFavorite(context: Context, albumId: String): Boolean =
        readAll(context).any { it.album.id == albumId }

    /** 切换收藏状态，返回切换后是否为已收藏。 */
    fun toggle(context: Context, album: CctvAlbum, categoryCode: String): Boolean {
        val current = readAll(context)
        val existed = current.any { it.album.id == album.id }
        val next = if (existed) {
            current.filterNot { it.album.id == album.id }
        } else {
            listOf(Entry(album, categoryCode, System.currentTimeMillis())) + current
        }
        writeAll(context, next)
        return !existed
    }

    private fun readAll(context: Context): List<Entry> {
        val raw = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getString(KEY_ITEMS, null) ?: return emptyList()
        return runCatching {
            val array = JSONArray(raw)
            val result = ArrayList<Entry>(array.length())
            for (index in 0 until array.length()) {
                array.optJSONObject(index)?.toEntry()?.let(result::add)
            }
            result
        }.getOrElse { emptyList() }
    }

    private fun writeAll(context: Context, entries: List<Entry>) {
        val array = JSONArray()
        entries.forEach { array.put(it.toJson()) }
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putString(KEY_ITEMS, array.toString()).apply()
    }

    private fun Entry.toJson(): JSONObject = JSONObject().apply {
        put("id", album.id)
        put("title", album.title)
        put("cover", album.coverUrl)
        put("brief", album.brief)
        put("count", album.episodeCount)
        put("area", album.area)
        put("year", album.year)
        put("genre", album.genre)
        put("actors", album.actors)
        put("mode", album.episodeMode)
        put("category", categoryCode)
        put("at", favoritedAt)
    }

    private fun JSONObject.toEntry(): Entry? {
        val id = optString("id").trim()
        if (id.isBlank()) return null
        return Entry(
            album = CctvAlbum(
                id = id,
                title = optString("title"),
                coverUrl = optString("cover"),
                brief = optString("brief"),
                episodeCount = optInt("count"),
                area = optString("area"),
                year = optString("year"),
                genre = optString("genre"),
                actors = optString("actors"),
                episodeMode = optInt("mode")
            ),
            categoryCode = optString("category"),
            favoritedAt = optLong("at")
        )
    }
}
