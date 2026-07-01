package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/blurs.cpp::blur2rects`.
 * Two nested rects (CW outer + CCW inner ring) with kNormal blur,
 * drawn twice at different sub-pixel phases.
 * @see https://github.com/google/skia/blob/main/gm/blurs.cpp
 */
class Blur2RectsGm : SkiaGm {
    override val name = "blur2rects"
    override val renderFamily = RenderFamily.BLUR
    override val minSimilarity = 0.0
    override val width = 700
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val paint = Paint(
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 2.3f),
        )

        val outer = Rect.fromXYWH(10.125f, 10.125f, 100.125f, 100f)
        val inner = Rect.fromXYWH(20.25f, 20.125f, 80f, 80f)
        val path = Path {
            moveTo(outer.left, outer.top)
            lineTo(outer.right, outer.top)
            lineTo(outer.right, outer.bottom)
            lineTo(outer.left, outer.bottom)
            close()
            moveTo(inner.left, inner.top)
            lineTo(inner.left, inner.bottom)
            lineTo(inner.right, inner.bottom)
            lineTo(inner.right, inner.top)
            close()
        }

        canvas.drawPath(path, paint)
        val dx = kotlin.math.round(outer.width) + 14f + 0.25f
        canvas.translate(dx, 0f)
        canvas.drawPath(path, paint)
    }
}
