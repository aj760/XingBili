package com.mytvb.feature.live

import com.mytvb.core.common.json.GsonHolder
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.os.Bundle
import androidx.core.os.bundleOf
import com.mytvb.R
import com.mytvb.databinding.FragmentLiveBaseListBinding
import com.mytvb.model.live.LiveAreaCategory
import com.mytvb.model.live.LiveAreaCategoryParent
import com.mytvb.core.ui.base.BaseFragment
import com.mytvb.core.ui.base.RecyclerViewFocusRestoreHelper
import com.mytvb.core.common.ext.toast
import com.mytvb.network.session.NetworkSessionGateway
import com.mytvb.feature.settings.SignInFragment
import com.mytvb.core.ui.layout.WrapContentGridLayoutManager
import com.mytvb.core.ui.focus.SpatialFocusNavigator
import com.mytvb.core.ui.focus.TabContentFocusHelper
import org.koin.android.ext.android.inject
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class LiveAreaFragment : BaseFragment<FragmentLiveBaseListBinding>(), LiveTabPage {

    companion object {
        private const val ARG_CATEGORY_JSON = "category_json"

        private val gson = GsonHolder.DEFAULT

        fun newInstance(category: LiveAreaCategoryParent): LiveAreaFragment {
            val json = gson.toJson(category)
            return LiveAreaFragment().apply {
                arguments = bundleOf(ARG_CATEGORY_JSON to json)
            }
        }
    }

    private lateinit var adapter: LiveAreaAdapter
    private val sessionGateway: NetworkSessionGateway by inject()
    private var areaList: List<LiveAreaCategory> = emptyList()

    override fun initArguments() {
        arguments?.let { args ->
            val json = args.getString(ARG_CATEGORY_JSON).orEmpty()
            if (json.isNotBlank()) {
                val type = object : TypeToken<LiveAreaCategoryParent>() {}.type
                val category = gson.fromJson<LiveAreaCategoryParent>(json, type)
                areaList = category?.areaList.orEmpty()
            }
        }
    }

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentLiveBaseListBinding {
        return FragmentLiveBaseListBinding.inflate(inflater, container ?: android.widget.FrameLayout(inflater.context))
    }

    override fun initView() {
        adapter = LiveAreaAdapter(
            onItemClick = ::openAreaDetail,
            onTopEdgeUp = ::focusTopTab
        )
        binding.recyclerView.layoutManager = WrapContentGridLayoutManager(requireContext(), 8)
        binding.recyclerView.adapter = adapter
        binding.recyclerView.itemAnimator = null
        binding.recyclerView.setHasFixedSize(true)
    }

    override fun initData() {
        adapter.setData(areaList)
    }

    override fun scrollToTop() {
        binding.recyclerView.smoothScrollToPosition(0)
    }

    override fun focusPrimaryContent(): Boolean {
        if (!isAdded || view == null || adapter.itemCount == 0) {
            return false
        }
        val result = TabContentFocusHelper.requestRecyclerPrimaryFocus(
            recyclerView = binding.recyclerView,
            itemCount = adapter.itemCount
        )
        return result.resolved
    }

    override fun focusPrimaryContent(anchorView: View?, preferSpatialEntry: Boolean): Boolean {
        if (preferSpatialEntry) {
            val handled = SpatialFocusNavigator.requestBestDescendant(
                anchorView = anchorView,
                root = binding.recyclerView,
                direction = View.FOCUS_RIGHT,
                fallback = null
            )
            if (handled) {
                return true
            }
        }
        return focusPrimaryContent()
    }

    private fun openAreaDetail(area: LiveAreaCategory) {
        if (!sessionGateway.isLoggedIn()) {
            context?.toast(getString(R.string.need_sign_in))
            return
        }
        val areaId = area.areaV2Id.takeIf { it > 0 } ?: area.id
        val parentAreaId = area.areaV2ParentId.takeIf { it > 0 } ?: area.parentId
        val title = area.title.ifBlank { area.name }
        openInHostContainer(
            LiveListFragment.newAreaInstance(
                areaId = areaId,
                parentAreaId = parentAreaId,
                title = title
            )
        )
    }

    private fun focusTopTab(): Boolean {
        return (parentFragment as? LiveFragment)?.focusCurrentTab() == true
    }
}
