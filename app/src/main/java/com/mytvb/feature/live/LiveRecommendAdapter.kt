package com.mytvb.feature.live

import android.view.LayoutInflater
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mytvb.databinding.CellLaneScrollableBinding
import com.mytvb.model.live.LiveRecommendSection
import com.mytvb.model.live.LiveRoomItem
import com.mytvb.core.ui.base.adaptiveSpanCount
import com.mytvb.core.ui.layout.WrapContentGridLayoutManager

class LiveRecommendAdapter(
    private val onRoomClick: (LiveRoomItem) -> Unit,
    private val onTopEdgeUp: () -> Boolean = { false },
    private val onLeftEdge: () -> Boolean = { false }
) : RecyclerView.Adapter<LiveRecommendAdapter.ViewHolder>() {

    private val items = ArrayList<LiveRecommendSection>()
    private val sharedRoomViewPool = RecyclerView.RecycledViewPool()

    val currentList: List<LiveRecommendSection>
        get() = items

    init {
        setHasStableIds(true)
    }

    fun setData(list: List<LiveRecommendSection>, onCommitted: (() -> Unit)? = null) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
        onCommitted?.invoke()
    }

    fun addData(list: List<LiveRecommendSection>) {
        if (list.isEmpty()) return
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CellLaneScrollableBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding, onRoomClick, onTopEdgeUp, onLeftEdge, sharedRoomViewPool)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.title?.hashCode()?.toLong() ?: RecyclerView.NO_ID

    class ViewHolder(
        private val binding: CellLaneScrollableBinding,
        onRoomClick: (LiveRoomItem) -> Unit,
        private val onTopEdgeUp: () -> Boolean,
        private val onLeftEdge: () -> Boolean,
        sharedViewPool: RecyclerView.RecycledViewPool
    ) : RecyclerView.ViewHolder(binding.root) {

        // 推荐流开放「不想看该分区」；关注模块同样生效——不想看的分区在哪都不该再出现
        private val roomAdapter = LiveRoomAdapter(onRoomClick, allowAreaDislike = true)

        init {
            // 列数须与 LiveRecommendFragment.applySections 的行高估算保持同一 adaptiveSpanCount 判定
            binding.recyclerView.layoutManager = object : WrapContentGridLayoutManager(
                binding.root.context,
                binding.root.context.resources.adaptiveSpanCount()
            ) {
                override fun canScrollVertically(): Boolean = false
            }
            binding.recyclerView.adapter = roomAdapter
            binding.recyclerView.setRecycledViewPool(sharedViewPool)
            sharedViewPool.setMaxRecycledViews(roomAdapter.getItemViewType(0), 24)
            binding.recyclerView.isNestedScrollingEnabled = false
            binding.recyclerView.setHasFixedSize(true)
            binding.recyclerView.itemAnimator = null
            binding.topTitle.setOnKeyListener { _, keyCode, event ->
                if (event.action != KeyEvent.ACTION_DOWN) {
                    return@setOnKeyListener false
                }
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_UP -> bindingAdapterPosition == 0 && onTopEdgeUp()
                    KeyEvent.KEYCODE_DPAD_LEFT -> onLeftEdge()
                    else -> false
                }
            }
        }

        fun bind(item: LiveRecommendSection) {
            binding.topTitle.text = item.title
            roomAdapter.setData(item.rooms)
        }

        fun requestPrimaryFocus(): Boolean {
            return binding.topTitle.requestFocus()
        }

        fun focusRoomAt(roomIndex: Int): Boolean {
            val innerRv = binding.recyclerView
            val holder = innerRv.findViewHolderForAdapterPosition(roomIndex)
            if (holder != null && holder.itemView.isAttachedToWindow) {
                return holder.itemView.requestFocus()
            }
            innerRv.scrollToPosition(roomIndex)
            innerRv.post {
                innerRv.findViewHolderForAdapterPosition(roomIndex)?.itemView?.requestFocus()
            }
            return true
        }

        fun findRoomPositionByRoomId(roomId: Long): Int {
            return roomAdapter.currentList.indexOfFirst { it.roomId == roomId }
                .takeIf { it >= 0 } ?: RecyclerView.NO_POSITION
        }
    }
}
