package com.mytvb.feature.live

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.mytvb.core.ui.image.ImageLoader
import com.mytvb.R
import com.mytvb.databinding.CellLiveAreaBinding
import com.mytvb.model.live.LiveAreaCategory
import com.mytvb.core.ui.focus.VideoCardFocusHelper

class LiveAreaAdapter(
    private val onItemClick: (LiveAreaCategory) -> Unit,
    private val onTopEdgeUp: (() -> Boolean)? = null
) : RecyclerView.Adapter<LiveAreaAdapter.ViewHolder>() {

    private val items = ArrayList<LiveAreaCategory>()

    init {
        setHasStableIds(true)
    }

    fun setData(list: List<LiveAreaCategory>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemCount(): Int = items.size

    override fun getItemId(position: Int): Long =
        items.getOrNull(position)?.id ?: RecyclerView.NO_ID

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val binding = CellLiveAreaBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return ViewHolder(binding)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    inner class ViewHolder(
        private val binding: CellLiveAreaBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        init {
            binding.imageView.setBorderEnabled(false)
            binding.root.setOnClickListener {
                val position = bindingAdapterPosition
                if (position != RecyclerView.NO_POSITION) {
                    onItemClick(items[position])
                }
            }
            VideoCardFocusHelper.bindSidebarExit(
                view = binding.root,
                onTopEdgeUp = onTopEdgeUp
            )
        }

        fun bind(item: LiveAreaCategory) {
            binding.root.contentDescription = item.title.ifBlank { item.name }
            ImageLoader.loadSmallSquare(
                imageView = binding.imageView,
                url = item.pic,
                placeholder = R.drawable.default_avatar
            )
        }
    }
}
