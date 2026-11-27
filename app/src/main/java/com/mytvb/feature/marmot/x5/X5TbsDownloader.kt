package com.mytvb.feature.marmot.x5

import android.app.Activity
import android.content.Context
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.mytvb.R
import com.tencent.smtt.sdk.QbSdk
import com.tencent.smtt.sdk.TbsListener
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.math.BigInteger
import java.security.MessageDigest
import java.net.HttpURLConnection
import java.net.URL

/**
 * X5 TBS 内核下载安装器（对标参考 `tv.utao.x5.helper.X5DownloadHelper`）。
 *
 * 正确的 X5 内核方案（修正之前错误用 OciDownloader 的 AOSP WebView APK）：
 * 1. [download] 从腾讯 tbsall.imtt.qq.com 下载 `.tbs` 内核文件（按 ABI 64/32 位选 URL）
 * 2. [isDownloaded] MD5 校验下载完整性
 * 3. [install] `QbSdk.installLocalTbsCore` 安装 + `TbsListener` 监听 → 成功 3 秒重启
 *
 * 固定 URL/MD5/version 取自参考反编译 X5DownloadHelper 常量。
 */
object X5TbsDownloader {
    private const val TAG = "X5TbsDownloader"

    // 64位 X5 内核（arm64-v8a）
    private const val X5_URL_64 =
        "https://tbsall.imtt.qq.com/mtt/release/component/tbs_core_046285_20240613152541_nolog_fs_obfs_arm64-v8a_release.tbs"
    private const val X5_MD5_64 = "a240d924e2bdec85c1929501bccce83d"
    private const val X5_VERSION_64 = 46285

    // 32位 X5 内核（armeabi）
    private const val X5_URL_32 =
        "https://tbsall.imtt.qq.com/others/release/x5/tbs_core_046238_20230210164344_nolog_fs_obfs_armeabi_release.tbs"
    private const val X5_MD5_32 = "6632d006015e2b014e0cd04ae04d6c00"
    private const val X5_VERSION_32 = 46238

    /** .tbs 文件名（存 filesDir）。 */
    private const val TBS_FILE_NAME = "x5_core.tbs"

    @Volatile
    private var isDownloading = false

    interface Callback {
        fun onProgress(hint: String)
        fun onComplete(success: Boolean, message: String)
    }

    /** 当前设备是否 64 位。 */
    private fun is64Bit(): Boolean =
        Build.SUPPORTED_ABIS.isNotEmpty() && Build.SUPPORTED_ABIS[0].contains("64")

    /** 对应 ABI 的下载 URL。 */
    private fun getUrl(): String = if (is64Bit()) X5_URL_64 else X5_URL_32

    /** 对应 ABI 的期望 MD5。 */
    private fun getExpectedMd5(): String = if (is64Bit()) X5_MD5_64 else X5_MD5_32

    /** 对应 ABI 的 X5 版本号。 */
    fun getVersion(): Int = if (is64Bit()) X5_VERSION_64 else X5_VERSION_32

    /** 主线程 Handler（回调切主线程，避免子线程操作 UI 崩溃）。 */
    private val mainHandler = Handler(Looper.getMainLooper())

    /** 包装回调，确保在主线程执行。 */
    private fun Callback.onMain(): Callback = object : Callback {
        override fun onProgress(hint: String) { mainHandler.post { this@onMain.onProgress(hint) } }
        override fun onComplete(success: Boolean, message: String) { mainHandler.post { this@onMain.onComplete(success, message) } }
    }

    /** .tbs 文件路径。 */
    private fun getTbsFile(context: Context): File = File(context.filesDir, TBS_FILE_NAME)

    /** 持久化"已安装"标记（canLoadX5 在模拟器/部分设备不可靠，用 SP 记录安装流程完成）。 */
    private const val PREF_NAME = "x5_status"
    private const val KEY_INSTALLED = "installed_version"

