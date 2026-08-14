package blbl.cat3399.feature.player.danmaku

import android.graphics.Typeface
import blbl.cat3399.core.prefs.AppPrefs

enum class DanmakuLaneDensity(
    val prefValue: String,
    val laneHeightFactor: Float,
) {
    Sparse(AppPrefs.DANMAKU_LANE_DENSITY_SPARSE, 1.25f),
    Standard(AppPrefs.DANMAKU_LANE_DENSITY_STANDARD, 1.0f),
    Dense(AppPrefs.DANMAKU_LANE_DENSITY_DENSE, 0.85f),
    ;

    companion object {
        fun fromPrefValue(value: String): DanmakuLaneDensity =
            when (value.trim()) {
                AppPrefs.DANMAKU_LANE_DENSITY_SPARSE -> Sparse
                AppPrefs.DANMAKU_LANE_DENSITY_DENSE -> Dense
                else -> Standard
            }
    }
}

enum class DanmakuFontWeight(
    val prefValue: String,
    val typeface: Typeface,
) {
    Normal(AppPrefs.DANMAKU_FONT_WEIGHT_NORMAL, Typeface.DEFAULT),
    Bold(AppPrefs.DANMAKU_FONT_WEIGHT_BOLD, Typeface.DEFAULT_BOLD),
    ;

    companion object {
        fun fromPrefValue(value: String): DanmakuFontWeight =
            when (value.trim()) {
                AppPrefs.DANMAKU_FONT_WEIGHT_NORMAL -> Normal
                else -> Bold
            }
    }
}

data class DanmakuConfig(
    val enabled: Boolean,
    val opacity: Float,
    val textSizeSp: Float,
    val fontWeight: DanmakuFontWeight,
    val strokeWidthPx: Int,
    val speedLevel: Int,
    val area: Float,
    val laneDensity: DanmakuLaneDensity,
    val showHighLikeIcon: Boolean,
)

data class DanmakuSessionSettings(
    val enabled: Boolean,
    val opacity: Float,
    val textSizeSp: Float,
    val fontWeight: DanmakuFontWeight,
    val strokeWidthPx: Int,
    val speedLevel: Int,
    val area: Float,
    val laneDensity: DanmakuLaneDensity,
    val followBiliShield: Boolean = true,
    val showHighLikeIcon: Boolean = true,
    val aiShieldEnabled: Boolean = false,
    val aiShieldLevel: Int = 3,
    val allowScroll: Boolean = true,
    val allowTop: Boolean = true,
    val allowBottom: Boolean = true,
    val allowColor: Boolean = true,
    val allowSpecial: Boolean = true,
) {
    fun toConfig(): DanmakuConfig =
        DanmakuConfig(
            enabled = enabled,
            opacity = opacity,
            textSizeSp = textSizeSp,
            fontWeight = fontWeight,
            strokeWidthPx = strokeWidthPx,
            speedLevel = speedLevel,
            area = area,
            laneDensity = laneDensity,
            showHighLikeIcon = showHighLikeIcon,
        )
}
