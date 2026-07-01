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

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val drawProc: List<(GmCanvas, Rect, ImageFilter?) -> Unit> = listOf(
            ::drawRectProc, ::drawCircleProc, ::drawColorProc, ::drawRectProc,
        )

        val cf = ColorFilter.Blend(Color.BLUE, BlendMode.SRC_IN)

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
                drawCheckerboard(canvas, 80f, 80f)
                drawProc[j](canvas, r, filters[i])
                canvas.translate(0f, dy)
            }
            canvas.restore()
            canvas.translate(dx, 0f)
        }
    }

    private fun drawColorProc(canvas: GmCanvas, r: Rect, imf: ImageFilter?) {
        val paint = Paint(
            imageFilter = imf,
            color = Color.BLACK,
        )
        canvas.save()
        canvas.clipRect(r)
        canvas.drawRect(r, paint)
        canvas.restore()
    }

    private fun drawCircleProc(canvas: GmCanvas, r: Rect, imf: ImageFilter?) {
        val magenta = Color.fromRGBA(1f, 0f, 1f, 1f)
        val paint = Paint(
            color = magenta,
            imageFilter = imf,
            antiAlias = true,
        )
        canvas.drawCircle(r.center.x, r.center.y, r.width * 2f / 5f, paint)
    }

    private fun drawRectProc(canvas: GmCanvas, r: Rect, imf: ImageFilter?) {
        val paint = Paint(
            imageFilter = imf,
            color = Color.GREEN,
        )
        canvas.drawRect(r, paint)
    }

    private fun drawCheckerboard(canvas: GmCanvas, w: Float, h: Float) {
        val cell = w / CHECKER_COLS.toFloat()
        for (row in 0 until CHECKER_COLS) {
            for (col in 0 until CHECKER_COLS) {
                val color = if ((row + col) % 2 == 0) Color.WHITE else Color.BLACK
                val paint = Paint(color = color, antiAlias = false)
                val cx = col * cell
                val cy = row * cell
                canvas.drawRect(Rect(cx, cy, cx + cell, cy + cell), paint)
            }
        }
    }

    private companion object {
        private const val CHECKER_COLS = 8
    }
}
