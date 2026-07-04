package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/imagefiltersclipped.cpp::ImageFiltersClippedGM` (860 × 500).
 * Walks an 8-filter table over a 5-row × 8-col grid, sliding the clip rect
 * leftward by xOffset = {0, 16, 32, 48, 64} per row.
 * **Adaptation**: Skia's [SkImageFilters.Image] and [SkImageFilters.Shader] are not
 * available in Kanvas. Only the filters available on [ImageFilter] are used.
 * @see https://github.com/google/skia/blob/main/gm/imagefiltersclipped.cpp
 */
class ImageFiltersClippedGm : SkiaGm {
    override val name = "imagefiltersclipped"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 860
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f, 1f)

        val filters: Array<ImageFilter?> = arrayOf(
            ImageFilter.Blur(12f, 12f, input = null),
            ImageFilter.DropShadow(10f, 10f, 3f, 3f, Color.GREEN, null),
            ImageFilter.Offset(-16f, 32f, null),
            ImageFilter.Dilate(2f, 2f, null),
            ImageFilter.Erode(2f, 2f, null),
            null,
            null,
            null,
        )

        val margin = 16f
        val r = Rect(0f, 0f, 64f, 64f)
        val bounds = Rect(0f, 0f, 64f, 64f)

        canvas.save()
        var xOffset = 0
        while (xOffset < 80) {
            canvas.save()
            val b = Rect(xOffset.toFloat(), bounds.top, bounds.right, bounds.bottom)
            for (i in filters.indices) {
                drawClippedFilter(canvas, filters[i], r, b)
                canvas.translate(r.width + margin, 0f)
            }
            canvas.restore()
            canvas.translate(0f, r.height + margin)
            xOffset += 16
        }
        canvas.restore()

        canvas.translate(filters.size * (r.width + margin), 0f)
        xOffset = 0
        while (xOffset < 80) {
            val b = Rect(xOffset.toFloat(), bounds.top, bounds.right, bounds.bottom)
            drawClippedFilter(canvas, null, r, b)
            canvas.translate(0f, r.height + margin)
            xOffset += 16
        }
    }

    private fun drawClippedFilter(
        canvas: GmCanvas,
        filter: ImageFilter?,
        primBounds: Rect,
        clipBounds: Rect,
    ) {
        val paint = Paint(color = Color.WHITE, imageFilter = filter, antiAlias = true)
        canvas.save()
        canvas.clipRect(clipBounds)
        canvas.drawCircle(primBounds.center.x, primBounds.center.y, primBounds.width * 2f / 5f, paint)
        canvas.restore()
    }
}
