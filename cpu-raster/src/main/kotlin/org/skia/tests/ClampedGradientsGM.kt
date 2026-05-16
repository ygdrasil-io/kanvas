package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.foundation.SkPaint
import org.skia.foundation.SkRadialGradient
import org.skia.foundation.SkTileMode
import org.skia.math.SkISize
import org.skia.math.SkPoint
import org.skia.math.SkRect

/**
 * Port of Skia's `gm/gradients.cpp:ClampedGradientsGM` (the `dither = true`
 * variant — `dither` is a no-op in our 8-bit pipeline, so both upstream
 * variants render identically through our renderer).
 *
 * Single radial gradient centred at `(0, 300)` with radius `200`, stops
 * `[red, green, blue, white, black]` evenly distributed under
 * [SkTileMode.kClamp]. Drawn into a `100 × 300` rect translated by
 * `(20, 20)`.
 *
 * Reference image: `clamped_gradients.png`, 640 × 510, BG `0xFFDDDDDD`.
 *
 * Stresses the radial-gradient lookup at the *corner* (the gradient's
 * centre is outside the drawn rect) — every pixel sees a non-trivial
 * distance, no degenerate `t = 0` shortcut.
 */
public class ClampedGradientsGM : GM() {

    override fun getName(): String = "clamped_gradients"
    override fun getISize(): SkISize = SkISize.Make(640, 510)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // Mirror upstream's drawColor(0xFFDDDDDD) — but route through
        // drawPaint so the colour goes through the device's colour-space
        // transform (eraseColor would skip it).
        c.drawPaint(SkPaint().apply { color = 0xFFDDDDDD.toInt() })

        val rect = SkRect.MakeLTRB(0f, 0f, 100f, 300f)
        c.translate(20f, 20f)

        val paint = SkPaint().apply {
            isAntiAlias = true
            shader = SkRadialGradient.Make(
                center = SkPoint(0f, 300f),
                radius = 200f,
                colors = intArrayOf(
                    0xFFFF0000.toInt(),    // RED
                    0xFF00FF00.toInt(),    // GREEN
                    0xFF0000FF.toInt(),    // BLUE
                    0xFFFFFFFF.toInt(),    // WHITE
                    0xFF000000.toInt(),    // BLACK
                ),
                positions = null,           // evenly spaced
                tileMode = SkTileMode.kClamp,
            )
        }
        c.drawRect(rect, paint)
    }
}
