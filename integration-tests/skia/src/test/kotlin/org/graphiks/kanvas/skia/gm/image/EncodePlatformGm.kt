package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/encode_platform.cpp::EncodePlatformGM`.
 * 5-column × 3-row matrix of 256×256 images.
 * **Adaptation**: Kanvas does not expose a high-level encode → decode round-trip
 * from [Image]; source images are drawn directly in the upstream grid layout.
 * @see https://github.com/google/skia/blob/main/gm/encode_platform.cpp
 */
class EncodePlatformGm : SkiaGm {
    override val name = "encode-platform"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256 * 5
    override val height = 256 * 3

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val opaqueBytes = javaClass.classLoader?.getResourceAsStream("images/mandrill_256.png")?.readAllBytes() ?: return
        val opaqueImg = Image.decode(opaqueBytes)
        if (opaqueImg.width == 0) return

        val roseBytes = javaClass.classLoader?.getResourceAsStream("images/yellow_rose.png")?.readAllBytes() ?: return
        val roseImg = Image.decode(roseBytes)
        if (roseImg.width == 0) return

        var x = 0f
        for (i in 0..4) {
            canvas.drawImage(opaqueImg, Rect(x, 0f, x + 256f, 256f))
            canvas.drawImage(roseImg, Rect(x, 256f, x + 256f, 512f))
            canvas.drawImage(roseImg, Rect(x, 512f, x + 256f, 768f))
            x += 256f
        }
    }
}
