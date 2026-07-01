package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/clippedcubic.cpp` (`ClippedCubicGM`).
 *
 * A 3 × 3 grid of cells, each clipping a self-intersecting cubic at
 * `(0,0)` ⇒ `(170, 150)` (control points `(140, 150)`, `(40, 10)`) to
 * the path's own bounding box, then translating by `(dx, dy)` ∈
 * `{ -1, 0, +1 }² px` before drawing. The `dx`/`dy` shifts pull a
 * sliver of the cubic outside the clip in each cell, exposing the
 * scanline rasterizer's clip-edge arithmetic on a curve.
 *
 * Reference image: `clippedcubic.png`, 1240 × 390. Black fill (default
 * paint), default `kWinding` fill rule.
 * @see https://github.com/google/skia/blob/main/gm/clippedcubic.cpp
 */
class ClippedCubicGm : SkiaGm {
    override val name = "clippedcubic"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 1240
    override val height = 390

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = Path {
            moveTo(0f, 0f)
            cubicTo(140f, 150f, 40f, 10f, 170f, 150f)
        }
        val paint = Paint()
        val bounds = Rect(0f, 0f, 170f, 150f)

        var dy = -1f
        while (dy <= 1f) {
            canvas.save()
            var dx = -1f
            while (dx <= 1f) {
                canvas.save()
                canvas.clipRect(bounds)
                canvas.translate(dx, dy)
                canvas.drawPath(path, paint)
                canvas.restore()

                canvas.translate(bounds.width, 0f)
                dx += 1f
            }
            canvas.restore()
            canvas.translate(0f, bounds.height)
            dy += 1f
        }
    }
}
