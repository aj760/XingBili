package com.mytvb.feature.user

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup

import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.RecyclerView
import com.mytvb.R
import com.mytvb.databinding.FragmentFollowUserListBinding
import com.mytvb.event.AppEventHub
import com.mytvb.model.user.FollowingModel
import com.mytvb.repository.UserRepository
import com.mytvb.ui.adapter.FollowUserAdapter
import com.mytvb.core.ui.base.BaseFragment
import com.mytvb.core.ui.base.adaptiveSpanCount
import com.mytvb.core.ui.focus.hasFocusInChildren
import com.mytvb.core.ui.focus.tv.GridTvFocusStrategy
import com.mytvb.core.ui.focus.tv.ListAdapterTvFocusBridge
import com.mytvb.core.ui.focus.tv.TvListFocusController
import com.mytvb.feature.detail.UserSpaceFragment
import com.mytvb.core.ui.layout.WrapContentGridLayoutManager
import com.mytvb.core.ui.decoration.GridSpacingItemDecoration
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import com.mytvb.core.ui.base.AppToast

class FollowUserListFragment : BaseFragment<FragmentFollowUserListBinding>() {

    companion object {
        const val TYPE_FOLLOWING = 0
        const val TYPE_FOLLOWER = 1

        private const val ARG_USER_ID = "user_id"
        private const val ARG_TYPE = "type"

        fun newInstance(userId: Long, type: Int): FollowUserListFragment {
            return FollowUserListFragment().apply {
                arguments = bundleOf(
                    ARG_USER_ID to userId,
                    ARG_TYPE to type
                )
            }
        }
    }

    // 用户卡片是“头像+两行文字”，普通屏 4 列、超宽屏 8 列，写死 8 列在窄格
    // 里昵称/简介都放不下
    private val spanCount by lazy { resources.adaptiveSpanCount() }

    private val appEventHub: AppEventHub by inject()
    private val userRepository: UserRepository by inject()

    private lateinit var adapter: FollowUserAdapter
    private var tvFocusController: TvListFocusController? = null
    private var userId: Long = 0L
    private var type: Int = TYPE_FOLLOWING
    private var currentPage = 1
    private var isLoading = false
    private var hasMore = true
    private var totalCount = 0
    private val pageSize = 50
    private var lastFocusedPosition = RecyclerView.NO_POSITION
    private var hasRequestedInitialFocus = false

    override fun getViewBinding(
        inflater: LayoutInflater,
        container: ViewGroup?
    ): FragmentFollowUserListBinding {
        return FragmentFollowUserListBinding.inflate(inflater, container, false)
    }

    override fun initArguments() {
        userId = arguments?.getLong(ARG_USER_ID) ?: 0L
        type = arguments?.getInt(ARG_TYPE, TYPE_FOLLOWING) ?: TYPE_FOLLOWING
    }

    override fun initView() {
        binding.tvTitle.text = getString(
            if (type == TYPE_FOLLOWER) R.string.user_follower else R.string.user_following
        )
        binding.buttonBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }
        binding.buttonBack.setOnFocusChangeListener { _, hasFocus ->
            if (hasFocus) {
                lastFocusedPosition = RecyclerView.NO_POSITION
            }
        }

