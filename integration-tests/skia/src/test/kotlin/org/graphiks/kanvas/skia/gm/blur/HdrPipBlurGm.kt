package org.graphiks.kanvas.skia.gm.blur

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/hdr_pip_blur.cpp::HDRPiPBlurGM` (640 x 360).
 * Emulates Android HDR pipeline rendering with blur shade.
 * Simplified: uses programmatic images instead of resource loading.
 * @see https://github.com/google/skia/blob/main/gm/hdr_pip_blur.cpp
 */
class HdrPipBlurGm : SkiaGm {
    override val name = "hdr-pip-blur"
    override val renderFamily = RenderFamily.BLUR
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 640
    override val height = 360

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.25f, 0.25f, 0.25f, 1f)

        val bgW = 200
        val bgH = 200
        val bgPixels = ByteArray(bgW * bgH * 4)
        for (y in 0 until bgH) {
            for (x in 0 until bgW) {
                val i = (y * bgW + x) * 4
                val r = ((x.toFloat() / bgW) * 255f).toInt().coerceIn(0, 255)
                val g = ((y.toFloat() / bgH) * 255f).toInt().coerceIn(0, 255)
                val b = (((1f - x.toFloat() / bgW) * 255f).toInt()).coerceIn(0, 255)
                bgPixels[i] = r.toByte()
                bgPixels[i + 1] = g.toByte()
                bgPixels[i + 2] = b.toByte()
                bgPixels[i + 3] = (-1).toByte()
            }
        }
        val bgImage = Image.fromPixels(bgW, bgH, bgPixels, ColorType.RGBA_8888, "bg")

        canvas.drawImage(
            bgImage,
            Rect.fromXYWH(50f, 30f, width.toFloat() - 100f, (height * 0.5f).toInt().toFloat()),
            Paint(color = Color.WHITE),
        )

        val pipRect = Rect.fromXYWH(100f, 80f, 400f, 200f)
        val pipPaint = Paint(
            color = Color.fromRGBA(1f, 0f, 0f, 0.3f),
            imageFilter = ImageFilter.Blur(8f, 8f),
        )
        canvas.drawRect(pipRect, pipPaint)

        val shadeRect = Rect.fromXYWH(0f, 0f, width.toFloat(), height.toFloat())
        val shadePaint = Paint(
            color = Color.fromRGBA(0f, 0f, 0f, 0.5f),
            imageFilter = ImageFilter.Blur(32f, 32f),
        )
        canvas.drawRect(shadeRect, shadePaint)
    }
}
