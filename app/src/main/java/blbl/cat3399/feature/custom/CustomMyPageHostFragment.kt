package blbl.cat3399.feature.custom

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import blbl.cat3399.R
import blbl.cat3399.core.ui.TabContentFocusTarget
import blbl.cat3399.databinding.FragmentMyContainerBinding
import blbl.cat3399.feature.my.BangumiDetailActivity
import blbl.cat3399.feature.my.MyBangumiFollowFragment
import blbl.cat3399.feature.my.MyFavFolderDetailFragment
import blbl.cat3399.feature.my.MyFavFoldersFragment
import blbl.cat3399.feature.my.MyHistoryFragment
import blbl.cat3399.feature.my.MyLikeFragment
import blbl.cat3399.feature.my.MyNavigator
import blbl.cat3399.feature.my.MyToViewFragment
import blbl.cat3399.ui.BackPressHandler
import blbl.cat3399.ui.RefreshKeyHandler

class CustomMyPageHostFragment : Fragment(), MyNavigator, BackPressHandler, RefreshKeyHandler, TabContentFocusTarget {
    private var _binding: FragmentMyContainerBinding? = null
    private val binding get() = _binding!!

    private val pageKind: String by lazy { requireArguments().getString(ARG_PAGE_KIND).orEmpty() }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyContainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            showRootPage()
        }
    }

    override fun onResume() {
        super.onResume()
        if (childFragmentManager.backStackEntryCount == 0) {
            showRootPage()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun openFavFolder(mediaId: Long, title: String) {
        if (_binding == null || childFragmentManager.isStateSaved) return
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.my_container, MyFavFolderDetailFragment.newInstance(mediaId = mediaId, title = title))
            .addToBackStack(null)
            .commit()
    }

    override fun openBangumiDetail(seasonId: Long, isDrama: Boolean, continueEpId: Long?, continueEpIndex: Int?) {
        if (!isAdded) return
        startActivity(
            Intent(requireContext(), BangumiDetailActivity::class.java)
                .putExtra(BangumiDetailActivity.EXTRA_SEASON_ID, seasonId)
                .putExtra(BangumiDetailActivity.EXTRA_IS_DRAMA, isDrama)
                .apply { continueEpId?.let { putExtra(BangumiDetailActivity.EXTRA_CONTINUE_EP_ID, it) } }
                .apply { continueEpIndex?.let { putExtra(BangumiDetailActivity.EXTRA_CONTINUE_EP_INDEX, it) } },
        )
    }

    override fun handleBackPressed(): Boolean {
        if (childFragmentManager.popBackStackImmediate()) return true
        val current = currentChild()
        return (current as? BackPressHandler)?.handleBackPressed() == true
    }

    override fun handleRefreshKey(): Boolean {
        val current = currentChild()
        return (current as? RefreshKeyHandler)?.handleRefreshKey() == true
    }

    override fun requestFocusPrimaryItemFromTab(): Boolean = requestCurrentPrimaryFocus { it.requestFocusPrimaryItemFromTab() }

    override fun requestFocusPrimaryItemFromContentSwitch(): Boolean =
        requestCurrentPrimaryFocus { it.requestFocusPrimaryItemFromContentSwitch() }

    override fun requestFocusPrimaryItemFromBackToTab0(): Boolean =
        requestCurrentPrimaryFocus { it.requestFocusPrimaryItemFromBackToTab0() }

    private fun requestCurrentPrimaryFocus(block: (TabContentFocusTarget) -> Boolean): Boolean {
        val current = currentChild()
        val target = current as? TabContentFocusTarget
        if (target != null) return block(target)
        return focusFallbackView(current)
    }

    private fun focusFallbackView(fragment: Fragment?): Boolean {
        val view = fragment?.view ?: return false
        val backButton = view.findViewById<View?>(R.id.btn_back)
        return backButton?.requestFocus() == true || view.requestFocus()
    }

    private fun currentChild(): Fragment? = childFragmentManager.findFragmentById(R.id.my_container)

    private fun showRootPage() {
        if (_binding == null || childFragmentManager.isStateSaved) return
        val current = currentChild()
        if (childFragmentManager.backStackEntryCount == 0 && current != null) return

        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.my_container, createRootFragment())
            .commit()
    }

    private fun createRootFragment(): Fragment {
        return when (pageKind) {
            KIND_HISTORY -> MyHistoryFragment()
            KIND_FAV -> MyFavFoldersFragment()
            KIND_BANGUMI -> MyBangumiFollowFragment.newInstance(type = 1)
            KIND_DRAMA -> MyBangumiFollowFragment.newInstance(type = 2)
            KIND_TO_VIEW -> MyToViewFragment()
            KIND_LIKE -> MyLikeFragment()
            else -> error("Unsupported custom my page kind=$pageKind")
        }
    }

    companion object {
        private const val ARG_PAGE_KIND = "page_kind"

        private const val KIND_HISTORY = "history"
        private const val KIND_FAV = "fav"
        private const val KIND_BANGUMI = "bangumi"
        private const val KIND_DRAMA = "drama"
        private const val KIND_TO_VIEW = "to_view"
        private const val KIND_LIKE = "like"

        fun newHistory() = newInstance(KIND_HISTORY)

        fun newFav() = newInstance(KIND_FAV)

        fun newBangumi() = newInstance(KIND_BANGUMI)

        fun newDrama() = newInstance(KIND_DRAMA)

        fun newToView() = newInstance(KIND_TO_VIEW)

        fun newLike() = newInstance(KIND_LIKE)

        private fun newInstance(kind: String): CustomMyPageHostFragment {
            return CustomMyPageHostFragment().apply {
                arguments = Bundle().apply { putString(ARG_PAGE_KIND, kind) }
            }
        }
    }
}
