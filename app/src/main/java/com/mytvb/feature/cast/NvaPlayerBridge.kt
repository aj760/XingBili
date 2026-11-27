package com.mytvb.feature.cast

/**
 * 投屏播放桥接：控制中枢只通过它读写播放页，不直接引用 Activity 类型。
 *
 * 所有方法都由 [NvaCastController] 投递到主线程调用，实现方无需考虑线程安全；
 * [stateSnapshot] 由每秒状态轮询在主线程调用，结果缓存后供 socket 读线程使用。
 */
interface NvaPlayerBridge {

    /** 暂停播放（含弹幕渲染同步暂停）。 */
    fun pause()

    /** 继续播放。 */
    fun resume()

    /** 停止并退出播放页。 */
    fun stopAndExit()

    /** 跳转到指定毫秒位置。 */
    fun seekTo(positionMs: Long)

    /** 设置播放倍速（仅本会话生效，不写默认倍速设置）。 */
    fun setSpeed(speed: Float)

    /** 开关弹幕显示。 */
    fun setDanmakuEnabled(enabled: Boolean)

    /**
     * 切换清晰度：从当前 playurl 下发档位里选目标（精确匹配，无则取不高于目标的
     * 最高档，都无则忽略），超权限档位由 ViewModel 的降档链路兜底。
     */
    fun selectQuality(qn: Int)

    /** 读取当前播放状态快照。 */
    fun stateSnapshot(): NvaPlayerSnapshot
}

/** 播放状态快照（秒 + NVA 状态码），由播放页在主线程产出。 */
data class NvaPlayerSnapshot(
    val durationSec: Long = 0L,
    val positionSec: Long = 0L,
    /** NVA 约定：3 加载中 / 4 播放中 / 5 暂停 / 6 结束 / 7 停止。 */
    val playState: Int = 7,
    /** 当前清晰度 qn（0=未知/未就绪）。 */
    val currentQn: Int = 0,
    /**
     * 可选清晰度列表：手机端投屏面板的"分辨率"菜单内容完全来自 TV 端
     * OnQnSwitch 回传里的 supportQnList，缺失时菜单一直停在"自动分辨率"。
     */
    val supportQnList: List<NvaQnOption> = emptyList(),
)

/** NVA supportQnList 单项（对齐官方 tv/playurl 的 accept_description 结构）。 */
data class NvaQnOption(
    val quality: Int,
    val displayDesc: String,
    val description: String,
    val needVip: Boolean = false,
    val needLogin: Boolean = false,
    val superscript: String = "",
)
