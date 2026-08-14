package blbl.cat3399.feature.my

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.R
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.image.ImageUrl
import blbl.cat3399.core.model.FavFolder
import blbl.cat3399.core.ui.cloneInUserScale
import blbl.cat3399.databinding.ItemFavFolderBinding

class FavFolderAdapter(
    private val onClick: (position: Int, folder: FavFolder) -> Unit,
) : RecyclerView.Adapter<FavFolderAdapter.Vh>() {
    private val items = ArrayList<FavFolder>()

    init {
        setHasStableIds(true)
    }

    fun invalidateSizing() {
        if (itemCount <= 0) return
        notifyItemRangeChanged(0, itemCount)
    }

    fun submit(list: List<FavFolder>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = items[position].mediaId

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding =
            ItemFavFolderBinding.inflate(
                LayoutInflater.from(parent.context).cloneInUserScale(parent.context),
                parent,
                false,
            )
        return Vh(binding)
    }

    override fun onBindViewHolder(holder: Vh, position: Int) = holder.bind(items[position], onClick)

    override fun getItemCount(): Int = items.size

    class Vh(private val binding: ItemFavFolderBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(item: FavFolder, onClick: (position: Int, folder: FavFolder) -> Unit) {
            binding.tvTitle.text = item.title
            binding.tvCount.text = binding.root.context.getString(R.string.my_fav_item_count_fmt, item.mediaCount)
            ImageLoader.loadInto(binding.ivCover, ImageUrl.cover(item.coverUrl))
            binding.root.setOnClickListener {
                val pos = bindingAdapterPosition.takeIf { it != RecyclerView.NO_POSITION } ?: return@setOnClickListener
                onClick(pos, item)
            }
        }
    }
}
