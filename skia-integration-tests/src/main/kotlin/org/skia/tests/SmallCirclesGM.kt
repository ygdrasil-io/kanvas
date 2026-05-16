package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.SkSurface
import org.skia.foundation.SkImageInfo
import org.skia.foundation.SkPaint
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/smallcircles.cpp::smallcircles` (425 × 425).
 *
 * Renders an 11 × 11 grid of sub-pixel-radius (`r = 0.8`) AA circles
 * via `drawArc(0..360°)` into a 100 × 100 raster surface, then scales
 * the snapshot up by 7× when compositing onto the main canvas. The
 * test exists because earlier `drawArc` paths produced empty sweeps
 * for very small radii — the bug fix forced the analytic path through
 * `drawCircle` for tiny ovals.
 */
public class SmallCirclesGM : GM() {

    override fun getName(): String = "smallcircles"
    override fun getISize(): SkISize = SkISize.Make(425, 425)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return

        val surface = SkSurface.MakeRaster(SkImageInfo.MakeN32Premul(100, 100))
        val canv: SkCanvas = surface.canvas
        canv.translate(5f, 5f)

        val p = SkPaint().apply {
            isAntiAlias = true
            style = SkPaint.Style.kFill_Style
        }

        for (i in 0 until 11) {
            canv.save()
            for (j in 0 until 11) {
                drawSmallCircle(canv, p, 0.8f)
                canv.translate(5.1f, 0f)
            }
            canv.restore()
            canv.translate(0f, 5.1f)
        }

        // Scale-up of the small-circles image.
        c.scale(7f, 7f)
        val img = surface.makeImageSnapshot()
        c.drawImage(img, 0f, 0f)
    }

    private fun drawSmallCircle(canvas: SkCanvas, p: SkPaint, radius: Float) {
        val oval = SkRect.MakeLTRB(-radius, -radius, radius, radius)
        canvas.drawArc(oval, 0f, 360f, useCenter = false, paint = p)
    }
}
