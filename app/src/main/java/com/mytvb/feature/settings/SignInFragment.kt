package com.mytvb.feature.settings

import android.graphics.Bitmap
import android.view.LayoutInflater
import android.view.ViewGroup

import androidx.lifecycle.lifecycleScope
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.mytvb.R
import com.mytvb.databinding.FragmentSignInBinding
import com.mytvb.core.common.cache.FileCacheManager
import com.mytvb.core.common.log.AppLog
import com.mytvb.network.NetworkManager
import com.mytvb.network.cookie.CookieManager
import okhttp3.Cookie
import com.mytvb.repository.UserRepository
import com.mytvb.repository.remote.TvAuthRepository
import com.mytvb.core.ui.base.BaseFragment
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import org.koin.android.ext.android.inject
import com.mytvb.core.ui.base.AppToast

class SignInFragment : BaseFragment<FragmentSignInBinding>() {

    companion object {
        private const val TAG = "SignInFragment"
        // 1.5s 间隔下约 8s：足够跨过网络抖动，又不至于让用户对着死码干等
        private const val NETWORK_WARN_THRESHOLD = 5

        fun newInstance() = SignInFragment()
    }

    private val tvAuthRepository: TvAuthRepository by inject()
    private val cookieManager: CookieManager by inject()
    private val userRepository: UserRepository by inject()
    private var authCode = ""
    private var pollingJob: Job? = null
    private val pollingInterval = 1500L

    // auth_code 服务端有效期 180s；断网时拿不到 86038，只能本地计时兜底刷新
    private val qrValidityMs = 180_000L
    private var qrGeneratedAtMs = 0L
    // 连续轮询失败计数：断网时 poll 会一直抛 UnknownHostException，原实现只打日志，
    // 用户看到的是一张永远不变的二维码，无从判断是网断了还是码失效了
    private var consecutivePollFailures = 0
    private var networkWarningShown = false
    private var countdownJob: Job? = null
    // 二维码已进入过期态：覆盖层盖住旧码，轮询停止，等用户手动刷新
    private var qrExpired = false

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentSignInBinding {
        return FragmentSignInBinding.inflate(inflater, container, false)
    }

    override fun initView() {
        binding.overlayExpired.setOnClickListener { loadQrCode() }
    }

    override fun initData() {
        loadQrCode()
    }

