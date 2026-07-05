package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/dropshadowimagefilter.cpp`.
 * Tests drop shadow image filter with various parameters and draw recipes.
 * @see https://github.com/google/skia/blob/main/gm/dropshadowimagefilter.cpp
 */
class DropShadowImageFilterGm : SkiaGm {
    override val name = "dropshadowimagefilter"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 656

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val drawProcs: List<(GmCanvas, Rect, ImageFilter?) -> Unit> = listOf(
            ::drawBitmap, ::drawPath, ::drawPaint, ::drawTextSubstitute,
        )

        val cf = ColorFilter.Blend(Color.fromRGBA(1f, 0f, 1f, 1f), BlendMode.SRC_IN)
        val cfif = ImageFilter.ColorFilter(cf, null)

        val filters: List<ImageFilter?> = listOf(
            null,
            ImageFilter.DropShadow(7f, 0f, 0f, 3f, Color.BLUE, null),
            ImageFilter.DropShadow(0f, 7f, 3f, 0f, Color.BLUE, null),
            ImageFilter.DropShadow(7f, 7f, 3f, 3f, Color.BLUE, null),
            ImageFilter.DropShadow(7f, 7f, 3f, 3f, Color.BLUE, cfif),
            ImageFilter.DropShadow(7f, 7f, 3f, 3f, Color.fromRGBA(0f, 0.5f, 0f, 1f), null),
            ImageFilter.DropShadow(7f, 7f, 3f, 3f, Color.BLUE, null),
            ImageFilter.DropShadow(7f, 7f, 3f, 3f, Color.BLUE, null),
        )

        val r = Rect(0f, 0f, 64f, 64f)
        val margin = 16f
        val dx = r.width + margin
        val dy = r.height + margin

        canvas.translate(margin, margin)
        for (j in drawProcs.indices) {
            canvas.save()
            for (i in filters.indices) {
                drawProcs[j](canvas, r, filters[i])
                canvas.translate(0f, dy)
            }
            canvas.restore()
            canvas.translate(dx, 0f)
        }
    }

    private fun drawPaint(canvas: GmCanvas, r: Rect, imf: ImageFilter?) {
        val paint = Paint(
            imageFilter = imf,
            color = Color.BLACK,
        )
        canvas.save()
        canvas.clipRect(r)
        canvas.drawRect(r, paint)
        canvas.restore()
    }

    private fun drawPath(canvas: GmCanvas, r: Rect, imf: ImageFilter?) {
        val paint = Paint(
            color = Color.fromRGBA(0f, 1f, 0f, 1f),
            imageFilter = imf,
            antiAlias = true,
        )
        canvas.save()
        canvas.clipRect(r)
        canvas.drawCircle(r.center.x, r.center.y, r.width / 3f, paint)
        canvas.restore()
    }

    private fun drawTextSubstitute(canvas: GmCanvas, r: Rect, imf: ImageFilter?) {
        val paint = Paint(
            color = Color.fromRGBA(0f, 1f, 0f, 1f),
            imageFilter = imf,
            antiAlias = true,
        )
        canvas.save()
        canvas.clipRect(r)
        val textRect = Rect(
            r.center.x - r.width / 4f,
            r.center.y - r.height / 6f,
            r.center.x + r.width / 4f,
            r.center.y + r.height / 6f,
        )
        canvas.drawRect(textRect, paint)
        canvas.restore()
    }

    private fun drawBitmap(canvas: GmCanvas, r: Rect, imf: ImageFilter?) {
        val surf = Surface(r.width.toInt(), r.height.toInt())
        surf.canvas {
            drawRect(Rect(0f, 0f, r.width, r.height), Paint(color = Color.TRANSPARENT))
            val cx = r.width / 2f
            val cy = r.height / 2f
            val path = org.graphiks.kanvas.geometry.Path { }.apply { addCircle(cx, cy, cx / 2f) }
            drawPath(path, Paint(color = Color.fromRGBA(0f, 1f, 0f, 1f), antiAlias = true))
        }
        val image = surf.makeImageSnapshot()
        val paint = Paint(imageFilter = imf)
        canvas.save()
        canvas.clipRect(r)
        canvas.drawImage(image, r, paint)
        canvas.restore()
    }
}
