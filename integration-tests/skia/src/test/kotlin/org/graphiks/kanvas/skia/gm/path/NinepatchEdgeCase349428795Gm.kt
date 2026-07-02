package org.graphiks.kanvas.skia.gm.path

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/lattice.cpp::ninepatch_edge_case_349428795` (500 × 150).
 * Regression test for b/349428795: a nine-patch should be able to have
 * zero-sized regions on either end (left/right or top/bottom).
 * @see https://github.com/google/skia/blob/main/gm/lattice.cpp
 */
class NinepatchEdgeCase349428795Gm : SkiaGm {
    override val name = "ninepatch_edge_case_349428795"
    override val renderFamily = RenderFamily.PATH
    override val minSimilarity = 0.0
    override val width = 500
    override val height = 150

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0f, 0f, 1f, 1f) // blue background

        val nine = makeSymmetryTestImage()

        for (i in -1 until 6) {
            val center = Rect(i.toFloat(), 2f, (i + 4).toFloat(), 6f)
            // X-axis variant (top row)
            canvas.drawImageNine(
                nine,
                Rect.fromLTRB(center.left, center.top, center.right, center.bottom),
                Rect.fromXYWH(i * 70f + 80f, 10f, 64f, 64f),
            )
            // Y-axis variant (bottom row)
            canvas.drawImageNine(
                nine,
                Rect.fromLTRB(2f, i.toFloat(), 6f, (i + 4).toFloat()),
                Rect.fromXYWH(i * 70f + 80f, 80f, 64f, 64f),
            )
        }
    }

    private fun makeSymmetryTestImage(): Image {
        val w = 8
        val h = 8
        val pixels = ByteArray(w * h * 4)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val i = (y * w + x) * 4
                if (x in 2..5 && y in 2..5) {
                    // Green center
                    pixels[i] = 0.toByte()
                    pixels[i + 1] = (-1).toByte()
                    pixels[i + 2] = 0.toByte()
                    pixels[i + 3] = (-1).toByte()
                } else {
                    // Blue fill
                    pixels[i] = 0.toByte()
                    pixels[i + 1] = 0.toByte()
                    pixels[i + 2] = (-1).toByte()
                    pixels[i + 3] = (-1).toByte()
                }
            }
        }
        return Image.fromPixels(w, h, pixels, ColorType.RGBA_8888, "ninepatch_edge")
    }
}
