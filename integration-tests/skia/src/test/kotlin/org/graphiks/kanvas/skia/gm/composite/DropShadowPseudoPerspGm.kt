package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.CornerRadii
import org.graphiks.kanvas.types.Matrix33
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/dropshadowimagefilter.cpp` `DEF_SIMPLE_GM(dropshadow_pseudopersp, ...)` (155 × 155).
 * Exercises drop-shadow image filter under a perspective-like canvas matrix.
 * **Adaptation**: Upstream uses [SkM44] (4×4 projective). Kanvas uses [Matrix33] (3×3 projective).
 * The 4×4 Z-computation is not replicated.
 * @see https://github.com/google/skia/blob/main/gm/dropshadowimagefilter.cpp
 */
class DropShadowPseudoPerspGm : SkiaGm {
    override val name = "dropshadow_pseudopersp"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 155
    override val height = 155

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.75f, 0.75f, 0.75f, 1f)

        canvas.concat(Matrix33.makeAll(0.5f, 0f, -75f, 0f, 0.5f, -30f))

        canvas.concat(Matrix33.makeAll(
            0.623f, 0f, 134.8f,
            0f, 0.623f, 45.1f,
            0f, 0f, 1f,
        ))

        val layerBounds = Rect(42.5f, 42.5f, 457.5f, 457.5f)

        val shadowColor = Color.fromRGBA(0.14902f, 0.215686f, 0.329412f, 0.666667f)
        val layerPaint = Paint(
            imageFilter = ImageFilter.DropShadow(30f, 30f, 12f, 12f, shadowColor, null),
        )
        canvas.saveLayer(bounds = layerBounds, paint = layerPaint)

        val rrect = RRect(Rect(-250f, -250f, 250f, 250f), CornerRadii(45f, 45f))

        canvas.concat(Matrix33.makeAll(0.83f, 0f, 250f, 0f, 0.83f, 250f))

        val rrectPaint = Paint(color = Color.WHITE, antiAlias = true)
        canvas.drawRRect(rrect, rrectPaint)
        canvas.restore()

        canvas.concat(Matrix33.makeAll(0.83f, 0f, 250f, 0f, 0.83f, 250f))

        val strokePaint = Paint(
            color = Color.BLACK,
            style = PaintStyle.STROKE,
        )
        canvas.drawRRect(rrect, strokePaint)
    }
}
