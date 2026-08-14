package blbl.cat3399.feature.live

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.model.LiveAreaParent
import blbl.cat3399.core.ui.cloneInUserScale
import blbl.cat3399.databinding.ItemLiveAreaBinding

class LiveAreaAdapter(
    private val onClick: (position: Int, area: LiveAreaParent.Child) -> Unit,
) : RecyclerView.Adapter<LiveAreaAdapter.Vh>() {
    private val items = ArrayList<LiveAreaParent.Child>()

    init {
        setHasStableIds(true)
    }

    fun invalidateSizing() {
        if (itemCount <= 0) return
        notifyItemRangeChanged(0, itemCount)
    }

    fun submit(list: List<LiveAreaParent.Child>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = items[position].id.toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding =
            ItemLiveAreaBinding.inflate(
                LayoutInflater.from(parent.context).cloneInUserScale(parent.context),
                parent,
                false,
            )
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) = holder.bind(items[position], onClick)

    override fun getItemCount(): Int = items.size

    class Vh(private val binding: ItemLiveAreaBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: LiveAreaParent.Child, onClick: (position: Int, area: LiveAreaParent.Child) -> Unit) {
            binding.tvTitle.text = item.name
            binding.tvBadge.visibility = if (item.hot) View.VISIBLE else View.GONE
            ImageLoader.loadInto(binding.ivCover, ImageUrl.cover(item.coverUrl))
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnClickListener
                onClick(pos, item)
            }
        }
    }
}
