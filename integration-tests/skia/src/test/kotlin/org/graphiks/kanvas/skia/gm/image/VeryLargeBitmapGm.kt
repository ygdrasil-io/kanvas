package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
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
import org.graphiks.math.geometry.Point2F32
import org.graphiks.math.geometry.RectF32

/** Port of Skia's `gm/verylargebitmap.cpp`.
 *  Creates a very large (65K wide) bitmap and renders it as a tiled
 *  shader to test large-bitmap handling.
 *  @see https://github.com/google/skia/blob/main/gm/verylargebitmap.cpp
 */
class VeryLargeBitmapGm : SkiaGm {
    override val name = "verylargebitmap"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
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

    private fun showImage(canvas: GmCanvas, width: Int, height: Int, c1: Color, c2: Color) {
        val image = makeRasterImage(width, height, c1, c2)

        val borderPaint = Paint(style = PaintStyle.STROKE)

        var dstRect = RectF32.ofOriginSize(0f, 0f, 128f, 128f)

        canvas.save()
        canvas.clipRect(dstRect)
        canvas.drawImage(image, RectF32(0f, 0f, 128f, 128f))
        canvas.restore()
        canvas.drawRect(dstRect, borderPaint)

        dstRect = RectF32.ofOriginSize(dstRect.left + 150f, dstRect.top, dstRect.width(), dstRect.height())
        val hw = width / 2
        val hh = height / 2
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

    private fun makeRasterImage(width: Int, height: Int, c1: Color, c2: Color): Image {
        val surface = Surface(width, height)
        surface.canvas {
            val center = Point2F32(width / 2f, height / 2f)
            val radius = 40f
            val shader = Shader.RadialGradient(
                center = center,
                radius = radius,
                stops = listOf(
                    org.graphiks.kanvas.paint.GradientStop(0f, c1),
                    org.graphiks.kanvas.paint.GradientStop(1f, c2),
                ),
                tileMode = TileMode.MIRROR,
            )
            drawRect(RectF32.ofOriginSize(0f, 0f, width.toFloat(), height.toFloat()), Paint(shader = shader))
        }
        return surface.makeImageSnapshot()
    }
}
