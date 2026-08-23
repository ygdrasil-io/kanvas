package org.graphiks.kanvas.codec

import org.graphiks.kanvas.image.Bitmap
import org.graphiks.kanvas.image.ColorType
import org.graphiks.kanvas.image.Image
import org.graphiks.kanvas.image.ImageInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A concrete image generator backed by an [Codec] — mirrors
 * Skia's `CodecImageGenerator` (`src/codec/CodecImageGenerator.h`).
 *
 * Decodes the codec on demand into the destination buffer ; the codec's
 * own [Codec.getInfo] drives the generator's reported [ImageInfo].
 *
 * **Use** : pair with [ImageGeneratorImages.DeferredFromGenerator]
 * ( (alias removed in iter 3c)) to produce a
 * deferred-decoded [Image] from raw encoded bytes.
 */
public class CodecImageGenerator private constructor(
    private val codec: Codec,
) {
    public val info: ImageInfo = codec.getInfo()

    public fun getPixels(info: ImageInfo, pixels: ByteBuffer, rowBytes: Int): Boolean {
        if (info.width <= 0 || info.height <= 0 || rowBytes < info.minRowBytes()) return false
        if (info.width != this.info.width || info.height != this.info.height) return false
        if (info.colorType != ColorType.RGBA_8888) return false
        if (info.alphaType != this.info.alphaType || info.colorSpace !== this.info.colorSpace) return false
        val requiredBytes = (info.height - 1).toLong() * rowBytes.toLong() + info.minRowBytes().toLong()
        if (pixels.remaining().toLong() < requiredBytes) return false

        val bm = Bitmap(info)
        val res = codec.getPixels(info, bm)
        if (res != Codec.Result.kSuccess) return false
        // Pack the 32-bit pixels into the destination ByteBuffer in
        // RGBA byte order (matches the buffer layout the upstream
        // generator's [getPixels] consumers expect).
        val output = pixels.slice().order(ByteOrder.LITTLE_ENDIAN)
        val width = info.width
        val height = info.height
        for (y in 0 until height) {
            val rowOff = y * rowBytes
            for (x in 0 until width) {
                val c = bm.getArgb(x, y)
                val a = (c ushr 24) and 0xFF
                val r = (c ushr 16) and 0xFF
                val g = (c ushr 8) and 0xFF
                val b = c and 0xFF
                val off = rowOff + x * 4
                output.put(off, r.toByte())
                output.put(off + 1, g.toByte())
                output.put(off + 2, b.toByte())
                output.put(off + 3, a.toByte())
            }
        }
        return true
    }

    public companion object {
        /**
         * Mirrors Skia's
         * `CodecImageGenerator::MakeFromEncodedCodec`.
         * Returns `null` when the bytes cannot be sniffed by any
         * registered [Codec] decoder.
         */
        public fun MakeFromEncodedCodec(data: ByteArray): CodecImageGenerator? {
            val codec = Codec.MakeFromData(data) ?: return null
            return CodecImageGenerator(codec)
        }
    }
}

/**
 * Static factories for [Image] creation that hinge on an
 * [CodecImageGenerator]. Lives in its own file to keep generator-owned factories
 * separate from bitmap and encoded-image factories.
 */
public object ImageGeneratorImages {

    /**
     * Mirrors an upstream deferred-from-generator factory.
     *
     * Decodes the generator into an [ColorType.RGBA_8888] buffer at
     * the generator's reported size and returns a fresh [Image]. The
     * upstream "deferred" semantic (the generator is held lazily and
     * decoded only when the image is first drawn) is *not* preserved by
     * the Kanvas raster image path — every renderer consumer reads from a materialised
     * pixel buffer, so we decode eagerly. The factory name is kept for
     * source-compatibility with upstream call sites.
     *
     * Returns `null` if the generator's [CodecImageGenerator.getPixels]
     * call fails.
     */
    public fun DeferredFromGenerator(generator: CodecImageGenerator): Image? {
        val info = generator.info
        if (info.isEmpty()) return null
        val target = info.makeColorType(ColorType.RGBA_8888)
            .makeAlphaType(info.alphaType)
        val rowBytes = target.minRowBytes()
        val bytes = ByteBuffer
            .allocate(rowBytes * target.height)

        if (!generator.getPixels(target, bytes, rowBytes)) return null
        val bitmap = Bitmap(target)
        bytes.rewind()
        bytes.get(bitmap.pixels)
        return bitmap.toImageOrNull()
    }
}
