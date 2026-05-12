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
) {
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
