package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.math.SK_ColorWHITE
import org.skia.math.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkPathFillType
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkMatrix
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of upstream Skia's `gm/filltypespersp.cpp` :
 * `DEF_GM(return new FillTypePerspGM;)`.
 *
 * 4×4 grid showing each `SkPathFillType` (`kWinding`, `kEvenOdd`,
 * `kInverseWinding`, `kInverseEvenOdd`) drawn in perspective with
 * an underlying radial-gradient background. Two columns × two rows :
 * column = scale (1× / 1.25×) ; row = AA (off / on).
 *
 * The path is two overlapping circles — exercising winding rules
 * with an explicit ambiguous overlap region.
 */
public class FillTypePerspGM : GM() {

    override fun getName(): String = "filltypespersp"
    override fun getISize(): SkISize = SkISize.Make(835, 840)

    private lateinit var fPath: SkPath

    override fun onOnceBeforeDraw() {
        val radius = 45f
        fPath = SkPathBuilder()
            .addCircle(50f, 50f, radius)
            .addCircle(100f, 100f, radius)
            .detach()
    }

    private fun showPath(canvas: SkCanvas, x: Int, y: Int, ft: SkPathFillType,
                         scale: Float, paint: SkPaint) {
        val r = SkRect.MakeLTRB(0f, 0f, 150f, 150f)
        canvas.save()
        canvas.translate(x.toFloat(), y.toFloat())
        canvas.clipRect(r)
        canvas.drawColor(SK_ColorWHITE)
        val withFill = fPath.makeFillType(ft)
        canvas.translate(r.centerX(), r.centerY())
        canvas.scale(scale, scale)
        canvas.translate(-r.centerX(), -r.centerY())
        canvas.drawPath(withFill, paint)
        canvas.restore()
    }

    private fun showFour(canvas: SkCanvas, scale: Float, aa: Boolean) {
        val center = SkPoint(100f, 100f)
        val colors = intArrayOf(
            SkColorSetARGB(0xFF, 0, 0, 0xFF),       // blue
            SkColorSetARGB(0xFF, 0xFF, 0, 0),       // red
            SkColorSetARGB(0xFF, 0, 0xFF, 0),       // green
        )
        val pos = floatArrayOf(0f, 0.5f, 1f)
        val paint = SkPaint().apply {
            shader = SkRadialGradient.Make(center, 100f, colors, pos, SkTileMode.kClamp)
            isAntiAlias = aa
        }
        showPath(canvas,   0,   0, SkPathFillType.kWinding,        scale, paint)
        showPath(canvas, 200,   0, SkPathFillType.kEvenOdd,        scale, paint)
        showPath(canvas,   0, 200, SkPathFillType.kInverseWinding, scale, paint)
        showPath(canvas, 200, 200, SkPathFillType.kInverseEvenOdd, scale, paint)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        // Perspective background : black → cyan → yellow → white radial gradient.
        val bgCenter = SkPoint(100f, 100f)
        val bgColors = intArrayOf(
            SkColorSetARGB(0xFF, 0, 0, 0),                  // black
            SkColorSetARGB(0xFF, 0, 0xFF, 0xFF),            // cyan
            SkColorSetARGB(0xFF, 0xFF, 0xFF, 0),            // yellow
            SkColorSetARGB(0xFF, 0xFF, 0xFF, 0xFF),         // white
        )
        val bgPos = floatArrayOf(0f, 0.25f, 0.75f, 1f)
        val bgPaint = SkPaint().apply {
            shader = SkRadialGradient.Make(bgCenter, 1000f, bgColors, bgPos, SkTileMode.kClamp)
        }
        c.save()
        c.translate(100f, 100f)
        c.concat(SkMatrix.Identity.copy(persp1 = 1f / 1000f))
        c.drawPaint(bgPaint)
        c.restore()

        // Path drawing in perspective.
        c.concat(SkMatrix.Identity.copy(persp0 = -1f / 1800f, persp1 = 1f / 500f))
        c.translate(20f, 20f)
        val scale = 5f / 4f

        showFour(c, 1f, false)
        c.translate(450f, 0f)
        showFour(c, scale, false)

        c.translate(-450f, 450f)
        showFour(c, 1f, true)
        c.translate(450f, 0f)
        showFour(c, scale, true)
    }
}
