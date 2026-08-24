package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.CornerRadiiF32
import org.graphiks.math.matrix.Matrix3x3F32
import org.graphiks.math.geometry.RRectF32
import org.graphiks.math.geometry.RectF32
import kotlin.math.pow

/**
 * Port of Skia's `gm/shadowutils.cpp`:
 * `DEF_SIMPLE_GM(shadow_utils_directional, canvas, 256, 384)`.
 * Exercises directional shadow drawing with transforms.
 * @see https://github.com/google/skia/blob/main/gm/shadowutils.cpp
 */
class ShadowUtilsDirectionalGm : SkiaGm {
    override val name = "shadow_utils_directional"
    override val renderFamily = RenderFamily.PATH
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 384

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val rrect = Path { }.apply {
            addRRect(RRectF32.of(RectF32.ofLTRB(-25f, -25f, 25f, 25f), CornerRadiiF32.of(10f, 10f)))
        }
        val fillPaint = Paint(
            color = ColorARGB.White,
            antiAlias = true,
            style = PaintStyle.FILL,
        )

        // Row 1 - translation
        canvas.save()
        canvas.translate(35f, 35f)
        for (i in 0 until 3) {
            canvas.drawPath(rrect, fillPaint)
            canvas.translate(80f, 0f)
        }
        canvas.restore()

        // Row 2 - rotation
        for (i in 0 until 3) {
            canvas.save()
            canvas.translate(35f + 80f * i, 105f)
            canvas.rotate(20f * (i + 1))
            canvas.drawPath(rrect, fillPaint)
            canvas.restore()
        }

        // Row 3 - scale
        for (i in 0 until 3) {
            canvas.save()
            val scaleFactor = 2.0.pow(-i.toDouble()).toFloat()
            canvas.translate(35f + 80f * i, 185f)
            canvas.scale(scaleFactor, scaleFactor)
            canvas.drawPath(rrect, fillPaint)
            canvas.restore()
        }

        // Row 4 - perspective
        for (i in 0 until 3) {
            canvas.save()
            val mat = Matrix3x3F32.of(
                1f, 0f, 0f,
                0f, 1f, 0f,
                0.005f, 0f, 1.005f,
            )
            canvas.translate(35f + 80f * i, 265f)
            canvas.concat(mat)
            canvas.drawPath(rrect, fillPaint)
            canvas.restore()
        }
    }
}
