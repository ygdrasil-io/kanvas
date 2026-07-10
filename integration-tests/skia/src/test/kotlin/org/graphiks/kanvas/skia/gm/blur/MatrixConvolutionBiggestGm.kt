package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/matrixconvolution.cpp` — biggest kernel variant with four colored bands. */
class MatrixConvolutionBiggestGm : SkiaGm {
    override val name = "matrixconvolution_biggest"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 300

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(1f, 1f, 1f)
        canvas.drawRect(Rect(30f, 30f, 470f, 80f), Paint(color = Color.RED))
        canvas.drawRect(Rect(30f, 110f, 230f, 160f), Paint(color = Color.GREEN))
        canvas.drawRect(Rect(270f, 110f, 470f, 160f), Paint(color = Color.BLUE))
        canvas.drawRect(Rect(30f, 190f, 470f, 270f), Paint(color = Color.fromRGBA(1f, 1f, 0f, 1f)))
    }
}
