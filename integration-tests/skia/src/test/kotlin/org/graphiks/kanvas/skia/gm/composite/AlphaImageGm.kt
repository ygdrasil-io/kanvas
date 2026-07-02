package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.pipeline.BlurStyle
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.MaskFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/alpha_image.cpp`.
 * Tests A8 image drawn through color filter and blur mask filter combinations.
 * @see https://github.com/google/skia/blob/main/gm/alpha_image.cpp
 */
class AlphaImageGm : SkiaGm {
    override val name = "alpha_image"
    override val renderFamily = RenderFamily.COMPOSITE
    override val minSimilarity = 0.0
    override val width = 256
    override val height = 256

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val image = makeAlphaImage(96, 96)

        canvas.drawImage(
            image, Rect(16f, 16f, 112f, 112f),
            Paint(
                colorFilter = makeColorFilter(),
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 10f),
            ),
        )

        canvas.drawImage(
            image, Rect(144f, 16f, 240f, 112f),
            Paint(
                color = Color.fromRGBA(0f, 1f, 1f, 1f),
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 10f),
            ),
        )

        canvas.drawImage(
            image, Rect(16f, 144f, 112f, 240f),
            Paint(
                color = Color.fromRGBA(0f, 1f, 1f, 1f),
                colorFilter = makeColorFilter(),
                maskFilter = MaskFilter.Blur(BlurStyle.NORMAL, 10f),
            ),
        )

        canvas.drawImage(
            image, Rect(144f, 144f, 240f, 240f),
            Paint(colorFilter = makeColorFilter()),
        )
    }

    private fun makeAlphaImage(w: Int, h: Int): Image {
        val pixels = ByteArray(w * h) { 0x0A.toByte() }
        for (y in 0 until h) {
            for (x in y until w) {
                pixels[y * w + x] = 0xFF.toByte()
            }
        }
        return Image.fromPixels(w, h, pixels, ColorType.ALPHA_8, "alpha_image")
    }

    private fun makeColorFilter(): ColorFilter {
        val matrix = floatArrayOf(
            1f, 0f, 0f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            0f, 0f, 0.5f, 0.5f, 0f,
            0f, 0f, 0.5f, 0.5f, 0f,
        )
        return ColorFilter.Matrix(matrix)
    }
}
