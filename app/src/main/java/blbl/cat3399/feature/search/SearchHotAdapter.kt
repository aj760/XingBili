package blbl.cat3399.feature.search

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import blbl.cat3399.core.ui.cloneInUserScale
import blbl.cat3399.databinding.ItemSearchHotBinding

class SearchHotAdapter(
    private val onClick: (String) -> Unit,
) : RecyclerView.Adapter<SearchHotAdapter.Vh>() {
    private val items = ArrayList<String>()

    init {
        setHasStableIds(true)
    }

    fun invalidateSizing() {
        if (itemCount <= 0) return
        notifyItemRangeChanged(0, itemCount)
    }

    fun submit(list: List<String>) {
        items.clear()
        items.addAll(list)
        notifyDataSetChanged()
    }

    override fun getItemId(position: Int): Long = items[position].hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Vh {
        val binding =
            ItemSearchHotBinding.inflate(
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

    class Vh(private val binding: ItemSearchHotBinding) : RecyclerView.ViewHolder(binding.root) {
        fun bind(keyword: String, onClick: (String) -> Unit) {
            binding.tvKeyword.text = keyword
            binding.root.setOnClickListener { onClick(keyword) }
        }
    }
}
