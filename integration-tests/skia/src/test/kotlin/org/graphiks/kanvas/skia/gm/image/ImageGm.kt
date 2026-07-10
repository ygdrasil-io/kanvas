package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.geometry.Path
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.PaintStyle
import org.graphiks.kanvas.paint.SamplingOptions
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.text.Font
import org.graphiks.kanvas.text.Typefaces
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/image.cpp::ImageGM` (960 x 1200, name "image-surface").
 * Snapshots a 64x64 raster surface in successive draw+snapshot stages.
 * @see https://github.com/google/skia/blob/main/gm/image.cpp
 */
class ImageGm : SkiaGm {
    override val name = "image-surface"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 960
    override val height = 1200

    private val typeface = Typefaces.fromResource("fonts/LiberationSans-Regular.ttf")!!

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.scale(2f, 2f)

        val font = Font(typeface, size = 8f)
        canvas.drawString("Original Img", 10f, 60f, font, Paint())
        canvas.drawString("Modified Img", 10f, 140f, font, Paint())
        canvas.drawString("Cur Surface", 10f, 220f, font, Paint())
        canvas.drawString("Full Crop", 10f, 300f, font, Paint())
        canvas.drawString("Over-crop", 10f, 380f, font, Paint())
        canvas.drawString("Upper-left", 10f, 460f, font, Paint())
        canvas.drawString("No Crop", 10f, 540f, font, Paint())
        canvas.drawString("Pre-Alloc Img", 80f, 10f, font, Paint())
        canvas.drawString("New Alloc Img", 160f, 10f, font, Paint())
        canvas.drawString("GPU", 265f, 10f, font, Paint())

        canvas.translate(80f, 20f)

        val surf0 = Surface(K_W, K_H)
        testSurface(canvas, surf0)
        canvas.translate(80f, 0f)

        val surf1 = Surface(K_W, K_H)
        testSurface(canvas, surf1)
    }

    private fun drawContents(surface: Surface, fill: Color) {
        val w = K_W.toFloat()
        val h = K_H.toFloat()
        val stroke = w / 10f
        val radius = (w - stroke) / 2f
        val cx = w / 2f
        val cy = h / 2f
        val fillPaint = Paint(color = fill)
        surface.canvas {
            val path = org.graphiks.kanvas.geometry.Path { }.apply { addCircle(cx, cy, radius) }
            drawPath(path, fillPaint)
        }

        val strokePaint = Paint(
            color = Color.BLACK,
            style = PaintStyle.STROKE,
            strokeWidth = stroke,
        )
        surface.canvas {
            val path = org.graphiks.kanvas.geometry.Path { }.apply { addCircle(cx, cy, radius) }
            drawPath(path, strokePaint)
        }
    }

    private fun testSurface(canvas: GmCanvas, surf: Surface) {
        drawContents(surf, Color.RED)
        val imgR = surf.makeImageSnapshot()
        drawContents(surf, Color.GREEN)
        val imgG = surf.makeImageSnapshot()
        drawContents(surf, Color.BLUE)

        val sampling = SamplingOptions.NEAREST
        val paint: Paint? = null

        canvas.drawImage(imgR, Rect(0f, 0f, 64f, 64f), paint)
        canvas.drawImage(imgG, Rect(0f, 80f, 64f, 144f), paint)

        val surfImage = surf.makeImageSnapshot()
        canvas.drawImage(surfImage, Rect(0f, 160f, 64f, 224f), paint)

        val src1 = Rect.fromXYWH(0f, 0f, 64f, 64f)
        val src2 = Rect.fromLTRB(-32f, -32f, 64f, 64f)
        val src3 = Rect.fromXYWH(0f, 0f, 32f, 32f)

        val dst1 = Rect.fromLTRB(0f, 240f, 65f, 305f)
        val dst2 = Rect.fromLTRB(0f, 320f, 65f, 385f)
        val dst3 = Rect.fromLTRB(0f, 400f, 65f, 465f)
        val dst4 = Rect.fromLTRB(0f, 480f, 65f, 545f)

        canvas.drawImageRect(imgR, src1, dst1, paint)
        canvas.drawImageRect(imgG, src2, dst2, paint)
        canvas.drawImageRect(imgR, src3, dst3, paint)
        val fullSrc = Rect.fromXYWH(0f, 0f, imgG.width.toFloat(), imgG.height.toFloat())
        canvas.drawImageRect(imgG, fullSrc, dst4, paint)
    }

    companion object {
        const val K_W = 64
        const val K_H = 64
    }
}
