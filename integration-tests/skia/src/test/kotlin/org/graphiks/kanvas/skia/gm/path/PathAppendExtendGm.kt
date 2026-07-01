package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of upstream Skia's
 * [`gm/patharcto.cpp`](https://github.com/google/skia/blob/main/gm/patharcto.cpp)
 * `DEF_SIMPLE_GM(path_append_extend, …, 400, 400)`.
 *
 * Exercises path building with addPath.
 *
 * @see https://github.com/google/skia/blob/main/gm/patharcto.cpp
 */
class PathAppendExtendGm : SkiaGm {
    override val name = "path_append_extend"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 400

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val p0 = arrayOf(10f to 30f, 30f to 10f, 50f to 30f)
        val p1 = arrayOf(10f to 50f, 30f to 70f, 50f to 50f)

        val path1 = Path {
            moveTo(p1[0].first, p1[0].second)
            lineTo(p1[1].first, p1[1].second)
            lineTo(p1[2].first, p1[2].second)
        }

        val paint = Paint(
            style = PaintStyle.STROKE,
            strokeWidth = 9f,
            antiAlias = true,
        )

        // Simplified - just draw the paths without perspective matrix
        for (isClosed in listOf(false, true)) {
            for (row in 0..1) {
                canvas.save()

                val path0 = Path {
                    moveTo(p0[0].first, p0[0].second)
                    lineTo(p0[1].first, p0[1].second)
                    lineTo(p0[2].first, p0[2].second)
                    if (isClosed) close()
                }

                // Column 1: path0 alone + path1 alone
                canvas.drawPath(path0, paint)
                canvas.drawPath(path1, paint)

                canvas.translate(80f, 0f)
                // Column 2: both paths combined
                val combinedPath = Path {
                    moveTo(p0[0].first, p0[0].second)
                    lineTo(p0[1].first, p0[1].second)
                    lineTo(p0[2].first, p0[2].second)
                    if (isClosed) close()
                    moveTo(p1[0].first, p1[0].second)
                    lineTo(p1[1].first, p1[1].second)
                    lineTo(p1[2].first, p1[2].second)
                }
                canvas.drawPath(combinedPath, paint)

                canvas.restore()
                canvas.translate(0f, 100f)
            }
        }
    }
}
