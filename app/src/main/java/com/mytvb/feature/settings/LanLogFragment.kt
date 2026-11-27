package com.mytvb.feature.settings

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.ImageView
import com.mytvb.R
import com.mytvb.core.common.log.AppLog
import com.mytvb.core.common.log.LanLogServer
import com.mytvb.core.common.util.QrCodeUtils
import com.mytvb.core.ui.base.BaseFragment
import com.mytvb.core.ui.navigation.navigateBackFromUi
import com.mytvb.databinding.FragmentLanLogBinding

/**
 * 局域网日志页：启动本地 HTTP 服务器并展示扫码地址，
 * 离开页面即停止服务（onStart 启动 / onStop 停止）。
 */
class LanLogFragment : BaseFragment<FragmentLanLogBinding>() {

    companion object {
        fun newInstance() = LanLogFragment()
    }

    private var server: LanLogServer? = null

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentLanLogBinding {
        return FragmentLanLogBinding.inflate(inflater, container, false)
    }

    override fun initView() {
        binding.buttonBack.setOnClickListener { navigateBackFromUi() }
    }

    override fun onStart() {
        super.onStart()
        startServer()
    }

    override fun onStop() {
        super.onStop()
        server?.stop()
        server = null
    }

    private fun startServer() {
        val lanLogServer = LanLogServer(requireContext().applicationContext)
        if (!lanLogServer.start()) {
            binding.tvUrl.setText(R.string.lan_log_server_failed)
            binding.imageQrCode.setImageDrawable(null)
            AppLog.e("LanLogFragment", "server start failed")
            return
        }
        server = lanLogServer
        val url = lanLogServer.buildUrl()
        if (url == null) {
            binding.tvUrl.setText(R.string.lan_log_no_network)
            binding.imageQrCode.setImageDrawable(null)
            AppLog.w("LanLogFragment", "no lan ipv4 found, port=${lanLogServer.port}")
            return
        }
        binding.tvUrl.text = url
        QrCodeUtils.generate(url)?.let { bitmap ->
            binding.imageQrCode.scaleType = ImageView.ScaleType.FIT_CENTER
            binding.imageQrCode.setImageBitmap(bitmap)
        }
        binding.tvLogState.setText(
            if (AppLog.isEnabled) R.string.lan_log_recording_on else R.string.lan_log_recording_off
        )
        AppLog.i("LanLogFragment", "server started port=${lanLogServer.port}")
    }
}
