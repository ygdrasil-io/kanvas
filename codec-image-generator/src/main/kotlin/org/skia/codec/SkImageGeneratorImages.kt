package org.skia.codec

import org.skia.foundation.SkAlphaType
import org.skia.foundation.SkBitmap
import org.graphiks.math.SkColorGetA
import org.graphiks.math.SkColorGetB
import org.graphiks.math.SkColorGetG
import org.graphiks.math.SkColorGetR
import org.graphiks.math.SkColorSetARGB
import org.skia.foundation.SkColorType
import org.skia.foundation.SkImage
import org.skia.foundation.SkImageGenerator
import org.skia.foundation.SkImageInfo
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * A concrete [SkImageGenerator] backed by an [SkCodec] — mirrors
 * Skia's `SkCodecImageGenerator` (`src/codec/SkCodecImageGenerator.h`).
 *
 * Decodes the codec on demand into the destination buffer ; the codec's
 * own [SkCodec.getInfo] drives the generator's reported [SkImageInfo].
 *
 * **Use** : pair with [SkImageGeneratorImages.DeferredFromGenerator]
 * ( (alias removed in iter 3c)) to produce a
 * deferred-decoded [SkImage] from raw encoded bytes.
 */
public class SkCodecImageGenerator private constructor(
    private val codec: SkCodec,
) : SkImageGenerator(codec.getInfo()) {

    override fun onGetPixels(info: SkImageInfo, pixels: ByteBuffer, rowBytes: Int): Boolean {
        val bm = SkBitmap(
            width = info.width,
            height = info.height,
            colorSpace = info.colorSpace,
            colorType = SkColorType.kRGBA_8888,
        )
        val res = codec.getPixels(codec.getInfo(), bm)
        if (res != SkCodec.Result.kSuccess) return false
        // Pack the 32-bit pixels into the destination ByteBuffer in
        // RGBA byte order (matches the buffer layout the upstream
        // generator's [getPixels] consumers expect).
        val width = info.width
        val height = info.height
        for (y in 0 until height) {
            val rowOff = y * rowBytes
            for (x in 0 until width) {
                val c = bm.pixels[y * width + x]
                val off = rowOff + x * 4
                pixels.put(off, SkColorGetR(c).toByte())
                pixels.put(off + 1, SkColorGetG(c).toByte())
                pixels.put(off + 2, SkColorGetB(c).toByte())
                pixels.put(off + 3, SkColorGetA(c).toByte())
            }
        }
        return true
    }

    public companion object {
        /**
         * Mirrors Skia's
         * `SkCodecImageGenerator::MakeFromEncodedCodec(sk_sp<SkData>)`.
         * Returns `null` when the bytes cannot be sniffed by any
         * registered [SkCodec] decoder.
         */
        public fun MakeFromEncodedCodec(data: ByteArray): SkCodecImageGenerator? {
            val codec = SkCodec.MakeFromData(data) ?: return null
            return SkCodecImageGenerator(codec)
        }
    }
}

/**
 * Static factories for [SkImage] creation that hinge on an
 * [SkImageGenerator]. Lives in its own file so the future cross-cutting
 * `SkImages` aggregator (mirrors Skia's `SkImages` namespace) can pick
 * this up without an ownership conflict with the parallel agent
 * responsible for the bitmap / encoded factories.
 */
public object SkImageGeneratorImages {

    /**
     * Mirrors Skia's `SkImages::DeferredFromGenerator(sk_sp<SkImageGenerator>)`.
     *
     * Decodes the generator into an `SkColorType.kRGBA_8888` buffer at
     * the generator's reported size and returns a fresh [SkImage]. The
     * upstream "deferred" semantic (the generator is held lazily and
     * decoded only when the image is first drawn) is *not* preserved by
     * the kanvas-skia raster path — every [SkImage] consumer
     * ([SkBitmapShader], the raster device) reads from a materialised
     * pixel buffer, so we decode eagerly. The factory name is kept for
     * source-compatibility with upstream call sites.
     *
     * Returns `null` if the generator's [SkImageGenerator.getPixels]
     * call fails.
     */
    public fun DeferredFromGenerator(generator: SkImageGenerator): SkImage? {
        val info = generator.getInfo()
        if (info.isEmpty()) return null
        // Always materialise into 8888 — matches [SkImage]'s internal
        // pixel-buffer contract (see SkImage.kt KDoc).
        val target = info.makeColorType(SkColorType.kRGBA_8888)
            .makeAlphaType(SkAlphaType.kUnpremul)
        val rowBytes = target.minRowBytes()
        val bytes = ByteBuffer
            .allocate(rowBytes * target.height)
            .order(ByteOrder.LITTLE_ENDIAN)
        if (!generator.getPixels(target, bytes, rowBytes)) return null
        // Convert the byte buffer into the IntArray the SkImage
        // constructor wants. Pack as Pascal-Argb (`0xAARRGGBB`) — the
        // same layout SkBitmap.pixels8888 uses on a little-endian host.
        val w = target.width
        val h = target.height
        val pixels = IntArray(w * h)
        for (y in 0 until h) {
            val rowOffset = y * rowBytes
            for (x in 0 until w) {
                val off = rowOffset + x * 4
                val r = bytes.get(off).toInt() and 0xFF
                val g = bytes.get(off + 1).toInt() and 0xFF
                val b = bytes.get(off + 2).toInt() and 0xFF
                val a = bytes.get(off + 3).toInt() and 0xFF
                pixels[y * w + x] = SkColorSetARGB(a, r, g, b)
            }
        }
        return SkImage(w, h, pixels, SkColorType.kRGBA_8888)
    }
}
