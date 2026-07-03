package org.graphiks.kanvas.skia.gm.image

import org.graphiks.kanvas.codec.Codec
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.skia.GmCanvas
import org.graphiks.kanvas.skia.RenderFamily
import org.graphiks.kanvas.skia.SkiaGm
import org.graphiks.kanvas.surface.Surface
import org.graphiks.kanvas.types.Rect

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
        val codecImage = skBitmapToImage(codecBitmap ?: error("Codec.getImage returned null bitmap"))

        val surfFlags = Surface(kSize * 2, kSize)
        surfFlags.canvas {
            drawImage(image, Rect(0f, 0f, kSize.toFloat(), kSize.toFloat()))
            drawImage(codecImage, Rect(kSize.toFloat(), 0f, (kSize * 2).toFloat(), kSize.toFloat()))
        }
        canvas.drawImage(surfFlags.makeImageSnapshot(), Rect(0f, 0f, (kSize * 2).toFloat(), kSize.toFloat()))
        canvas.translate(0f, kSize.toFloat())

        val surfSrgb = Surface(kSize * 2, kSize)
        surfSrgb.canvas {
            drawImage(image, Rect(0f, 0f, kSize.toFloat(), kSize.toFloat()))
            drawImage(codecImage, Rect(kSize.toFloat(), 0f, (kSize * 2).toFloat(), kSize.toFloat()))
        }
        canvas.drawImage(surfSrgb.makeImageSnapshot(), Rect(0f, 0f, (kSize * 2).toFloat(), kSize.toFloat()))
    }

    private fun skBitmapToImage(bm: org.skia.foundation.SkBitmap): Image {
        val w = bm.width
        val h = bm.height
        val argb = bm.pixels8888
        val rgba = ByteArray(w * h * 4)
        var di = 0
        for (pixel in argb) {
            val a = (pixel ushr 24) and 0xFF
            val r = (pixel ushr 16) and 0xFF
            val g = (pixel ushr 8) and 0xFF
            val b = pixel and 0xFF
            rgba[di] = b.toByte()
            rgba[di + 1] = g.toByte()
            rgba[di + 2] = r.toByte()
            rgba[di + 3] = a.toByte()
            di += 4
        }
        return Image.fromPixels(w, h, rgba)
    }

    private fun loadResource(path: String): ByteArray? {
        return this::class.java.classLoader?.getResourceAsStream(path)?.readBytes()
    }

    private companion object {
        private const val kSize: Int = 512
    }
}
