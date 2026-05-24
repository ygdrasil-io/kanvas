package org.skia.tests

import org.skia.core.SkCanvas
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorTRANSPARENT
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.skia.foundation.SkBlurMask
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathDirection
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkTileMode

/**
 * Port of upstream Skia `gm/blurrect.cpp::BlurRectGM`.
 *
 * Original draws a wide matrix of rects with `SkMaskFilter::MakeBlur`
 * across all `SkBlurStyle` values, with / without a radial gradient
 * shader and clipping, at two scales. Exercises the analytic
 * fast-path for blurred rects.
 */
public class BlurRectGM(
    private val gmName: String = "blurrects",
    private val alpha: Int = 0xFF,
) : GM() {
    override fun getName(): String = gmName
    override fun getISize(): SkISize = SkISize.Make(860, 820)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val rect = SkRect.MakeWH(100f, 50f)
        val scales = floatArrayOf(1f, 0.6f)

        c.translate(STROKE_WIDTH * 1.5f, STROKE_WIDTH * 1.5f)

        for (scale in scales) {
            c.save()
            for (style in SkBlurStyle.entries) {
                val paint = SkPaint().apply {
                    isAntiAlias = true
                    color = SK_ColorBLACK
                    this.alpha = this@BlurRectGM.alpha
                    maskFilter = SkBlurMaskFilter.Make(style, blurSigma)
                }
                val radialPaint = paint.copy().apply {
                    shader = makeRadial(rect)
                }

                c.save()
                c.scale(scale, scale)
                drawProcs(c, rect, paint, doClip = false)
                c.translate(rect.width() * 4f / 3f, 0f)
                drawProcs(c, rect, radialPaint, doClip = false)
                c.translate(rect.width() * 4f / 3f, 0f)
                drawProcs(c, rect, paint, doClip = true)
                c.translate(rect.width() * 4f / 3f, 0f)
                drawProcs(c, rect, radialPaint, doClip = true)
                c.restore()

                c.translate(0f, PROC_COUNT * rect.height() * 4f / 3f * scale)
            }
            c.restore()
            c.translate(4f * rect.width() * 4f / 3f * scale, 0f)
        }
    }

    private fun drawProcs(canvas: SkCanvas, rect: SkRect, paint: SkPaint, doClip: Boolean) {
        canvas.save()
        val procs = arrayOf(::fillRect, ::drawDonut, ::drawDonutSkewed)
        for (proc in procs) {
            if (doClip) {
                val clipRect = rect.makeInset(STROKE_WIDTH / 2f, STROKE_WIDTH / 2f)
                canvas.save()
                canvas.clipRect(clipRect)
            }
            proc(canvas, rect, paint)
            if (doClip) {
                canvas.restore()
            }
            canvas.translate(0f, rect.height() * 4f / 3f)
        }
        canvas.restore()
    }

    private fun fillRect(canvas: SkCanvas, rect: SkRect, paint: SkPaint) {
        canvas.drawRect(rect, paint)
    }

    private fun drawDonut(canvas: SkCanvas, rect: SkRect, paint: SkPaint) {
        canvas.drawPath(makeDonut(rect), paint)
    }

    private fun drawDonutSkewed(canvas: SkCanvas, rect: SkRect, paint: SkPaint) {
        canvas.save()
        canvas.translate(rect.centerX(), rect.centerY())
        canvas.skew(0.35f, 0f)
        canvas.translate(-rect.centerX(), -rect.centerY())
        canvas.drawPath(makeDonut(rect), paint)
        canvas.restore()
    }

    private fun makeDonut(rect: SkRect): SkPath {
        val outer = rect
        val inner = rect.makeInset(STROKE_WIDTH, STROKE_WIDTH)
        return SkPathBuilder(SkPathFillType.kWinding)
            .addOval(outer, SkPathDirection.kCW)
            .addOval(inner, SkPathDirection.kCCW)
            .detach()
    }

    private fun makeRadial(rect: SkRect) = SkRadialGradient.Make(
        center = SkPoint(rect.centerX(), rect.centerY()),
        radius = rect.width() * 0.5f,
        colors = intArrayOf(SK_ColorWHITE, SK_ColorTRANSPARENT, SK_ColorBLACK),
        positions = floatArrayOf(0f, 0.65f, 1f),
        tileMode = SkTileMode.kClamp,
    )

    private companion object {
        const val STROKE_WIDTH: Float = 20f
        const val PROC_COUNT: Int = 3

        val blurSigma: Float = SkBlurMask.ConvertRadiusToSigma(STROKE_WIDTH / 2f)
    }
}
