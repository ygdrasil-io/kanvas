package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.image.AlphaType
import org.graphiks.kanvas.paint.Paint
import org.graphiks.kanvas.paint.Shader
import org.graphiks.kanvas.paint.TileMode
import org.graphiks.kanvas.render.ir.CompositionDomain
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.math.color.ColorARGB
import org.graphiks.math.geometry.RectF32

/** Port of Skia's `gm/tinybitmap.cpp`.
 *  Tiles one premultiplied translucent red pixel across the declared gray background.
 *  @see https://github.com/google/skia/blob/4f26f22daa4bf124e2999145f5caad4b10625580/gm/tinybitmap.cpp
 */
class TinyBitmapGm : SkiaGm {
    // The pinned bare8888 harness composes this non-AA GM in encoded space.
    override val compositionDomain = CompositionDomain.SRGB_ENCODED
    override val name = "tinybitmap"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 100
    override val height = 100
    override val backgroundColor = ColorARGB.of(0xFF, 0xDD, 0xDD, 0xDD)

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val pixels = byteArrayOf(0x80.toByte(), 0x00, 0x00, 0x80.toByte())
        val image = Image.fromPixels(1, 1, pixels, alphaType = AlphaType.PREMUL)

        val paint = Paint(
            shader = Shader.Image(image, TileMode.REPEAT, TileMode.MIRROR),
            antiAlias = false,
        )
        canvas.drawRect(
            RectF32(0f, 0f, width.toFloat(), height.toFloat()),
            paint.copy(color = ColorARGB.fromRGBA(1f, 1f, 1f, 0.5f)),
        )
    }
}
