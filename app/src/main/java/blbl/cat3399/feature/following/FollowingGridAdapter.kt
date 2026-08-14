package blbl.cat3399.feature.following

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.view.isVisible
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.model.Following
import blbl.cat3399.core.ui.cloneInUserScale
import blbl.cat3399.databinding.ItemFollowingGridBinding

class FollowingGridAdapter(
    private val onClick: (Following) -> Unit,
) : RecyclerView.Adapter<FollowingGridAdapter.Vh>() {
    private val items = ArrayList<Following>()

    init {
        setHasStableIds(true)
    }

    fun invalidateSizing() {
        if (itemCount <= 0) return
        notifyItemRangeChanged(0, itemCount)
    }

    fun submit(list: List<Following>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    fun append(list: List<Following>) {
        if (list.isEmpty()) return
        val start = items.size
        items.addAll(list)
        notifyItemRangeInserted(start, list.size)
    }

    override fun getItemId(position: Int): Long = items[position].mid

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding =
            ItemFollowingGridBinding.inflate(
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

    class Vh(private val binding: ItemFollowingGridBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: Following, onClick: (Following) -> Unit) {
            binding.tvName.text = item.name
            binding.tvSign.text = item.sign.orEmpty()
            binding.tvSign.isVisible = !item.sign.isNullOrBlank()
            binding.tvBadgeLive.isVisible = item.isLive

            ImageLoader.loadInto(binding.ivAvatar, ImageUrl.avatar(item.avatarUrl))
            binding.root.setOnClickListener { onClick(item) }
        }
    }
}