    private fun loadQrCode() {
        binding.progressBar.visibility = android.view.View.VISIBLE
        // 刷新期间收起过期覆盖层与倒计时，成败之后再决定去留
        binding.overlayExpired.visibility = android.view.View.GONE
        binding.overlayScanned.visibility = android.view.View.GONE
        binding.tvCountdown.visibility = android.view.View.GONE

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                NetworkManager.ensureWebFingerprintCookies()
            } catch (e: kotlinx.coroutines.CancellationException) {
                throw e
            } catch (_: Exception) {
                // 预热 web 指纹 cookie，失败不阻塞出码
            }
            try {
                val response = tvAuthRepository.generateTvQrCode()
                binding.progressBar.visibility = android.view.View.GONE

                if (response.isSuccess && response.data != null) {
                    authCode = response.data.authCode
                    val qrUrl = response.data.url
                    // 新码落位：标题从「已扫码待确认」等状态恢复为初始提示；
                    // 覆盖层文字恢复为过期文案（可能刚被失败态改成「获取失败/点击重试」）
                    binding.textView.text = getString(R.string.scan_sign_in)
                    binding.tvExpiredTitle.text = getString(R.string.sign_in_qr_expired)
                    binding.tvExpiredAction.text = getString(R.string.sign_in_qr_refresh)
                    qrGeneratedAtMs = System.currentTimeMillis()
                    consecutivePollFailures = 0
                    networkWarningShown = false
                    qrExpired = false
                    displayQrCode(qrUrl)
                    binding.tvCountdown.visibility = android.view.View.VISIBLE
                    startPolling()
                    startCountdown()
                } else {
                    showQrLoadFailed("code=${response.code} ${response.message.orEmpty()}")
                }
            } catch (e: Exception) {
                binding.progressBar.visibility = android.view.View.GONE
                showQrLoadFailed(e.message.orEmpty())
            }
        }
    }

    private fun displayQrCode(url: String) {
        try {
            val bitmap = generateQrCode(url)
            bitmap?.let {
                binding.imageView.setImageBitmap(it)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "displayQrCode failed", e)
            AppToast.show(requireContext(), "生成二维码失败")
        }
    }

    private fun generateQrCode(content: String, size: Int = 512): Bitmap? {
        return try {
            val hints = mapOf(
                EncodeHintType.MARGIN to 1,
                EncodeHintType.CHARACTER_SET to "UTF-8"
            )
            val bitMatrix = QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, size, size, hints)
            val width = bitMatrix.width
            val height = bitMatrix.height
            val pixels = IntArray(width * height)
            for (y in 0 until height) {
                for (x in 0 until width) {
                    pixels[y * width + x] = if (bitMatrix[x, y]) {
                        android.graphics.Color.BLACK
                    } else {
                        android.graphics.Color.WHITE
                    }
                }
            }
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888).apply {
                setPixels(pixels, 0, width, 0, 0, width, height)
            }
        } catch (e: Exception) {
            AppLog.e(TAG, "generateQrCode failed", e)
            null
        }
    }

    private fun startPolling() {
        pollingJob?.cancel()
        pollingJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive && authCode.isNotEmpty()) {
                delay(pollingInterval)
                checkTvPollResult()
            }
        }
    }

    /**
     * 过期倒计时：以本地出码时间为基准（服务端 auth_code 有效期 180s），每秒刷新剩余
     * 时间。到点进入过期态——不自动换码，盖覆盖层等用户手动刷新；断网期间 poll 拿不到
     * 服务端 86038，本地计时同样兜底到点。
     */
    private fun startCountdown() {
        countdownJob?.cancel()
        countdownJob = viewLifecycleOwner.lifecycleScope.launch {
            while (isActive) {
                val remainingMs = qrGeneratedAtMs + qrValidityMs - System.currentTimeMillis()
                if (remainingMs <= 0L) {
                    showExpiredState()
                    return@launch
                }
                val totalSec = (remainingMs + 999L) / 1000L
                binding.tvCountdown.text = getString(
                    R.string.sign_in_qr_countdown_format,
                    String.format(java.util.Locale.US, "%d:%02d", totalSec / 60, totalSec % 60)
                )
                delay(1000)
            }
        }
    }

    private fun showExpiredState() {
        qrExpired = true
        pollingJob?.cancel()
        countdownJob?.cancel()
        binding.tvCountdown.visibility = android.view.View.GONE
        // 扫了码一直没确认直到过期：先收起扫码成功层，再盖过期层
        binding.overlayScanned.visibility = android.view.View.GONE
        binding.overlayExpired.visibility = android.view.View.VISIBLE
        // 登录页没有其他可聚焦控件，焦点落上即可用 OK 键刷新
        binding.overlayExpired.requestFocus()
        AppLog.i(TAG, "QR expired, stop polling and wait for manual refresh")
    }

    // 刷新请求失败时若此前已是过期态，把覆盖层盖回去，保持「点击刷新」可再试
    private fun restoreExpiredOverlayIfNeeded() {
        if (qrExpired) binding.overlayExpired.visibility = android.view.View.VISIBLE
    }

    /**
     * 出码失败（网络断开/服务端报错）：不 toast 原始异常（英文技术信息对用户无意义），
     * 改为在二维码区盖「获取失败/点击重试」覆盖层——遥控 OK 与触摸都可再试，比
     * 死白一片只能退出重进强。原始错误记日志排查。
     */
    private fun showQrLoadFailed(raw: String) {
        AppLog.w(TAG, "generate QR failed: $raw")
        // 置过期语义：此状态下再失败，restoreExpiredOverlayIfNeeded 才会把覆盖层盖回
        qrExpired = true
        binding.tvExpiredTitle.text = getString(R.string.sign_in_qr_failed_title)
        binding.tvExpiredAction.text = getString(R.string.sign_in_qr_retry)
        binding.overlayExpired.visibility = android.view.View.VISIBLE
        binding.overlayExpired.requestFocus()
    }

    private suspend fun checkTvPollResult() {
        if (authCode.isEmpty()) return

        try {
            val response = tvAuthRepository.pollTvQrCode(authCode)
            consecutivePollFailures = 0
            networkWarningShown = false
            when {
                response.code == 0 && response.data != null -> {
                    pollingJob?.cancel()
                    val pollData = response.data
                    // 注入 cookie（直接构建 OkHttp Cookie 对象，保留 httpOnly/secure）
                    pollData.cookieInfo?.cookies?.let { cookies ->
                        val nowMs = System.currentTimeMillis()
                        val cookieObjects = cookies.mapNotNull { c ->
                            val name = c.name.trim()
                            val value = c.value.trim()
                            if (name.isBlank() || value.isBlank()) return@mapNotNull null
                            val expiresSec = c.expires
                            val expiresAt = if (expiresSec > 0L) expiresSec * 1000L else (nowMs + 180L * 24 * 60 * 60 * 1000)
                            Cookie.Builder()
                                .name(name).value(value)
                                .domain("bilibili.com").path("/")
                                .expiresAt(expiresAt)
                                .apply { if (c.secure == 1) secure() }
                                .apply { if (c.httpOnly == 1) httpOnly() }
                                .build()
                        }
                        if (cookieObjects.isNotEmpty()) {
                            cookieManager.saveCookieObjects(cookieObjects)
                        }
                    }
                    // TV 登录不产生 web refresh_token，清空防止后续刷新用无效 token
                    NetworkManager.clearWebRefreshToken()
                    // app 端 OAuth 凭据持久化，供 app 推荐流等 app 侧接口使用
                    pollData.tokenInfo
                        ?.takeIf { it.accessToken.isNotBlank() }
                        ?.let { NetworkManager.saveAppToken(it.accessToken, it.refreshToken, it.expiresIn) }
                        ?: AppLog.w(TAG, "TV poll success but token_info missing, app feed unavailable")
                    AppToast.show(requireContext(), "登录成功")
                    onLoginSuccess()
                }
                response.code == 86038 -> {
                    // 服务端判定过期：与本地倒计时到点走同一条过期态路径，等用户手动刷新
                    showExpiredState()
                }
                response.code == 86101 -> {
                    // 未扫码，继续轮询；若服务端状态自「已扫码」回退，收起扫码成功覆盖层
                    binding.overlayScanned.visibility = android.view.View.GONE
                }
                response.code == 86090 -> {
                    // 已扫码待确认：二维码上盖「扫码成功」覆盖层（官方同款对勾反馈），
                    // 此前只改标题一行小字，用户扫完码界面几乎无变化
                    binding.overlayScanned.visibility = android.view.View.VISIBLE
                }
            }
        } catch (e: Exception) {
            // 协程取消必须原样上抛，否则页面销毁后轮询不会真正停下
            if (e is kotlinx.coroutines.CancellationException) throw e
            consecutivePollFailures++
            AppLog.w(TAG, "TV poll error(${consecutivePollFailures}): ${e.message}")
            // 断网时 poll 每 1.5s 抛一次 UnknownHostException，原实现只打日志：
            // 用户面对一张永不变化的二维码，无法区分「没扫上」和「根本没网」
            if (consecutivePollFailures >= NETWORK_WARN_THRESHOLD && !networkWarningShown) {
                networkWarningShown = true
                if (isAdded) {
                    AppToast.show(requireContext(), R.string.sign_in_poll_network_error)
                }
            }
        }
    }

    private fun onLoginSuccess() {
        FileCacheManager.clearUserCaches()
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                runCatching { NetworkManager.activateAfterLogin() }
                // refreshCurrentUserInfo → syncUserSession 检测到"无会话→有会话"跳变时会在
                // NetworkManager 内部统一广播 UserSessionChanged，此处不再手动 dispatch
                userRepository.refreshCurrentUserInfo()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // 登录链路自身会推进会话代际（activateAfterLogin invalidate / nav Set-Cookie），
                // refreshCurrentUserInfo 的 requireCurrent 判过期抛 StaleSessionException（属取消），
                // 被 launch 静默吞掉后 popBackStack 永不执行——扫码页卡住的回归点。cookie 已落地，
                // 用户信息由会话健康检查兜底，页面照常关闭。
                AppLog.w(TAG, "post-login refresh cancelled (stale session expected): ${e.message}")
                throw e
            } finally {
                // 无论用户信息刷新成败，扫码页都必须关闭；用户已按 BACK 退出时不重复 pop
                if (isAdded) parentFragmentManager.popBackStackImmediate()
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        pollingJob?.cancel()
        countdownJob?.cancel()
    }
}
