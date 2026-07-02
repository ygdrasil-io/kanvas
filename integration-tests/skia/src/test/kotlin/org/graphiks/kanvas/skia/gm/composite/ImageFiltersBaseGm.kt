package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/imagefiltersbase.cpp`.
 * Tests basic image filter types across a variety of draw operations.
 * @see https://github.com/google/skia/blob/main/gm/imagefiltersbase.cpp
 */
class ImageFiltersBaseGm : SkiaGm {
    override val name = "imagefiltersbase"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 700
    override val height = 500

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val drawProcs: List<(GmCanvas, Rect, ImageFilter?) -> Unit> = listOf(
            ::drawPaintCell, ::drawLineCell, ::drawRectCell, ::drawPathCell,
            ::drawBitmapCell, ::drawRectCell,
        )

        val cf = ColorFilter.Blend(Color.RED, BlendMode.SRC_IN)
        val filters: List<ImageFilter?> = listOf(
            null,
            ImageFilter.Offset(0f, 0f, null),
            null,
            ImageFilter.ColorFilter(cf, null),
            ImageFilter.Blur(12f, 0.29f, input = null),
            ImageFilter.DropShadow(10f, 5f, 3f, 3f, Color.BLUE, null),
        )

        val r = Rect(0f, 0f, 64f, 64f)
        val margin = 16f
        val dx = r.width + margin
        val dy = r.height + margin

        canvas.translate(margin, margin)
        for (i in drawProcs.indices) {
            canvas.save()
            for (j in filters.indices) {
                drawProcs[i](canvas, r, filters[j])
                drawFrame(canvas, r)
                canvas.translate(0f, dy)
            }
            canvas.restore()
            canvas.translate(dx, 0f)
        }
    }

    private fun drawFrame(canvas: GmCanvas, r: Rect) {
        val paint = Paint(
            color = Color.RED,
            style = org.graphiks.kanvas.paint.PaintStyle.STROKE,
        )
        canvas.drawRect(r, paint)
    }

    private fun drawPaintCell(canvas: GmCanvas, r: Rect, imf: ImageFilter?) {
        val paint = Paint(
            imageFilter = imf,
            color = Color.fromRGBA(0f, 1f, 0f, 1f),
        )
        canvas.save()
        canvas.clipRect(r)
        canvas.drawRect(r, paint)
        canvas.restore()
    }

    private fun drawLineCell(canvas: GmCanvas, r: Rect, imf: ImageFilter?) {
        val paint = Paint(
            color = Color.BLUE,
            imageFilter = imf,
            style = org.graphiks.kanvas.paint.PaintStyle.STROKE,
            strokeWidth = r.width / 10f,
        )
        canvas.drawLine(r.left, r.top, r.right, r.bottom, paint)
    }

    private fun drawRectCell(canvas: GmCanvas, r: Rect, imf: ImageFilter?) {
        val inset = r.width / 10f
        val rr = Rect(r.left + inset, r.top + inset, r.right - inset, r.bottom - inset)
        val paint = Paint(
            color = Color.fromRGBA(1f, 1f, 0f, 1f),
            imageFilter = imf,
        )
        canvas.drawRect(rr, paint)
    }

    private fun drawPathCell(canvas: GmCanvas, r: Rect, imf: ImageFilter?) {
        val paint = Paint(
            color = Color.fromRGBA(1f, 0f, 1f, 1f),
            imageFilter = imf,
            antiAlias = true,
        )
        canvas.drawCircle(r.center.x, r.center.y, r.width * 2f / 5f, paint)
    }

    private fun drawBitmapCell(canvas: GmCanvas, r: Rect, imf: ImageFilter?) {
        val paint = Paint(imageFilter = imf)
        val bw = r.width.toInt()
        val bh = r.height.toInt()
        val surface = Surface(bw, bh)
        surface.canvas {
            val cx = bw / 2f
            val cy = bh / 2f
            val radius = bw * 2f / 5f
            val path = org.graphiks.kanvas.geometry.Path { }.apply { addCircle(cx, cy, radius) }
            drawPath(path, Paint(color = Color.fromRGBA(1f, 0f, 1f, 1f), antiAlias = true))
        }
        val image = surface.makeImageSnapshot()
        canvas.drawImage(image, Rect(0f, 0f, r.width, r.height), paint)
    }
}
