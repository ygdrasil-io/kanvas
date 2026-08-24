package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32

/** Port of Skia's `gm/matrixconvolution.cpp` — big kernel variant with multiple colors. */
class MatrixConvolutionBigColorGm : SkiaGm {
    override val name = "matrixconvolution_big_color"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f)
        val colors = listOf(ColorARGB.Red, ColorARGB.Green, ColorARGB.Blue, ColorARGB.fromRGBA(1f, 1f, 0f, 1f), ColorARGB.fromRGBA(0f, 1f, 1f, 1f), ColorARGB.fromRGBA(1f, 0f, 1f, 1f))
        for (i in colors.indices) {
            canvas.drawRect(RectF32(30f + (i % 3) * 155f, 30f + (i / 3) * 130f, 170f + (i % 3) * 155f, 140f + (i / 3) * 130f), Paint(color = colors[i]))
        }
    }
}
