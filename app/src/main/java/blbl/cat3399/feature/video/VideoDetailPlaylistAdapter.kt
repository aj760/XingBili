package blbl.cat3399.feature.video

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.core.ui.cloneInUserScale
import blbl.cat3399.databinding.ItemVideoDetailPlaylistBinding
import blbl.cat3399.feature.player.PlayerPlaylistItem

class VideoDetailPlaylistAdapter(
    private val onClick: (item: PlayerPlaylistItem, position: Int) -> Unit,
) : RecyclerView.Adapter<VideoDetailPlaylistAdapter.Vh>() {
    private val items = ArrayList<PlayerPlaylistItem>()

    init {
        setHasStableIds(true)
    }

    fun submit(list: List<PlayerPlaylistItem>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long {
        val item = items[position]
        val key =
            buildString {
                append(item.bvid)
                append('|')
                append(item.cid ?: -1L)
                append('|')
                append(item.aid ?: -1L)
                append('|')
                append(item.title.orEmpty())
            }
        return key.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding =
            ItemVideoDetailPlaylistBinding.inflate(
                LayoutInflater.from(parent.context).cloneInUserScale(parent.context),
                parent,
                false,
            )
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) {
        holder.bind(items[position], onClick)
    }

    override fun getItemCount(): Int = items.size

    class Vh(private val binding: ItemVideoDetailPlaylistBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: PlayerPlaylistItem, onClick: (item: PlayerPlaylistItem, position: Int) -> Unit) {
            binding.btn.text = item.title?.trim().takeIf { !it.isNullOrBlank() } ?: "视频"
            binding.btn.setOnClickListener {
                val pos = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnClickListener
                onClick(item, pos)
            }
        }
    }
}
