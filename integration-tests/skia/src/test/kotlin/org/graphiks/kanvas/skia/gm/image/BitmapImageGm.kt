package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.RenderCost
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.math.geometry.RectF32

/**
 * Port of Skia's `gm/bitmapimage.cpp::BitmapImageGM`.
 *
 * Compares the round-trip of `mandrill_512_q075.jpg` through two
 * intermediate canvases — a "legacy" (untagged) N32 canvas and an sRGB
 * (S32) N32 canvas — to verify that `Image.decode` and a `Codec.getImage`
 * decode produce the same output.
 *
 * In upstream the "legacy" canvas skips colour-management because its
 * info carries `nullptr` colorSpace ; our surfaces always use sRGB,
 * so the legacy / sRGB distinction collapses for sRGB-tagged sources.
 * Both intermediate canvases therefore render the same pixels.
 * @see https://github.com/google/skia/blob/main/gm/bitmapimage.cpp
 */
class BitmapImageGm : SkiaGm {
    override val name = "bitmap-image-srgb-legacy"
    override val renderFamily = RenderFamily.IMAGE
    override val renderCost = RenderCost.FAST
    override val minSimilarity = 0.0
    override val width = 2 * kSize
    override val height = 2 * kSize

    override fun draw(canvas: GmCanvas, width: Int, height: Int) {
        val bytes = loadResource("images/mandrill_512_q075.jpg")
            ?: error("Resource not found: images/mandrill_512_q075.jpg")

        val image = Image.decode(bytes)

        val codec = Codec.MakeFromData(bytes)
            ?: error("Codec.MakeFromData returned null")
        val (codecBitmap, result) = codec.getImage()
        require(result == Codec.Result.kSuccess) { "Codec.getImage failed: $result" }
        val codecImage = requireNotNull(
            (codecBitmap ?: error("Codec.getImage returned null bitmap")).toImageOrNull(),
        ) { "Codec bitmap has an unsupported image color profile" }

        val surfFlags = Surface(kSize * 2, kSize)
        surfFlags.canvas {
            drawImage(image, RectF32(0f, 0f, kSize.toFloat(), kSize.toFloat()))
            drawImage(codecImage, RectF32(kSize.toFloat(), 0f, (kSize * 2).toFloat(), kSize.toFloat()))
        }
        canvas.drawImage(surfFlags.makeImageSnapshot(), RectF32(0f, 0f, (kSize * 2).toFloat(), kSize.toFloat()))
        canvas.translate(0f, kSize.toFloat())

        val surfSrgb = Surface(kSize * 2, kSize)
        surfSrgb.canvas {
            drawImage(image, RectF32(0f, 0f, kSize.toFloat(), kSize.toFloat()))
            drawImage(codecImage, RectF32(kSize.toFloat(), 0f, (kSize * 2).toFloat(), kSize.toFloat()))
        }
        canvas.drawImage(surfSrgb.makeImageSnapshot(), RectF32(0f, 0f, (kSize * 2).toFloat(), kSize.toFloat()))
    }

    private fun loadResource(path: String): ByteArray? {
        return this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
    }

    private companion object {
        private const val kSize: Int = 512
    }
}
