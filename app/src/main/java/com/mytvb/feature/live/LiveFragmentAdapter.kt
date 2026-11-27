package com.mytvb.feature.live

import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.viewpager2.adapter.FragmentStateAdapter
import com.mytvb.core.common.log.AppLog
import com.mytvb.model.live.LiveAreaCategoryParent

class LiveFragmentAdapter(
    fragmentManager: FragmentManager,
    lifecycle: Lifecycle
) : FragmentStateAdapter(fragmentManager, lifecycle) {

    private val categories = mutableListOf<LiveAreaCategoryParent>()
    private val fragments = mutableMapOf<Long, LiveTabPage>()

    fun setCategories(list: List<LiveAreaCategoryParent>) {
        val diffResult = DiffUtil.calculateDiff(LiveFragmentDiff(categories, list))
        categories.clear()
        categories.addAll(list)
        val liveItemIds = categories.indices.mapTo(mutableSetOf()) { position -> itemIdFor(position) }
        fragments.keys.retainAll(liveItemIds)
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemCount(): Int = categories.size

    override fun getItemId(position: Int): Long {
        return itemIdFor(position)
    }

    override fun containsItem(itemId: Long): Boolean {
        return categories.indices.any { position -> itemIdFor(position) == itemId }
    }

    override fun createFragment(position: Int): Fragment {
        val category = categories[position]
        AppLog.d("LivePerf", "LiveFragmentAdapter.createFragment: position=$position, name=${category.name}")
        val fragment: Fragment = if (position == 0) {
            LiveRecommendFragment.newInstance()
        } else {
            LiveAreaFragment.newInstance(category)
        }
        if (fragment is LiveTabPage) {
            fragments[itemIdFor(position)] = fragment
        }
        return fragment
    }

    fun getPageTitle(position: Int): CharSequence? {
        return if (position < categories.size) categories[position].name else null
    }

    fun getCurrentFragment(position: Int): LiveTabPage? =
        if (position in categories.indices) fragments[itemIdFor(position)] else null

    private fun itemIdFor(position: Int): Long {
        val category = categories[position]
        return if (position == 0) Long.MIN_VALUE else category.id
    }

    private class LiveFragmentDiff(
        private val oldList: List<LiveAreaCategoryParent>,
        private val newList: List<LiveAreaCategoryParent>
    ) : DiffUtil.Callback() {

        override fun getOldListSize(): Int = oldList.size

        override fun getNewListSize(): Int = newList.size

        override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition].id == newList[newItemPosition].id
        }

        override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
            return oldList[oldItemPosition] == newList[newItemPosition]
        }
    }
}
