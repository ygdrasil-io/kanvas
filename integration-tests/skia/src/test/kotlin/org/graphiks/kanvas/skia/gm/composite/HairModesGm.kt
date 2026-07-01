package org.graphiks.kanvas.skia.gm.composite

/**
 * Port of Skia's `gm/hairmodes.cpp`.
 * Tests all blend modes on hairline strokes drawn from center of cell with oval background.
 * @see https://github.com/google/skia/blob/main/gm/hairmodes.cpp
 */

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect
import org.graphiks.kanvas.types.r
import org.graphiks.kanvas.types.g
import org.graphiks.kanvas.types.b
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class HairModesGm : SkiaGm {
    override val name = "hairmodes"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 42.9
    override val width = 640
    override val height = 480

    private val cellW = 64f
    private val cellH = 64f

    private val modes = listOf(
        BlendMode.CLEAR, BlendMode.SRC, BlendMode.DST,
        BlendMode.SRC_OVER, BlendMode.DST_OVER,
        BlendMode.SRC_IN, BlendMode.DST_IN,
        BlendMode.SRC_OUT, BlendMode.DST_OUT,
        BlendMode.SRC_ATOP, BlendMode.DST_ATOP,
        BlendMode.XOR,
    )

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val alphaValues = intArrayOf(0xFF, 0x88, 0x88)
        canvas.translate(4f, 4f)

        for (alpha in 0 until 4) {
            canvas.save()
            canvas.save()
            for (i in modes.indices) {
                if (i == 6) {
                    canvas.restore()
                    canvas.translate(cellW * 5, 0f)
                    canvas.save()
                }
                val a0 = alphaValues[alpha and 1]
                val a1 = alphaValues[alpha and 2]
                drawCell(canvas, modes[i], a0, a1)
                canvas.translate(0f, cellH * 5f / 4f)
            }
            canvas.restore()
            canvas.restore()
            canvas.translate(cellW * 5f / 4f, 0f)
        }
    }

    private fun drawCell(canvas: GmCanvas, mode: BlendMode, a0: Int, a1: Int) {
        drawCheckerboard(canvas)
        val r = Rect(cellW / 10f, cellH / 10f, cellW - cellW / 10f, cellH - cellH / 10f)
        val ovalPaint = Paint(color = colorWithAlpha(Color.BLUE, a0), antiAlias = true)
        canvas.drawOval(r, ovalPaint)

        val linesPaint = Paint(color = colorWithAlpha(Color.RED, a1), blendMode = mode, style = PaintStyle.STROKE)
        for (angle in 0 until 24) {
            val theta = angle * (2.0 * PI) / 24.0
            val dx = (cos(theta) * cellW).toFloat()
            val dy = (sin(theta) * cellH).toFloat()
            val sw = (angle * 2f) / 24f
            val p = linesPaint.copy(strokeWidth = sw)
            canvas.drawLine(cellW / 2f, cellH / 2f, cellW / 2f + dx, cellH / 2f + dy, p)
        }
    }

    private fun drawCheckerboard(canvas: GmCanvas) {
        val cellSize = 6f
        val white = 0xFFFFFFFF.toInt()
        val gray = 0xFFCECFCE.toInt()
        var y = 0f
        while (y < cellH) {
            var x = 0f
            while (x < cellW) {
                val cx = (x / cellSize).toInt()
                val cy = (y / cellSize).toInt()
                val color = if (((cx + cy) and 1) == 0) white else gray
                canvas.drawRect(
                    Rect(x, y, (x + cellSize).coerceAtMost(cellW), (y + cellSize).coerceAtMost(cellH)),
                    Paint(color = intToColor(color)),
                )
                x += cellSize
            }
            y += cellSize
        }
    }

    private fun intToColor(value: Int): Color {
        val a = (value ushr 24) and 0xFF
        val r = (value ushr 16) and 0xFF
        val g = (value ushr 8) and 0xFF
        val b = value and 0xFF
        return Color.fromRGBA(r / 255f, g / 255f, b / 255f, a / 255f)
    }

    private fun colorWithAlpha(color: Color, alpha: Int): Color {
        return Color.fromRGBA(color.r, color.g, color.b, alpha / 255f)
    }
}
