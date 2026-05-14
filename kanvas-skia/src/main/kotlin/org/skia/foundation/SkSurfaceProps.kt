package org.skia.foundation

/**
 * Mirrors Skia's
 * [`SkSurfaceProps`](https://github.com/google/skia/blob/main/include/core/SkSurfaceProps.h)
 * — pixel-geometry + behaviour flags carried by an
 * [org.skia.core.SkSurface] and inherited by sub-surfaces created via
 * [org.skia.core.SkCanvas.makeSurface].
 *
 * Skia uses these to drive LCD-stripe ordering for sub-pixel text
 * rendering (see `LcdBlendGM` in `gm/lcdblendmodes.cpp`) — a surface
 * advertising `kRGB_H_SkPixelGeometry` knows the device's stripes are
 * RGB-ordered horizontally and biases the LCD coverage masks
 * accordingly. Raster surfaces created without an explicit pixel
 * geometry default to [SkPixelGeometry.kUnknown], which collapses LCD
 * rendering to greyscale (the safe choice when the device's stripe
 * ordering is unknown).
 *
 * `:kanvas-skia` — the raster backend doesn't yet emit LCD masks (text
 * always renders as greyscale alpha) so `pixelGeometry` is informational
 * only ; it round-trips through the canvas / surface API surface so call
 * sites that capture the value (e.g. recording sinks, sub-surface chains
 * via [org.skia.core.SkCanvas.makeSurface]) preserve the upstream API
 * contract. The `flags` field is reserved for the upstream
 * `kUseDeviceIndependentFonts_Flag` / `kAlwaysDither_Flag` bitmask but
 * has no behavioural effect in the raster path today.
 *
 * Default-constructed value matches upstream's
 * `SkSurfaceProps()` zero-init : `flags = 0` and
 * `pixelGeometry = kUnknown_SkPixelGeometry`. Equality is value-based
 * (Kotlin data class).
 */
public data class SkSurfaceProps(
    /**
     * Bitmask of upstream `SkSurfaceProps::Flags` values. Mirrors the
     * `uint32_t fFlags` field — defaults to `0` to match upstream's
     * zero-init constructor. Flag bits :
     *
     *  - `0x1` — `kUseDeviceIndependentFonts_Flag` : prefer device-
     *    independent font metrics (glyph hinting bypass). Reserved for
     *    parity ; no behavioural effect in `:kanvas-skia` today.
     *  - `0x2` — `kAlwaysDither_Flag` : force dithering on every draw
     *    (raster has no native dither — also reserved).
     *  - `0x4` — `kDynamicMSAA_Flag` : GPU-only ; no-op on raster.
     */
    public val flags: Int = 0,
    /**
     * Pixel geometry advertised by the surface — drives LCD stripe
     * ordering. Defaults to [SkPixelGeometry.kUnknown] which collapses
     * LCD-text rendering to greyscale (the safe default for unknown
     * displays).
     */
    public val pixelGeometry: SkPixelGeometry = SkPixelGeometry.kUnknown,
) {
    /**
     * Mirrors upstream's `SkPixelGeometry` enum
     * (`include/core/SkSurfaceProps.h:18`). Names match the upstream
     * `k…_SkPixelGeometry` values one-for-one.
     */
    public enum class SkPixelGeometry {
        /** Arbitrary stripe ordering — LCD text falls back to greyscale. */
        kUnknown,

        /** Horizontal stripes, R-G-B order. */
        kRGB_H,

        /** Horizontal stripes, B-G-R order. */
        kBGR_H,

        /** Vertical stripes, R-G-B order (top-to-bottom). */
        kRGB_V,

        /** Vertical stripes, B-G-R order. */
        kBGR_V,
    }

    public companion object {
        /** Mirrors upstream `SkSurfaceProps::Flags::kUseDeviceIndependentFonts_Flag`. */
        public const val kUseDeviceIndependentFonts_Flag: Int = 0x1

        /** Mirrors upstream `SkSurfaceProps::Flags::kAlwaysDither_Flag`. */
        public const val kAlwaysDither_Flag: Int = 0x2

        /** Mirrors upstream `SkSurfaceProps::Flags::kDynamicMSAA_Flag`. */
        public const val kDynamicMSAA_Flag: Int = 0x4
    }
}
