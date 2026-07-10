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
 * Port of Skia's `gm/path_stroke_with_zero_length.cpp::zero_length_paths_aa` (522 × 398).
 *
 * Tests rendering of zero-length stroked paths with every combination of
 * 3 stroke caps × 6 stroke widths × 10 path verbs, with AA enabled.
 * Each cell shows the rendered cap geometry on a black background.
 * @see https://github.com/google/skia/blob/main/gm/path_stroke_with_zero_length.cpp
 */
class ZeroLengthPathsAaGm : SkiaGm {
    override val name = "zero_length_paths_aa"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 80.0
    override val width = 522
    override val height = 398

    private val cellWidth = 50f
    private val cellHeight = 20f
    private val cellPad = 2f

    private val caps = listOf(StrokeCap.BUTT, StrokeCap.ROUND, StrokeCap.SQUARE)
    private val widths = listOf(0f, 0.9f, 1f, 1.1f, 15f, 25f)

    private val verbs: List<Path> = listOf(
        Path { moveTo(24.5f, 9.5f) },
        Path { moveTo(24.5f, 9.5f); close() },
        Path { moveTo(24.5f, 9.5f); lineTo(24.5f, 9.5f) },
        Path { moveTo(24.5f, 9.5f); lineTo(24.5f, 9.5f); close() },
        Path { moveTo(24.5f, 9.5f); quadTo(24.5f, 9.5f, 24.5f, 9.5f) },
        Path { moveTo(24.5f, 9.5f); quadTo(24.5f, 9.5f, 24.5f, 9.5f); close() },
        Path { moveTo(24.5f, 9.5f); cubicTo(24.5f, 9.5f, 24.5f, 9.5f, 24.5f, 9.5f) },
        Path { moveTo(24.5f, 9.5f); cubicTo(24.5f, 9.5f, 24.5f, 9.5f, 24.5f, 9.5f); close() },
        Path { moveTo(24.5f, 9.5f); arcTo(0f, 0f, 0f, false, false, 0f, 0f) },
        Path { moveTo(24.5f, 9.5f); arcTo(0f, 0f, 0f, false, false, 0f, 0f); close() },
    )

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(r = 0f, g = 0f, b = 0f)

        for (cap in caps) {
            for (strokeWidth in widths) {
                canvas.save()
                for (verb in verbs) {
                    val paint = Paint(
                        color = Color.WHITE,
                        antiAlias = true,
                        style = PaintStyle.STROKE,
                        strokeCap = cap,
                        strokeWidth = strokeWidth,
                    )
                    canvas.drawPath(verb, paint)
                    canvas.translate(cellWidth + cellPad, 0f)
                }
                canvas.restore()
                canvas.translate(0f, cellHeight + cellPad)
            }
        }
    }
}
