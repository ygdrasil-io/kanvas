package org.graphiks.kanvas.skia.gm.text

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.StrokeJoin
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.PI

/**
 * Port of Skia's `gm/drawatlas.cpp::drawTextRSXform` (430 × 860).
 * Draws the Latin alphabet along two ovals (CW and CCW) with per-glyph rotation,
 * using a linear red-to-blue gradient shader. Repeats with stroke style.
 * RSXform is emulated via per-glyph canvas save/rotate/translate.
 * @see https://github.com/google/skia/blob/main/gm/drawatlas.cpp
 */
class DrawTextRSXformGm : SkiaGm {
    override val name = "drawTextRSXform"
    override val renderFamily = RenderFamily.TEXT
    override val renderCost = RenderCost.MEDIUM
    override val minSimilarity = 0.0
    override val width = 430
    override val height = 860

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        canvas.scale(0.5f, 0.5f)

        val fillPaint = createGradientPaint(doStroke = false)
        drawTextOnOval(canvas, fillPaint)

        canvas.translate(0f, 860f)

        val strokePaint = createGradientPaint(doStroke = true)
        drawTextOnOval(canvas, strokePaint)
    }

    private fun createGradientPaint(doStroke: Boolean): Paint {
        val shader = Shader.LinearGradient(
            start = Point(0f, 0f),
            end = Point(220f, 0f),
            stops = listOf(
                GradientStop(0f, Color.fromRGBA(1f, 0f, 0f, 1f)),
                GradientStop(1f, Color.fromRGBA(0f, 0f, 1f, 1f)),
            ),
            tileMode = TileMode.MIRROR,
        )
        return if (doStroke) {
            Paint(
                shader = shader,
                style = PaintStyle.STROKE,
                strokeWidth = 2.25f,
                strokeJoin = StrokeJoin.ROUND,
            )
        } else {
            Paint(shader = shader)
        }
    }

    private fun drawTextOnOval(canvas: GmCanvas, paint: Paint) {
        val text = "ABCDFGHJKLMNOPQRSTUVWXYZ"
        val font = Font(typeface, size = 100f)
        val n = text.length

        // Measure individual glyph widths
        val widths = FloatArray(n) { i ->
            font.measureText(text.substring(i, i + 1))
        }
        val totalWidth = widths.sum()
        val baselineOffset = -5f

        // Oval centered at (430, 430) with rx=270, ry=270
        val cx = 430f
        val cy = 430f
        val rx = 270f
        val ry = 270f
        val perimeter = PI.toFloat() * (3f * (rx + ry) - kotlin.math.sqrt((3f * rx + ry) * (rx + 3f * ry)))

        // Compute cumulative positions along the oval
        val cumDist = FloatArray(n)
        for (i in 1 until n) {
            cumDist[i] = cumDist[i - 1] + widths[i - 1]
        }
        val textSpan = totalWidth
        val startOffset = (perimeter - textSpan) / 2f

        for (dir in listOf(1f, -1f)) {
            for (i in 0 until n) {
                val dist = startOffset + cumDist[i] + widths[i] / 2f
                val frac = dist / perimeter * dir
                val angle = frac * 2f * PI.toFloat()

                val px = cx + rx * cos(angle)
                val py = cy + ry * sin(angle)

                // Tangent direction
                val tx = -rx * sin(angle).toDouble()
                val ty = ry * cos(angle).toDouble()
                val theta = atan2(ty, tx).toFloat()

                // Normal offset for baseline
                val nx = -sin(angle) * baselineOffset
                val ny = cos(angle) * baselineOffset

                canvas.save()
                canvas.translate(px + nx, py + ny)
                canvas.rotate(theta * 180f / PI.toFloat())
                canvas.drawString(text.substring(i, i + 1), -widths[i] / 2f, 0f, font, paint)
                canvas.restore()
            }
        }

        // Draw the oval outline
        val outlinePaint = Paint(style = PaintStyle.STROKE)
        canvas.drawOval(Rect.fromXYWH(cx - rx, cy - ry, 2f * rx, 2f * ry), outlinePaint)
    }
}
