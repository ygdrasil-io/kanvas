package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/arcto.cpp` (`DEF_SIMPLE_GM(bug583299, canvas, 300, 300)`).
 * Regression test for bug 583299 — degenerate dash covering the entire path.
 * @see https://github.com/google/skia/blob/main/gm/arcto.cpp
 */
class Bug583299Gm : SkiaGm {
    override val name = "bug583299"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 83.9
    override val width = 300
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val path = Path {
            moveTo(60f, 60f)
            arcTo(50f, 50f, 0f, false, false, 160f, 60f)
            arcTo(50f, 50f, 0f, false, false, 60f, 60f)
            close()
        }
        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 100f,
            antiAlias = true,
            color = Color.fromRGBA(0f, 0x82 / 255f, 0f),
            strokeCap = StrokeCap.SQUARE,
            pathEffect = PathEffect.Dash(floatArrayOf(0f, 1000f), 0f),
        )
        canvas.drawPath(path, paint)
    }
}
