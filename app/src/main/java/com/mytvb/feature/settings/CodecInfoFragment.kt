package com.mytvb.feature.settings

import android.graphics.Color
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.os.Build
import android.util.TypedValue
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import com.mytvb.R
import com.mytvb.core.common.media.VideoCodecSupport
import com.mytvb.core.ui.base.BaseFragment
import com.mytvb.core.ui.base.ScaledTextView
import com.mytvb.core.ui.navigation.navigateBackFromUi
import com.mytvb.databinding.FragmentCodecInfoBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * 编解码信息页（设置 → 关于 → 编解码信息）。
 *
 * 列出本机全部解码器（硬解/软解、MIME、分辨率/帧率能力）与设备芯片信息，
 * 用于排查"设备解码能力申报与实际表现不一致"类问题（如竖屏 HEVC 花屏）。
 */
class CodecInfoFragment : BaseFragment<FragmentCodecInfoBinding>() {

    companion object {
        fun newInstance() = CodecInfoFragment()
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private data class DecoderEntry(
        val name: String,
        val mimeType: String,
        val isVideo: Boolean,
        val isHardware: Boolean,
        val detail: String
    )

    override fun getViewBinding(inflater: LayoutInflater, container: ViewGroup?): FragmentCodecInfoBinding {
        return FragmentCodecInfoBinding.inflate(inflater, container, false)
    }

    override fun initView() {
        binding.buttonBack.setOnClickListener { navigateBackFromUi() }
    }

    override fun initData() {
        val container = binding.codecContainer
        container.removeAllViews()
        container.addView(buildDeviceSummaryView())
        scope.launch {
            // MediaCodecList 与 capabilities 查询较重，放 IO 构建；条目数量有限，一次性加入。
            val entries = withContext(Dispatchers.IO) { collectDecoderEntries() }
            if (!isAdded) return@launch
            entries.forEach { container.addView(buildEntryView(it)) }
        }
    }

    // ===== 视图构建 =====

    /** 顶部设备摘要：芯片字段直接暴露厂商申报口径，是排查解码问题的第一手信息。 */
    private fun buildDeviceSummaryView(): View {
        val chipParts = buildList {
            add(Build.HARDWARE)
            add(Build.BOARD)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add("${Build.SOC_MODEL}/${Build.SOC_MANUFACTURER}")
            }
        }.filter { it.isNotBlank() }.joinToString(" / ")
        val lines = listOf(
            getString(R.string.codec_info_model_format, "${Build.MANUFACTURER} ${Build.MODEL}"),
            getString(R.string.codec_info_chip_format, chipParts),
            getString(R.string.codec_info_system_format, Build.VERSION.RELEASE, Build.VERSION.SDK_INT),
            getString(
                R.string.codec_info_default_codec_format,
                VideoCodecSupport.defaultPreferredCodec(requireContext()).name
            )
        )
        val summary = ScaledTextView(requireContext()).apply {
            text = lines.joinToString("\n")
            setTextColor(resolveThemeColor(android.R.attr.textColorPrimary) ?: Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.px26))
            setLineSpacing(resources.getDimension(R.dimen.px14), 1f)
            setBackgroundResource(R.drawable.cell_background)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.px24),
                resources.getDimensionPixelSize(R.dimen.px20),
                resources.getDimensionPixelSize(R.dimen.px24),
                resources.getDimensionPixelSize(R.dimen.px20)
            )
        }
        return LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = resources.getDimensionPixelSize(R.dimen.px16) }
            addView(summary)
        }
    }

    private fun buildEntryView(entry: DecoderEntry): View {
        val context = requireContext()
        val nameRow = ScaledTextView(context).apply {
            val modeLabel = getString(
                if (entry.isHardware) R.string.codec_decoder_hardware else R.string.codec_decoder_software
            )
            text = "[$modeLabel] ${entry.name}"
            setTextColor(resolveThemeColor(android.R.attr.textColorPrimary) ?: Color.WHITE)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.px28))
        }
        val detailRow = ScaledTextView(context).apply {
            text = entry.detail
            setTextColor(resolveThemeColor(android.R.attr.textColorSecondary) ?: Color.GRAY)
            setTextSize(TypedValue.COMPLEX_UNIT_PX, resources.getDimension(R.dimen.px24))
        }
        return LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_VERTICAL
            setBackgroundResource(R.drawable.cell_background)
            setPadding(
                resources.getDimensionPixelSize(R.dimen.px24),
                resources.getDimensionPixelSize(R.dimen.px16),
                resources.getDimensionPixelSize(R.dimen.px24),
                resources.getDimensionPixelSize(R.dimen.px16)
            )
            addView(nameRow)
            addView(detailRow)
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { bottomMargin = resources.getDimensionPixelSize(R.dimen.px14) }
        }
    }

    // ===== 数据收集 =====

    private fun collectDecoderEntries(): List<DecoderEntry> {
        return runCatching {
            MediaCodecList(MediaCodecList.REGULAR_CODECS).codecInfos
                .asSequence()
                .filter { !it.isEncoder }
                .flatMap { info ->
                    val isHardware = VideoCodecSupport.isHardwareDecoder(info)
                    info.supportedTypes.asSequence()
                        // secure/DRM 变体与播放无关，过滤以减少清单噪音
                        .filterNot { mime ->
                            mime.contains("-secure", ignoreCase = true) ||
                                mime.contains("drm", ignoreCase = true)
                        }
                        .mapNotNull { mime ->
                            val caps = runCatching { info.getCapabilitiesForType(mime) }.getOrNull()
                                ?: return@mapNotNull null
                            DecoderEntry(
                                name = info.name,
                                mimeType = mime,
                                isVideo = caps.videoCapabilities != null,
                                isHardware = isHardware,
                                detail = buildDetail(mime, caps)
                            )
                        }
                }
                .sortedWith(compareBy({ !it.isVideo }, { !it.isHardware }, { it.name }, { it.mimeType }))
                .toList()
        }.getOrDefault(emptyList())
    }

    /** 参数行使用符号化格式（无语言依赖）：`mime ｜ 宽range × 高range ｜ ≤fps ｜ ≤码率`。 */
    private fun buildDetail(mime: String, caps: MediaCodecInfo.CodecCapabilities): String {
        caps.videoCapabilities?.let { video ->
            val width = "${video.supportedWidths.lower}~${video.supportedWidths.upper}"
            val height = "${video.supportedHeights.lower}~${video.supportedHeights.upper}"
            val fps = runCatching { video.supportedFrameRates }
                .getOrNull()
                ?.let { "≤${it.upper.toInt()}fps" }
            val bitrateUpper = video.bitrateRange.upper
            val bitrate = if (bitrateUpper >= 1_000_000) {
                "≤${bitrateUpper / 1_000_000}Mbps"
            } else {
                "≤${bitrateUpper / 1000}kbps"
            }
            return listOfNotNull(mime, "$width × $height", fps, bitrate).joinToString(" ｜ ")
        }
        caps.audioCapabilities?.let { audio ->
            val sampleRates = runCatching { audio.supportedSampleRates }
                .getOrNull()
                ?.takeIf { it.isNotEmpty() }
                ?.let { rates ->
                    "${(rates.minOrNull() ?: 0) / 1000}~${(rates.maxOrNull() ?: 0) / 1000}kHz"
                }
            val bitrate = "≤${audio.bitrateRange.upper / 1000}kbps"
            return listOfNotNull(mime, sampleRates, bitrate).joinToString(" ｜ ")
        }
        return mime
    }

    // ===== 辅助 =====

    private fun resolveThemeColor(attr: Int): Int? {
        val typedArray = requireContext().obtainStyledAttributes(intArrayOf(attr))
        return try {
            typedArray.getColorStateList(0)?.defaultColor
        } finally {
            typedArray.recycle()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        scope.cancel()
    }
}
