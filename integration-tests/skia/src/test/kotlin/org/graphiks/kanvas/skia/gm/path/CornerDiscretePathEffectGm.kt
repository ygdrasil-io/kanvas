package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/patheffects.cpp` — CornerDiscrete path effect smoke test.
 * Validates SkCornerPathEffect + SkDiscretePathEffect composition via the
 * pathEffect pipeline.
 * @see https://github.com/google/skia/blob/main/gm/patheffects.cpp
 */
class CornerDiscretePathEffectGm : SkiaGm {
    override val name = "corner_discrete_path_effect"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 320

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val zigzag = Path {
            moveTo(20f, 60f)
            lineTo(60f, 20f)
            lineTo(100f, 100f)
            lineTo(140f, 30f)
        }

        val configs = arrayOf(
            Triple(0f, 0f, 0f),
            Triple(15f, 0f, 0f),
            Triple(0f, 8f, 4f),
            Triple(15f, 8f, 4f),
            Triple(0f, 0f, 0f),
            Triple(30f, 0f, 0f),
            Triple(0f, 16f, 8f),
            Triple(30f, 16f, 8f),
        )

        for ((i, cfg) in configs.withIndex()) {
            val col = i % 4
            val row = i / 4
            val ox = 10f + col * 160f
            val oy = 10f + row * 160f

            canvas.save()
            canvas.translate(ox, oy)

            val (r, segLen, dev) = cfg
            val pathEffect = if (segLen > 0f && dev > 0f && r <= 0f) {
                PathEffect.Discrete(segLen, dev)
            } else if (r > 0f) {
                PathEffect.Corner(r)
            } else {
                null
            }

            val paint = Paint(
                color = Color.BLACK,
                antiAlias = true,
                style = PaintStyle.STROKE,
                strokeWidth = 2f,
                pathEffect = pathEffect,
            )
            canvas.drawPath(zigzag, paint)
            canvas.restore()
        }
    }
}
