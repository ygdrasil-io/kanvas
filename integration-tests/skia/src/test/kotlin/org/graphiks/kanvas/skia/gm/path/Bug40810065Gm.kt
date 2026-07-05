package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/**
 * Port of Skia's `gm/bug12866.cpp::bug40810065`.
 * Stroker recursion-limit regression test: two near-identical cubic paths
 * (last point differs by 0.01 px) drawn under scale(2, 2) with kRound_Cap.
 * @see https://github.com/google/skia/blob/main/gm/bug12866.cpp
 */
class Bug40810065Gm : SkiaGm {
    override val name = "bug40810065"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 82.9
    override val width = 256
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.scale(2f, 2f)

        val path1 = Path {
            moveTo(108.87f, 3.78f)
            cubicTo(201.1f, -128.61f, 34.21f, 82.54f, 134.14f, 126.01f)
        }
        val path2 = Path {
            moveTo(108.87f, 3.78f)
            cubicTo(201f, -128.61f, 34.21f, 82.54f, 134.14f, 126f)
        }

        val stroke = Paint(
            color = Color.BLACK,
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeWidth = 1f,
            strokeCap = StrokeCap.ROUND,
        )

        canvas.save()
        canvas.translate(-75f, 50f)
        canvas.drawPath(path1, stroke)
        canvas.restore()

        canvas.save()
        canvas.translate(-20f, 100f)
        canvas.drawPath(path2, stroke)
        canvas.restore()
    }
}
