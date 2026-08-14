package blbl.cat3399.feature.live

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.model.LiveRoomCard
import blbl.cat3399.core.ui.cloneInUserScale
import blbl.cat3399.core.util.Format
import blbl.cat3399.databinding.ItemLiveCardBinding

class LiveRoomAdapter(
    private val onClick: (position: Int, room: LiveRoomCard) -> Unit,
) : RecyclerView.Adapter<LiveRoomAdapter.Vh>() {
    private val items = ArrayList<LiveRoomCard>()

    init {
        setHasStableIds(true)
    }

    fun invalidateSizing() {
        if (itemCount <= 0) return
        notifyItemRangeChanged(0, itemCount)
    }

    fun submit(list: List<LiveRoomCard>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun append(list: List<LiveRoomCard>) {
        if (list.isEmpty()) return
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    override fun getItemId(position: Int): Long = items[position].roomId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding =
            ItemLiveCardBinding.inflate(
                LayoutInflater.from(parent.context).cloneInUserScale(parent.context),
                parent,
                false,
            )
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) = holder.bind(items[position], onClick)

    override fun getItemCount(): Int = items.size

    class Vh(private val binding: ItemLiveCardBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LiveRoomCard, onClick: (position: Int, room: LiveRoomCard) -> Unit) {
            binding.tvTitle.text = item.title
            binding.tvSubtitle.text =
                buildString {
                    if (item.uname.isNotBlank()) append(item.uname)
                    val area = item.areaName ?: item.parentAreaName
                    if (!area.isNullOrBlank()) {
                        if (isNotEmpty()) append(" · ")
                        append(area)
                    }
                    if (!item.isLive) {
                        if (isNotEmpty()) append(" · ")
                        append("未开播")
                    }
                }
            binding.tvOnline.text = if (item.isLive) Format.count(item.online) else "-"
            binding.tvBadge.visibility = if (item.isLive) View.VISIBLE else View.GONE
            ImageLoader.loadInto(binding.ivCover, ImageUrl.cover(item.coverUrl))

            applyStatsOverlayTranslation()

            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnClickListener
                onClick(pos, item)
            }
        }

        private fun applyStatsOverlayTranslation() {
            val res = binding.root.resources
            val textMargin = res.getDimensionPixelSize(R.dimen.video_card_text_margin)
            val padV = res.getDimensionPixelSize(R.dimen.video_card_duration_padding_v)

            fun applyShiftY() {
                val overlayH = binding.llStats.height
                val shiftY = (textMargin + padV) * 0.2f + overlayH * 0.2f
                binding.llStats.translationY = shiftY
            }

            if (binding.llStats.height > 0) {
                applyShiftY()
            } else {
                binding.root.post { applyShiftY() }
            }
        }
    }
}
