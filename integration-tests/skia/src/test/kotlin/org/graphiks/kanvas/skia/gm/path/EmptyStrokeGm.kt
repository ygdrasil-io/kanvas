package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point

/**
 * Port of Skia's `gm/emptypath.cpp` (EmptyStrokeGM).
 * Exercises the stroker's handling of zero-length sub-paths.
 * @see https://github.com/google/skia/blob/main/gm/emptypath.cpp
 */
class EmptyStrokeGm : SkiaGm {
    override val name = "emptystroke"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 200
    override val height = 240

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val strokePaint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 21f,
            strokeCap = StrokeCap.SQUARE,
        )
        val dotPaint = Paint(color = Color.RED, strokeWidth = 7f)

        val procs = listOf<(Path) -> Unit>(
            { path ->
                for (p in kPts) path.moveTo(p.x, p.y)
            },
            { path ->
                for (p in kPts) { path.moveTo(p.x, p.y); path.close() }
            },
            { path ->
                for (p in kPts) { path.moveTo(p.x, p.y); path.lineTo(p.x, p.y) }
            },
            { path ->
                path.moveTo(kPts[0].x, kPts[0].y)
                path.moveTo(kPts[1].x, kPts[1].y); path.close()
                path.moveTo(kPts[2].x, kPts[2].y); path.lineTo(kPts[2].x, kPts[2].y)
            },
        )

        for (proc in procs) {
            for (p in kPts) {
                canvas.drawCircle(p.x, p.y, 3.5f, dotPaint)
            }
            val path = Path { }
            proc(path)
            canvas.drawPath(path, strokePaint)
            canvas.translate(0f, 40f)
        }
    }

    private companion object {
        val kPts = listOf(Point(40f, 40f), Point(80f, 40f), Point(120f, 40f))
    }
}
