package com.mytvb.feature.player.speed

import kotlin.math.abs

/**
 * 倍速档位单一数据源：播放器倍速菜单、设置页「默认播放速度」弹窗、
 * 默认倍速存储值解析共用同一份档位表，消除两处硬编码重复维护。
 */
object PlaybackSpeedPolicy {

    val DEFAULT_PLAYBACK_SPEED_OPTIONS: FloatArray = floatArrayOf(
        0.25f, 0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f
    )

    /**
     * 归一到最近档位：档位表外的值（存储异常旧值、手改 DataStore 等）
     * 吸附到最近可用档，保证解析结果永远落在档位表内；非有限值回落 1.0x。
     */
    fun nearestPlaybackSpeed(
        value: Float,
        options: FloatArray = DEFAULT_PLAYBACK_SPEED_OPTIONS
    ): Float {
        if (!value.isFinite()) return 1.0f
        return options.minByOrNull { abs(it - value) } ?: 1.0f
    }

    /** 设置页「默认播放速度」单选弹窗的选项串（与档位表同源，字面量与既有存储值一致）。 */
    fun storedSpeedOptions(options: FloatArray = DEFAULT_PLAYBACK_SPEED_OPTIONS): Array<String> =
        options.map(Float::toString).toTypedArray()
}
