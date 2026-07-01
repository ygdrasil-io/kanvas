package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.PathEffect
import org.graphiks.kanvas.paint.StrokeCap
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import kotlin.math.sqrt

/**
 * Port of Dashing3GM.
 *
 * Renders horizontal dashed lines via `drawLine` at various intervals.
 * The original `drawPoints(PointMode.kLines)` has been replaced with
 * equivalent `drawLine` calls.
 * The 45° rotated block at the center is skipped because GmCanvas does
 * not support canvas rotation.
 */
class Dashing3Gm : SkiaGm {
    override val name = "dashing3"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 480

    private fun drawDashedLines(
        canvas: GmCanvas,
        lineLength: Float,
        phase: Float,
        dashLength: Float,
        strokeWidth: Int,
        circles: Boolean,
    ) {
        val strokeCap = if (circles) StrokeCap.ROUND else StrokeCap.BUTT
        var paint = Paint(
            color = Color.BLACK,
            style = PaintStyle.STROKE,
            strokeWidth = strokeWidth.toFloat(),
            strokeCap = strokeCap,
            pathEffect = PathEffect.Dash(floatArrayOf(dashLength, dashLength), phase),
        )

        var y = 0
        while (y < 100) {
            canvas.drawLine(0f, y.toFloat(), lineLength, y.toFloat(), paint)
            y += 10 * strokeWidth
        }
        paint = paint.copy(antiAlias = true)
        var x = 0
        while (x < 100) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), lineLength, paint)
            x += 14 * strokeWidth
        }
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.save(); canvas.translate(2f, 0f);    drawDashedLines(canvas, 100f, 0f, 1f, 1, false); canvas.restore()
        canvas.save(); canvas.translate(112f, 0f);  drawDashedLines(canvas, 100f, 0.5f, 1f, 1, false); canvas.restore()
        canvas.save(); canvas.translate(222f, 0f);  drawDashedLines(canvas, 100f, 1f, 1f, 1, false); canvas.restore()
        canvas.save(); canvas.translate(332f, 0f);  drawDashedLines(canvas, 99.5f, 0.5f, 1f, 1, false); canvas.restore()
        canvas.save(); canvas.translate(446f, 0f);  drawDashedLines(canvas, 100f, 0f, 255f, 1, false); canvas.restore()

        canvas.save(); canvas.translate(2f, 110f);  drawDashedLines(canvas, 100f, 0f, 3f, 3, false); canvas.restore()
        canvas.save(); canvas.translate(112f, 110f); drawDashedLines(canvas, 100f, 1.5f, 3f, 3, false); canvas.restore()

        canvas.save(); canvas.translate(2f, 220f);  drawDashedLines(canvas, 100f, 1f, 1f, 1, true); canvas.restore()
        canvas.save(); canvas.translate(112f, 220f); drawDashedLines(canvas, 100f, 0f, 3f, 3, true); canvas.restore()

        val r2 = sqrt(2f) / 2f
        canvas.save()
        canvas.translate(332f + r2 * 100f, 110f + r2 * 100f)
        // rotate(45) not available in GmCanvas — skipping this block
        canvas.restore()

        for (phase in 0..3) {
            canvas.save()
            canvas.translate((phase * 110 + 2).toFloat(), 330f)
            drawDashedLines(canvas, 100f, phase.toFloat(), 3f, 1, false)
            canvas.restore()
        }
    }
}
