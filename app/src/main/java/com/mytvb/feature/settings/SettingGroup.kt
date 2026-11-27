package com.mytvb.feature.settings

import androidx.annotation.StringRes
import com.mytvb.model.SettingModel

/**
 * 设置分组：组标题 + 组内条目。
 * 列表渲染时每组先出组头，再出组内条目；条目按「组首/组中/组尾」拼接为一个圆角框。
 */
class SettingGroup(
    @StringRes val titleRes: Int,
    val items: List<SettingModel>
)
