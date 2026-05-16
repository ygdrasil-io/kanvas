package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.math.SkISize

/**
 * Port of Skia's `gm/strokes.cpp` (`Strokes4GM`).
 *
 * One stroked circle drawn at `(0, 2)` with radius `1.97` and stroke width
 * `0.055`, under a `1000 ×` uniform CTM scale. Net device-space geometry
 * is a stroke ~55 px wide on a circle of radius ~1970 px centred at
 * `(0, 2000)` — only the bottom-of-canvas slice falls inside the
 * 400 × 800 viewport, producing a thick horizontal strip.
 *
 * Reference image: `strokes_zoomed.png`, 400 × 800. The test stresses the
 * stroker under large CTM scale (the inverse of [TeenyStrokesGM]'s tiny
 * scales, so the union covers ~9 orders of magnitude of CTM scale).
 */
public class Strokes4GM : GM() {

    override fun getName(): String = "strokes_zoomed"
    override fun getISize(): SkISize = SkISize.Make(W, H * 2)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        val paint = SkPaint().apply {
            style = SkPaint.Style.kStroke_Style
            strokeWidth = 0.055f
        }
        c.scale(1000f, 1000f)
        c.drawCircle(0f, 2f, 1.97f, paint)
    }

    private companion object {
        // From upstream `gm/strokes.cpp` (W = H = 400, N = 50).
        const val W = 400
        const val H = 400
    }
}
