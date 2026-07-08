package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/matrixconvolution.cpp` — bigger kernel variant with three colored bands. */
class MatrixConvolutionBiggerGm : SkiaGm {
    override val name = "matrixconvolution_bigger"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f)
        canvas.drawRect(Rect(50f, 50f, 200f, 250f), Paint(color = Color.fromRGBA(0.1f, 0.5f, 0.9f, 1f)))
        canvas.drawRect(Rect(300f, 50f, 450f, 150f), Paint(color = Color.fromRGBA(0.9f, 0.5f, 0.1f, 1f)))
        canvas.drawRect(Rect(300f, 180f, 450f, 250f), Paint(color = Color.fromRGBA(0.1f, 0.9f, 0.5f, 1f)))
    }
}
