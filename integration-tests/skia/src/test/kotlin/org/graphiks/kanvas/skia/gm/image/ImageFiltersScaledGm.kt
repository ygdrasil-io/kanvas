package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

class ImageFiltersScaledGm : SkiaGm {
    override val name = "imagefiltersscaled"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1428
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 0f, 1f)

        val filters: List<ImageFilter?> = listOf(
            ImageFilter.Blur(4f, 4f),
            ImageFilter.DropShadow(5f, 10f, 3f, 3f, Color.fromRGBA(1f, 1f, 0f, 1f), null),
            ImageFilter.Dilate(1f, 1f, null),
            ImageFilter.Erode(1f, 1f, null),
            ImageFilter.Offset(32f, 0f, null),
            ImageFilter.PointLitDiffuse(Point(0f, 0f), Color.WHITE, 1f, 2f, null),
            ImageFilter.SpotLitDiffuse(Point(-10f, -10f), Point(40f, 40f), 1f, 15f, Color.WHITE, 1f, 2f, null),
        )

        val scales = listOf(
            Point(0.5f, 0.5f),
            Point(1f, 1f),
            Point(1f, 2f),
            Point(2f, 1f),
            Point(2f, 2f),
        )

        val r = Rect.fromXYWH(0f, 0f, 64f, 64f)
        val margin = 16f

        val circlePath = Path { }
        circlePath.addCircle(r.center.x, r.center.y, r.width * 2f / 5f)

        for (j in scales.indices) {
            canvas.save()
            for (i in filters.indices) {
                val paint = Paint(color = Color.BLUE, imageFilter = filters[i], antiAlias = true)
                canvas.save()
                canvas.scale(scales[j].x, scales[j].y)
                canvas.clipRect(r)
                if (i == 4) {
                    canvas.translate(-32f, 0f)
                }
                canvas.drawPath(circlePath, paint)
                canvas.restore()
                canvas.translate(r.width * scales[j].x + margin, 0f)
            }
            canvas.restore()
            canvas.translate(0f, r.height * scales[j].y + margin)
        }
    }
}
