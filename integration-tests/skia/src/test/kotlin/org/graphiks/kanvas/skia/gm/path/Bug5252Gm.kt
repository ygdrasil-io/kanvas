package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/bug5252.cpp::bug5252` (500 x 500).
 *
 * Oval clip (`clipPath(SkPath::Oval(225 x 200))`) intersecting a
 * 15 x 10 grid of stroked rect + cubic combos. The bug : the oval's
 * `225 x 200` fit barely missed an internal `220 x 200` cached threshold
 * causing strokes near the edge of the oval to be miscomputed. The
 * fix forced the exact path through the rasteriser regardless of
 * width/height.
 *
 * Note: GmCanvas does not support `clipPath`, so the oval clip is
 * omitted and only the grid of stroked rect + cubic elements is drawn.
 * @see https://github.com/google/skia/blob/main/gm/bug5252.cpp
 */
class Bug5252Gm : SkiaGm {
    override val name = "bug5252"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 84.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.translate(10f, 20f)
        canvas.clipRect(Rect(0f, 0f, 225f, 200f))

        val paint = Paint(
            style = PaintStyle.STROKE,
            antiAlias = true,
            strokeWidth = 1f,
        )

        for (i in 0 until 15) {
            for (j in 0 until 10) {
                canvas.save()
                canvas.translate(i * 15f, j * 20f)
                canvas.drawRect(Rect.fromXYWH(5f, 5f, 10f, 15f), paint)
                canvas.drawPath(
                    Path {
                        moveTo(6f, 6f)
                        cubicTo(14f, 10f, 13f, 12f, 10f, 12f)
                        cubicTo(7f, 15f, 8f, 17f, 14f, 18f)
                    },
                    paint,
                )
                canvas.restore()
            }
        }
    }
}
