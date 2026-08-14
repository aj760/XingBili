package blbl.cat3399.feature.player

import blbl.cat3399.core.ui.focus.FocusHintOverlay

/**
 * Attaches D-Pad focus hints to the main OSD buttons of [PlayerActivity].
 *
 * Called once from [PlayerActivity.initControls] (we attach the listener once; visibility is
 * already managed by [PlayerActivity.applyOsdButtonsVisibility] — the listener is a no-op when
 * the view is gone or unfocused).
 *
 * The hint bubbles themselves are gated by `AppPrefs.focusHintEnabled` inside
 * [FocusHintOverlay], so toggling the Settings switch takes effect immediately without
 * re-attaching anything.
 */
internal fun PlayerActivity.installFocusHints() {
    FocusHintOverlay.from(this).attachAll(
        linkedMapOf(
            binding.btnBack to "返回",
            binding.btnPrev to "上一个",
            binding.btnPlayPause to "播放/暂停",
            binding.btnNext to "下一个",
            binding.btnSubtitle to "字幕",
            binding.btnDanmaku to "弹幕开关",
            binding.btnComments to "评论区",
            binding.btnDetail to "视频详情",
            binding.btnUp to "UP 主",
            binding.btnLike to "点赞（长按＝三连）",
            binding.btnCoin to "投币",
            binding.btnFav to "收藏",
            binding.btnListPanel to "播放列表",
            binding.btnSponsorSubmit to "提交赞助",
            binding.btnAdvanced to "高级选项",
        ),
    )
}
