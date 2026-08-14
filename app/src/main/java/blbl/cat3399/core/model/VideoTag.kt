package blbl.cat3399.core.model

data class VideoTag(
    val tagId: Long,
    val tagName: String,
    val tagType: String? = null,
    val jumpUrl: String? = null,
    val musicId: String? = null,
) {
    fun stableKey(): String =
        when {
            tagId > 0L -> "tag:$tagId"
            tagName.isNotBlank() -> "name:${tagName.hashCode()}"
            else -> "unknown"
        }
}

