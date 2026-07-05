package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/imagefiltersstroked.cpp` (860 × 500).
 * Draws three stroked primitives (line, rect, circle) under four
 * image-filters (blur / drop-shadow / offset / dilate).
 * **Adaptation**: Upstream uses [SkImageFilters.MatrixTransform]; Kanvas does not have
 * a MatrixTransform filter. Dilate is used instead.
 * @see https://github.com/google/skia/blob/main/gm/imagefiltersstroked.cpp
 */
class ImageFiltersStrokedGm : SkiaGm {
    override val name = "imagefiltersstroked"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 860
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f, 1f)

        val filters: Array<ImageFilter?> = arrayOf(
            ImageFilter.Blur(5f, 5f, input = null),
            ImageFilter.DropShadow(10f, 10f, 3f, 3f, Color.GREEN, null),
            ImageFilter.Offset(-16f, 32f, null),
            ImageFilter.Dilate(4f, 4f, null),
        )

        val r = Rect(0f, 0f, 64f, 64f)
        val margin = 32f
        val paint = Paint(
            color = Color.WHITE,
            antiAlias = true,
            strokeWidth = 10f,
            style = PaintStyle.STROKE,
        )

        for (i in 0..2) {
            canvas.translate(0f, margin)
            canvas.save()
            for (j in filters.indices) {
                canvas.translate(margin, 0f)
                canvas.save()
                if (j == 2) {
                    canvas.translate(16f, -32f)
                }
                val fp = paint.copy(imageFilter = filters[j])
                when (i) {
                    0 -> canvas.drawLine(r.left, r.bottom, r.right, r.top, fp)
                    1 -> canvas.drawRect(r, fp)
                    2 -> canvas.drawCircle(r.center.x, r.center.y, r.width * 2f / 5f, fp)
                }
                canvas.restore()
                canvas.translate(r.width + margin, 0f)
            }
            canvas.restore()
            canvas.translate(0f, r.height)
        }
    }
}
