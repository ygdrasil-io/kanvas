package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32

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
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 80.0
    override val width = 500
    override val height = 600

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val veryBig = 65 * 1024
        val big = 33 * 1024
        val medium = 5 * 1024
        val small = 150

        canvas.translate(10f, 10f)

        showImage(canvas, small, small, ColorARGB.Red, ColorARGB.Green)
        canvas.translate(0f, 150f)

        showImage(canvas, big, small, ColorARGB.Blue, ColorARGB.fromRGBA(1f, 0f, 1f, 1f))
        canvas.translate(0f, 150f)

        showImage(canvas, medium, medium, ColorARGB.fromRGBA(1f, 0f, 1f, 1f), ColorARGB.fromRGBA(1f, 1f, 0f, 1f))
        canvas.translate(0f, 150f)

        showImage(canvas, veryBig, small, ColorARGB.Green, ColorARGB.fromRGBA(1f, 1f, 0f, 1f))
    }

    private fun showImage(canvas: GmCanvas, w: Int, h: Int, c1: ColorARGB, c2: ColorARGB) {
        val image = makePictureImage(w, h, c1, c2)

        val borderPaint = Paint(style = PaintStyle.STROKE)

        var dstRect = RectF32.ofOriginSize(0f, 0f, 128f, 128f)

        canvas.save()
        canvas.clipRect(dstRect)
        canvas.drawImage(image, RectF32(0f, 0f, 128f, 128f))
        canvas.restore()
        canvas.drawRect(dstRect, borderPaint)

        dstRect = RectF32.ofOriginSize(dstRect.left + 150f, dstRect.top, dstRect.width(), dstRect.height())
        val hw = w / 2
        val hh = h / 2
        val subset = RectF32.ofLTRB((hw - 64).toFloat(), (hh - 32).toFloat(), (hw + 64).toFloat(), (hh + 32).toFloat())
        canvas.drawImageRect(image, subset, dstRect)
        canvas.drawRect(dstRect, borderPaint)

        dstRect = RectF32.ofOriginSize(dstRect.left + 150f, dstRect.top, dstRect.width(), dstRect.height())
        canvas.drawImageRect(
            image,
            RectF32.ofLTRB(0f, 0f, image.width.toFloat(), image.height.toFloat()),
            dstRect,
        )
        canvas.drawRect(dstRect, borderPaint)
    }

    private fun makePictureImage(width: Int, height: Int, c1: ColorARGB, c2: ColorARGB): Image {
        val surface = Surface(width, height)
        surface.canvas {
            val paint = Paint(shader = org.graphiks.kanvas.paint.Shader.LinearGradient(
                start = Point2F32(0f, 0f), end = Point2F32(width.toFloat(), height.toFloat()),
                stops = listOf(
                    org.graphiks.kanvas.paint.GradientStop(0f, c1),
                    org.graphiks.kanvas.paint.GradientStop(1f, c2),
                ),
            ))
            drawRect(RectF32.ofOriginSize(0f, 0f, width.toFloat(), height.toFloat()), paint)
        }
        return surface.makeImageSnapshot()
    }
}
