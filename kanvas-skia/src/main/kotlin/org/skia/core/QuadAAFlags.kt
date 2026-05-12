package org.skia.core

/**
 * Mirrors Skia's `SkCanvas::QuadAAFlags`
 * ([include/core/SkCanvas.h](https://github.com/google/skia/blob/main/include/core/SkCanvas.h)):
 *
 * ```cpp
 * enum QuadAAFlags : unsigned {
 *     kLeft_QuadAAFlag    = 0b0001,
 *     kTop_QuadAAFlag     = 0b0010,
 *     kRight_QuadAAFlag   = 0b0100,
 *     kBottom_QuadAAFlag  = 0b1000,
 *
 *     kNone_QuadAAFlags   = 0b0000,
 *     kAll_QuadAAFlags    = 0b1111,
 * };
 * ```
 *
 * Used to drive selective per-edge anti-aliasing in
 * [SkCanvas.experimental_DrawEdgeAAQuad]. Each entry's [bits] mirrors the
 * upstream constant; consumers receive the flag set as a plain `Int` bitmask
 * (matching the C++ `unsigned` parameter). The two convenience entries
 * [kNone] (`0`) and [kAll] (`15`) cover the all-or-nothing cases that hit
 * upstream's CPU fast paths.
 *
 * **AA semantics on CPU raster** — upstream's `SkDevice::drawEdgeAAQuad`
 * (`src/core/SkDevice.cpp`) enables AA *only when all four edges are
 * flagged* (`paint.setAntiAlias(aa == kAll_QuadAAFlags)`). Per-edge AA is a
 * GPU-only feature; the raster backend deliberately uses the all-or-nothing
 * shortcut to avoid seaming in tiled composited layers. We follow upstream
 * verbatim — see [SkCanvas.experimental_DrawEdgeAAQuad].
 */
public enum class QuadAAFlags(public val bits: Int) {
    kNone(0b0000),
    kLeft(0b0001),
    kTop(0b0010),
    kRight(0b0100),
    kBottom(0b1000),
    kAll(0b1111);

    public companion object {
        /** Bitmask value for the empty set. Matches `kNone_QuadAAFlags = 0`. */
        public const val kNone_QuadAAFlags: Int = 0b0000

        /** Bitmask value for "left edge AA". Matches `kLeft_QuadAAFlag`. */
        public const val kLeft_QuadAAFlag: Int = 0b0001

        /** Bitmask value for "top edge AA". Matches `kTop_QuadAAFlag`. */
        public const val kTop_QuadAAFlag: Int = 0b0010

        /** Bitmask value for "right edge AA". Matches `kRight_QuadAAFlag`. */
        public const val kRight_QuadAAFlag: Int = 0b0100

        /** Bitmask value for "bottom edge AA". Matches `kBottom_QuadAAFlag`. */
        public const val kBottom_QuadAAFlag: Int = 0b1000

        /** Bitmask value with all four edges AA. Matches `kAll_QuadAAFlags = 15`. */
        public const val kAll_QuadAAFlags: Int = 0b1111

        /**
         * Compose a bitmask from any combination of [QuadAAFlags] entries:
         *
         * ```kotlin
         * val mask = QuadAAFlags.of(QuadAAFlags.kLeft, QuadAAFlags.kTop)
         * // → 0b0011  (== kLeft_QuadAAFlag | kTop_QuadAAFlag)
         * ```
         *
         * Empty argument list returns [kNone_QuadAAFlags] (`0`).
         * Duplicate flags are OR-idempotent.
         */
        public fun of(vararg flags: QuadAAFlags): Int = flags.fold(0) { acc, f -> acc or f.bits }
    }
}
