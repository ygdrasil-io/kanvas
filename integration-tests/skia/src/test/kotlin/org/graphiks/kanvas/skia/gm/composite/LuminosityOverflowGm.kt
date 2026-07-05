package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/luminosity.cpp::luminosity_overflow`.
 * Reproduces b/359049360 — kLuminosity blend dividing by destination
 * luminance producing black overflow boxes on certain GPUs.
 * @see https://github.com/google/skia/blob/main/gm/luminosity.cpp
 */
class LuminosityOverflowGm : SkiaGm {
    override val name = "luminosity_overflow"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rgbs = intArrayOf(243, 247, 251, 255)

        canvas.save()
        for (r in rgbs) {
            for (g in rgbs) {
                for (b in rgbs) {
                    val paint = Paint(color = Color.fromRGBA(r / 255f, g / 255f, b / 255f, 1f))
                    canvas.drawRect(Rect.fromXYWH(0f, 0f, 4f, 256f), paint)
                    canvas.translate(4f, 0f)
                }
            }
        }
        canvas.restore()

        for (a in 1..16) {
            val paint = Paint(
                color = Color.fromRGBA(1f, 1f, 1f, a / 255f),
                blendMode = BlendMode.LUMINOSITY,
            )
            canvas.drawRect(Rect.fromXYWH(0f, 0f, 256f, 16f), paint)
            canvas.translate(0f, 16f)
        }
    }
}
