package com.mytvb.core.common.time

import android.content.Context
import com.mytvb.R
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeUtils {

    private val historyTimeFormat = SimpleDateFormat("HH:mm", Locale.CHINA)
    private val historyMonthDayFormat = SimpleDateFormat("MM-dd HH:mm", Locale.CHINA)
    private val historyYearFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)
    private val publishMonthDayFormat = SimpleDateFormat("MM-dd", Locale.CHINA)
    private val publishYearFormat = SimpleDateFormat("yyyy-MM-dd", Locale.CHINA)

    private val fullFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

    fun formatTime(timestamp: Long): String {
        return if (timestamp > 0) {
            fullFormat.format(Date(timestamp * 1000))
        } else {
            ""
        }
    }

    fun formatRelativeTime(timestamp: Long): String {
        if (timestamp <= 0) {
            return ""
        }
        val now = System.currentTimeMillis() / 1000
        val diff = now - timestamp
        if (diff < 0) {
            return publishMonthDayFormat.format(Date(timestamp * 1000))
        }

        return when {
            diff < 60 -> "刚刚"
            diff < 3600 -> "${diff / 60}分钟前"
            diff < 86400 -> "${diff / 3600}小时前"
            diff < 86400 * 4 -> "${diff / 86400}天前"
            else -> {
                val targetDate = Date(timestamp * 1000)
                if (isSameYear(targetDate, Date(now * 1000))) {
                    publishMonthDayFormat.format(targetDate)
                } else {
                    publishYearFormat.format(targetDate)
                }
            }
        }
    }

    fun formatPubDate(timestamp: Long): String {
        return if (timestamp > 0) {
            publishYearFormat.format(Date(timestamp * 1000))
        } else {
            ""
        }
    }

    /** 多语言版本的 [formatRelativeTime]。 */
    fun formatRelativeTime(context: Context, timestamp: Long): String {
        if (timestamp <= 0) {
            return ""
        }
        val now = System.currentTimeMillis() / 1000
        val diff = now - timestamp
        if (diff < 0) {
            return publishMonthDayFormat.format(Date(timestamp * 1000))
        }

        return when {
            diff < 60 -> context.getString(R.string.core_time_just_now)
            diff < 3600 -> context.getString(R.string.core_time_minutes_ago_format, diff / 60)
            diff < 86400 -> context.getString(R.string.core_time_hours_ago_format, diff / 3600)
            diff < 86400 * 4 -> context.getString(R.string.core_time_days_ago_format, diff / 86400)
            else -> {
                val targetDate = Date(timestamp * 1000)
                if (isSameYear(targetDate, Date(now * 1000))) {
                    publishMonthDayFormat.format(targetDate)
                } else {
                    publishYearFormat.format(targetDate)
                }
            }
        }
    }

    fun formatHistoryViewTime(timestamp: Long): String {
        if (timestamp <= 0L) {
            return ""
        }
        val targetDate = Date(timestamp * 1000)
        val nowDate = Date()
        val diffDays = ((nowDate.time - targetDate.time) / 1000L) / 86400L
        return when (diffDays) {
            0L -> "今天 ${historyTimeFormat.format(targetDate)}"
            1L -> "昨天 ${historyTimeFormat.format(targetDate)}"
            2L -> "前天 ${historyTimeFormat.format(targetDate)}"
            else -> {
                if (isSameYear(targetDate, nowDate)) {
                    historyMonthDayFormat.format(targetDate)
                } else {
                    historyYearFormat.format(targetDate)
                }
            }
        }
    }

    /** 多语言版本的 [formatHistoryViewTime]。 */
    fun formatHistoryViewTime(context: Context, timestamp: Long): String {
        if (timestamp <= 0L) {
            return ""
        }
        val targetDate = Date(timestamp * 1000)
        val nowDate = Date()
        val diffDays = ((nowDate.time - targetDate.time) / 1000L) / 86400L
        return when (diffDays) {
            0L -> context.getString(R.string.core_time_today_time_format, historyTimeFormat.format(targetDate))
            1L -> context.getString(R.string.core_time_yesterday_time_format, historyTimeFormat.format(targetDate))
            2L -> context.getString(R.string.core_time_day_before_time_format, historyTimeFormat.format(targetDate))
            else -> {
                if (isSameYear(targetDate, nowDate)) {
                    historyMonthDayFormat.format(targetDate)
                } else {
                    historyYearFormat.format(targetDate)
                }
            }
        }
    }

    private fun isSameYear(first: Date, second: Date): Boolean {
        val firstCalendar = Calendar.getInstance().apply { time = first }
        val secondCalendar = Calendar.getInstance().apply { time = second }
        return firstCalendar.get(Calendar.YEAR) == secondCalendar.get(Calendar.YEAR)
    }
}
