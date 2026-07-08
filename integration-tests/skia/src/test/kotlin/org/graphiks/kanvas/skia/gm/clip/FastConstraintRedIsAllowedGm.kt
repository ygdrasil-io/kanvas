package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Tests fast constraint mode where red guard pixels are allowed to bleed
 * into the clipped draw region. Draws a guarded image (red border, green
 * center) with a clip applied and uses fast image sampling, so red pixels
 * outside the constrained source rect may be visible.
 *
 * @see org.graphiks.kanvas.skia.gm.image.SrcRectConstraintGm
 */
class FastConstraintRedIsAllowedGm : SkiaGm {
    override val name = "fast_constraint_red_is_allowed"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 80.0
    override val width = 256
    override val height = 128

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.5f, 0.5f, 0.5f)

        val image = makeGuardedImage()
        val src = Rect.fromLTRB(1f, 1f, 5f, 5f)

        // clip area matches src rect → fast sampling may bring in red guard pixels
        canvas.save()
        canvas.clipRect(Rect.fromXYWH(8f, 8f, 32f, 32f))
        canvas.drawImageRect(image, src, Rect.fromXYWH(8f, 8f, 32f, 32f))
        canvas.restore()

        canvas.save()
        canvas.clipRect(Rect.fromXYWH(56f, 8f, 32f, 32f))
        canvas.drawImageRect(image, src, Rect.fromXYWH(56f, 8f, 32f, 32f))
        canvas.restore()

        canvas.save()
        canvas.clipRect(Rect.fromXYWH(104f, 8f, 32f, 32f))
        canvas.drawImageRect(image, Rect.fromLTRB(0f, 0f, 6f, 6f), Rect.fromXYWH(104f, 8f, 32f, 32f))
        canvas.restore()

        canvas.save()
        canvas.clipRect(Rect.fromXYWH(152f, 8f, 32f, 32f))
        canvas.drawImageRect(image, Rect.fromLTRB(2f, 2f, 4f, 4f), Rect.fromXYWH(152f, 8f, 32f, 32f))
        canvas.restore()

        canvas.save()
        canvas.clipRect(Rect.fromXYWH(200f, 8f, 32f, 32f))
        canvas.drawImageRect(image, Rect.fromLTRB(1f, 1f, 5f, 5f), Rect.fromXYWH(200f, 8f, 32f, 32f))
        canvas.restore()
    }

    private fun makeGuardedImage(): Image {
        val pixels = ByteArray(6 * 6 * 4)
        for (y in 0 until 6) {
            for (x in 0 until 6) {
                val i = (y * 6 + x) * 4
                val inside = x in 1..4 && y in 1..4
                if (inside) {
                    pixels[i] = 0x00.toByte(); pixels[i + 1] = 0xFF.toByte()
                    pixels[i + 2] = 0x00.toByte(); pixels[i + 3] = 0xFF.toByte()
                } else {
                    pixels[i] = 0xFF.toByte(); pixels[i + 1] = 0x00.toByte()
                    pixels[i + 2] = 0x00.toByte(); pixels[i + 3] = 0xFF.toByte()
                }
            }
        }
        return Image.fromPixels(6, 6, pixels, sourceId = "guarded")
    }
}
