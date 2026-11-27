package com.mytvb.ui.dialog

import android.content.Context
import android.graphics.Color
import android.os.CountDownTimer
import android.text.Html
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.Window
import androidx.appcompat.app.AppCompatDialog
import com.mytvb.R
import com.mytvb.core.common.log.AppLog
import com.mytvb.core.ui.base.DialogWindowFit
import com.mytvb.databinding.DialogUsageTipBinding

class UsageTipDialog(context: Context) : AppCompatDialog(context, R.style.DialogTheme) {

    private companion object {
        const val TAG = "UsageTipDialog"
    }

    private val binding = DialogUsageTipBinding.inflate(LayoutInflater.from(context))
    private var countDownTimer: CountDownTimer? = null

    init {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        // 倒计时期间禁止任何关闭途径（返回键/外侧触摸/确定键全无效），
        // 强制阅读满 10 秒；onFinish 里恢复 cancelable 与按钮可点。
        setCancelable(false)
        setCanceledOnTouchOutside(false)
        // fill 全屏窗口 + 居中卡片（华为电视对独立小窗有延迟裁高，fill 载体不受影响）
        DialogWindowFit.overlay(
            this, binding.root,
            (context.resources.displayMetrics.widthPixels * 0.6).toInt(),
            cancelOnOutside = false,
            tag = "usageTip"
        )
        // 内容在小屏/大字号下可能高过屏幕，限制滚动区高度避免按钮被推出屏幕
        binding.textScroll.maxHeight =
            (context.resources.displayMetrics.heightPixels * 0.65f).toInt()

        // setOnClickListener 内部会自动 setClickable(true)，必须先挂监听再禁用，
        // 否则倒计时期间遥控/触屏仍可点确定直接关掉提示。
        binding.buttonConfirm.setOnClickListener { dismiss() }
        binding.buttonConfirm.setTextColor(Color.GRAY)
        binding.buttonConfirm.isClickable = false
        binding.buttonConfirm.text = context.getString(R.string.confirm_countdown, 10)

        binding.textContent.text =
            Html.fromHtml(context.getString(R.string.usage_tip_content), Html.FROM_HTML_MODE_COMPACT)

        setOnShowListener {
            startCountdown()
        }
    }

    private fun startCountdown() {
        countDownTimer = object : CountDownTimer(10_000, 1_000) {
            override fun onTick(millisUntilFinished: Long) {
                val seconds = millisUntilFinished / 1000
                binding.buttonConfirm.text =
                    context.getString(R.string.confirm_countdown, seconds.toInt())
            }

            override fun onFinish() {
                AppLog.i(TAG, "onFinish: 恢复 cancelable=true 与按钮可点")
                binding.buttonConfirm.setText(R.string.confirm)
                binding.buttonConfirm.setTextColor(
                    context.resources.getColor(R.color.pink, null)
                )
                binding.buttonConfirm.isClickable = true
                binding.buttonConfirm.requestFocus()
                // 倒计时结束，返回键恢复可关
                setCancelable(true)
            }
        }.start()
    }

    // ===== 以下为返回键链路诊断日志，定位后可整体删除 =====

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.keyCode == KeyEvent.KEYCODE_BACK) {
            AppLog.i(TAG, "dispatchKeyEvent BACK action=${event.action}")
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            AppLog.i(TAG, "onKeyDown BACK")
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            AppLog.i(TAG, "onKeyUp BACK")
        }
        return super.onKeyUp(keyCode, event)
    }

    override fun onBackPressed() {
        AppLog.i(TAG, "onBackPressed")
        super.onBackPressed()
    }

    override fun onStop() {
        super.onStop()
        countDownTimer?.cancel()
    }
}
