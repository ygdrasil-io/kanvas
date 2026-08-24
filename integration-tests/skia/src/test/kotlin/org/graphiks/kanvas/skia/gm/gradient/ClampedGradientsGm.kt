package org.graphiks.kanvas.skia.gm.gradient

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32
import org.graphiks.math.geometry.Point2F32
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

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
 * @see https://github.com/google/skia/blob/main/gm/gradients.cpp
 */
class ClampedGradientsGm : SkiaGm {
    override val name = "clamped_gradients"
    override val renderFamily = RenderFamily.GRADIENT
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 510

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xDD / 255f, 0xDD / 255f, 0xDD / 255f)

        val rect = RectF32(0f, 0f, 100f, 300f)
        canvas.translate(20f, 20f)

        val paint = Paint(
            antiAlias = true,
            shader = Shader.RadialGradient(
                center = Point2F32(0f, 300f),
                radius = 200f,
                stops = listOf(
                    GradientStop(0f, ColorARGB.Red),
                    GradientStop(0.25f, ColorARGB.fromRGBA(0f, 1f, 0f)),
                    GradientStop(0.5f, ColorARGB.Blue),
                    GradientStop(0.75f, ColorARGB.White),
                    GradientStop(1f, ColorARGB.Black),
                ),
                tileMode = TileMode.CLAMP,
            ),
        )
        canvas.drawRect(rect, paint)
    }
}
