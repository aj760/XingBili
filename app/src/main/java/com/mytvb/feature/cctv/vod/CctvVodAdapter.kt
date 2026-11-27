package com.mytvb.feature.cctv.vod

import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mytvb.R
import com.mytvb.core.common.log.VideoCardPerfLogger
import com.mytvb.core.ui.base.BaseVideoAdapter
import com.mytvb.core.ui.focus.VideoCardFocusHelper
import com.mytvb.core.ui.image.ImageLoader
import com.mytvb.core.ui.video.VideoCardCoverRatio
import com.mytvb.core.ui.video.VideoCardViews
import com.mytvb.core.ui.video.VideoLightCardFactory
import com.mytvb.ui.adapter.VideoAdapter

/**
 * 央视片库卡片列表（复用全 App 统一卡片：封面 + 标题 + 副标题）。
 *
 * 片库素材是竖版海报（央视片库页 178×226），故封面走 [VideoCardCoverRatio.POSTER_4_5]：
 * 16:9 横版会把海报上下主体裁掉，卡片观感也与央视片库页对不上。
 */
class CctvVodAdapter(
    private val onItemClick: (CctvAlbum) -> Unit
) : BaseVideoAdapter<CctvAlbum, CctvVodAdapter.ViewHolder>() {

    override fun itemKey(item: CctvAlbum): String = item.id

    /** 卡片顶行再往上时的落点（回到筛选行）。 */
    fun bindTopEdgeUp(callback: () -> Boolean) {
        onTopEdgeUp = callback
    }

    /** 焦点落到卡片时的回调，交由 [com.mytvb.core.ui.focus.tv.TvListFocusController] 记锚点/触发加载更多。 */
    fun setOnItemFocusListeners(
        onFocused: (Int) -> Unit,
        onFocusedWithView: (View, Int) -> Unit
    ) {
        onItemFocused = onFocused
        onItemFocusedWithView = onFocusedWithView
    }

    override fun onCreateContentViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val views = VideoCardPerfLogger.measureInflate("CctvVodAdapter.light") {
            VideoLightCardFactory.create(
                parent = parent,
                source = "CctvVodAdapter.light",
                coverRatio = VideoCardCoverRatio.POSTER_4_5
            )
        }
        return ViewHolder(views)
    }

    override fun onBindContentViewHolder(holder: ViewHolder, position: Int) {
        getItem(position)?.let(holder::bind)
    }

    inner class ViewHolder(
        private val views: VideoCardViews
    ) : RecyclerView.ViewHolder(views.root) {

        init {
            views.imageView.clipToOutline = true
            views.imageView.outlineProvider =
                VideoAdapter.VideoViewHolder.coverOutlineProviderFor(views.imageView.resources)
            views.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    getItem(position)?.let(onItemClick)
                }
            }
            views.root.onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
                if (!hasFocus) return@OnFocusChangeListener
                val position = bindingAdapterPosition
                if (position == RecyclerView.NO_POSITION) return@OnFocusChangeListener
                onItemFocused?.invoke(position)
                onItemFocusedWithView?.invoke(view, position)
            }
            VideoCardFocusHelper.bindSidebarExit(
                view = views.root,
                onTopEdgeUp = { onTopEdgeUp?.invoke() == true }
            )        }

        fun bind(album: CctvAlbum) {
            // 与央视片库页一致：标题单行（超出省略），副标题一行主演
            views.textLayer.setTitle(album.title, lines = 1)
            views.textLayer.setOwner(
                ownerText = buildSubtitle(album),
                showAvatar = false,
                show = true
            )
            views.textLayer.clearHistoryTrailing()
            views.progressBar.visibility = View.GONE
            views.coverMetaOverlay.visibility = View.GONE
            views.imageView.clearColorFilter()
            ImageLoader.load(
                imageView = views.imageView,
                url = album.coverUrl,
                placeholder = R.drawable.default_video,
                error = R.drawable.default_video
            )
        }

        private fun buildSubtitle(album: CctvAlbum): String {
            val context = views.root.context
            // 与央视片库页一致：副标题优先展示主演（接口自带 actors 字段），
            // 没有演员信息时退回 年份·类型·集数
            if (album.actors.isNotBlank()) {
                return context.getString(R.string.cctv_vod_actors_format, album.actors)
            }
            val episodeText = if (album.episodeCount > 0) {
                context.getString(R.string.cctv_vod_episode_count_format, album.episodeCount)
            } else {
                context.getString(R.string.cctv_vod_episode_no_count)
            }
            return listOf(album.year, album.genre, episodeText)
                .filter { it.isNotBlank() }
                .joinToString(" · ")
        }
    }
}
