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
 * Port of Skia's `gm/path_stroke_with_zero_length.cpp::zero_length_paths_dbl_aa` (1874 × 398).
 *
 * Tests rendering of zero-length stroked paths with TWO contours per cell,
 * every combination of 3 stroke caps × 6 stroke widths × 6×6 verb pairs,
 * with AA enabled. Each cell on a black background.
 * @see https://github.com/google/skia/blob/main/gm/path_stroke_with_zero_length.cpp
 */
class ZeroLengthPathsDblAaGm : SkiaGm {
    override val name = "zero_length_paths_dbl_aa"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 80.0
    override val width = 1874
    override val height = 398

    private val cellWidth = 50f
    private val cellHeight = 20f
    private val cellPad = 2f

    private val caps = listOf(StrokeCap.BUTT, StrokeCap.ROUND, StrokeCap.SQUARE)
    private val widths = listOf(0f, 0.9f, 1f, 1.1f, 15f, 25f)

    private val someVerbs: List<Path> = listOf(
        Path { moveTo(9.5f, 9.5f) },
        Path { moveTo(9.5f, 9.5f); close() },
        Path { moveTo(9.5f, 9.5f); lineTo(9.5f, 9.5f) },
        Path { moveTo(9.5f, 9.5f); lineTo(9.5f, 9.5f); close() },
        Path { moveTo(9.5f, 9.5f); quadTo(9.5f, 9.5f, 9.5f, 9.5f) },
        Path { moveTo(9.5f, 9.5f); quadTo(9.5f, 9.5f, 9.5f, 9.5f); close() },
    )

    private fun makeDblContourPath(first: Path, second: Path): Path {
        val result = Path { }
        result.addPath(first)
        result.addPath(second)
        return result
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(r = 0f, g = 0f, b = 0f)

        for (cap in caps) {
            for (strokeWidth in widths) {
                canvas.save()
                for (first in someVerbs) {
                    for (second in someVerbs) {
                        val path = makeDblContourPath(first, second)
                        val paint = Paint(
                            color = Color.WHITE,
                            antiAlias = true,
                            style = PaintStyle.STROKE,
                            strokeCap = cap,
                            strokeWidth = strokeWidth,
                        )
                        canvas.drawPath(path, paint)
                        canvas.translate(cellWidth + cellPad, 0f)
                    }
                }
                canvas.restore()
                canvas.translate(0f, cellHeight + cellPad)
            }
        }
    }
}
