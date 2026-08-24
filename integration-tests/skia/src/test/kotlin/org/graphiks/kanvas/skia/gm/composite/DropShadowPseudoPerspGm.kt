package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.ImageFilter
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

/**
 * Port of Skia's `gm/dropshadowimagefilter.cpp` `DEF_SIMPLE_GM(dropshadow_pseudopersp, ...)` (155 × 155).
 * Exercises drop-shadow image filter under a perspective-like canvas matrix.
 * **Adaptation**: Upstream uses [SkM44] (4×4 projective). Kanvas uses [Matrix3x3F32] (3×3 projective).
 * The 4×4 Z-computation is not replicated.
 * @see https://github.com/google/skia/blob/main/gm/dropshadowimagefilter.cpp
 */
class DropShadowPseudoPerspGm : SkiaGm {
    override val name = "dropshadow_pseudopersp"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.TRIVIAL
    override val minSimilarity = 0.0
    override val width = 155
    override val height = 155

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.75f, 0.75f, 0.75f, 1f)

        canvas.concat(Matrix3x3F32.of(0.5f, 0f, -75f, 0f, 0.5f, -30f))

        canvas.concat(Matrix3x3F32.of(
            0.623f, 0f, 134.8f,
            0f, 0.623f, 45.1f,
            0f, 0f, 1f,
        ))

        val layerBounds = RectF32(42.5f, 42.5f, 457.5f, 457.5f)

        val shadowColor = ColorARGB.fromRGBA(0.14902f, 0.215686f, 0.329412f, 0.666667f)
        val layerPaint = Paint(
            imageFilter = ImageFilter.DropShadow(30f, 30f, 12f, 12f, shadowColor, null),
        )
        canvas.saveLayer(bounds = layerBounds, paint = layerPaint)

        val rrect = RRectF32.of(RectF32(-250f, -250f, 250f, 250f), CornerRadiiF32.of(45f, 45f))

        canvas.concat(Matrix3x3F32.of(0.83f, 0f, 250f, 0f, 0.83f, 250f))

        val rrectPaint = Paint(color = ColorARGB.White, antiAlias = true)
        canvas.drawRRect(rrect, rrectPaint)
        canvas.restore()

        canvas.concat(Matrix3x3F32.of(0.83f, 0f, 250f, 0f, 0.83f, 250f))

        val strokePaint = Paint(
            color = ColorARGB.Black,
            style = PaintStyle.STROKE,
        )
        canvas.drawRRect(rrect, strokePaint)
    }
}
