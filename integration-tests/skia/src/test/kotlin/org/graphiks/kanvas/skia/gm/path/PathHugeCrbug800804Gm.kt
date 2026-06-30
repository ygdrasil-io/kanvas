package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.Paint
import org.graphiks.kanvas.PaintStyle
import org.graphiks.kanvas.Path
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/path_huge_crbug_800804.cpp`.
 * Exercises paths with extreme coordinate values with varying stroke widths.
 * @see https://github.com/google/skia/blob/main/gm/path_huge_crbug_800804.cpp
 */
class PathHugeCrbug800804Gm : SkiaGm {
    override val name = "path_huge_crbug_800804"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 50
    override val height = 600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint().apply {
            r = 0f; g = 0f; b = 0f; a = 1f
            antiAlias = true
            style = PaintStyle.STROKE
        }
        for (w in floatArrayOf(0.9f, 1.0f, 1.1f)) {
            paint.strokeWidth = w
            val pathA = Path().apply {
                moveTo(-1000f, 12345678901234567890f)
                lineTo(10.5f, 200f)
            }
            canvas.drawPath(pathA, paint)
            val pathB = Path().apply {
                moveTo(30.5f, 400f)
                lineTo(1000f, -9.8765432109876543210e+19f)
            }
            canvas.drawPath(pathB, paint)
            canvas.translate(3f, 0f)
        }
    }
}
