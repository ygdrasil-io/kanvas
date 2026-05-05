package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize

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
 */
public class ClippedCubicGM : GM() {

    override fun getName(): String = "clippedcubic"
    override fun getISize(): SkISize = SkISize.Make(1240, 390)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val path = SkPathBuilder()
            .moveTo(0f, 0f)
            .cubicTo(140f, 150f, 40f, 10f, 170f, 150f)
            .detach()
        val paint = SkPaint()
        val bounds = path.computeBounds()

        var dy = -1f
        while (dy <= 1f) {
            c.save()
            var dx = -1f
            while (dx <= 1f) {
                c.save()
                c.clipRect(bounds)
                c.translate(dx, dy)
                c.drawPath(path, paint)
                c.restore()

                c.translate(bounds.width(), 0f)
                dx += 1f
            }
            c.restore()
            c.translate(0f, bounds.height())
            dy += 1f
        }
    }
}
