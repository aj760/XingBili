package blbl.cat3399.core.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object Format {
    private val sdfHmChina =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
        }

    private val sdfYmdChina =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
        }

    private val sdfYMdChina =
        object : ThreadLocal<SimpleDateFormat>() {
            override fun initialValue(): SimpleDateFormat = SimpleDateFormat("yyyy.M.d", Locale.CHINA)
        }

    private fun sdfHm(): SimpleDateFormat =
        sdfHmChina.get()
            ?: SimpleDateFormat("HH:mm", Locale.CHINA).also { sdfHmChina.set(it) }

    private fun sdfYmd(): SimpleDateFormat =
        sdfYmdChina.get()
            ?: SimpleDateFormat("yyyy-MM-dd", Locale.CHINA).also { sdfYmdChina.set(it) }

    private fun sdfYMd(): SimpleDateFormat =
        sdfYMdChina.get()
            ?: SimpleDateFormat("yyyy.M.d", Locale.CHINA).also { sdfYMdChina.set(it) }

    fun duration(sec: Int): String {
        val s = if (sec < 0) 0 else sec
        val h = s / 3600
        val m = (s % 3600) / 60
        val ss = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, ss)
        else String.format(Locale.US, "%02d:%02d", m, ss)
    }

    fun clock(sec: Long): String {
        val safe = sec.coerceAtLeast(0L)
        val h = safe / 3600L
        val m = (safe % 3600L) / 60L
        val ss = safe % 60L
        return if (h > 0L) String.format(Locale.US, "%d:%02d:%02d", h, m, ss)
        else String.format(Locale.US, "%d:%02d", m, ss)
    }

    fun count(n: Long?): String {
        val v = n ?: return "-"
        return when {
            v >= 100_000_000 -> String.format(Locale.US, "%.1f亿", v / 100_000_000.0)
            v >= 10_000 -> String.format(Locale.US, "%.1f万", v / 10_000.0)
            else -> v.toString()
        }
    }

    fun timeText(epochSec: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (epochSec <= 0) return "-"
        val whenMs = epochSec * 1000

        val now = Calendar.getInstance().apply { timeInMillis = nowMs }
        val then = Calendar.getInstance().apply { timeInMillis = whenMs }

        val sameDay =
            now.get(Calendar.YEAR) == then.get(Calendar.YEAR) &&
                now.get(Calendar.DAY_OF_YEAR) == then.get(Calendar.DAY_OF_YEAR)

        return if (sameDay) {
            "今天 ${sdfHm().format(Date(whenMs))}"
        } else {
            sdfYmd().format(Date(whenMs))
        }
    }

    fun pubDateText(epochSec: Long, nowMs: Long = System.currentTimeMillis()): String {
        if (epochSec <= 0) return ""
        val whenMs = epochSec * 1000
        val diffMs = nowMs - whenMs
        if (diffMs < 0) {
            return sdfYMd().format(Date(whenMs))
        }

        val minuteMs = 60_000L
        val hourMs = 3_600_000L
        val dayMs = 86_400_000L

        return when {
            diffMs < minuteMs -> {
                val sec = maxOf(1, diffMs / 1000)
                "${sec}秒前"
            }
            diffMs < hourMs -> {
                val min = maxOf(1, diffMs / minuteMs)
                "${min}分钟前"
            }
            diffMs < dayMs -> {
                val hour = maxOf(1, diffMs / hourMs)
                "${hour}小时前"
            }
            diffMs < 3 * dayMs -> {
                val day = maxOf(1, diffMs / dayMs)
                "${day}天前"
            }
            else -> {
                sdfYMd().format(Date(whenMs))
            }
        }
    }
}
