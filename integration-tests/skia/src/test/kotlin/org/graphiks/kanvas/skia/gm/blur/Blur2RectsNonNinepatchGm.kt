package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/blurs.cpp::blur2rectsnonninepatch` (700 x 500).
 *
 * Two rects with same winding (both CW) under kNormal sigma=4.3 blur,
 * drawn at canonical position, sub-pixel shifted, and partly offscreen.
 * @see https://github.com/google/skia/blob/main/gm/blurs.cpp
 */
class Blur2RectsNonNinepatchGm : SkiaGm {
    override val name = "blur2rectsnonninepatch"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 90.5
    override val width = 700
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 4.3f),
        )

        val outer = Rect.fromXYWH(10f, 110f, 100f, 100f)
        val inner = Rect.fromXYWH(50f, 150f, 10f, 10f)
        val path = Path {
            moveTo(outer.left, outer.top)
            lineTo(outer.right, outer.top)
            lineTo(outer.right, outer.bottom)
            lineTo(outer.left, outer.bottom)
            close()
            moveTo(inner.left, inner.top)
            lineTo(inner.right, inner.top)
            lineTo(inner.right, inner.bottom)
            lineTo(inner.left, inner.bottom)
            close()
        }
        canvas.drawPath(path, paint)

        val dx = kotlin.math.round(outer.width) + 40f + 0.25f
        canvas.translate(dx, 0f)
        canvas.drawPath(path, paint)

        canvas.translate(-dx, 0f)
        canvas.translate(-30f, -150f)
        canvas.drawPath(path, paint)
    }
}
