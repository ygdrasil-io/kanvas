package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/animated_gif.cpp`.
 * @see https://github.com/google/skia/blob/main/gm/animated_gif.cpp
 */
class AnimatedGifGm : SkiaGm {
    override val name = "animatedGif"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 2560
    override val height = 958

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        val data = loadResource("images/test640x479.gif") ?: return
        val image = Image.decode(data, "image/gif")
        canvas.drawImage(image, Rect.fromXYWH(0f, 0f, image.width.toFloat(), image.height.toFloat()))
    }

    private fun loadResource(path: String): ByteArray? =
        this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
}
