package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color
import org.graphiks.kanvas.types.Rect

/** Port of Skia's `gm/tinybitmap.cpp`.
 *  Creates a small gradient image and renders it as a tiled shader
 *  across the canvas.
 *  @see https://github.com/google/skia/blob/main/gm/tinybitmap.cpp
 */
class TinyBitmapGm : SkiaGm {
    override val name = "tinybitmap"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 100
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        canvas.drawColor(0xDD / 255f, 0xDD / 255f, 0xDD / 255f)

        val pixels = byteArrayOf(0x80.toByte(), 0xFF.toByte(), 0x00, 0x00)
        val image = Image.fromPixels(1, 1, pixels)

        val paint = Paint(
            shader = Shader.Image(image, TileMode.REPEAT, TileMode.MIRROR),
        )
        canvas.drawRect(
            Rect(0f, 0f, width.toFloat(), height.toFloat()),
            paint.copy(color = Color.fromRGBA(1f, 1f, 1f, 0.5f)),
        )
    }
}
