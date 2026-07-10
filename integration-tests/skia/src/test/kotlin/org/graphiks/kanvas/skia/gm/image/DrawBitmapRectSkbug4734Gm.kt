package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Rect

/**
 * Port of Skia's `gm/draw_bitmap_rect_skbug4374.cpp::draw_bitmap_rect_skbug4734`.
 * Loads `images/randPixels.png`, insets the source rect by (0.5, 1.5) and maps
 * it through Scale(8, 8) to produce the destination rect. Exercises sub-pixel
 * src insets for drawImageRect precision under fractional src coordinates.
 * @see https://github.com/google/skia/blob/main/gm/draw_bitmap_rect_skbug4374.cpp
 */
class DrawBitmapRectSkbug4734Gm : SkiaGm {
    override val name = "draw_bitmap_rect_skbug4734"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 64
    override val height = 64

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = loadResource("images/randPixels.png") ?: return
        val image = Image.decode(bytes)
        val w = image.width.toFloat()
        val h = image.height.toFloat()
        val src = Rect.fromLTRB(0.5f, 1.5f, w - 0.5f, h - 1.5f)
        val dst = Rect.fromLTRB(0.5f * 8f, 1.5f * 8f, (w - 0.5f) * 8f, (h - 1.5f) * 8f)
        canvas.drawImageRect(image, src, dst)
    }

    private fun loadResource(path: String): ByteArray? {
        return this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
    }
}
