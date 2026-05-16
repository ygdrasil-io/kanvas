package org.skia.foundation

import org.skia.math.SkMatrix

/**
 * Mirrors Skia's
 * [`SkEncodedOrigin`](https://github.com/google/skia/blob/main/include/codec/SkEncodedOrigin.h)
 * — the EXIF "Orientation" tag, used by JPEG / TIFF / HEIF decoders to
 * tell the renderer how the pixel grid was stored relative to the
 * scene's intended top-left.
 *
 * The numeric values mirror the EXIF spec ([www.exif.org/Exif2-2.PDF]) so
 * encoded streams can be read out as raw `Int`s and converted via
 * [fromExifValue] when needed.
 */
public enum class SkEncodedOrigin(public val exifValue: Int) {
    /** Default. */
    kTopLeft(1),
    /** Reflected across the y axis. */
    kTopRight(2),
    /** Rotated 180°. */
    kBottomRight(3),
    /** Reflected across the x axis. */
    kBottomLeft(4),
    /** Reflected across x, then rotated 90° CCW. */
    kLeftTop(5),
    /** Rotated 90° CW. */
    kRightTop(6),
    /** Reflected across x, then rotated 90° CW. */
    kRightBottom(7),
    /** Rotated 90° CCW. */
    kLeftBottom(8);

    /**
     * True if the orientation includes a 90° rotation — meaning the
     * decoded width and height are swapped relative to the encoded
     * pixel grid. Mirrors upstream's `SkEncodedOriginSwapsWidthHeight`.
     */
    public fun swapsWidthHeight(): Boolean = this.ordinal >= kLeftTop.ordinal

    /**
     * Return the matrix that maps a source rectangle `[0, 0, w, h]` to
     * a correctly-oriented destination of the same dimensions (with
     * width/height swapped if [swapsWidthHeight]). Mirrors
     * `SkEncodedOriginToMatrix`.
     */
    public fun toMatrix(w: Int, h: Int): SkMatrix {
        val W = w.toFloat()
        val H = h.toFloat()
        return when (this) {
            kTopLeft     -> SkMatrix.I()
            kTopRight    -> SkMatrix.MakeAll(-1f,  0f,  W,   0f,  1f, 0f, 0f, 0f, 1f)
            kBottomRight -> SkMatrix.MakeAll(-1f,  0f,  W,   0f, -1f, H,  0f, 0f, 1f)
            kBottomLeft  -> SkMatrix.MakeAll( 1f,  0f,  0f,  0f, -1f, H,  0f, 0f, 1f)
            kLeftTop     -> SkMatrix.MakeAll( 0f,  1f,  0f,  1f,  0f, 0f, 0f, 0f, 1f)
            kRightTop    -> SkMatrix.MakeAll( 0f, -1f,  W,   1f,  0f, 0f, 0f, 0f, 1f)
            kRightBottom -> SkMatrix.MakeAll( 0f, -1f,  W,  -1f,  0f, H,  0f, 0f, 1f)
            kLeftBottom  -> SkMatrix.MakeAll( 0f,  1f,  0f, -1f,  0f, H,  0f, 0f, 1f)
        }
    }

    public companion object {
        public val kDefault: SkEncodedOrigin = kTopLeft
        public val kLast: SkEncodedOrigin = kLeftBottom

        /** Convert a raw EXIF Orientation int (1..8) to the enum. */
        public fun fromExifValue(v: Int): SkEncodedOrigin =
            values().firstOrNull { it.exifValue == v } ?: kDefault
    }
}
