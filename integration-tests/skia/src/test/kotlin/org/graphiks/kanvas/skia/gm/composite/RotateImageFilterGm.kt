package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `DEF_SIMPLE_GM(rotate_imagefilter, canvas, 500, 500)`
 * in `gm/imagefilterstransformed.cpp` (name `rotate_imagefilter`, 500 × 500).
 *
 * Draws 3 rows of 3 rects each (null, Blur(6,0), Blend(SrcOver) filters),
 * showing plain rect, 30° rotated no-AA, and 30° rotated AA.
 * @see https://github.com/google/skia/blob/main/gm/imagefilterstransformed.cpp
 */
class RotateImageFilterGm : SkiaGm {
    override val name = "rotate_imagefilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val r = Rect.fromXYWH(50f, 50f, 100f, 100f)

        val filters = listOf<ImageFilter?>(
            null,
            ImageFilter.Blur(6f, 0f, input = null),
            ImageFilter.Blend(
                mode = org.graphiks.kanvas.paint.BlendMode.SRC_OVER,
                background = ImageFilter.Blur(0f, 0f, input = null),
                foreground = ImageFilter.Blur(0f, 0f, input = null),
            ),
        )

        for (filter in filters) {
            val paint = Paint(antiAlias = false, imageFilter = filter)

            canvas.save()

            // left: plain rect
            canvas.drawRect(r, paint)

            // centre: rotated, no AA
            canvas.translate(150f, 0f)
            canvas.save()
            canvas.rotate(30f)
            canvas.translate(0f, 0f)
            canvas.drawRect(r, paint)
            canvas.restore()

            // right: rotated, AA
            val aaPaint = paint.copy(antiAlias = true)
            canvas.translate(150f, 0f)
            canvas.save()
            canvas.rotate(30f)
            canvas.drawRect(r, aaPaint)
            canvas.restore()

            canvas.restore()
            canvas.translate(0f, 150f)
        }
    }
}
