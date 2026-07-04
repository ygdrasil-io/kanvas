package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/inversepaths.cpp::inverse_fill_filters` (384 x 128).
 * Renders 3 cells side-by-side, each 128x128 (clipped), showing an
 * inverse-winding circle (r=30) at (65, 65) under 3 filter states:
 * no filter, image filter blur, mask filter blur.
 * @see https://github.com/google/skia/blob/main/gm/inversepaths.cpp
 */
class InverseFillFiltersGm : SkiaGm {
    override val name = "inverse_fill_filters"
    override val renderFamily = RenderFamily.BLUR
    override val minSimilarity = 0.0
    override val width = 384
    override val height = 128

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val draw: (GmCanvas, Paint) -> Unit = { c, paint ->
            val path = Path { }.apply { addCircle(65f, 65f, 30f) }
            path.fillType = org.graphiks.kanvas.geometry.FillType.INVERSE_WINDING

            c.save()
            c.clipRect(Rect.fromLTRB(0f, 0f, 128f, 128f))
            c.drawPath(path, paint)
            c.restore()

            val stroke = Paint(
                style = PaintStyle.STROKE,
                color = Color.WHITE,
            )
            c.drawRect(Rect.fromLTRB(0f, 0f, 128f, 128f), stroke)
        }

        val paint = Paint(antiAlias = true)

        // Cell 1: no filter
        draw(canvas, paint)

        // Cell 2: image filter blur
        canvas.translate(128f, 0f)
        draw(canvas, Paint(
            antiAlias = true,
            imageFilter = ImageFilter.Blur(5f, 5f),
        ))

        // Cell 3: mask filter blur
        canvas.translate(128f, 0f)
        draw(canvas, Paint(
            antiAlias = true,
            maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 5f),
        ))
    }
}
