package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.graphiks.math.SK_ColorBLACK
import org.graphiks.math.SK_ColorWHITE
import org.skia.foundation.SkCubicResampler
import org.skia.foundation.SkFilterMode
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.skia.foundation.SkSamplingOptions
import org.skia.foundation.SkTileMode
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/bicubic.cpp::DEF_SIMPLE_GM(bicubic, ...)` (300 × 320).
 *
 * Builds a 7×7 black source surface with a single 1-px vertical white
 * line at `x = 3.5`, then draws it three ways at `canvas.scale(40, 8)` :
 *
 *  1. `drawImage(img, sampling = SkSamplingOptions(kNearest))`
 *  2. `drawImage(img, sampling = SkSamplingOptions(kLinear))`
 *  3. `drawImage(img, sampling = SkSamplingOptions(SkCubicResampler::Mitchell))`
 *
 * After each row a `translate(0, h + 1)` separates the bands.
 *
 * Then two additional rows render the same image as a **shader fill**
 * over a 7×7 destination rect, exercising `SkImage::makeShader` with the
 * cubic sampler under two presets — Catmull-Rom and Mitchell.
 *
 * This is the canary GM for Phase G2 — every other GM in the
 * bicubic-ROI (`anisotropic_image_scale_mip`, `bigbitmaprect`,
 * `bitmaprect_with_paintfilters`, `drawbitmaprect-imagerect`,
 * `image-cacherator-*`) reuses the same code path.
 */
public class BicubicGM : GM() {

    init {
        setBGColor(SK_ColorWHITE)
    }

    override fun getName(): String = "bicubic"
    override fun getISize(): SkISize = SkISize.Make(300, 320)

    private fun makeImage() = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(7, 7)).apply {
        canvas.drawColor(SK_ColorBLACK)
        // Upstream uses default-style paint; in Skia drawLine effectively
        // strokes through drawPoints. Our drawLine routes through drawPath
        // which honours paint.style, so set kStroke_Style explicitly to
        // produce the single-pixel white vertical line at x=3.5.
        val paint = SkPaint().apply {
            color = SK_ColorWHITE
            style = SkPaint.Style.kStroke_Style
        }
        canvas.drawLine(3.5f, 0f, 3.5f, 8f, paint)
    }.makeImageSnapshot()

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.clear(SK_ColorBLACK)

        val img = makeImage()

        val samplings = arrayOf(
            SkSamplingOptions(SkFilterMode.kNearest),
            SkSamplingOptions(SkFilterMode.kLinear),
            SkSamplingOptions(SkCubicResampler.Mitchell),
        )

        c.scale(40f, 8f)
        for (s in samplings) {
            c.drawImage(img, 0f, 0f, s, null)
            c.translate(0f, img.height + 1.0f)
        }

        val r = SkRect.MakeWH(img.width.toFloat(), img.height.toFloat())
        val paint = SkPaint()

        val cubics = arrayOf(
            SkCubicResampler.CatmullRom,
            SkCubicResampler.Mitchell,
        )
        for (cubic in cubics) {
            paint.shader = img.makeShader(SkTileMode.kClamp, SkTileMode.kClamp, SkSamplingOptions(cubic))
            c.drawRect(r, paint)
            c.translate(0f, img.height + 1.0f)
        }
    }
}
