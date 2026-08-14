package blbl.cat3399.core.log

import java.io.File
import java.io.IOException

/**
 * 日志上传客户端。
 *
 * 原实现会通过 OkHttp 把日志 zip 上传到作者服务器（upload.cat3399.top/logs）。
 * 该外发能力已被有意禁用（隐私保护），相关上传代码（UPLOAD_URL / AUTH_TOKEN /
 * OkHttp 客户端 / CountingRequestBody / sanitizeFileName 等）已整体移除，避免
 * 保留永远执行不到的死代码与无用依赖。调用方签名保持不变，便于后续按需在本地
 * 实现日志导出而不必改动上游调用点。
 */
object LogUploadClient {
    suspend fun uploadZip(
        file: File,
        fileName: String = file.name,
        onProgress: ((sentBytes: Long, totalBytes: Long) -> Unit)? = null,
    ) {
        throw IOException("日志上传已禁用（隐私保护）")
    }
}
