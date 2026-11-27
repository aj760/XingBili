package com.mytvb.feature.live

import android.annotation.SuppressLint
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mytvb.core.common.log.VideoCardPerfLogger
import com.mytvb.core.ui.image.ImageLoader
import com.mytvb.core.common.format.NumberUtils
import com.mytvb.core.ui.focus.VideoCardFocusHelper
import com.mytvb.core.ui.focus.tv.TvFocusableAdapter
import com.mytvb.core.ui.video.VideoCardViews
import com.mytvb.core.ui.video.VideoLightCardFactory
import com.mytvb.model.live.LiveRoomItem
import com.mytvb.model.video.Owner
import com.mytvb.model.video.VideoModel
import com.mytvb.ui.adapter.VideoAdapter
import com.mytvb.ui.dialog.VideoCardMenuDialog
import java.util.concurrent.atomic.AtomicInteger

class LiveRoomAdapter(
    private val onItemClick: (LiveRoomItem) -> Unit,
    private val onTopEdgeUp: (() -> Boolean)? = null,
    private val onDpadKey: ((View, Int, KeyEvent) -> Boolean)? = null,
    // 直播推荐流传 true，卡片菜单显示「不想看该分区」；分区详情页不提供（会清空用户正在看的列表）
    private val allowAreaDislike: Boolean = false
) : RecyclerView.Adapter<LiveRoomAdapter.ViewHolder>(), TvFocusableAdapter {

    private val items = ArrayList<LiveRoomItem>()
    private val contentViewType = nextViewType.getAndIncrement()

    val currentList: List<LiveRoomItem>
        get() = items

    init {
        setHasStableIds(true)
    }

    override fun focusableItemCount(): Int = items.size

    override fun stableKeyAt(position: Int): String? = items.getOrNull(position)?.roomId?.toString()

    override fun findPositionByStableKey(key: String): Int =
        items.indexOfFirst { it.roomId.toString() == key }
            .takeIf { it >= 0 } ?: RecyclerView.NO_POSITION

    fun setData(list: List<LiveRoomItem>, onCommitted: (() -> Unit)? = null) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
        onCommitted?.invoke()
    }

    fun addData(list: List<LiveRoomItem>) {
        if (list.isEmpty()) return
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long = items.getOrNull(position)?.roomId ?: RecyclerView.NO_ID

    override fun getItemViewType(position: Int): Int = contentViewType

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val views = VideoCardPerfLogger.measureInflate("LiveRoomAdapter.light") {
            VideoLightCardFactory.create(parent, source = "LiveRoomAdapter.light")
        }
        return ViewHolder(views)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    private fun removeItemsByAnchorName(blockedName: String) {
        val filtered = items.filter { !it.uname.equals(blockedName, ignoreCase = true) }
        if (filtered.size == items.size) return
        setData(filtered)
    }

    private fun removeRoomsByArea(blockedAreaName: String) {
        val filtered = items.filter { room ->
            val area = room.areaV2Name.ifEmpty { room.areaName }
            !area.equals(blockedAreaName, ignoreCase = true)
        }
        if (filtered.size == items.size) return
        setData(filtered)
    }

    private fun removeRoom(roomId: Long) {
        val index = items.indexOfFirst { it.roomId == roomId }
        if (index < 0) return
        items.removeAt(index)
        notifyItemRemoved(index)
    }

    inner class ViewHolder(
        private val views: VideoCardViews
    ) : RecyclerView.ViewHolder(views.root) {

        private var currentItem: LiveRoomItem? = null
        private val handler = Handler(Looper.getMainLooper())
        private var longPressRunnable: Runnable? = null
        private var longPressTriggered = false

        private val keyListener = View.OnKeyListener { view, keyCode, event ->
            if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER || keyCode == KeyEvent.KEYCODE_ENTER) {
                when (event.action) {
                    KeyEvent.ACTION_DOWN -> {
                        if (event.repeatCount == 0) {
                            startLongPressTimer()
                        }
                    }
                    KeyEvent.ACTION_UP -> {
                        cancelLongPressTimer()
                    }
                }
            }
            // 把 DPAD 方向键交给网格焦点控制器（GridTvFocusStrategy），
            // 保证严格按“下一行同列”跳格，避免退化为 FocusFinder 的空间查找导致跳行不稳定。
            onDpadKey?.invoke(view, keyCode, event) ?: false
        }

        private fun startLongPressTimer() {
            cancelLongPressTimer()
            longPressTriggered = false
            longPressRunnable = Runnable {
                val item = currentItem ?: return@Runnable
                longPressTriggered = true
                VideoCardMenuDialog(
                    context = itemView.context,
                    video = item.toFeedbackVideoModel(),
                    onDislikeVideo = {
                        removeRoom(item.roomId)
                    },
                    onDislikeUp = { upName ->
                        removeItemsByAnchorName(upName)
                    },
                    allowAreaDislike = allowAreaDislike,
                    onDislikeArea = { areaName ->
                        removeRoomsByArea(areaName)
                    }
                ).show()
            }
            // 与普通视频卡片(BaseVideoViewHolder)一致使用系统长按阈值，
            // 旧值 5s 远超用户耐心，等于弹窗不可发现
            handler.postDelayed(
                longPressRunnable!!,
                ViewConfiguration.getLongPressTimeout().toLong()
            )
        }

        private fun cancelLongPressTimer() {
            longPressRunnable?.let { handler.removeCallbacks(it) }
            longPressRunnable = null
        }

        init {
            views.imageView.clipToOutline = true
            views.imageView.outlineProvider = VideoAdapter.VideoViewHolder.coverOutlineProviderFor(views.imageView.resources)
            views.root.setOnClickListener {
                if (longPressTriggered) {
                    longPressTriggered = false
                    return@setOnClickListener
                }
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    items.getOrNull(position)?.let(onItemClick)
                }
            }
            @SuppressLint("ClickableViewAccessibility")
            views.root.setOnTouchListener { _, event ->
                when (event.action) {
                    MotionEvent.ACTION_DOWN -> startLongPressTimer()
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> cancelLongPressTimer()
                }
                false
            }
            VideoCardFocusHelper.bindSidebarExit(
                view = views.root,
                onTopEdgeUp = onTopEdgeUp,
                handleListDpadDown = false,
                chainedListener = keyListener
            )
        }

        fun bind(item: LiveRoomItem) {
            currentItem = item
            views.textLayer.setTitle(item.title, lines = 1)
            views.textLayer.setOwner(
                ownerText = item.uname,
                showAvatar = item.uname.isNotBlank(),
                show = item.uname.isNotBlank()
            )
            views.textLayer.clearHistoryTrailing()
            views.progressBar.visibility = View.GONE
            views.coverMetaOverlay.bind(
                playCountText = NumberUtils.formatCount(itemView.context, item.online.toLong()),
                showPlayCount = true,
                showDanmakuCount = false,
                durationText = "",
                showChargeBadge = false,
                showInteractionBadge = false
            )

            ImageLoader.loadVideoCover(
                imageView = views.imageView,
                url = item.cover,
                deferUntilPreDraw = true
            )
        }
    }

    private fun LiveRoomItem.toFeedbackVideoModel(): VideoModel {
        return VideoModel(
            title = title,
            cover = cover,
            goto = "live",
            roomId = roomId,
            // 分区名填入 typeName 供弹窗「不想看该分区」显示；二级分区优先，旧体系 areaName 兜底
            typeName = areaV2Name.ifEmpty { areaName },
            owner = Owner(
                mid = uid,
                name = uname,
                face = face
            )
        )
    }

    companion object {
        private val nextViewType = AtomicInteger(0x5B0100)
    }
}
