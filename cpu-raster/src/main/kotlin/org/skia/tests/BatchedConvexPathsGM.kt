package org.skia.tests

import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.skia.foundation.SK_ColorBLACK
import org.skia.foundation.SkPaint
import org.skia.foundation.SkPathBuilder
import org.skia.math.SkISize
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Port of Skia's `gm/batchedconvexpaths.cpp` (`BatchedConvexPathsGM`,
 * GM name `batchedconvexpaths`).
 *
 * Black background. Ten translucent convex polygons stacked at the same
 * approximate origin, each formed of `(i + 3) * 3` cubic Bézier segments
 * sweeping a unit circle. Loop `i = 0..9`:
 *  - `numPoints = (i + 3) * 3` (so 9, 12, 15, …, 36 points);
 *  - 1 `moveTo(1, 0)` + `(numPoints / 3)` `cubicTo` segments. Each
 *    segment uses `cos/sin` of `j/numPoints`, `(j+1)/numPoints`,
 *    `(j+2)/numPoints` × 2π. The last segment snaps its endpoint back
 *    to `(1, 0)` so the contour closes cleanly.
 *  - `scale = 256 - i*24` — the path shrinks per iteration.
 *  - `paint.color = ((i + 123458383) * 285018463) | 0xFF808080` then
 *    `setAlphaf(0.3)` — the colour is "noisy" (random RGB ≥ 0x80) and
 *    the alpha is overridden to `~77/255` for translucency.
 *
 * Reference image: `batchedconvexpaths.png`, 512 × 512, BG cleared to
 * `SK_ColorBLACK`.
 *
 * Stresses :
 *  - Cubic-only convex paths (no line/quad verbs) under AA;
 *  - per-iteration `translate` + `scale` to a sub-pixel origin —
 *    triggers the rasterizer's CTM path on translucent fills;
 *  - SrcOver alpha-blend stacking (each polygon partially overlaps
 *    those drawn before it).
 */
public class BatchedConvexPathsGM : GM() {

    init { setBGColor(SK_ColorBLACK) }

    override fun getName(): String = "batchedconvexpaths"
    override fun getISize(): SkISize = SkISize.Make(512, 512)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        // Upstream `clear(SK_ColorBLACK)` happens after onDraw entry; the
        // bgColor() machinery already painted the bitmap black. No further
        // erase required.

        for (i in 0 until 10) {
            // Iso with upstream `SkAutoCanvasRestore acr(canvas, true);` at
            // the top of each loop iteration in `gm/batchedconvexpaths.cpp`.
            c.withSave {
                val numPoints = (i + 3) * 3
                val builder = SkPathBuilder()
                builder.moveTo(1f, 0f)
                var j = 1
                while (j < numPoints) {
                    val k2pi = (PI * 2.0).toFloat()
                    val a1 = j.toFloat() / numPoints * k2pi
                    val a2 = (j + 1).toFloat() / numPoints * k2pi
                    val a3 = (j + 2).toFloat() / numPoints * k2pi
                    val ex: Float
                    val ey: Float
                    if (j + 2 == numPoints) {
                        ex = 1f
                        ey = 0f
                    } else {
                        ex = cos(a3.toDouble()).toFloat()
                        ey = sin(a3.toDouble()).toFloat()
                    }
                    builder.cubicTo(
                        cos(a1.toDouble()).toFloat(), sin(a1.toDouble()).toFloat(),
                        cos(a2.toDouble()).toFloat(), sin(a2.toDouble()).toFloat(),
                        ex, ey,
                    )
                    j += 3
                }

                val scale = (256 - i * 24).toFloat()
                translate(scale + (256f - scale) * 0.33f, scale + (256f - scale) * 0.33f)
                scale(scale, scale)

                // Reproduce upstream's color computation exactly. Kotlin's
                // signed Int arithmetic matches the low 32 bits of unsigned
                // C++ integer multiply / add.
                val raw = ((i + 123458383) * 285018463) or 0xff808080.toInt()

                // Iso with upstream `gm/batchedconvexpaths.cpp`: setColor +
                // setAlphaf(0.3f). Slice 2.2 plumbs float-precision colour
                // through the F16 raster pipeline so 0.3f survives end-to-end
                // (no longer quantised to 77/255 ≈ 0.30196).
                val paint = SkPaint().apply {
                    color = raw
                    alphaf = 0.3f
                    isAntiAlias = true
                }
                drawPath(builder.detach(), paint)
            }
        }
    }
}
