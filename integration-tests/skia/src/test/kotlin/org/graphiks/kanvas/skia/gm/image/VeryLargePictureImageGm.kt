package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Point
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/verylargebitmap.cpp` VeryLargeBitmapGM with
 * make_picture_image and manuallyTile=false.
 *
 * Tests rendering of very large picture-backed images in various sizes,
 * drawn via clip+drawnImage and drawImageRect with subset sampling.
 * @see https://github.com/google/skia/blob/main/gm/verylargebitmap.cpp
 */
class VeryLargePictureImageGm : SkiaGm {
    override val name = "verylarge_picture_image"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 80.0
    override val width = 500
    override val height = 600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val veryBig = 65 * 1024
        val big = 33 * 1024
        val medium = 5 * 1024
        val small = 150

        canvas.translate(10f, 10f)

        showImage(canvas, small, small, Color.RED, Color.GREEN)
        canvas.translate(0f, 150f)

        showImage(canvas, big, small, Color.BLUE, Color.fromRGBA(1f, 0f, 1f, 1f))
        canvas.translate(0f, 150f)

        showImage(canvas, medium, medium, Color.fromRGBA(1f, 0f, 1f, 1f), Color.fromRGBA(1f, 1f, 0f, 1f))
        canvas.translate(0f, 150f)

        showImage(canvas, veryBig, small, Color.GREEN, Color.fromRGBA(1f, 1f, 0f, 1f))
    }

    private fun showImage(canvas: GmCanvas, w: Int, h: Int, c1: Color, c2: Color) {
        val image = makePictureImage(w, h, c1, c2)

        val borderPaint = Paint(style = PaintStyle.STROKE)

        var dstRect = Rect.fromXYWH(0f, 0f, 128f, 128f)

        canvas.save()
        canvas.clipRect(dstRect)
        canvas.drawImage(image, Rect(0f, 0f, 128f, 128f))
        canvas.restore()
        canvas.drawRect(dstRect, borderPaint)

        dstRect = Rect.fromXYWH(dstRect.left + 150f, dstRect.top, dstRect.width, dstRect.height)
        val hw = w / 2
        val hh = h / 2
        val subset = Rect.fromLTRB((hw - 64).toFloat(), (hh - 32).toFloat(), (hw + 64).toFloat(), (hh + 32).toFloat())
        canvas.drawImageRect(image, subset, dstRect)
        canvas.drawRect(dstRect, borderPaint)

        dstRect = Rect.fromXYWH(dstRect.left + 150f, dstRect.top, dstRect.width, dstRect.height)
        canvas.drawImageRect(
            image,
            Rect.fromLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat()),
            dstRect,
        )
        canvas.drawRect(dstRect, borderPaint)
    }

    private fun makePictureImage(width: Int, height: Int, c1: Color, c2: Color): Image {
        val surface = Surface(width, height)
        surface.canvas {
            val paint = Paint(shader = org.graphiks.kanvas.paint.Shader.LinearGradient(
                start = Point(0f, 0f), end = Point(width.toFloat(), height.toFloat()),
                stops = listOf(
                    org.graphiks.kanvas.paint.GradientStop(0f, c1),
                    org.graphiks.kanvas.paint.GradientStop(1f, c2),
                ),
            ))
            drawRect(Rect.fromXYWH(0f, 0f, width.toFloat(), height.toFloat()), paint)
        }
        return surface.makeImageSnapshot()
    }
}