        adapter = FollowUserAdapter(
            onItemClick = ::onUserClick,
            onItemFocused = { position -> lastFocusedPosition = position }
        )
        binding.recyclerView.layoutManager = WrapContentGridLayoutManager(requireContext(), spanCount)
        binding.recyclerView.adapter = adapter
        if (binding.recyclerView.itemDecorationCount == 0) {
            binding.recyclerView.addItemDecoration(
                GridSpacingItemDecoration(
                    spanCount,
                    resources.getDimensionPixelSize(R.dimen.px20),
                    includeEdge = true
                )
            )
        }
        binding.recyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
                super.onScrolled(recyclerView, dx, dy)
                val layoutManager = recyclerView.layoutManager as? WrapContentGridLayoutManager ?: return
                if (!isLoading && hasMore && layoutManager.findLastVisibleItemPosition() >= adapter.itemCount - 5) {
                    currentPage++
                    loadUsers()
                }
            }
        })
        installTvFocusController()
    }

    /**
     * 只接「锚点捕获 + 返回恢复/初始聚焦」的轻量 controller：D-pad 导航、数据变化
     * 停泊均不接线（本页按键行为保持框架默认），FollowUserAdapter 经
     * [ListAdapterTvFocusBridge] 提供稳定 key（用户 mid）。
     */
    private fun installTvFocusController() {
        tvFocusController?.release()
        tvFocusController = TvListFocusController(
            recyclerView = binding.recyclerView,
            adapter = ListAdapterTvFocusBridge(adapter) { it.mid.toString() },
            strategy = GridTvFocusStrategy { spanCount },
            canLoadMore = { false },
            loadMore = {}
        )
    }

    override fun onDestroyView() {
        tvFocusController?.release()
        tvFocusController = null
        super.onDestroyView()
    }

    override fun initData() {
        loadUsers()
    }

    override fun onResume() {
        super.onResume()
        restoreFocus()
    }

    override fun initObserver() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                appEventHub.events.collectLatest { event ->
                    if (event == AppEventHub.Event.UserSessionChanged && !isHidden && isVisible) {
                        currentPage = 1
                        hasMore = true
                        totalCount = 0
                        loadUsers()
                    }
                }
            }
        }
    }

    private fun loadUsers() {
        if (userId <= 0L || isLoading || !hasMore) {
            return
        }

        isLoading = true
        if (currentPage == 1 && adapter.itemCount == 0) {
            binding.progressBar.visibility = View.VISIBLE
        }

        viewLifecycleOwner.lifecycleScope.launch {
            val result = if (type == TYPE_FOLLOWER) {
                userRepository.getFollower(userId, currentPage, pageSize)
            } else {
                userRepository.getFollowing(userId, currentPage, pageSize)
            }

            binding.progressBar.visibility = View.GONE
            isLoading = false

            result.onSuccess { response ->
                val wrapper = response.data
                val users = wrapper?.list.orEmpty()
                if (response.isSuccess) {
                    totalCount = wrapper?.total ?: totalCount
                    hasMore = when {
                        totalCount > 0 -> currentPage * pageSize < totalCount
                        else -> users.size >= pageSize
                    }
                    renderUsers(users)
                } else {
                    rollbackPage()
                    handleLoadError(response.errorMessage)
                }
            }.onFailure { throwable ->
                rollbackPage()
                handleLoadError(throwable.message ?: getString(R.string.net_error))
            }
        }
    }

    private fun renderUsers(users: List<FollowingModel>) {
        if (currentPage == 1) {
            adapter.setData(users)
        } else if (users.isNotEmpty()) {
            adapter.addData(users)
        }

        val isEmpty = currentPage == 1 && users.isEmpty()
        if (isEmpty) {
            showEmpty(getString(R.string.empty))
        } else {
            showListContent()
            if (!hasRequestedInitialFocus && currentPage == 1) {
                hasRequestedInitialFocus = true
                if (adapter.itemCount > 0) {
                    tvFocusController?.requestFocusPosition(0)
                } else {
                    requestBackFocus()
                }
            } else if (lastFocusedPosition != RecyclerView.NO_POSITION) {
                restoreFocus()
            }
        }
    }

    private fun showListContent() {
        binding.tvEmpty.visibility = View.GONE
        binding.recyclerView.visibility = View.VISIBLE
    }

    private fun showEmpty(message: String) {
        if (currentPage == 1 && adapter.itemCount == 0) {
            binding.tvEmpty.visibility = View.VISIBLE
            binding.tvEmpty.text = message
            binding.recyclerView.visibility = View.GONE
            requestBackFocus()
        }
    }

    private fun handleLoadError(message: String) {
        if (currentPage == 1 && adapter.itemCount == 0) {
            showEmpty(message)
            return
        }
        showListContent()
        AppToast.show(requireContext(), message)
    }

    private fun rollbackPage() {
        if (currentPage > 1) {
            currentPage--
        }
    }

    private fun onUserClick(user: FollowingModel) {
        if (user.mid > 0) {
            lastFocusedPosition = adapter.getFocusedPosition()
            tvFocusController?.captureCurrentAnchor()
            openInHostContainer(UserSpaceFragment.newInstance(user.mid))
        }
    }

    private fun restoreFocus() {
        if (!isAdded) return
        binding.recyclerView.post {
            if (!isAdded) return@post
            if (binding.recyclerView.isVisible && adapter.itemCount > 0 && lastFocusedPosition != RecyclerView.NO_POSITION) {
                val controller = tvFocusController
                if (controller != null && controller.hasCapturedAnchor()) {
                    // 统一走带仲裁的返回恢复：锚点即点击时的用户卡片（稳定 key 经
                    // ListAdapterTvFocusBridge 重解析），转场轮询/数据落地等待内置，
                    // 手写 retry 轮询已删除。失败走 lastFocusedPosition 兜底。
                    controller.restoreFocusAfterReturn(onFailed = { restoreFallbackFocus() })
                } else {
                    restoreFallbackFocus()
                }
            } else {
                requestBackFocus()
            }
        }
    }

    /** 锚点恢复失败后的兜底：lastFocusedPosition 单次聚焦（返回值不可靠，以真实落点为准），再退返回键。 */
    private fun restoreFallbackFocus() {
        if (!isAdded) return
        val targetPosition = lastFocusedPosition
            .takeIf { it != RecyclerView.NO_POSITION }
            ?.coerceIn(0, adapter.itemCount - 1)
        if (targetPosition == null ||
            tvFocusController?.requestFocusPosition(targetPosition) != true ||
            !binding.recyclerView.hasFocusInChildren()
        ) {
            requestBackFocus()
        }
    }

    private fun requestBackFocus() {
        if (!isAdded) return
        binding.buttonBack.post {
            if (isAdded && !binding.buttonBack.hasFocus()) {
                binding.buttonBack.requestFocus()
            }
        }
    }
}
