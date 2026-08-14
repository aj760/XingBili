package blbl.cat3399.ui

import android.os.Bundle
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.lifecycleScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import blbl.cat3399.R
import blbl.cat3399.core.api.BiliApi
import blbl.cat3399.core.image.ImageLoader
import blbl.cat3399.core.log.AppLog
import blbl.cat3399.core.net.BiliClient
import blbl.cat3399.core.ui.AppToast
import blbl.cat3399.databinding.DialogUserInfoBinding
import blbl.cat3399.feature.following.FollowingListActivity
import kotlinx.coroutines.launch
import org.json.JSONObject
import java.util.Locale

class UserInfoDialogFragment : DialogFragment() {
    private var _binding: DialogUserInfoBinding? = null
    private val binding: DialogUserInfoBinding get() = _binding!!

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setStyle(STYLE_NO_TITLE, R.style.ThemeOverlay_Blbl_TransparentDialog)
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?,
    ): View {
        _binding = DialogUserInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        binding.root.setOnClickListener { dismissAllowingStateLoss() }
        binding.card.setOnClickListener { /* consume */ }
        binding.btnFollowing.setOnClickListener {
            dismissAllowingStateLoss()
            startActivity(Intent(requireContext(), FollowingListActivity::class.java))
        }
        binding.btnFollower.setOnClickListener {
            AppToast.show(requireContext(), "粉丝列表未实现")
        }

        binding.tvName.text = ""
        binding.tvMid.text = ""
        binding.tvFollowing.text = "--"
        binding.tvFollower.text = "--"
        binding.tvCoins.text = "--"
        binding.tvLevel.text = ""
        binding.tvExp.text = ""
        binding.progressExp.visibility = View.GONE

        load()
    }

    override fun onStart() {
        super.onStart()
        val window = dialog?.window ?: return
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
        applyImmersive(window, BiliClient.prefs.fullscreenEnabled)
    }

    override fun onDestroyView() {
        _binding = null
        super.onDestroyView()
    }

    private fun load() {
        binding.pbLoading.visibility = View.VISIBLE
        viewLifecycleOwner.lifecycleScope.launch {
            runCatching {
                val nav = BiliApi.nav()
                val data = nav.optJSONObject("data")
                val isLogin = data?.optBoolean("isLogin") ?: false
                if (!isLogin) {
                    dismissAllowingStateLoss()
                    return@launch
                }

                val mid = data?.optLong("mid") ?: 0L
                val name = data?.optString("uname", "").orEmpty()
                val avatarUrl = data?.optString("face")?.takeIf { it.isNotBlank() }
                val coins = parseCoins(data)
                val levelInfo = data?.optJSONObject("level_info")
                val level = levelInfo?.optInt("current_level") ?: 0
                val currentExp = parseInt(levelInfo, "current_exp") ?: 0
                val nextExp = parseInt(levelInfo, "next_exp")

                val stat = if (mid > 0) BiliApi.relationStat(mid) else null

                val b = _binding ?: return@launch
                b.tvName.text = name
                b.tvMid.text = getString(R.string.label_uid_fmt, mid.toString())
                val normalizedUrl = blbl.cat3399.core.image.ImageUrl.avatar(avatarUrl)
                ImageLoader.loadInto(b.ivAvatar, normalizedUrl)

                b.tvFollowing.text = (stat?.following ?: 0L).toString()
                b.tvFollower.text = (stat?.follower ?: 0L).toString()
                b.tvCoins.text = formatCoins(coins)

                b.tvLevel.text = getString(R.string.label_level_fmt, level)
                val expText = if (nextExp != null && nextExp > 0) "$currentExp/$nextExp" else "已满级"
                b.tvExp.text = getString(R.string.label_exp_fmt, expText)

                if (nextExp != null && nextExp > 0) {
                    b.progressExp.visibility = View.VISIBLE
                    b.progressExp.max = nextExp
                    b.progressExp.progress = currentExp.coerceIn(0, nextExp)
                } else {
                    b.progressExp.visibility = View.GONE
                }
            }.onFailure {
                AppLog.w("UserInfoDialog", "load failed", it)
                if (isAdded) {
                    _binding?.let { b ->
                        b.tvName.text = "加载失败"
                        b.tvMid.text = it.message.orEmpty()
                    }
                }
            }
            _binding?.pbLoading?.visibility = View.GONE
        }
    }

    private fun parseCoins(data: JSONObject?): Double {
        if (data == null) return 0.0
        val money = data.optDouble("money", Double.NaN)
        if (!money.isNaN()) return money
        val coins = data.optDouble("coins", Double.NaN)
        if (!coins.isNaN()) return coins
        return 0.0
    }

    private fun formatCoins(value: Double): String {
        val v = value.coerceAtLeast(0.0)
        return if (v >= 1000) String.format(Locale.getDefault(), "%.0f", v) else String.format(Locale.getDefault(), "%.1f", v)
    }

    private fun parseInt(obj: JSONObject?, key: String): Int? {
        val any = obj?.opt(key) ?: return null
        return when (any) {
            is Number -> any.toInt()
            is String -> any.toIntOrNull()
            else -> null
        }
    }

    private fun applyImmersive(window: android.view.Window, enabled: Boolean) {
        WindowCompat.setDecorFitsSystemWindows(window, !enabled)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        if (enabled) {
            controller.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            controller.hide(WindowInsetsCompat.Type.systemBars())
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    companion object {
        private const val TAG = "UserInfoDialog"

        fun show(fm: FragmentManager) {
            if (fm.findFragmentByTag(TAG) != null) return
            UserInfoDialogFragment().show(fm, TAG)
        }
    }
}
