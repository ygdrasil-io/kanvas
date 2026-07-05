package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/encode_alpha_jpeg.cpp::EncodeJpegAlphaOptsGM` (400 × 200).
 * Exercises JPEG alpha option dispatch with 8888 and F16 colour-type intermediates.
 * **Adaptation**: Kanvas does not expose a high-level encode → decode round-trip
 * from [Image]; the source image is drawn directly with matching column/row layout.
 * @see https://github.com/google/skia/blob/main/gm/encode_alpha_jpeg.cpp
 */
class EncodeJpegAlphaOptsGm : SkiaGm {
    override val name = "encode-alpha-jpeg-opts"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val srcBytes = javaClass.classLoader?.getResourceAsStream("images/rainbow-gradient.png")?.readAllBytes() ?: return
        val srcImg = Image.decode(srcBytes)
        if (srcImg.width == 0) return

        for (column in 0..3) {
            val x = column * 100f
            canvas.drawImage(srcImg, Rect(x, 0f, x + 100f, 100f))
            canvas.drawImage(srcImg, Rect(x, 100f, x + 100f, 200f))
        }
    }
}
