package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/colorwheel.cpp::DEF_SIMPLE_GM(colorwheel_alphatypes, canvas, 256, 128)`.
 * Compares premul-then-filter vs filter-then-premul (unpremul) image draws.
 * @see https://github.com/google/skia/blob/main/gm/colorwheel.cpp
 */
class ColorWheelAlphaTypesGm : SkiaGm {
    override val name = "colorwheel_alphatypes"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 128

    override fun draw(canvas: GmCanvas, width0: Int, height0: Int) {
        canvas.drawColor(1f, 1f, 1f, 1f)

        val bytes = loadResource("images/color_wheel.png") ?: return
        val img = Image.decode(bytes)

        val srcRect = Rect.fromXYWH(12f, 102f, 8f, 8f)
        val dstRect = Rect.fromXYWH(0f, 0f, 128f, 128f)

        canvas.drawImageRect(img, srcRect, dstRect)
        canvas.drawImageRect(img, srcRect, dstRect.copy(left = 128f, right = 256f))
    }

    private fun loadResource(path: String): ByteArray? =
        this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
}
