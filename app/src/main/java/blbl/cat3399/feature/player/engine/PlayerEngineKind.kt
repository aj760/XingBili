package blbl.cat3399.feature.player.engine

import blbl.cat3399.core.prefs.AppPrefs

internal enum class PlayerEngineKind(
    val prefValue: String,
) {
    ExoPlayer(AppPrefs.PLAYER_ENGINE_EXO),
    IjkPlayer(AppPrefs.PLAYER_ENGINE_IJK),
    ;

    companion object {
        fun fromPrefValue(value: String): PlayerEngineKind {
            return when (value.trim()) {
                AppPrefs.PLAYER_ENGINE_IJK -> IjkPlayer
                else -> ExoPlayer
            }
        }
    }
}
