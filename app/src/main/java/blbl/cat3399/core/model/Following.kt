package blbl.cat3399.core.model

data class Following(
    val mid: Long,
    val name: String,
    val avatarUrl: String?,
    val sign: String? = null,
    val isLive: Boolean = false,
    val liveRoomId: Long = 0L,
)
