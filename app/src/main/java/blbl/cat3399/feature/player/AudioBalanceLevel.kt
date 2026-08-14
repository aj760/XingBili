package blbl.cat3399.feature.player

import blbl.cat3399.core.prefs.AppPrefs

internal enum class AudioBalanceLevel(
    val prefValue: String,
    val label: String,
) {
    Off(AppPrefs.PLAYER_AUDIO_BALANCE_OFF, "关"),
    Low(AppPrefs.PLAYER_AUDIO_BALANCE_LOW, "低"),
    Medium(AppPrefs.PLAYER_AUDIO_BALANCE_MEDIUM, "中"),
    High(AppPrefs.PLAYER_AUDIO_BALANCE_HIGH, "高"),
    ;

    companion object {
        val ordered: List<AudioBalanceLevel> = listOf(Off, Low, Medium, High)

        fun fromPrefValue(value: String): AudioBalanceLevel {
            return when (value.trim()) {
                AppPrefs.PLAYER_AUDIO_BALANCE_LOW -> Low
                AppPrefs.PLAYER_AUDIO_BALANCE_MEDIUM -> Medium
                AppPrefs.PLAYER_AUDIO_BALANCE_HIGH -> High
                else -> Off
            }
        }
    }
}

