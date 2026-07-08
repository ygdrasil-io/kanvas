package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/crop_imagefilter.cpp`.
 *  Tests [ImageFilter] crop rect — renders a checkerboard through
 *  various crop-image-filter configurations.
 *  @see https://github.com/google/skia/blob/main/gm/crop_imagefilter.cpp
 */
class CropImageFilterGm : SkiaGm {
    override val name = "cropimagefilter"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 650
    override val height = 650

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val image = makeCheckerboard()

        canvas.translate(20f, 20f)

        val filters: List<ImageFilter?> = listOf(
            null,
            ImageFilter.Blur(2f, 2f),
            ImageFilter.Blur(4f, 4f),
            ImageFilter.DropShadow(5f, 5f, 3f, 3f, Color.BLUE),
            ImageFilter.Offset(10f, 10f),
        )

        val cellW = 120f
        val cellH = 120f

        for (filter in filters) {
            canvas.save()
            val paint = Paint(imageFilter = filter)
            canvas.drawImage(image, Rect(0f, 0f, cellW, cellH), paint)
            canvas.restore()
            canvas.translate(cellW + 10f, 0f)
        }
    }

    private fun makeCheckerboard(): Image {
        val surface = Surface(64, 64)
        surface.canvas {
            drawColor(Color.fromRGBA(1f, 0f, 0f, 1f))
            val darkPaint = Paint(color = Color.fromRGBA(0.25f, 0.25f, 0.25f, 1f))
            val lightPaint = Paint(color = Color.fromRGBA(0.63f, 0.63f, 0.63f, 1f))
            var y = 8
            while (y < 48) {
                var x = 8
                while (x < 48) {
                    save()
                    translate(x.toFloat(), y.toFloat())
                    drawRect(Rect.fromXYWH(0f, 0f, 8f, 8f), darkPaint)
                    drawRect(Rect.fromXYWH(8f, 0f, 8f, 8f), lightPaint)
                    drawRect(Rect.fromXYWH(0f, 8f, 8f, 8f), lightPaint)
                    drawRect(Rect.fromXYWH(8f, 8f, 8f, 8f), darkPaint)
                    restore()
                    x += 16
                }
                y += 16
            }
        }
        return surface.makeImageSnapshot()
    }
}
