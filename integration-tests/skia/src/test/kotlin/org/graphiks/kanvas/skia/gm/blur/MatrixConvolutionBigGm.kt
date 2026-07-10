package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/matrixconvolution.cpp` — big kernel variant with wide colored bands. */
class MatrixConvolutionBigGm : SkiaGm {
    override val name = "matrixconvolution_big"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f)
        canvas.drawRect(Rect(30f, 30f, 470f, 130f), Paint(color = Color.fromRGBA(0.2f, 0.4f, 0.8f, 1f)))
        canvas.drawRect(Rect(30f, 160f, 470f, 270f), Paint(color = Color.fromRGBA(0.8f, 0.3f, 0.1f, 1f)))
    }
}
