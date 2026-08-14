package blbl.cat3399.feature.my

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import blbl.cat3399.R
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.TabContentSwitchFocusHost
import blbl.cat3399.databinding.FragmentMyContainerBinding
import blbl.cat3399.ui.BackPressHandler

class MyFragment : Fragment(), BackPressHandler, MyNavigator, TabContentSwitchFocusHost {
    private var _binding: FragmentMyContainerBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMyContainerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        if (savedInstanceState == null) {
            showRootForLoginState()
        }
    }

    override fun onResume() {
        super.onResume()
        if (childFragmentManager.backStackEntryCount == 0) {
            showRootForLoginState()
        }
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    override fun handleBackPressed(): Boolean {
        if (childFragmentManager.popBackStackImmediate()) return true
        val current = childFragmentManager.findFragmentById(R.id.my_container)
        return (current as? BackPressHandler)?.handleBackPressed() == true
    }

    override fun requestFocusCurrentPagePrimaryItemFromContentSwitch(): Boolean {
        val current = childFragmentManager.findFragmentById(R.id.my_container) as? TabContentSwitchFocusHost ?: return false
        return current.requestFocusCurrentPagePrimaryItemFromContentSwitch()
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

    private fun showRootForLoginState() {
        if (_binding == null || childFragmentManager.isStateSaved) return
        val wantLogin = !BiliClient.cookies.hasSessData()
        val current = childFragmentManager.findFragmentById(R.id.my_container)
        if (wantLogin && current is MyLoginFragment) return
        if (!wantLogin && current is MyTabsFragment) return

        val fragment = if (wantLogin) MyLoginFragment() else MyTabsFragment()
        AppLog.d("My", "showRoot loginRequired=$wantLogin")
        childFragmentManager.beginTransaction()
            .setReorderingAllowed(true)
            .replace(R.id.my_container, fragment)
            .commit()
    }

    companion object {
        fun newInstance() = MyFragment()
    }
}
