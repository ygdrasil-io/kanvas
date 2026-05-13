package org.skia.foundation

import java.nio.ByteBuffer
import java.nio.ByteOrder

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
