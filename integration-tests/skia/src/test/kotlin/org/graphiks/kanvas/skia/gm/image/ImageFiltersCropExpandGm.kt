package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.GradientStop
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/imagefilterscropexpand.cpp`.
 *  Tests image-filter crop/expand — draws paths and shapes with blur
 *  and offset image filters combined with crop rects.
 *  @see https://github.com/google/skia/blob/main/gm/imagefilterscropexpand.cpp
 */
class ImageFiltersCropExpandGm : SkiaGm {
    override val name = "imagefilterscropexpand"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 730
    override val height = 650

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val checkerboard = makeCheckerboard()

        val cfAlphaTrans = ColorFilter.Matrix(floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 1f,
            0f, 0f, 1f, 0f, 0f,
            0f, 0f, 0f, 1f, 32f / 255f,
        ))

        val margin = 12f
        val pointLocation = Point(0f, 0f)

        canvas.translate(margin, margin)

        var outset = -15
        while (outset <= 20) {
            canvas.save()

            val bigRect = Rect.fromLTRB(
                (10 - outset).toFloat(),
                (10 - outset).toFloat(),
                (54 + outset).toFloat(),
                (54 + outset).toFloat(),
            )

            draw(canvas, checkerboard, bigRect, ImageFilter.ColorFilter(cfAlphaTrans, null))
            draw(canvas, checkerboard, bigRect, ImageFilter.Blur(0.3f, 0.3f, TileMode.DECAL, null))
            draw(canvas, checkerboard, bigRect, ImageFilter.Blur(8f, 8f, TileMode.DECAL, null))
            draw(canvas, checkerboard, bigRect, ImageFilter.Dilate(2f, 2f, null))
            draw(canvas, checkerboard, bigRect, ImageFilter.Erode(2f, 2f, null))
            draw(canvas, checkerboard, bigRect, ImageFilter.DropShadow(10f, 10f, 3f, 3f, Color.BLUE, null))
            draw(canvas, checkerboard, bigRect, ImageFilter.Offset(-8f, 16f, null))
            draw(canvas, checkerboard, bigRect, ImageFilter.PointLitDiffuse(pointLocation, Color.WHITE, 1f, 2f, null))

            canvas.restore()
            canvas.translate(0f, 80f)
            outset += 5
        }
    }

    private fun draw(canvas: GmCanvas, image: Image, layerRect: Rect, filter: ImageFilter?) {
        val paint = Paint(imageFilter = filter)
        canvas.saveLayer(null, paint)
        canvas.drawImage(image, Rect(0f, 0f, image.width.toFloat(), image.height.toFloat()))
        canvas.restore()

        val strokePaint = Paint(color = Color.RED, style = PaintStyle.STROKE)
        canvas.drawRect(layerRect, strokePaint)

        canvas.translate(80f, 0f)
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
