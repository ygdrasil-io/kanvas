package org.graphiks.kanvas.skia.gm.composite

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.ColorFilter
import org.graphiks.kanvas.paint.ImageFilter
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/imagefiltersbase.cpp` (offset subset).
 * Tests ImageFilter.Offset, ImageFilter.ColorFilter, and ImageFilter.Compose.
 * @see https://github.com/google/skia/blob/main/gm/imagefiltersbase.cpp
 */
class ImageFilterOffsetGm : SkiaGm {
    override val name = "image_filter_offset"
    override val renderFamily = RenderFamily.COMPOSITE
    override val renderCost = RenderCost.BLOCKING
    override val minSimilarity = 0.0
    override val width = 280
    override val height = 100

    private val sourceImage: Image = run {
        val w = 64; val h = 64
        val pixels = ByteArray(w * h * 4) { 0xFF.toByte() }
        val img = Image.fromPixels(w, h, pixels, ColorType.RGBA_8888, "source")
        val surf = org.graphiks.kanvas.surface.Surface(w, h)
        surf.canvas {
            drawImage(img, Rect(0f, 0f, w.toFloat(), h.toFloat()))
            drawRect(Rect(8f, 8f, 56f, 56f), Paint(color = Color.RED))
            drawRect(Rect(20f, 28f, 44f, 36f), Paint(color = Color.BLACK))
        }
        surf.makeImageSnapshot()
    }

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val dst = Rect(0f, 0f, 64f, 64f)

        canvas.drawImage(sourceImage, Rect(10f, 10f, 74f, 74f))

        canvas.drawImage(
            sourceImage, Rect(100f, 10f, 164f, 74f),
            Paint(imageFilter = ImageFilter.Offset(15f, 10f)),
        )

        val swapRB = ColorFilter.Matrix(floatArrayOf(
            0f, 0f, 1f, 0f, 0f,
            0f, 1f, 0f, 0f, 0f,
            1f, 0f, 0f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ))
        canvas.drawImage(
            sourceImage, Rect(190f, 10f, 254f, 74f),
            Paint(imageFilter = ImageFilter.Compose(
                outer = ImageFilter.Offset(15f, 10f),
                inner = ImageFilter.ColorFilter(swapRB),
            )),
        )
    }
}
