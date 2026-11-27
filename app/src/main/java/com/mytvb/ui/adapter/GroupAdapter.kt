package com.mytvb.ui.adapter

import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.mytvb.R
import com.mytvb.feature.marmot.domain.MarmotModels.Live

/**
 * 分组列表适配器（双排左侧，严格对标参考 GroupDualAdapter）。
 *
 * OnKey 逻辑（与参考完全一致）：
 * - 只在 ACTION_DOWN 处理，ACTION_UP 返回 false
 * - 上键 且在第一项 → 跳到最后（环形），return true
 * - 下键 且在最后项 → 跳到第一（环形），return true
 * - 右键 → 焦点切到右排第一项，return true
 * - 其他 → return false（交给系统，正常上下移动）
 */
class GroupAdapter(
    private val onGroupClick: (Int, Live) -> Unit
) : ListAdapter<Live, GroupAdapter.VH>(DIFF) {

    var currentSelected: Int = 0
        set(value) {
            if (field == value) return
            // 只刷新新旧两项的高亮，不 notifyDataSetChanged（否则重建 View 导致焦点丢失）
            val old = field
            field = value
            notifyItemChanged(old)
            notifyItemChanged(value)
        }

    var channelRecyclerView: RecyclerView? = null

    inner class VH(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val name: TextView = itemView.findViewById(R.id.group_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_marmot_group, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        holder.name.text = item.name
        holder.itemView.isSelected = (position == currentSelected)

        holder.itemView.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) currentSelected = holder.bindingAdapterPosition
        }

        holder.itemView.setOnClickListener {
            val pos = holder.bindingAdapterPosition
            if (pos != RecyclerView.NO_POSITION) onGroupClick(pos, getItem(pos))
        }

        holder.itemView.setOnKeyListener { v, keyCode, event ->
            val pos = holder.bindingAdapterPosition
            // 严格对标参考：只 ACTION_DOWN 处理，否则返回 false
            if (event.action != KeyEvent.ACTION_DOWN || pos == RecyclerView.NO_POSITION) {
                return@setOnKeyListener false
            }
            when (keyCode) {
                // 上键在第一项 → 跳到最后（环形）
                KeyEvent.KEYCODE_DPAD_UP -> {
                    if (pos == 0) {
                        scrollToPositionAndFocus(holder.itemView, itemCount - 1)
                        return@setOnKeyListener true
                    }
                }
                // 下键在最后项 → 跳到第一（环形）
                KeyEvent.KEYCODE_DPAD_DOWN -> {
                    if (pos == itemCount - 1) {
                        scrollToPositionAndFocus(holder.itemView, 0)
                        return@setOnKeyListener true
                    }
                }
                // 右键 → 焦点切到右排第一项
                KeyEvent.KEYCODE_DPAD_RIGHT -> {
                    channelRecyclerView?.post {
                        channelRecyclerView?.layoutManager?.findViewByPosition(0)?.requestFocus()
                    }
                    return@setOnKeyListener true
                }
            }
            // 正常上下移动：交给系统
            false
        }
    }

    private fun scrollToPositionAndFocus(itemView: View, pos: Int) {
        val rv = itemView.parent as? RecyclerView ?: return
        rv.smoothScrollToPosition(pos)
        rv.post { rv.layoutManager?.findViewByPosition(pos)?.requestFocus() }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Live>() {
            override fun areItemsTheSame(o: Live, n: Live) = o.tag == n.tag
            override fun areContentsTheSame(o: Live, n: Live) = o.name == n.name && o.tag == n.tag
        }
    }
}
