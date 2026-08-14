package blbl.cat3399.core.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.ColorDrawable
import android.widget.ImageView
import androidx.collection.LruCache
import blbl.cat3399.R
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import kotlinx.coroutines.CoroutineScope
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.IOException
import java.security.MessageDigest
import java.util.WeakHashMap
import java.util.concurrent.atomic.AtomicLong

object ImageLoader {
    private const val TAG = "ImageLoader"

    // Cap parallel fetch+decode so the device (and the main-thread bitmap uploads that follow)
    // stay responsive instead of stalling under a storm of simultaneous home-grid requests.
    private const val MAX_CONCURRENT_LOADS = 4

    // Reasonable upper bound used when a view's measured size is not yet known (first bind).
    private const val FALLBACK_MAX_W = 480
    private const val FALLBACK_MAX_H = 720

    private val placeholder = ColorDrawable(0xFF2A2A2A.toInt())
    private val inFlight = WeakHashMap<ImageView, Job>()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val loadSemaphore = Semaphore(MAX_CONCURRENT_LOADS)

    private val cache = object : LruCache<String, Bitmap>(maxCacheBytes()) {
        override fun sizeOf(key: String, value: Bitmap): Int = value.byteCount
    }

    private var diskCacheDir: File? = null
    private var diskMaxBytes = 80L * 1024 * 1024
    private val diskSize = AtomicLong(0L)

    fun init(context: Context) {
        val dir = File(context.cacheDir, "blbl_img")
        if (dir.exists() || dir.mkdirs()) {
            diskCacheDir = dir
            val free = dir.freeSpace
            if (free > 0) {
                diskMaxBytes = minOf(100L * 1024 * 1024, maxOf(20L * 1024 * 1024, free / 4))
            }
            // Scan disk usage + trim off the main thread: with a large image cache this
            // listFiles/sort pass can take hundreds of ms and would delay app startup.
            Thread {
                try {
                    diskSize.set(computeDiskSize(dir))
                    trimDiskIfNeeded()
                } catch (t: Throwable) {
                    AppLog.w(TAG, "disk cache init failed", t)
                }
            }.apply {
                isDaemon = true
                name = "blbl-img-disk-init"
            }.start()
        }
    }

    fun loadInto(view: ImageView, url: String?) {
        val normalized = normalizeImageUrl(url)

        if (normalized == null) {
            view.setTag(R.id.tag_image_loader_url, null)
            inFlight.remove(view)?.cancel()
            if (view.drawable !== placeholder) view.setImageDrawable(placeholder)
            return
        }

        val lastUrl = view.getTag(R.id.tag_image_loader_url) as? String
        if (lastUrl == normalized) {
            // If we already have a non-placeholder image for the same URL, keep it to prevent
            // flicker on rebind (e.g. switching tabs triggers notifyItemRangeChanged).
            val drawable = view.drawable
            if (drawable != null && drawable !== placeholder) {
                inFlight.remove(view)?.cancel()
                return
            }
            // If the same URL is already loading, keep the current placeholder.
            val inFlightJob = inFlight[view]
            if (inFlightJob != null && inFlightJob.isActive) return
        } else {
            view.setTag(R.id.tag_image_loader_url, normalized)
            inFlight.remove(view)?.cancel()
        }

        val cached = cache.get(normalized)
        if (cached != null) {
            view.setImageBitmap(cached)
            return
        }

        if (view.drawable !== placeholder) view.setImageDrawable(placeholder)
        // Capture the display size on the main thread (best-effort) so the background decode
        // can downsample to roughly the on-screen resolution.
        val targetW = view.width.takeIf { it > 0 } ?: FALLBACK_MAX_W
        val targetH = view.height.takeIf { it > 0 } ?: FALLBACK_MAX_H
        val job = scope.launch {
            try {
                val bmp = withContext(Dispatchers.IO) { loadSampled(normalized, targetW, targetH) }
                if ((view.getTag(R.id.tag_image_loader_url) as? String) == normalized && !bmp.isRecycled) {
                    view.setImageBitmap(bmp)
                    cache.put(normalized, bmp)
                } else {
                    bmp.recycle()
                }
            } catch (t: Throwable) {
                AppLog.w(TAG, "load failed url=$normalized", t)
            }
        }
        inFlight[view] = job
    }

