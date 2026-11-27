package com.mytvb.feature.player.danmaku

/**
 * 直播 SC（醒目留言）信号：SUPER_CHAT_MESSAGE / SUPER_CHAT_MESSAGE_DELETE 的解析产物。
 * SC 与普通弹幕的消费形态不同（左侧卡片队列而非弹幕轨道），走独立 flow，不进 DmModel。
 */
sealed class LiveSuperChatSignal {

    /** 一条新 SC 上屏。durationSec 为剩余展示秒数（官方按金额 60~600s 不等）。 */
    data class New(
        val id: Long,
        val userName: String,
        val avatarUrl: String,
        val message: String,
        val priceYuan: Long,
        val backgroundColor: Int,
        val durationSec: Long,
        val avatarFrameUrl: String = ""
    ) : LiveSuperChatSignal()

    /** SC 被删除（UP/房管撤下），提前移除对应卡片。 */
    data class Delete(val id: Long) : LiveSuperChatSignal()
}
