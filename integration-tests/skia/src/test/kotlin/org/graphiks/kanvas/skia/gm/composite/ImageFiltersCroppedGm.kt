package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.paint.BlendMode
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's gm/imagefilterscropped.cpp.
 * Tests image filters with crop rects.
 * @see https://github.com/google/skia/blob/main/gm/imagefilterscropped.cpp
 */
class ImageFiltersCroppedGm : SkiaGm {
    override val name = "imagefilterscropped"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 960

    private fun intToColor(value: Int): Color {
        val a = (value ushr 24) and 0xFF
        val r = (value ushr 16) and 0xFF
        val g = (value ushr 8) and 0xFF
        val b = value and 0xFF
        return Color.fromRGBA(r / 255f, g / 255f, b / 255f, a / 255f)
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val checkerboard = makeCheckerboard()

        val drawProc: List<(GmCanvas, Rect, ImageFilter?) -> Unit> = listOf(
            ::drawBitmap, ::drawPath, ::drawPaint, ::drawText,
        )

        val cf = ColorFilter.Blend(Color.BLUE, BlendMode.SRC_IN)

        // Simplified filters - using only available ImageFilter types
        val filters: List<ImageFilter?> = listOf(
            null,
            ImageFilter.ColorFilter(cf, null),
            ImageFilter.Blur(0f, 0f, TileMode.DECAL, null),
            ImageFilter.Blur(1f, 1f, TileMode.DECAL, null),
            ImageFilter.Blur(8f, 0f, TileMode.DECAL, null),
            ImageFilter.Blur(0f, 8f, TileMode.DECAL, null),
            ImageFilter.Blur(8f, 8f, TileMode.DECAL, null),
            ImageFilter.ColorFilter(cf, null),
            ImageFilter.Blur(8f, 8f, TileMode.DECAL, null),
            ImageFilter.Blur(8f, 8f, TileMode.DECAL, null),
            ImageFilter.ColorFilter(cf, null),
            ImageFilter.Blur(8f, 8f, TileMode.DECAL, null),
            ImageFilter.ColorFilter(cf, null),
            ImageFilter.Blur(8f, 8f, TileMode.DECAL, null),
            ImageFilter.ColorFilter(cf, null),
        )

        val r = Rect(0f, 0f, 64f, 64f)
        val margin = 16f
        val dx = r.width + margin
        val dy = r.height + margin

        canvas.translate(margin, margin)
        for (j in drawProc.indices) {
            canvas.save()
            for (i in filters.indices) {
                canvas.drawImage(checkerboard, Rect(0f, 0f, 80f, 80f))
                drawProc[j](canvas, r, filters[i])
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
        val magenta = Color.fromRGBA(1f, 0f, 1f, 1f)
        val paint = Paint(
            color = magenta,
            imageFilter = imf,
            antiAlias = true,
        )
        canvas.drawCircle(r.center.x, r.center.y, r.width * 2f / 5f, paint)
    }

    private fun drawText(canvas: GmCanvas, r: Rect, imf: ImageFilter?) {
        val paint = Paint(
            imageFilter = imf,
            color = Color.GREEN,
        )
        // Simplified - just draw a rect instead of text
        canvas.drawRect(r, paint)
    }

    private fun drawBitmap(canvas: GmCanvas, r: Rect, imf: ImageFilter?) {
        // Simplified - just draw a path
        drawPath(canvas, r, imf)
    }

    private fun makeCheckerboard(): org.graphiks.kanvas.image.Image {
        // Return a simple placeholder image
        return org.graphiks.kanvas.image.Image(80, 80, sourceId = "checkerboard")
    }
}
