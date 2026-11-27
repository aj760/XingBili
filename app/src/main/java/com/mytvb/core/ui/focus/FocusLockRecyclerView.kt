package com.mytvb.core.ui.focus

import android.content.Context
import androidx.recyclerview.widget.RecyclerView

/**
 * 普通 RecyclerView（之前用于焦点锁定，现改用 Adapter 的 OnKeyListener 方案，
 * 对标参考 GroupDualAdapter/ChannelDualAdapter）。
 * 保留此类仅为兼容 XML 布局引用，实际为标准 RecyclerView。
 */
class FocusLockRecyclerView @JvmOverloads constructor(
    context: Context,
    attrs: android.util.AttributeSet? = null,
    defStyleAttr: Int = 0
) : RecyclerView(context, attrs, defStyleAttr)
