package com.mytvb.feature.player.settings

/**
 * 音量均衡档位：PCM 级自适应 RMS 响度归一（VolumeBalanceAudioProcessor），
 * 区别于旧的系统音效方案——不依赖设备 audiofx 驱动，无"电音"失真风险。
 */
enum class AudioBalanceLevel(val settingValue: String) {
    OFF("关"),
    LOW("低"),
    MEDIUM("中"),
    HIGH("高");

    companion object {
        fun fromSettingValue(value: String?): AudioBalanceLevel = when (value?.trim()) {
            "低" -> LOW
            "中" -> MEDIUM
            "高" -> HIGH
            else -> OFF
        }
    }
}

/**
 * 音量均衡全局档位：设置页与播放器构建方写入，PCM 处理器每个音频块读取。
 * 档位切换即时生效，无需重建播放器——解决旧方案"开关后要等 player 重建才生效"的问题。
 */
object AudioBalanceSettings {
    @Volatile
    var level: AudioBalanceLevel = AudioBalanceLevel.OFF

    /** 设置页选择后调用：持久化由调用方负责，这里只刷内存档位让当前播放器立即生效。 */
    fun applySettingValue(value: String) {
        level = AudioBalanceLevel.fromSettingValue(value)
    }
}
