package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.graphiks.math.SkISize
import org.graphiks.math.SkMatrix
import org.graphiks.math.SkRect

/**
 * Port of Skia's `gm/cubicpaths.cpp:ClippedCubic2GM`.
 *
 * 8 cells in a 4×2 layout: a self-intersecting cubic and its 90°-flipped
 * variant (built via [SkPath.makeTransform]) drawn under various clip
 * rectangles to expose the rasterizer's behaviour with curves that exit
 * and re-enter the clip.
 *
 * The "flipped" path is constructed by applying a `(scaleX=0, scaleY=0,
 * skewX=1, skewY=1)` matrix — effectively swapping x and y, mirroring the
 * cubic across the diagonal. This is the GM that exercises the new
 * `SkPath.makeTransform(SkMatrix)` API end-to-end on a real path.
 *
 * Reference image: `clippedcubic2.png`, 1240 × 390, default white BG.
 */
public class ClippedCubic2GM : GM() {

    private lateinit var path: SkPath
    private lateinit var flipped: SkPath

    override fun getName(): String = "clippedcubic2"
    override fun getISize(): SkISize = SkISize.Make(1240, 390)

    override fun onOnceBeforeDraw() {
        path = SkPathBuilder()
            .moveTo(69.7030518991886f, 0f)
            .cubicTo(
                69.7030518991886f, 21.831149999999997f,
                58.08369508178456f, 43.66448333333333f,
                34.8449814469765f, 65.5f,
            )
            .cubicTo(
                11.608591683531916f, 87.33115f,
                -0.010765133872116195f, 109.16448333333332f,
                -0.013089005235602302f, 131f,
            )
            .close()
            .detach()

        // matrix.reset() then setScaleX(0)/setScaleY(0)/setSkewX(1)/setSkewY(1)
        // gives the affine (sx=0, kx=1, tx=0, ky=1, sy=0, ty=0). A point
        // `(x, y)` maps to `(y, x)` — a swap across the diagonal — turning
        // the original "vertical" cubic into a "horizontal" one.
        val m = SkMatrix(sx = 0f, kx = 1f, tx = 0f, ky = 1f, sy = 0f, ty = 0f)
        flipped = path.makeTransform(m)
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.save()
        c.translate(-2f, 120f)
        drawOne(c, path, SkRect.MakeLTRB(0f, 0f, 80f, 150f))
        c.translate(0f, 170f)
        drawOne(c, path, SkRect.MakeLTRB(0f, 0f, 80f, 100f))
        c.translate(0f, 170f)
        drawOne(c, path, SkRect.MakeLTRB(0f, 0f, 30f, 150f))
        c.translate(0f, 170f)
        drawOne(c, path, SkRect.MakeLTRB(0f, 0f, 10f, 150f))
        c.restore()

        c.save()
        c.translate(20f, -2f)
        drawOne(c, flipped, SkRect.MakeLTRB(0f, 0f, 150f, 80f))
        c.translate(170f, 0f)
        drawOne(c, flipped, SkRect.MakeLTRB(0f, 0f, 100f, 80f))
        c.translate(170f, 0f)
        drawOne(c, flipped, SkRect.MakeLTRB(0f, 0f, 150f, 30f))
        c.translate(170f, 0f)
        drawOne(c, flipped, SkRect.MakeLTRB(0f, 0f, 150f, 10f))
        c.restore()
    }

    private fun drawOne(c: SkCanvas, p: SkPath, clip: SkRect) {
        val frame = SkPaint().apply { style = SkPaint.Style.kStroke_Style }
        val fill = SkPaint()
        c.drawRect(clip, frame)
        c.drawPath(p, frame)
        c.save()
        c.clipRect(clip)
        c.drawPath(p, fill)
        c.restore()
    }
}
