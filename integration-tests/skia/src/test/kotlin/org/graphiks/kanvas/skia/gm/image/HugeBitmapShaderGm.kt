package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.types.Color

/** Port of Skia's `gm/bitmapshader.cpp` (huge-bitmap variant).
 *  Creates a 1x60000 ALPHA_8 image and renders it as a mirrored shader
 *  in a circle to test large-bitmap-shader handling.
 *  @see https://github.com/google/skia/blob/main/gm/bitmapshader.cpp
 */
class HugeBitmapShaderGm : SkiaGm {
    override val name = "hugebitmapshader"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 100
    override val height = 100

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bitmapW = 1
        val bitmapH = 60_000

        val pixels = ByteArray(bitmapW * bitmapH)
        for (i in 0 until bitmapH) {
            pixels[i] = (i and 0xFF).toByte()
        }
        val image = Image.fromPixels(bitmapW, bitmapH, pixels, ColorType.ALPHA_8, "huge_bitmap_shader")

        val paint = Paint(
            color = Color.RED,
            antiAlias = true,
            shader = Shader.Image(image, TileMode.MIRROR, TileMode.MIRROR),
        )

        canvas.drawCircle(50f, 50f, 50f, paint)
    }
}
