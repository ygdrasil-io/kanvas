package org.graphiks.kanvas.skia.gm.clip

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/** Manual (low-similarity) variant of StrictConstraintBatchNoRedAllowedGm. */
class StrictConstraintBatchNoRedAllowedManualGm : SkiaGm {
    override val name = "strict_constraint_batch_no_red_allowed_manual"
    override val renderFamily = RenderFamily.CLIP
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 128

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0.5f, 0.5f, 0.5f)

        val image = makeGuardedImage()

        val draws = listOf(
            Rect.fromLTRB(1f, 1f, 5f, 5f) to Rect.fromXYWH(8f, 8f, 32f, 32f),
            Rect.fromLTRB(0f, 1f, 6f, 5f) to Rect.fromXYWH(56f, 8f, 32f, 32f),
            Rect.fromLTRB(1f, 0f, 5f, 6f) to Rect.fromXYWH(104f, 8f, 32f, 32f),
            Rect.fromLTRB(2f, 2f, 4f, 4f) to Rect.fromXYWH(152f, 8f, 32f, 32f),
            Rect.fromLTRB(1f, 1f, 5f, 5f) to Rect.fromXYWH(200f, 8f, 32f, 32f),
        )

        for ((src, dst) in draws) {
            canvas.save()
            canvas.clipRect(dst)
            canvas.drawImageRect(image, src, dst)
            canvas.restore()
        }
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
