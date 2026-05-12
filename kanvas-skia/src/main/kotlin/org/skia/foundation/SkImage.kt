package org.skia.foundation

/**
 * Mirrors Skia's [`SkImage`](https://github.com/google/skia/blob/main/include/core/SkImage.h).
 *
 * The kanvas-skia implementation is a thin wrapper around an immutable
 * snapshot of an [SkBitmap]'s pixels. Skia's full surface/texture model
 * (GPU backing, codec-decoded streams, lazy mips) is intentionally out of
 * scope — we only need read-only access to a rectangular pixel buffer.
 *
 * Construct via [SkBitmap.asImage] or the [Make] factory.
 */
public class SkImage internal constructor(
    public val width: Int,
    public val height: Int,
    internal val pixels: IntArray,
    /**
     * Colour type of the bitmap this image was snapshotted from.
     * Internally the pixel buffer is always 8888 (so [SkBitmapShader] and
     * the raster device can read [pixels] uniformly), but callers that
     * need to know the originating type — e.g. an Alpha8 source image
     * fed into a colour-filter pipeline — can introspect this field.
     */
    public val colorType: SkColorType = SkColorType.kRGBA_8888,
    /**
     * Phase G10 — optional pre-rendered mip pyramid. `null` until the
     * caller invokes [withDefaultMipmaps]. When non-null,
     * `mipLevels[0]` is the same `width × height × pixels` as this image
     * (level 0), and each subsequent entry halves the previous level
     * (rounding down, minimum 1) with 2×2 box-filter averaging. The
     * pyramid stops at 1×1, mirroring Skia's `SkMipmap::Build` levels.
     */
    internal val mipLevels: List<MipLevel>? = null,
) {
    /** A single pre-rendered mip level — level 0 is the base image. */
    internal class MipLevel(val width: Int, val height: Int, val pixels: IntArray)

    /** Direct read of pixel `(x, y)`; returns `0` if outside image bounds. */
    public fun peekPixel(x: Int, y: Int): SkColor =
        if (x in 0 until width && y in 0 until height) pixels[y * width + x] else 0

    /**
     * Mirrors Skia's `SkImage::makeShader(tmx, tmy, sampling, localMatrix)`.
     * Phase 5g — see [SkBitmapShader] for the sampling rules.
     */
    public fun makeShader(
        tileX: SkTileMode = SkTileMode.kClamp,
        tileY: SkTileMode = SkTileMode.kClamp,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
        localMatrix: org.skia.math.SkMatrix = org.skia.math.SkMatrix.Identity,
    ): SkShader = SkBitmapShader(this, tileX, tileY, sampling, localMatrix)

    /**
     * Mirrors Skia's `SkImage::makeShader(sampling, localMatrix)` —
     * defaults the tile modes to `kClamp`.
     */
    public fun makeShader(
        sampling: SkSamplingOptions,
        localMatrix: org.skia.math.SkMatrix = org.skia.math.SkMatrix.Identity,
    ): SkShader = SkBitmapShader(this, SkTileMode.kClamp, SkTileMode.kClamp, sampling, localMatrix)

    /**
     * Phase G10 — number of mip levels stored on this image, or `1`
     * when no mip pyramid has been pre-built. Matches the Skia
     * `SkMipmap::CountLevels` convention (`floor(log2(min(w, h))) + 1`
     * when the pyramid is built down to 1×1).
     */
    public fun levelCount(): Int = mipLevels?.size ?: 1

    /**
     * Phase G10 — pre-build a box-filtered mip pyramid down to 1×1.
     * Each level halves the previous (round-down, minimum 1) and stores
     * a fresh `IntArray` so the pyramid is self-contained.
     *
     * Mirrors Skia's `SkImage::withDefaultMipmaps()`. Returns a *new*
     * [SkImage] sharing the level-0 pixel buffer with `this` — the
     * original image is left untouched.
     *
     * Box-filter is the same simplification Skia falls back to for
     * raster mip generation : per output texel, average the four
     * 2×2 source texels in unpremul ARGB space. For odd-sized levels
     * the rightmost column / bottom row is averaged with the duplicated
     * last-column / last-row texel so the dimensions still halve.
     */
    public fun withDefaultMipmaps(): SkImage {
        // Build levels until the smaller dimension reaches 1.
        val levels = ArrayList<MipLevel>()
        levels += MipLevel(width, height, pixels)
        var w = width
        var h = height
        var src = pixels
        while (w > 1 || h > 1) {
            val nw = maxOf(1, w / 2)
            val nh = maxOf(1, h / 2)
            val dst = IntArray(nw * nh)
            for (y in 0 until nh) {
                val sy0 = (2 * y).coerceAtMost(h - 1)
                val sy1 = (2 * y + 1).coerceAtMost(h - 1)
                for (x in 0 until nw) {
                    val sx0 = (2 * x).coerceAtMost(w - 1)
                    val sx1 = (2 * x + 1).coerceAtMost(w - 1)
                    val c00 = src[sy0 * w + sx0]
                    val c10 = src[sy0 * w + sx1]
                    val c01 = src[sy1 * w + sx0]
                    val c11 = src[sy1 * w + sx1]
                    // Average 4 texels in unpremul ARGB. Skia's
                    // raster mip uses premul-RGB for the colour
                    // channels but the difference is invisible for
                    // opaque images (the common case) and minor for
                    // mixed-alpha mip building.
                    val a = (SkColorGetA(c00) + SkColorGetA(c10) +
                             SkColorGetA(c01) + SkColorGetA(c11) + 2) shr 2
                    val r = (SkColorGetR(c00) + SkColorGetR(c10) +
                             SkColorGetR(c01) + SkColorGetR(c11) + 2) shr 2
                    val g = (SkColorGetG(c00) + SkColorGetG(c10) +
                             SkColorGetG(c01) + SkColorGetG(c11) + 2) shr 2
                    val b = (SkColorGetB(c00) + SkColorGetB(c10) +
                             SkColorGetB(c01) + SkColorGetB(c11) + 2) shr 2
                    dst[y * nw + x] = SkColorSetARGB(a, r, g, b)
                }
            }
            levels += MipLevel(nw, nh, dst)
            w = nw
            h = nh
            src = dst
        }
        return SkImage(width, height, pixels, colorType, levels)
    }

    public companion object {
        /**
         * Snapshot the bitmap into a new immutable [SkImage]. The pixel
         * buffer is copied — subsequent edits to the source bitmap don't
         * leak into the image.
         *
         * The snapshot is always 8888 (Skia's `SkSurface::makeImageSnapshot()`
         * default contract — the snapshot's working precision is
         * implementation-defined; we pick 8888 to keep the [SkImage]
         * surface uniform and to match what every consumer downstream
         * ([SkBitmapShader] in particular) reads from `image.pixels`).
         * For F16 source bitmaps this means a per-pixel quantization
         * via [SkBitmap.getPixel] — same path the live `bitmap.getPixel`
         * accessor takes, so a snapshot stays bit-identical to the
         * live `bitmap.getPixel(x, y)` view.
         */
        public fun Make(bitmap: SkBitmap): SkImage {
            val w = bitmap.width
            val h = bitmap.height
            val out = when (bitmap.colorType) {
                SkColorType.kRGBA_8888 -> bitmap.pixels.copyOf()
                else -> {
                    // Walk via the colorType-aware accessor so F16 / Alpha8 /
                    // ARGB_4444 (and any future colorType) snapshots correctly
                    // through to 8888.
                    val a = IntArray(w * h)
                    for (y in 0 until h) {
                        for (x in 0 until w) a[y * w + x] = bitmap.getPixel(x, y)
                    }
                    a
                }
            }
            return SkImage(w, h, out, bitmap.colorType)
        }
    }
}
