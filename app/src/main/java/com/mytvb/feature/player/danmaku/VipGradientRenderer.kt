package com.mytvb.feature.player.danmaku

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Shader
import com.mytvb.feature.player.danmaku.common.BiliDanmakuStyle
import com.mytvb.feature.player.danmaku.common.DanmakuVipGradientStyle
import com.mytvb.feature.player.danmaku.common.VipDanmakuTextureCache

/**
 * VIP 弹幕渲染：只使用 B 站 colorful_src 贴图。
 * 没有贴图或贴图尚未加载时，按默认白色弹幕显示。
 */
internal object VipGradientRenderer {

    /** B站协议里 VIP 渐变的 colorful 字段值。 */
    const val COLORFUL_VIP_GRADIENT = 0xEA61

    /**
     * 绘制 VIP 渐变文字，完全复刻 B 站：描边色与填充色都由服务端下发的 colorful_src 贴图决定
     * （BitmapShader 把贴图铺到文字形状上）。贴图必须已加载，否则返回 false 由调用方兜底。
     *
     * 调用方需提供 [fillPaint] 和 [strokePaint]，绘制后本方法会清理 shader。
     * @return true 已按贴图绘制；false 贴图缺失，调用方应按普通弹幕渲染。
     */
    fun draw(
        canvas: Canvas,
        text: String,
        style: DanmakuVipGradientStyle,
        startX: Float,
        baselineY: Float,
        textSizePx: Float,
        opacityAlpha: Int = 255,
        fillPaint: Paint,
        strokePaint: Paint
    ): Boolean {
        if (text.isBlank()) return false
        val strokeBitmap = VipDanmakuTextureCache.getBitmap(style.strokeTextureUrl)
        val fillBitmap = VipDanmakuTextureCache.getBitmap(style.fillTextureUrl)
        // 严格复刻：贴图缺失时不做渐变/纯色兜底，交回调用方按普通弹幕渲染。
        if (strokeBitmap == null && fillBitmap == null) return false

        val alpha = opacityAlpha.coerceIn(0, 255)
        val textWidth = fillPaint.measureText(text).coerceAtLeast(1f)
        val top = baselineY + fillPaint.ascent()
        val bottom = baselineY + fillPaint.descent()
        val textHeight = (bottom - top).coerceAtLeast(1f)

        strokePaint.style = Paint.Style.STROKE
        strokePaint.strokeJoin = Paint.Join.ROUND
        strokePaint.strokeCap = Paint.Cap.ROUND
        strokePaint.clearShadowLayer()
        // 描边宽度：有描边贴图用 VIP 宽度；否则沿用普通描边宽度（只贴填充）。
        strokePaint.strokeWidth =
            if (strokeBitmap != null) BiliDanmakuStyle.resolveVipStrokeWidth(textSizePx)
            else 0f
        // 描边色完全由贴图决定；无贴边贴图时整段不画描边。
        if (strokeBitmap != null) {
            strokePaint.shader = strokeBitmap.createTextShader(startX, top, textWidth, textHeight)
            // color 仅作为 shader 的基调，alpha 统一用 opacity 控制。
            strokePaint.color = (alpha shl 24) or 0x00FFFFFF
            strokePaint.alpha = alpha
        }

        fillPaint.style = Paint.Style.FILL
        // 填充色完全由贴图决定；无填充贴图时按白色兜底（贴图协议里填充贴图一般总有）。
        if (fillBitmap != null) {
            fillPaint.shader = fillBitmap.createTextShader(startX, top, textWidth, textHeight)
            fillPaint.color = (alpha shl 24) or 0x00FFFFFF
            fillPaint.alpha = alpha
        } else {
            // 只描边贴图、无填充贴图的极少见情况，填充用白。
            fillPaint.color = (alpha shl 24) or 0x00FFFFFF
        }

        if (strokeBitmap != null && strokePaint.strokeWidth > 0.01f) {
            canvas.drawText(text, startX, baselineY, strokePaint)
        }
        canvas.drawText(text, startX, baselineY, fillPaint)

        strokePaint.shader = null
        fillPaint.shader = null
        strokePaint.clearShadowLayer()
        return true
    }

    private fun android.graphics.Bitmap.createTextShader(
        left: Float,
        top: Float,
        width: Float,
        height: Float
    ): Shader {
        return android.graphics.BitmapShader(this, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { shader ->
            val matrix = android.graphics.Matrix()
            matrix.setScale(
                width / this.width.coerceAtLeast(1).toFloat(),
                height / this.height.coerceAtLeast(1).toFloat()
            )
            matrix.postTranslate(left, top)
            shader.setLocalMatrix(matrix)
        }
    }
}
