package blbl.cat3399.core.emote

import android.content.Context
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

/**
 * Token -> image URL mapping used to render `[doge]`-style emotes in places where the API only
 * provides raw text (e.g. danmaku).
 *
 * Source: https://api.bilibili.com/x/emote/user/panel/web?business=reply
 *
 * - Loads a cached mapping from disk (app files dir).
 * - Refreshes at most once per day.
 */
object ReplyEmotePanelRepository {
    private const val TAG = "ReplyEmotePanel"
    private const val TTL_SEC: Long = 24L * 60 * 60

    private const val CACHE_DIR = "emote_cache"
    private const val CACHE_FILE = "reply_panel_v1.json"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lock = Any()

    @Volatile
    private var initStarted: Boolean = false

    @Volatile
    private var refreshJob: Job? = null

    @Volatile
    private var fetchedAtSec: Long = 0L

    @Volatile
    private var emoteMap: Map<String, String> = emptyMap()

    @Volatile
    private var version: Int = 0

    fun warmup(context: Context) {
        init(context)
        maybeRefresh(context)
    }

    fun init(context: Context) {
        if (initStarted) return
        synchronized(lock) {
            if (initStarted) return
            initStarted = true
        }
        val appContext = context.applicationContext
        scope.launch {
            loadFromDisk(appContext)
            maybeRefresh(appContext)
        }
    }

    fun version(): Int = version

    fun urlForToken(token: String): String? = emoteMap[token]

    /**
     * Snapshot of the currently loaded token -> image URL mapping, used by the comment
     * compose emote picker. Empty until [warmup] / [init] has loaded at least once.
     */
    fun entries(): Map<String, String> = emoteMap

    private fun maybeRefresh(context: Context) {
        val nowSec = System.currentTimeMillis() / 1000
        val needRefresh = emoteMap.isEmpty() || nowSec - fetchedAtSec >= TTL_SEC
        if (!needRefresh) return

        val active = refreshJob
        if (active != null && active.isActive) return

        val appContext = context.applicationContext
        refreshJob =
            scope.launch {
                refreshFromNetwork(appContext)
            }
    }

    private fun cacheFile(context: Context): File {
        val dir = File(context.filesDir, CACHE_DIR)
        runCatching { dir.mkdirs() }
        return File(dir, CACHE_FILE)
    }

    private fun loadFromDisk(context: Context) {
        val file = cacheFile(context)
        if (!file.exists()) return
        val raw =
            runCatching {
                file.readText(Charsets.UTF_8)
            }.getOrNull()
                ?: return

        runCatching {
            val root = JSONObject(raw)
            val ts = root.optLong("fetched_at_sec", 0L).coerceAtLeast(0L)
            val mapObj = root.optJSONObject("map") ?: JSONObject()
            val out = HashMap<String, String>(mapObj.length().coerceAtLeast(0))
            val it = mapObj.keys()
            while (it.hasNext()) {
                val k = it.next().trim()
                if (k.isBlank()) continue
                val url = mapObj.optString(k, "").trim()
                if (!url.startsWith("http")) continue
                out[k] = url
            }
            if (out.isNotEmpty()) {
                fetchedAtSec = ts
                emoteMap = out
                version++
                AppLog.i(TAG, "loadFromDisk size=${out.size} fetchedAtSec=$ts")
            }
        }.onFailure { t ->
            AppLog.w(TAG, "loadFromDisk parse failed; keep empty and refresh", t)
            // Do not delete files; keep the original for debugging.
            val bad =
                File(
                    file.parentFile ?: context.filesDir,
                    "${file.nameWithoutExtension}.bad_${System.currentTimeMillis()}.${file.extension}",
                )
            runCatching { file.renameTo(bad) }
        }
    }

    private suspend fun refreshFromNetwork(context: Context) {
        val url =
            BiliClient.withQuery(
                "https://api.bilibili.com/x/emote/user/panel/web",
                mapOf("business" to "reply"),
            )
        val json = runCatching { BiliClient.getJson(url) }.getOrNull() ?: return
        val code = json.optInt("code", 0)
        if (code != 0) {
            val msg = json.optString("message", json.optString("msg", ""))
            AppLog.w(TAG, "refresh failed code=$code msg=$msg")
            return
        }

        val data = json.optJSONObject("data") ?: JSONObject()
        val packages = data.optJSONArray("packages") ?: JSONArray()
        val out = HashMap<String, String>(512)
        for (i in 0 until packages.length()) {
            val pkg = packages.optJSONObject(i) ?: continue
            val emotes = pkg.optJSONArray("emote") ?: continue
            for (j in 0 until emotes.length()) {
                val e = emotes.optJSONObject(j) ?: continue
                val text = e.optString("text", "").trim()
                if (!looksLikeToken(text)) continue
                val urlStr = e.optString("url", "").trim()
                if (!urlStr.startsWith("http")) continue
                out[text] = urlStr
            }
        }
        if (out.isEmpty()) return

        val nowSec = System.currentTimeMillis() / 1000
        fetchedAtSec = nowSec
        emoteMap = out
        version++
        AppLog.i(TAG, "refresh ok size=${out.size} fetchedAtSec=$nowSec")

        persistToDisk(context, nowSec, out)
    }

    private fun persistToDisk(context: Context, fetchedAtSec: Long, map: Map<String, String>) {
        val file = cacheFile(context)
        runCatching {
            val mapObj = JSONObject()
            for ((k, v) in map) mapObj.put(k, v)
            val root =
                JSONObject()
                    .put("fetched_at_sec", fetchedAtSec)
                    .put("map", mapObj)
            file.writeText(root.toString(), Charsets.UTF_8)
        }.onFailure { t ->
            AppLog.w(TAG, "persist failed file=${file.absolutePath}", t)
        }
    }

    private fun looksLikeToken(text: String): Boolean {
        if (text.length < 3) return false
        return text.first() == '[' && text.last() == ']'
    }
}

