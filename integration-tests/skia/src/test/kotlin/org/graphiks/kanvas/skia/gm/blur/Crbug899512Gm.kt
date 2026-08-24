package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.RectF32
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm

/**
 * Port of Skia's `gm/crbug_899512.cpp` (520 x 520).
 * Flipped-CTM blur clipping bug: matrix flips X, then blur applied.
 * @see https://github.com/google/skia/blob/main/gm/crbug_899512.cpp
 */
class Crbug899512Gm : SkiaGm {
    override val name = "crbug_899512"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 87.7
    override val width = 520
    override val height = 520

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val matrix = Matrix3x3F32.translation(220f, 0f) * Matrix3x3F32.scaling(-1f, 1f)
        canvas.concat(matrix)
        val paint = Paint(
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 6.2735f),
            colorFilter = ColorFilter.Blend(ColorARGB.Black, BlendMode.SRC_IN),
        )
        canvas.drawRect(RectF32.ofOriginSize(0f, 10f, 200f, 200f), paint)
    }
}
