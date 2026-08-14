package blbl.cat3399.core.model

data class Danmaku(
    val timeMs: Int,
    val mode: Int,
    val text: String,
    val color: Int,
    val fontSize: Int,
    val weight: Int,
    val midHash: String? = null,
    val dmid: Long? = null,
    val attr: Int = 0,
)

val Danmaku.isHighLiked: Boolean
    get() = (attr and (1 shl 2)) != 0
