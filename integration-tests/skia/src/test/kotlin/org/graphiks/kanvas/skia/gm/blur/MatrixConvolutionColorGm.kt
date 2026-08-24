package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB

/** Port of Skia's `gm/matrixconvolution.cpp` — color variant drawing circles instead of rects. */
class MatrixConvolutionColorGm : SkiaGm {
    override val name = "matrixconvolution_color"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f)
        canvas.drawCircle(100f, 100f, 50f, Paint(color = ColorARGB.Red))
        canvas.drawCircle(250f, 100f, 50f, Paint(color = ColorARGB.Green))
        canvas.drawCircle(400f, 100f, 50f, Paint(color = ColorARGB.Blue))
        canvas.drawCircle(175f, 230f, 50f, Paint(color = ColorARGB.fromRGBA(1f, 1f, 0f, 1f)))
        canvas.drawCircle(325f, 230f, 50f, Paint(color = ColorARGB.fromRGBA(0f, 1f, 1f, 1f)))
    }
}
