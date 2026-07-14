package org.graphiks.math.geometry

/**
 * Integer size (width, height).
 *
 * Iso-aligned port of Skia's `SkISize`
 * ([include/core/SkSize.h](https://github.com/google/skia/blob/main/include/core/SkSize.h)).
 */
public data class SizeI32(val width: Int, val height: Int) {
    /** Returns `true` if width or height is non-positive. */
    public fun isEmpty(): Boolean = width <= 0 || height <= 0

    /** Returns `true` if both dimensions are zero. */
    public fun isZero(): Boolean = width == 0 && height == 0

    public companion object {
        /** Zero size. */
        public val Empty: SizeI32 = SizeI32(0, 0)

        /** Creates a [SizeI32] from dimensions. */
        public fun of(width: Int, height: Int): SizeI32 = SizeI32(width, height)
    }
}

/**
 * Float size (width, height).
 *
 * Iso-aligned port of Skia's `SkSize`
 * ([include/core/SkSize.h](https://github.com/google/skia/blob/main/include/core/SkSize.h)).
 */
public data class SizeF32(val width: Float, val height: Float) {
    /** Returns `true` if width or height is non-positive. */
    public fun isEmpty(): Boolean = width <= 0f || height <= 0f

    public companion object {
        /** Zero size. */
        public val Empty: SizeF32 = SizeF32(0f, 0f)

        /** Creates a [SizeF32] from dimensions. */
        public fun of(width: Float, height: Float): SizeF32 = SizeF32(width, height)

        /** Creates a [SizeF32] from a [SizeI32]. */
        public fun from(size: SizeI32): SizeF32 =
            SizeF32(size.width.toFloat(), size.height.toFloat())
    }
}
