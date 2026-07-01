package org.graphiks.kanvas.skia.gm.path

/**
 * Port of Skia's `gm/widebuttcaps.cpp`.
 * Tests wide stroke butt caps with bevel, round, and miter joins on line and cubic paths.
 * @see https://github.com/google/skia/blob/main/gm/widebuttcaps.cpp
 */

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import kotlin.random.Random

class WideButtCapsGm : SkiaGm {
    override val name = "widebuttcaps"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 120 * 4
    override val height = 120 * 3 + 140

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f, 1f)
        drawTest(canvas)
    }

    private fun drawTest(canvas: GmCanvas) {
        val rand = Random(0)
        canvas.drawColor(0f, 0f, 0f, 1f)

        canvas.save()
        canvas.translate(60f, 60f)

        drawStrokes(canvas, rand,
            Path { lineTo(10f, 0f); lineTo(10f, 10f) },
            Path { cubicTo(10f, 0f, 10f, 0f, 10f, 10f) },
        )
        canvas.translate(0f, 120f)

        drawStrokes(canvas, rand,
            Path { lineTo(0f, -10f); lineTo(0f, 10f) },
            Path { cubicTo(0f, -10f, 0f, -10f, 0f, 10f) },
        )
        canvas.translate(0f, 120f)

        drawStrokes(canvas, rand,
            Path { lineTo(0f, -10f); lineTo(10f, -10f); lineTo(10f, 10f); lineTo(0f, 10f) },
            Path { cubicTo(0f, -10f, 10f, 10f, 0f, 10f) },
        )
        canvas.translate(0f, 140f)

        drawStrokes(canvas, rand,
            Path { lineTo(0f, -10f); lineTo(10f, -10f); lineTo(10f, 0f); lineTo(0f, 0f) },
            Path { cubicTo(0f, -10f, 10f, 0f, 0f, 0f) },
        )
        canvas.translate(0f, 120f)

        canvas.restore()
    }

    private fun drawStrokes(canvas: GmCanvas, rand: Random, path: Path, cubic: Path) {
        val strokePaint = Paint(
            antiAlias = true,
            strokeWidth = kStrokeWidth,
            style = PaintStyle.STROKE,
        )

        canvas.save()
        canvas.drawPath(path, strokePaint.copy(strokeJoin = StrokeJoin.BEVEL, color = nextColor(rand)))
        canvas.translate(120f, 0f)

        canvas.drawPath(path, strokePaint.copy(strokeJoin = StrokeJoin.ROUND, color = nextColor(rand)))
        canvas.translate(120f, 0f)

        canvas.drawPath(path, strokePaint.copy(strokeJoin = StrokeJoin.MITER, color = nextColor(rand)))
        canvas.translate(120f, 0f)

        canvas.drawPath(cubic, strokePaint.copy(strokeJoin = StrokeJoin.MITER, color = nextColor(rand)))
        canvas.restore()
    }

    private fun nextColor(rand: Random): Color {
        val raw = rand.nextInt() or 0xFF808080.toInt()
        val r = ((raw ushr 16) and 0xFF) / 255f
        val g = ((raw ushr 8) and 0xFF) / 255f
        val b = (raw and 0xFF) / 255f
        return Color.fromRGBA(r, g, b, 1f)
    }

    private companion object {
        const val kStrokeWidth: Float = 100f
    }
}
