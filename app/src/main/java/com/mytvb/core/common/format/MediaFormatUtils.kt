package com.mytvb.core.common.format

object MediaFormatUtils {

    fun formatCodecName(mimeType: String?): String {
        if (mimeType == null) return "未知"
        return when {
            mimeType.contains("avc") || mimeType.contains("h264") -> "AVC (H.264)"
            mimeType.contains("hevc") || mimeType.contains("h265") -> "HEVC (H.265)"
            mimeType.contains("av01") -> "AV1"
            mimeType.contains("vp9") -> "VP9"
            mimeType.contains("vp8") -> "VP8"
            mimeType.contains("aac") -> "AAC"
            mimeType.contains("opus") -> "Opus"
            mimeType.contains("mp4a") -> "AAC"
            mimeType.contains("ec-3") || mimeType.contains("eac3") -> "E-AC-3"
            mimeType.contains("ac-3") -> "AC-3"
            mimeType.contains("flac") -> "FLAC"
            mimeType.contains("vorbis") -> "Vorbis"
            else -> mimeType.substringAfterLast("/")
        }
    }

    fun formatAspectRatio(w: Int, h: Int): String {
        if (w <= 0 || h <= 0) return ""
        val gcd = gcd(w, h)
        val rw = w / gcd
        val rh = h / gcd
        if (rw > 30 || rh > 30) return ""
        return " ($rw:$rh)"
    }

    private fun gcd(a: Int, b: Int): Int {
        var x = a
        var y = b
        while (y != 0) {
            val t = y
            y = x % y
            x = t
        }
        return x
    }

    fun formatDanmakuRange(rangeStartMs: Long?, rangeEndMs: Long?): String {
        return if (rangeStartMs == null && rangeEndMs == null) {
            "full"
        } else {
            "[${rangeStartMs ?: ""},${rangeEndMs ?: ""}]"
        }
    }
}
