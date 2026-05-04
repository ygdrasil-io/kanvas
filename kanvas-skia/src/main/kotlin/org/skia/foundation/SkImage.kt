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
         */
        public fun Make(bitmap: SkBitmap): SkImage =
            SkImage(bitmap.width, bitmap.height, bitmap.pixels.copyOf())
    }
}
