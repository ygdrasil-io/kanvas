package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/savelayer.cpp::DEF_SIMPLE_GM(savelayer_f16, canvas, 900, 300)`.
 *
 * Demonstrates the precision benefit of requesting an F16 saveLayer.
 * Draws a sweep-gradient oval on the root canvas, then two saveLayer
 * passes with kPlus blend accumulating 15x 1/15-alpha ovals.
 * @see https://github.com/google/skia/blob/main/gm/savelayer.cpp
 */
class SaveLayerF16Gm : SkiaGm {
    override val name = "savelayer_f16"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 900
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val n = 15
        val r = Rect.fromXYWH(0f, 0f, 300f, 300f)

        val stops = listOf(
            GradientStop(0f, Color.RED),
            GradientStop(1f / 3f, Color.GREEN),
            GradientStop(2f / 3f, Color.BLUE),
            GradientStop(1f, Color.RED),
        )
        val sweepPaint = Paint(
            shader = Shader.SweepGradient(
                center = r.center,
                stops = stops,
                tileMode = TileMode.CLAMP,
            ),
        )
        canvas.drawOval(r, sweepPaint)

        val alpha = 1.0f / n
        val layerPaint = Paint(
            color = Color.fromRGBA(0f, 0f, 0f, alpha),
            blendMode = BlendMode.PLUS,
        )

        // Pass 1: saveLayer with default paint
        canvas.saveLayer(r, Paint())
        repeat(n) { canvas.drawOval(r, layerPaint) }
        canvas.restore()

        canvas.translate(r.width, 0f)

        // Pass 2: saveLayer with null paint
        canvas.saveLayer(r, null)
        repeat(n) { canvas.drawOval(r, layerPaint) }
        canvas.restore()
    }
}
