package org.skia.tests

import org.graphiks.math.SK_ColorDKGRAY
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SK_ColorYELLOW
import org.graphiks.math.SkColor
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.graphiks.math.SkVector
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlurMaskFilter
import org.skia.foundation.SkBlurStyle
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkRRect
import org.skia.tools.ToolUtils
import kotlin.math.abs
import kotlin.math.min

/**
 * Port of Skia's `gm/rrect.cpp::RRectBlurGM` (`rrect_blurs`, 300 x 400).
 *
 * Draws each rounded-rect twice: once through `drawRRect`, once through
 * `drawPath(SkPath::RRect)`. The centre column is a magnified per-pixel
 * diff produced via `SkCanvas.readPixels` / `writePixels`; this is the
 * actual purpose of the GM.
 */
public class RRectBlurGM : GM() {

    override fun getName(): String = "rrect_blurs"

    override fun getISize(): SkISize = SkISize.Make(kWidth, kHeight)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.resetMatrix()
        c.clear(SK_ColorDKGRAY)

        drawBlurryRrect(
            c,
            cellY = 0,
            mf = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 1.0f, respectCTM = false) ?: return,
            color = SK_ColorWHITE,
            rr = SkRRect.MakeRectXY(SkRect.MakeWH(50f, 50f), 10f, 15f),
        )

        drawBlurryRrect(
            c,
            cellY = 100,
            mf = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 0.5f, respectCTM = false) ?: return,
            color = SK_ColorYELLOW,
            rr = SkRRect.MakeRectXY(SkRect.MakeWH(60f, 80f), 3.1f, 1.5f),
        )

        val ninePatch = SkRRect().apply {
            setNinePatch(SkRect.MakeWH(70f, 80f), 5f, 10f, 13f, 7f)
        }
        drawBlurryRrect(
            c,
            cellY = 200,
            mf = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 2.5f, respectCTM = false) ?: return,
            color = SkColorSetARGB(255, 200, 100, 30),
            rr = ninePatch,
        )

        val radii = arrayOf(
            SkVector(0f, 0f),
            SkVector(20f, 1f),
            SkVector(10f, 30f),
            SkVector(30f, 30f),
        )
        val complex = SkRRect().apply {
            setRectRadii(SkRect.MakeWH(90f, 90f), radii)
        }
        drawBlurryRrect(
            c,
            cellY = 300,
            mf = SkBlurMaskFilter.Make(SkBlurStyle.kNormal, 1.1f, respectCTM = false) ?: return,
            color = SkColorSetARGB(255, 35, 120, 220),
            rr = complex,
        )

        val labelPaint = SkPaint().apply {
            color = SK_ColorWHITE
            isAntiAlias = true
        }
        val font = ToolUtils.DefaultPortableFont()
        c.drawString("drawRRect", 15f, 15f, font, labelPaint)
        c.drawString("diff", 140f, 15f, font, labelPaint)
        c.drawString("drawPath", 220f, 15f, font, labelPaint)
        c.drawLine(100f, 0f, 100f, kHeight.toFloat(), labelPaint)
        c.drawLine(200f, 0f, 200f, kHeight.toFloat(), labelPaint)
        c.drawLine(0f, 100f, kWidth.toFloat(), 100f, labelPaint)
        c.drawLine(0f, 200f, kWidth.toFloat(), 200f, labelPaint)
        c.drawLine(0f, 300f, kWidth.toFloat(), 300f, labelPaint)
    }

    private fun drawBlurryRrect(
        canvas: SkCanvas,
        cellY: Int,
        mf: org.skia.foundation.SkMaskFilter,
        color: SkColor,
        rr: SkRRect,
    ) {
        val rrectPaint = SkPaint().apply {
            this.color = color
            maskFilter = mf
        }

        val paddingX = ((kCellSize - rr.width()) / 2f).toInt()
        val paddingY = ((kCellSize - rr.height()) / 2f).toInt()
        val left = rr.makeOffset(paddingX.toFloat(), paddingY.toFloat() + cellY)
        canvas.drawRRect(left, rrectPaint)

        val right = rr.makeOffset(2f * kCellSize + paddingX, paddingY.toFloat() + cellY)
        canvas.drawPath(SkPath.RRect(right), rrectPaint)

        val info = SkImageInfo.MakeN32Premul(kCellSize, kCellSize)
        val leftBitmap = SkBitmap.allocPixels(info)
        if (!canvas.readPixels(leftBitmap, 0, cellY)) return
        val rightBitmap = SkBitmap.allocPixels(info)
        if (!canvas.readPixels(rightBitmap, 2 * kCellSize, cellY)) return

        val diffBitmap = SkBitmap.allocPixels(info)
        for (y in 0 until kCellSize) {
            for (x in 0 until kCellSize) {
                val leftColor = leftBitmap.getPixel(x, y)
                val rightColor = rightBitmap.getPixel(x, y)
                val diff =
                    abs(SkColorGetA(leftColor) - SkColorGetA(rightColor)) +
                        abs(SkColorGetR(leftColor) - SkColorGetR(rightColor)) +
                        abs(SkColorGetG(leftColor) - SkColorGetG(rightColor)) +
                        abs(SkColorGetB(leftColor) - SkColorGetB(rightColor))
                val grey = min(diff * kDiffMagnification, 255)
                diffBitmap.setPixel(x, y, SkColorSetARGB(0xFF, grey, grey, grey))
            }
        }

        canvas.writePixels(diffBitmap, kCellSize, cellY)
    }

    public companion object {
        private const val kWidth = 300
        private const val kHeight = 400
        private const val kCellSize = 100
        private const val kDiffMagnification = 16
    }
}
