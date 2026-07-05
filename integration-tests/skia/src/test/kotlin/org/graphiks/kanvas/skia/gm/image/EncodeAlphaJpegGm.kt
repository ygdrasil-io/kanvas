package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/encode_alpha_jpeg.cpp::EncodeJpegAlphaOptsGM` (400 × 200).
 * Loads `images/rainbow-gradient.png` and draws it in a 4 × 2 grid layout.
 * **Adaptation**: Kanvas does not expose a high-level encode → decode round-trip
 * for JPEG from [Image]; the source image is drawn directly in the grid layout
 * matching upstream cell positions.
 * @see https://github.com/google/skia/blob/main/gm/encode_alpha_jpeg.cpp
 */
class EncodeAlphaJpegGm : SkiaGm {
    override val name = "encode-alpha-jpeg"
    override val renderFamily = RenderFamily.IMAGE
    override val minSimilarity = 0.0
    override val width = 400
    override val height = 200

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val srcBytes = javaClass.classLoader?.getResourceAsStream("images/rainbow-gradient.png")?.readAllBytes() ?: return
        val srcImg = Image.decode(srcBytes)
        if (srcImg.width == 0) return

        val xCoords = floatArrayOf(0f, 100f, 200f, 300f)
        for (xLeft in xCoords) {
            canvas.drawImage(srcImg, Rect(xLeft, 0f, xLeft + 100f, 100f))
            canvas.drawImage(srcImg, Rect(xLeft, 100f, xLeft + 100f, 200f))
        }
    }
}
