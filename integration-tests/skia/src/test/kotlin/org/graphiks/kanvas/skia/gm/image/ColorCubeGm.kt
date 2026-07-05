package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/jpg_color_cube.cpp::ColorCubeGM` (512 × 512).
 * Builds an 8×8 grid of 64×64 patches walking the full RGB cube.
 * Original upstream round-trips through JPEG encode/decode; this port
 * builds the pixel buffer directly since Kanvas does not expose an
 * equivalent pipeline in the GM context.
 * @see https://github.com/google/skia/blob/main/gm/jpg_color_cube.cpp
 */
class ColorCubeGm : SkiaGm {
    override val name = "jpg-color-cube"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 512
    override val height = 512

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val pixels = ByteArray(512 * 512 * 4)
        var idx = 0
        for (y in 0 until 512) {
            val by = y / 64
            val gLocal = y % 64
            for (x in 0 until 512) {
                val bx = x / 64
                val rLocal = x % 64
                val patchIndex = by * 8 + bx
                val red = (rLocal * 4).coerceIn(0, 255)
                val green = (gLocal * 4).coerceIn(0, 255)
                val blue = (patchIndex * 4).coerceIn(0, 255)
                pixels[idx++] = red.toByte()
                pixels[idx++] = green.toByte()
                pixels[idx++] = blue.toByte()
                pixels[idx++] = 255.toByte()
            }
        }

        val image = Image.fromPixels(512, 512, pixels, ColorType.RGBA_8888, "jpg-color-cube")
        canvas.drawImage(image, Rect.fromXYWH(0f, 0f, 512f, 512f))
    }
}
