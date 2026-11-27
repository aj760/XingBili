package com.mytvb.ui.dialog

import android.content.Context
import android.view.LayoutInflater
import android.view.Window
import androidx.appcompat.app.AppCompatDialog
import com.mytvb.R
import com.mytvb.databinding.DialogVideoInfoBinding
import com.mytvb.core.ui.image.ImageLoader
import com.mytvb.core.ui.base.DialogWindowFit

class VideoInfoDialog(
    context: Context,
    private val coverUrl: String,
    private val title: String,
    private val description: String
) : AppCompatDialog(context, R.style.DialogTheme) {

    private val binding = DialogVideoInfoBinding.inflate(LayoutInflater.from(context))

    init {
        supportRequestWindowFeature(Window.FEATURE_NO_TITLE)
        setCanceledOnTouchOutside(true)
        // 固定尺寸卡片（布局根 match_parent 依赖窗口定尺寸），fill 载体下显式传高
        DialogWindowFit.overlay(
            this, binding.root,
            context.resources.getDimensionPixelSize(R.dimen.px1200),
            heightPx = context.resources.getDimensionPixelSize(R.dimen.px615),
            tag = "videoInfo"
        )
        bindContent()
        binding.buttonClose.setOnClickListener { dismiss() }
    }

    private fun bindContent() {
        ImageLoader.loadVideoCover(
            imageView = binding.imageView,
            url = coverUrl,
            placeholder = R.drawable.default_video,
            error = R.drawable.default_video
        )
        binding.textTitle.text = title
        binding.textDescription.text = description
    }
}
