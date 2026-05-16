package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import kotlin.math.sqrt

/**
 * Port of Skia's `gm/conicpaths.cpp:ConicPathsGM`.
 *
 * Ten conic-Bézier paths (circle, hyperbolas, near-parabola, ellipses,
 * degenerate variants) drawn under every combination of:
 *  - alpha 0xFF / 0x40 (full / partial),
 *  - AA off / on,
 *  - fill / stroke.
 *
 * That's 8 cells per path × 10 paths = 80 cells, plus a "giant circle"
 * at fixed coordinates that the rasterizer must clip cleanly.
 *
 * Reference image: `conicpaths.png`, 920 × 960, default white BG.
 *
 * Stresses [SkPathBuilder.conicTo] / [SkPathBuilder.rConicTo] flattening
 * with the full range of conic weights (0.5, 0.999, 2, sqrt(2)/2) plus
 * the bbox-tracking path of [SkPath.computeBounds].
 */
public class ConicPathsGM : GM() {

    private val paths: MutableList<SkPath> = mutableListOf()
    private lateinit var giantCircle: SkPath

    override fun getName(): String = "conicpaths"
    override fun getISize(): SkISize = SkISize.Make(920, 960)

    override fun onOnceBeforeDraw() {
        val w = (sqrt(2.0) / 2.0).toFloat()

        // 1. Conic circle (4 conics, weight = √2 / 2).
        paths.add(SkPathBuilder()
            .moveTo(0f, 0f)
            .conicTo(0f, 50f, 50f, 50f, w)
            .rConicTo(50f, 0f, 50f, -50f, w)
            .rConicTo(0f, -50f, -50f, -50f, w)
            .rConicTo(-50f, 0f, -50f, 50f, w)
            .detach())

        // 2. Hyperbola (weight=2).
        paths.add(SkPathBuilder()
            .moveTo(0f, 0f)
            .conicTo(0f, 100f, 100f, 100f, 2f)
            .detach())

        // 3. Thin hyperbola.
        paths.add(SkPathBuilder()
            .moveTo(0f, 0f)
            .conicTo(100f, 100f, 5f, 0f, 2f)
            .detach())

        // 4. Very thin hyperbola.
        paths.add(SkPathBuilder()
            .moveTo(0f, 0f)
            .conicTo(100f, 100f, 1f, 0f, 2f)
            .detach())

        // 5. Closed hyperbola (endpoint = start).
        paths.add(SkPathBuilder()
            .moveTo(0f, 0f)
            .conicTo(100f, 100f, 0f, 0f, 2f)
            .detach())

        // 6. Near-parabola (weight=0.999, falls back to quadratic-like behaviour).
        paths.add(SkPathBuilder()
            .moveTo(0f, 0f)
            .conicTo(0f, 100f, 100f, 100f, 0.999f)
            .detach())

        // 7. Thin ellipse (weight=0.5).
        paths.add(SkPathBuilder()
            .moveTo(0f, 0f)
            .conicTo(100f, 100f, 5f, 0f, 0.5f)
            .detach())

        // 8. Very thin ellipse.
        paths.add(SkPathBuilder()
            .moveTo(0f, 0f)
            .conicTo(100f, 100f, 1f, 0f, 0.5f)
            .detach())

        // 9. Closed ellipse.
        paths.add(SkPathBuilder()
            .moveTo(0f, 0f)
            .conicTo(100f, 100f, 0f, 0f, 0.5f)
            .detach())

        // 10. Giant circle at extreme coordinates — exercises bbox / clip arithmetic.
        run {
            val ww = (sqrt(2.0) / 2.0).toFloat()
            giantCircle = SkPathBuilder()
                .moveTo(2.1e+11f, -1.05e+11f)
                .conicTo(2.1e+11f, 0f, 1.05e+11f, 0f, ww)
                .conicTo(0f, 0f, 0f, -1.05e+11f, ww)
                .conicTo(0f, -2.1e+11f, 1.05e+11f, -2.1e+11f, ww)
                .conicTo(2.1e+11f, -2.1e+11f, 2.1e+11f, -1.05e+11f, ww)
                .detach()
        }
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val alphaValues = intArrayOf(0xFF, 0x40)
        val margin = 15f
        c.translate(margin, margin)

        val paint = SkPaint()
        for (p in paths.indices) {
            c.save()
            for (a in alphaValues) {
                paint.color = SkColorSetARGB(a, 0, 0, 0)
                for (aa in 0 until 2) {
                    paint.isAntiAlias = (aa != 0)
                    for (fh in 0 until 2) {
                        paint.style = if (fh != 0) SkPaint.Style.kStroke_Style
                                      else SkPaint.Style.kFill_Style

                        val bounds = paths[p].computeBounds()
                        c.save()
                        c.translate(-bounds.left, -bounds.top)
                        c.drawPath(paths[p], paint)
                        c.restore()

                        c.translate(110f, 0f)
                    }
                }
            }
            c.restore()
            c.translate(0f, 110f)
        }

        // Giant circle drawn at the very end (no save/translate — the
        // `c.translate(0, 110*N)` at the end of the loop above leaves us
        // below all the cells; upstream relies on the same flow).
        c.drawPath(giantCircle, paint)
    }
}
