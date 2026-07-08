package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/** Manual (low-similarity) variant of StrictConstraintNoRedAllowedGm. */
class StrictConstraintNoRedAllowedManualGm : SkiaGm {
    override val name = "strict_constraint_no_red_allowed_manual"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 128
    override val height = 128

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.5f, 0.5f, 0.5f)

        val image = makeGuardedImage()
        val src = Rect.fromLTRB(1f, 1f, 5f, 5f)

        canvas.save()
        canvas.clipRect(Rect.fromXYWH(8f, 8f, 48f, 48f))
        canvas.drawImageRect(image, src, Rect.fromXYWH(8f, 8f, 48f, 48f))
        canvas.restore()

        canvas.save()
        canvas.clipRect(Rect.fromXYWH(72f, 8f, 48f, 48f))
        canvas.drawImageRect(image, Rect.fromLTRB(1f, 1f, 5f, 5f), Rect.fromXYWH(72f, 8f, 48f, 48f))
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