    private suspend fun loadSampled(url: String, dstW: Int, dstH: Int): Bitmap {
        loadSemaphore.acquire()
        try {
            val disk = diskCacheDir
            if (disk != null) {
                val file = File(disk, keyFor(url))
                if (file.exists()) {
                    try {
                        val bytes = file.readBytes()
                        file.setLastModified(System.currentTimeMillis())
                        decodeSampled(bytes, dstW, dstH)?.let { return it }
                    } catch (t: Throwable) {
                        AppLog.w(TAG, "disk read failed url=$url", t)
                    }
                }
            }

            val bytes = BiliClient.getBytes(url)
            val bmp = decodeSampled(bytes, dstW, dstH) ?: throw IOException("decode failed url=$url")
            if (disk != null) {
                try {
                    writeDisk(disk, keyFor(url), bytes)
                } catch (t: Throwable) {
                    AppLog.w(TAG, "disk write failed url=$url", t)
                }
            }
            return bmp
        } finally {
            loadSemaphore.release()
        }
    }

    private fun decodeSampled(bytes: ByteArray, dstW: Int, dstH: Int): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        val srcW = bounds.outWidth
        val srcH = bounds.outHeight
        if (srcW <= 0 || srcH <= 0) return null

        val sample = computeSampleSize(srcW, srcH, dstW, dstH)
        val opts = BitmapFactory.Options().apply {
            inJustDecodeBounds = false
            inSampleSize = sample
            inPreferredConfig = Bitmap.Config.ARGB_8888
            inMutable = true
        }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun computeSampleSize(srcW: Int, srcH: Int, dstW: Int, dstH: Int): Int {
        val raw = maxOf(srcW / dstW.toFloat(), srcH / dstH.toFloat())
        var sample = 1
        while (sample * 2 <= raw) sample *= 2
        return sample.coerceAtLeast(1)
    }

    private fun keyFor(url: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(url.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun writeDisk(dir: File, key: String, bytes: ByteArray) {
        val file = File(dir, key)
        file.writeBytes(bytes)
        diskSize.addAndGet(bytes.size.toLong())
        trimDiskIfNeeded()
    }

    private fun trimDiskIfNeeded() {
        val dir = diskCacheDir ?: return
        if (diskSize.get() <= diskMaxBytes) return
        val files = dir.listFiles()?.filter { it.isFile } ?: return
        val sorted = files.sortedBy { it.lastModified() }
        for (f in sorted) {
            if (diskSize.get() <= diskMaxBytes * 0.8) break
            val len = f.length()
            if (f.delete()) diskSize.addAndGet(-len)
        }
    }

    private fun computeDiskSize(dir: File): Long =
        dir.listFiles()?.filter { it.isFile }?.sumOf { it.length() } ?: 0L

    private fun normalizeImageUrl(url: String?): String? {
        val raw = url?.trim().takeIf { !it.isNullOrBlank() } ?: return null
        if (raw.startsWith("//")) return "https:$raw"
        if (!raw.startsWith("http://")) return raw

        val host = raw.toHttpUrlOrNull()?.host?.lowercase().orEmpty()
        val isBiliCdn =
            host == "hdslb.com" ||
                host.endsWith(".hdslb.com") ||
                host == "bilibili.com" ||
                host.endsWith(".bilibili.com") ||
                host == "bilivideo.com" ||
                host.endsWith(".bilivideo.com") ||
                host == "bilivideo.cn" ||
                host.endsWith(".bilivideo.cn")
        return if (isBiliCdn) raw.replaceFirst("http://", "https://") else raw
    }

    private fun maxCacheBytes(): Int {
        val maxMemory = Runtime.getRuntime().maxMemory().toInt()
        return maxMemory / 16
    }
}
