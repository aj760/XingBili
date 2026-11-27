package com.mytvb.core.common.format

import android.content.Context
import com.mytvb.R
import java.text.DecimalFormat
import java.util.Locale

object NumberUtils {

    private val wanFormat = DecimalFormat("#.#万")
    private val yiFormat = DecimalFormat("#.#亿")
    private val plainDecimalFormat = DecimalFormat("#.#")
    private val commaFormat = DecimalFormat("#,###")

    fun formatCount(count: Long): String {
        return when {
            count >= 100000000 -> {
                yiFormat.format(count / 100000000.0)
            }
            count >= 10000 -> {
                wanFormat.format(count / 10000.0)
            }
            else -> {
                commaFormat.format(count)
            }
        }
    }

    /** 多语言版本的 [formatCount]：中文万/亿分级，英文等千分位语言 K/M/B 分级（资源开关驱动）。 */
    fun formatCount(context: Context, count: Long): String {
        return if (context.resources.getBoolean(R.bool.count_scale_thousand)) {
            when {
                count >= 1_000_000_000L -> context.getString(
                    R.string.core_count_b_format,
                    plainDecimalFormat.format(count / 1_000_000_000.0)
                )
                count >= 1_000_000L -> context.getString(
                    R.string.core_count_m_format,
                    plainDecimalFormat.format(count / 1_000_000.0)
                )
                count >= 1_000L -> context.getString(
                    R.string.core_count_k_format,
                    plainDecimalFormat.format(count / 1_000.0)
                )
                else -> commaFormat.format(count)
            }
        } else {
            when {
                count >= 100000000 -> {
                    context.getString(
                        R.string.core_count_yi_format,
                        plainDecimalFormat.format(count / 100000000.0)
                    )
                }
                count >= 10000 -> {
                    context.getString(
                        R.string.core_count_wan_format,
                        plainDecimalFormat.format(count / 10000.0)
                    )
                }
                else -> {
                    commaFormat.format(count)
                }
            }
        }
    }

    fun formatDuration(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60

        return when {
            hours > 0 -> String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, minutes, secs)
            else -> String.format(Locale.getDefault(), "%02d:%02d", minutes, secs)
        }
    }

    fun formatTimeMs(timeMs: Long): String {
        if (timeMs < 0) return "00:00"
        return formatDuration(timeMs / 1000)
    }

    fun formatBytes(bytes: Long): String {
        if (bytes < 1024) return "$bytes B"
        val kb = bytes / 1024.0
        if (kb < 1024) return String.format(Locale.getDefault(), "%.1f KB", kb)
        val mb = kb / 1024.0
        if (mb < 1024) return String.format(Locale.getDefault(), "%.1f MB", mb)
        return String.format(Locale.getDefault(), "%.2f GB", mb / 1024.0)
    }
}
