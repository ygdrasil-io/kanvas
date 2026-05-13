package org.skia.foundation

import org.skia.math.SkISize

/**
 * Mirrors Skia's
 * [`SkImageInfo`](https://github.com/google/skia/blob/main/include/core/SkImageInfo.h)
 * — an immutable description of pixel geometry (width × height) and
 * encoding (colour type, alpha type, colour space).
 *
 * The kanvas-skia raster pipeline stores pixels as one of two colour
 * types ([SkColorType.kRGBA_8888] or [SkColorType.kRGBA_F16Norm]) under
 * [SkAlphaType.kUnpremul] (8888) or [SkAlphaType.kPremul] (F16) — see
 * [SkBitmap]'s comment block for the rationale. Other Skia colour types
 * are accepted by the constructor but are unsupported by the rasterizer.
 *
 * Used as the manifest passed to [org.skia.core.SkSurface.MakeRaster] /
 * [org.skia.core.SkSurface.MakeRasterDirect] when creating a backing
 * bitmap, and returned from [org.skia.core.SkSurface.imageInfo] so
 * client code can read back the configuration without poking at the
 * underlying [SkBitmap] directly.
 */
public class SkImageInfo private constructor(
    public val width: Int,
    public val height: Int,
    public val colorType: SkColorType,
    public val alphaType: SkAlphaType,
    public val colorSpace: SkColorSpace,
) {
    init {
        require(width >= 0 && height >= 0) { "negative dimensions: ${width}x${height}" }
    }

    public fun dimensions(): SkISize = SkISize.Make(width, height)
    public fun bounds(): org.skia.math.SkIRect =
        org.skia.math.SkIRect.MakeWH(width, height)
    public fun isEmpty(): Boolean = width <= 0 || height <= 0
    public fun isOpaque(): Boolean = alphaType == SkAlphaType.kOpaque

    /** Bytes per pixel implied by [colorType]. */
    public fun bytesPerPixel(): Int = when (colorType) {
        SkColorType.kAlpha_8 -> 1
        SkColorType.kGray_8 -> 1
        SkColorType.kARGB_4444 -> 2
        SkColorType.kRGB_565 -> 2
        SkColorType.kRGBA_8888 -> 4
        SkColorType.kBGRA_8888 -> 4
        SkColorType.kRGBA_F16Norm -> 8
        else -> error("bytesPerPixel: unsupported colorType $colorType")
    }

    /** Minimum row-bytes for tightly packed storage. */
    public fun minRowBytes(): Int = width * bytesPerPixel()

    public fun makeWH(newW: Int, newH: Int): SkImageInfo =
        SkImageInfo(newW, newH, colorType, alphaType, colorSpace)

    public fun makeColorType(ct: SkColorType): SkImageInfo =
        SkImageInfo(width, height, ct, alphaType, colorSpace)

    public fun makeAlphaType(at: SkAlphaType): SkImageInfo =
        SkImageInfo(width, height, colorType, at, colorSpace)

    public fun makeColorSpace(cs: SkColorSpace): SkImageInfo =
        SkImageInfo(width, height, colorType, alphaType, cs)

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is SkImageInfo) return false
        return width == other.width && height == other.height &&
            colorType == other.colorType && alphaType == other.alphaType &&
            colorSpace == other.colorSpace
    }

    override fun hashCode(): Int {
        var r = width
        r = 31 * r + height
        r = 31 * r + colorType.hashCode()
        r = 31 * r + alphaType.hashCode()
        r = 31 * r + colorSpace.hashCode()
        return r
    }

    override fun toString(): String =
        "SkImageInfo(${width}x$height, $colorType, $alphaType, $colorSpace)"

    public companion object {
        /**
         * Mirrors Skia's `SkImageInfo::Make(w, h, ct, at, cs)`. Defaults
         * for `at` and `cs` follow the kanvas-skia convention: 8888 →
         * unpremul + sRGB; F16 → premul + sRGB. Override explicitly when
         * targeting a different working space (e.g. Rec.2020 for
         * upstream-DM-faithful rendering).
         */
        public fun Make(
            width: Int,
            height: Int,
            colorType: SkColorType = SkColorType.kRGBA_8888,
            alphaType: SkAlphaType = defaultAlphaTypeFor(colorType),
            colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
        ): SkImageInfo = SkImageInfo(width, height, colorType, alphaType, colorSpace)

        /**
         * Mirrors `SkImageInfo::MakeN32(w, h, at, cs)` — N32 = the
         * platform's natural 32-bit colour type. We always pick
         * [SkColorType.kRGBA_8888] (kanvas-skia is colour-order
         * normalised: ARGB Int, A in MSB).
         */
        public fun MakeN32(
            width: Int,
            height: Int,
            alphaType: SkAlphaType = SkAlphaType.kUnpremul,
            colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
        ): SkImageInfo = Make(width, height, SkColorType.kRGBA_8888, alphaType, colorSpace)

        /** Mirrors `SkImageInfo::MakeN32Premul(w, h, cs)`. */
        public fun MakeN32Premul(
            width: Int,
            height: Int,
            colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
        ): SkImageInfo = Make(width, height, SkColorType.kRGBA_8888, SkAlphaType.kPremul, colorSpace)

        /**
         * Mirrors Skia's `SkImageInfo::MakeA8(w, h)` — alpha-only,
         * single-byte-per-pixel image info. Alpha type defaults to
         * premultiplied (matches upstream).
         */
        public fun MakeA8(
            width: Int,
            height: Int,
            colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
        ): SkImageInfo = Make(width, height, SkColorType.kAlpha_8, SkAlphaType.kPremul, colorSpace)

        /**
         * Phase G4c — convenience factory for `kARGB_4444` image info.
         *
         * Upstream callers spell this as
         * `SkImageInfo::Make(w, h, kARGB_4444_SkColorType, kPremul_SkAlphaType)`
         * (see `gm/bitmappremul.cpp` and `gm/copy_to_4444.cpp`). Skia
         * doesn't ship a `MakeARGB4444` shorthand, but porting any of
         * the in-scope 4444 GMs threads the same five-arg call enough
         * times that exposing a dedicated factory keeps the Kotlin
         * ports readable. Alpha type defaults to premultiplied —
         * matches the (only) configuration Skia GMs use for 4444.
         */
        public fun Make4444(
            width: Int,
            height: Int,
            colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
        ): SkImageInfo = Make(width, height, SkColorType.kARGB_4444, SkAlphaType.kPremul, colorSpace)

        /**
         * Phase R1-C — convenience factory for `kRGB_565` image info.
         * Mirrors Skia's `SkImageInfo::Make(w, h, kRGB_565_SkColorType, kOpaque_SkAlphaType)`
         * (used by `gm/all_bitmap_configs.cpp`, `gm/bitmapfilters.cpp`).
         * Alpha type is forced to opaque — 565 has no alpha channel.
         */
        public fun MakeRGB565(
            width: Int,
            height: Int,
            colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
        ): SkImageInfo = Make(width, height, SkColorType.kRGB_565, SkAlphaType.kOpaque, colorSpace)

        /**
         * Phase R1-C — convenience factory for `kGray_8` image info.
         * Mirrors Skia's `SkImageInfo::Make(w, h, kGray_8_SkColorType, kOpaque_SkAlphaType)`
         * (used by `gm/all_bitmap_configs.cpp`, `gm/bitmapcopy.cpp`).
         * Alpha type is forced to opaque — Gray8 has no alpha channel.
         */
        public fun MakeGray8(
            width: Int,
            height: Int,
            colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
        ): SkImageInfo = Make(width, height, SkColorType.kGray_8, SkAlphaType.kOpaque, colorSpace)

        private fun defaultAlphaTypeFor(ct: SkColorType): SkAlphaType = when (ct) {
            SkColorType.kRGBA_8888 -> SkAlphaType.kUnpremul
            SkColorType.kBGRA_8888 -> SkAlphaType.kUnpremul
            SkColorType.kRGBA_F16Norm -> SkAlphaType.kPremul
            SkColorType.kAlpha_8 -> SkAlphaType.kPremul
            SkColorType.kARGB_4444 -> SkAlphaType.kPremul
            // Phase R1-C — 565 and Gray8 are always opaque (no alpha channel).
            SkColorType.kRGB_565 -> SkAlphaType.kOpaque
            SkColorType.kGray_8 -> SkAlphaType.kOpaque
            else -> SkAlphaType.kUnknown
        }
    }
}
