package blbl.cat3399.core.net

import blbl.cat3399.core.log.AppLog
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.min

object WbiSigner {
    private const val TAG = "WbiSigner"
    private val mixinKeyEncTab = intArrayOf(
        46, 47, 18, 2, 53, 8, 23, 32, 15, 50, 10, 31, 58, 3, 45, 35, 27, 43, 5, 49,
        33, 9, 42, 19, 29, 28, 14, 39, 12, 38, 41, 13, 37, 48, 7, 16, 24, 55, 40,
        61, 26, 17, 0, 1, 60, 51, 30, 4, 22, 25, 54, 21, 56, 59, 6, 63, 57, 62, 11,
        36, 20, 34, 44, 52,
    )

    data class Keys(
        val imgKey: String,
        val subKey: String,
        val fetchedAtEpochSec: Long,
    )

    fun signQuery(params: Map<String, String>, keys: Keys, nowEpochSec: Long = System.currentTimeMillis() / 1000): Map<String, String> {
        val mixinKey = genMixinKey(keys.imgKey + keys.subKey)
        val withWts = params.toMutableMap()
        withWts["wts"] = nowEpochSec.toString()

        val sorted = withWts.entries.sortedBy { it.key }.associate { it.key to filterValue(it.value) }
        val query = sorted.entries.joinToString("&") { (k, v) -> "${enc(k)}=${enc(v)}" }
        val wRid = md5Hex(query + mixinKey)

        val out = params.toMutableMap()
        out["wts"] = nowEpochSec.toString()
        out["w_rid"] = wRid
        AppLog.v(TAG, "signQuery keysAgeSec=${nowEpochSec - keys.fetchedAtEpochSec} q=${min(query.length, 120)}")
        return out
    }

    private fun genMixinKey(raw: String): String {
        val bytes = raw.toByteArray()
        val sb = StringBuilder(64)
        for (i in mixinKeyEncTab) {
            if (i in bytes.indices) sb.append(bytes[i].toInt().toChar())
        }
        return sb.toString().substring(0, min(32, sb.length))
    }

    private fun filterValue(v: String): String = v.filterNot { it in "!\'()*" }

    private fun enc(s: String): String = percentEncodeUtf8(s)

    // Encode like encodeURIComponent (space -> %20, hex uppercase).
    private fun percentEncodeUtf8(s: String): String {
        val bytes = s.toByteArray(Charsets.UTF_8)
        val sb = StringBuilder(bytes.size * 3)
        for (b in bytes) {
            val c = b.toInt() and 0xFF
            val isUnreserved =
                (c in 'a'.code..'z'.code) ||
                    (c in 'A'.code..'Z'.code) ||
                    (c in '0'.code..'9'.code) ||
                    c == '-'.code || c == '_'.code || c == '.'.code || c == '~'.code
            if (isUnreserved) {
                sb.append(c.toChar())
            } else {
                sb.append('%')
                sb.append("0123456789ABCDEF"[c ushr 4])
                sb.append("0123456789ABCDEF"[c and 0x0F])
            }
        }
        return sb.toString()
    }

    private fun md5Hex(s: String): String {
        val digest = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        val sb = StringBuilder(digest.size * 2)
        for (b in digest) sb.append(String.format(Locale.US, "%02x", b))
        return sb.toString()
    }
}
