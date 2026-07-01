package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/smallarc.cpp`.
 * A single red AA cubic-Bézier stroked at width 120, drawn under
 * `translate(-400, -400) ; scale(8, 8)` so the source-space ¾-arc
 * lands as a thick wedge in the centre of the 762 × 762 canvas.
 * @see https://github.com/google/skia/blob/main/gm/smallarc.cpp
 */
class SmallArcGm : SkiaGm {
    override val name = "smallarc"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 762
    override val height = 762

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = Path {
            moveTo(75f, 0f)
            cubicTo(33.5f, 0f, 0f, 33.5f, 0f, 75f)
        }

        val paint = Paint(
            color = Color.RED,
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 120f,
        )
        canvas.translate(-400f, -400f)
        canvas.scale(8f, 8f)
        canvas.drawPath(path, paint)
    }
}