    /** 记录安装完成（installLocalTbsCore 返回 200 时调用）。 */
    private fun markInstalled(context: Context, version: Int) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .edit().putInt(KEY_INSTALLED, version).apply()
    }

    /** 是否已完成安装流程（installLocalTbsCore 成功过）。 */
    fun isInstalled(context: Context): Boolean {
        val v = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
            .getInt(KEY_INSTALLED, -1)
        return v > 0
    }

    /** 计算文件 MD5（对标参考 `calculateMd5`）。 */
    private fun calculateMd5(file: File): String = try {
        val md = MessageDigest.getInstance("MD5")
        FileInputStream(file).use { fis ->
            val buf = ByteArray(8192)
            while (true) {
                val n = fis.read(buf)
                if (n == -1) break
                md.update(buf, 0, n)
            }
        }
        var hex = BigInteger(1, md.digest()).toString(16)
        while (hex.length < 32) hex = "0$hex"
        hex
    } catch (e: Exception) {
        Log.w(TAG, "计算 MD5 失败: ${e.message}"); ""
    }

    /** 是否已下载（文件存在 + MD5 匹配，对标参考 `isDownloaded`）。 */
    fun isDownloaded(context: Context): Boolean {
        val file = getTbsFile(context)
        if (!file.exists()) return false
        val expected = getExpectedMd5()
        val actual = calculateMd5(file)
        Log.i(TAG, "MD5 校验: 期望=$expected, 实际=$actual")
        return expected.equals(actual, ignoreCase = true)
    }

    /** 是否正在下载/安装。 */
    fun isBusy(): Boolean = isDownloading

    /** 是否正在下载。 */
    fun isDownloading(): Boolean = isDownloading

    /**
     * 下载入口（对标参考 `download`）。
     * - 已在处理 → 提示
     * - 已下载 → 直接安装
     * - 未下载 → 启动下载
     */
    fun download(context: Context, callback: Callback) {
        val cb = callback.onMain()
        if (isDownloading) {
            cb.onProgress(context.getString(R.string.x5_status_processing))
            return
        }
        if (isDownloaded(context)) {
            Log.i(TAG, "X5 已下载，直接安装")
            install(context, cb)
        } else {
            startDownload(context, cb)
        }
    }

    /** 启动下载（对标参考 `startDownload`，HttpURLConnection 流式下载）。 */
    private fun startDownload(context: Context, callback: Callback) {
        isDownloading = true
        Thread {
            try {
                val url = getUrl()
                val file = getTbsFile(context)
                Log.i(TAG, "开始下载 X5: $url → ${file.absolutePath}")
                val conn = (URL(url).openConnection() as HttpURLConnection).apply {
                    connectTimeout = 15000
                    readTimeout = 30000
                    requestMethod = "GET"
                    connect()
                }
                val responseCode = conn.responseCode
                if (responseCode != 200) {
                    throw Exception(context.getString(R.string.x5_server_error_format, responseCode))
                }
                val total = conn.contentLength.toLong()
                Log.i(TAG, "X5 内核大小: $total 字节")
                var downloaded = 0L
                var lastPercent = -1
                conn.inputStream.use { input ->
                    FileOutputStream(file).use { out ->
                        val buf = ByteArray(8192)
                        while (true) {
                            val n = input.read(buf)
                            if (n == -1) break
                            out.write(buf, 0, n)
                            downloaded += n
                            if (total > 0) {
                                val percent = (downloaded * 100 / total).toInt()
                                if (percent != lastPercent) {
                                    callback.onProgress(context.getString(R.string.x5_downloading_percent_format, percent))
                                    lastPercent = percent
                                }
                            }
                        }
                    }
                }
                // 校验 MD5
                if (!isDownloaded(context)) {
                    file.delete()
                    throw Exception(context.getString(R.string.x5_md5_check_failed))
                }
                Log.i(TAG, "X5 下载完成，开始安装")
                install(context, callback)
            } catch (e: Exception) {
                Log.e(TAG, "X5 下载失败: ${e.message}", e)
                isDownloading = false
                callback.onComplete(false, context.getString(R.string.x5_download_failed_format, e.message))
            }
        }.start()
    }

    /**
     * 安装 X5 内核（对标参考 `installX5WithListener`）。
     * `QbSdk.installLocalTbsCore` + `TbsListener`，成功（code 200）3 秒重启。
     */
    @Suppress("DEPRECATION")
    fun install(context: Context, callback: Callback) {
        isDownloading = true
        val file = getTbsFile(context)
        val version = getVersion()
        val mainHandler = Handler(Looper.getMainLooper())
        mainHandler.post { callback.onProgress(context.getString(R.string.x5_installing)) }

        QbSdk.setTbsListener(object : TbsListener {
            override fun onDownloadFinish(errorCode: Int) {
                Log.i(TAG, "onDownloadFinish: $errorCode")
            }
            override fun onDownloadProgress(progress: Int) {
                Log.i(TAG, "onDownloadProgress: $progress")
            }
            override fun onInstallFinish(code: Int) {
                Log.i(TAG, "onInstallFinish: $code")
                mainHandler.post {
                    if (code == 200) {
                        isDownloading = false
                        markInstalled(context, version)
                        callback.onComplete(true, context.getString(R.string.x5_installed_restart_countdown))
                        mainHandler.postDelayed({ restartApp(context) }, 3000)
                    } else {
                        isDownloading = false
                        callback.onComplete(false, context.getString(R.string.x5_install_failed_code_format, code))
                    }
                }
            }
        })

        Thread {
            try {
                // 先 preInit 初始化 TBS SDK（installLocalTbsCore 要求 SDK 已初始化，否则无效）
                Log.i(TAG, "preInit TBS SDK...")
                try {
                    QbSdk.setDownloadWithoutWifi(true)
                    QbSdk.preInit(context.applicationContext, null)
                    Thread.sleep(1000)
                } catch (e: Exception) {
                    Log.w(TAG, "preInit 异常: ${e.message}")
                }
                Log.i(TAG, "开始安装 X5: ${file.absolutePath}, version: $version")
                QbSdk.installLocalTbsCore(context.applicationContext, version, file.absolutePath)
                Thread.sleep(5000)
                val canLoad = QbSdk.canLoadX5(context.applicationContext)
                Log.i(TAG, "5秒后检查 canLoadX5: $canLoad")
                if (!canLoad) {
                    mainHandler.post {
                        isDownloading = false
                        callback.onComplete(false, context.getString(R.string.x5_install_unsupported))
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "安装异常: ${e.message}")
                mainHandler.post {
                    isDownloading = false
                    callback.onComplete(false, context.getString(R.string.x5_install_error_format, e.message))
                }
            }
        }.start()
    }

    /** 重启 APP（对标参考 `restartApp`）。 */
    private fun restartApp(context: Context) {
        try {
            val intent = context.packageManager.getLaunchIntentForPackage(context.packageName)
            intent?.addFlags(
                android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                        android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
            )
            if (context is Activity) {
                context.startActivity(intent)
                context.finishAffinity()
            } else {
                intent?.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
            }
            android.os.Process.killProcess(android.os.Process.myPid())
            System.exit(0)
        } catch (e: Exception) {
            Log.w(TAG, "重启失败: ${e.message}")
            System.exit(0)
        }
    }

    /** 检查 X5 是否已加载（对标参考 `x5Ok`）。 */
    @Suppress("DEPRECATION")
    fun isX5Loaded(context: Context): Boolean = try {
        QbSdk.canLoadX5(context)
    } catch (t: Throwable) {
        Log.w(TAG, "canLoadX5 检查失败: ${t.message}"); false
    }
}
