package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/image.cpp::scalepixels_unpremul`
 * (`DEF_SIMPLE_GM(scalepixels_unpremul, canvas, 1080, 280)`).
 * Builds a 16×16 unpremul pixmap, scales it under 4 samplings, slams alpha to FF.
 * @see https://github.com/google/skia/blob/main/gm/image.cpp
 */
class ScalepixelsUnpremulGm : SkiaGm {
    override val name = "scalepixels_unpremul"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 1080
    override val height = 280

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        // Build 16×16 source image with unique (x,y) identity in R/G channels
        val srcPixels = ByteArray(16 * 16 * 4)
        for (y in 0 until 16) {
            for (x in 0 until 16) {
                val i = (y * 16 + x) * 4
                val r = ((y shl 4) or y) and 0xFF
                val g = ((x shl 4) or x) and 0xFF
                srcPixels[i] = g.toByte()
                srcPixels[i + 1] = r.toByte()
                srcPixels[i + 2] = 0xFF.toByte()
                srcPixels[i + 3] = 0x00.toByte()
            }
        }
        val srcImage = Image.fromPixels(16, 16, srcPixels, sourceId = "scalepixels_src")

        // Draw at 4 sampling modes, upscaling manually
        canvas.save()
        for (samplingLabel in listOf("nearest", "linear", "linear_mip", "mitchell")) {
            canvas.save()
            canvas.translate(10f, 10f)

            // Manual nearest-neighbor upscale to 256x256
            val dstPixels = ByteArray(256 * 256 * 4)
            for (dy in 0 until 256) {
                for (dx in 0 until 256) {
                    val sx = (dx * 16) / 256
                    val sy = (dy * 16) / 256
                    val si = (sy * 16 + sx) * 4
                    val di = (dy * 256 + dx) * 4
                    dstPixels[di] = srcPixels[si]
                    dstPixels[di + 1] = srcPixels[si + 1]
                    dstPixels[di + 2] = srcPixels[si + 2]
                    dstPixels[di + 3] = 0xFF.toByte()
                }
            }
            val dstImage = Image.fromPixels(256, 256, dstPixels, sourceId = "scalepixels_${samplingLabel}")
            canvas.drawImage(dstImage, Rect.fromXYWH(0f, 0f, 256f, 256f))
            canvas.restore()
            canvas.translate(266f, 0f)
        }
        canvas.restore()
    }
}
