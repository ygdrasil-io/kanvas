package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/trim.cpp`.
 * Tests path trim (offset) rendering on cubic, line, and polyline paths.
 * @see https://github.com/google/skia/blob/main/gm/trim.cpp
 */

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

class TrimGm : SkiaGm {
    override val name = "trimpatheffect"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 74.1
    override val width = 1400
    override val height = 1000

    private val paths: List<Path> = listOf(
        Path {
            moveTo(0f, 100f); cubicTo(10f, 50f, 190f, 50f, 200f, 100f)
            moveTo(200f, 100f); cubicTo(210f, 150f, 390f, 150f, 400f, 100f)
            moveTo(400f, 100f); cubicTo(390f, 50f, 210f, 50f, 200f, 100f)
            moveTo(200f, 100f); cubicTo(190f, 150f, 10f, 150f, 0f, 100f)
        },
        Path {
            moveTo(0f, 75f); lineTo(200f, 75f)
            moveTo(200f, 91f); lineTo(200f, 91f)
            moveTo(200f, 108f); lineTo(200f, 108f)
            moveTo(200f, 125f); lineTo(400f, 125f)
        },
        Path {
            moveTo(0f, 100f); lineTo(50f, 50f)
            moveTo(50f, 50f); lineTo(150f, 150f)
            moveTo(150f, 150f); lineTo(250f, 50f)
            moveTo(250f, 50f); lineTo(350f, 150f)
            moveTo(350f, 150f); lineTo(400f, 100f)
        },
    )

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val hairlinePaint = Paint(
            antiAlias = true,
            style = PaintStyle.STROKE,
            strokeCap = StrokeCap.ROUND,
            strokeWidth = 2f,
        )
        val normalPaint = hairlinePaint.copy(
            strokeWidth = 10f,
            color = Color.fromRGBA(0f, 1f, 0f, 0.5f),
        )
        val invertedPaint = normalPaint.copy(
            color = Color.fromRGBA(1f, 0f, 0f, 0.5f),
        )

        for (offset in OFFSETS) {
            canvas.save()
            for (path in paths) {
                canvas.drawPath(path, normalPaint)
                canvas.drawPath(path, invertedPaint)
                canvas.drawPath(path, hairlinePaint)
                canvas.translate(CELL_WIDTH, 0f)
            }
            canvas.restore()
            canvas.translate(0f, CELL_HEIGHT)
        }
    }

    private companion object {
        private const val CELL_WIDTH = 440f
        private const val CELL_HEIGHT = 150f
        private val OFFSETS = arrayOf(
            floatArrayOf(-0.33f, -0.66f),
            floatArrayOf(0f, 1f),
            floatArrayOf(0f, 0.25f),
            floatArrayOf(0.25f, 0.75f),
            floatArrayOf(0.75f, 1f),
            floatArrayOf(1f, 0.75f),
        )
    }
}
