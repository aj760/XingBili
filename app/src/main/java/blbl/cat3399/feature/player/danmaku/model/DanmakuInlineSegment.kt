package blbl.cat3399.feature.player.danmaku.model

internal sealed interface DanmakuInlineSegment {
    data class Text(
        val start: Int,
        val end: Int,
    ) : DanmakuInlineSegment

    data class Emote(
        val url: String,
    ) : DanmakuInlineSegment

    data object HighLikeIcon : DanmakuInlineSegment
}
