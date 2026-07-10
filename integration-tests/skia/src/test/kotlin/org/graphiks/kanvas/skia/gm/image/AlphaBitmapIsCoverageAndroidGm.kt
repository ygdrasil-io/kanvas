package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.RRect
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/alpha_image.cpp::alpha_bitmap_is_coverage_ANDROID` (128x128).
 *
 * Best-effort: Skia tests Android workaround where A8 bitmaps are treated as coverage
 * instead of alpha. Draws a mandrill-like rectangle with a round-rect clear through
 * an A8 mask. Kanvas approximates with a filled rect and round-rect stroke.
 * @see https://github.com/google/skia/blob/main/gm/alpha_image.cpp
 */
class AlphaBitmapIsCoverageAndroidGm : SkiaGm {
    override val name = "alpha_bitmap_is_coverage_android"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 128
    override val height = 128

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.6f, 0.4f, 0.2f)
        val inner = Paint(color = Color.fromRGBA(1f, 0.8f, 0.2f, 1f))
        canvas.drawRect(Rect(8f, 8f, 120f, 120f), inner)
        val border = Paint(
            color = Color.WHITE,
            style = PaintStyle.STROKE,
            strokeWidth = 4f,
            antiAlias = true,
        )
        canvas.drawRRect(RRect(Rect(4f, 4f, 124f, 124f), radius = 16f), border)
    }
}
