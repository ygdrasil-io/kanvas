package org.skia.foundation

/**
 * Describes how a pixel's bits encode colour. Mirrors Skia's `SkColorType`.
 *
 * Each variant's bit layout matches upstream verbatim. The upstream names
 * carry a `_SkColorType` suffix; under Kotlin enum scoping it's redundant
 * (e.g. upstream `kRGBA_8888_SkColorType` ↔ [kRGBA_8888]).
 *
 * `:kanvas-skia`'s `SkBitmap` currently only renders into [kRGBA_8888]
 * laid out as 0xAARRGGBB Int (interpreted as ARGB on a LE host). Other
 * variants are listed for forward compatibility with future image /
 * surface support, plus inspection of upstream pixel data.
 *
 * @property bytesPerPixel size of one pixel on disk; `0` for [kUnknown].
 *   Returns `bytesPerPixel * width` when computing row strides on a
 *   gap-free buffer (the `:kanvas-skia` default).
 */
public enum class SkColorType(public val bytesPerPixel: Int) {
    /** Unknown or unrepresentable as an `SkColorType`. */
    kUnknown(0),

    /** Single 8-bit channel interpreted as alpha. RGB are 0. Bits: `[A:7..0]`. */
    kAlpha_8(1),

    /** BGR data packed into a LE 16-bit word (5R/6G/5B). Bits: `[R:15..11 G:10..5 B:4..0]`. */
    kRGB_565(2),

    /** ABGR data packed into a LE 16-bit word (4 bits / channel). Bits: `[R:15..12 G:11..8 B:7..4 A:3..0]`. */
    kARGB_4444(2),

    /** RGBA data packed into a LE 32-bit word (8 bits / channel). Bits: `[A:31..24 B:23..16 G:15..8 R:7..0]`. */
    kRGBA_8888(4),

    /** RGB data packed into a LE 32-bit word; alpha forced opaque. Bits: `[x:31..24 B:23..16 G:15..8 R:7..0]`. */
    kRGB_888x(4),

    /** BGRA data packed into a LE 32-bit word (R and B swapped vs `kRGBA_8888`). Bits: `[A:31..24 R:23..16 G:15..8 B:7..0]`. */
    kBGRA_8888(4),

    /** RGBA in a LE 32-bit word, 10/10/10/2. Bits: `[A:31..30 B:29..20 G:19..10 R:9..0]`. */
    kRGBA_1010102(4),

    /** BGRA in a LE 32-bit word, 10/10/10/2. */
    kBGRA_1010102(4),

    /** RGB in a LE 32-bit word, 10/10/10 + 2 unused. Alpha forced opaque. */
    kRGB_101010x(4),

    /** BGR in a LE 32-bit word, 10/10/10 + 2 unused. Alpha forced opaque. */
    kBGR_101010x(4),

    /** Extended-range BGR in a LE 32-bit word, 10/10/10 + 2 unused. Alpha forced opaque. */
    kBGR_101010x_XR(4),

    /** Extended-range BGRA in a LE 64-bit word, 10/10/10/10 with 6-bit padding per channel. */
    kBGRA_10101010_XR(8),

    /** RGBA in a LE 64-bit word, 10/10/10/10 with 6-bit padding per channel. */
    kRGBA_10x6(8),

    /** Single 8-bit channel interpreted as grayscale (replicated to RGB). */
    kGray_8(1),

    /** RGBA, 16-bit half-float per channel; values clamped to [0, 1] semantically. */
    kRGBA_F16Norm(8),

    /** RGBA, 16-bit half-float per channel; extended range. */
    kRGBA_F16(8),

    /** RGB, 16-bit half-float per channel; alpha forced opaque. Bits: `[x:63..48 B:47..32 G:31..16 R:15..0]`. */
    kRGB_F16F16F16x(8),

    /** RGBA, 32-bit float per channel. */
    kRGBA_F32(16),

    /** Two-channel RG, 8 bits per channel; B forced 0, alpha forced opaque. */
    kR8G8_unorm(2),

    /** Single 16-bit half-float channel interpreted as alpha. */
    kA16_float(2),

    /** Two-channel RG, 16-bit half-float per channel; B forced 0, alpha forced opaque. */
    kR16G16_float(4),

    /** Single 16-bit channel interpreted as alpha. */
    kA16_unorm(2),

    /** Single 16-bit channel interpreted as red. G/B forced 0, alpha forced opaque. */
    kR16_unorm(2),

    /** Two-channel RG, 16 bits per channel; B forced 0, alpha forced opaque. */
    kR16G16_unorm(4),

    /** RGBA, 16 bits per channel. */
    kR16G16B16A16_unorm(8),

    /** RGBA, 8 bits per channel. RGB encoded with sRGB transfer function. */
    kSRGBA_8888(4),

    /** Single 8-bit channel interpreted as red. G/B forced 0, alpha forced opaque. */
    kR8_unorm(1),
    ;

    /** Mirrors Skia's `SkColorTypeIsAlwaysOpaque`. */
    public fun isAlwaysOpaque(): Boolean = when (this) {
        kRGB_565,
        kRGB_888x,
        kRGB_101010x,
        kBGR_101010x,
        kBGR_101010x_XR,
        kRGB_F16F16F16x,
        kGray_8,
        kR8G8_unorm,
        kR16_unorm,
        kR16G16_unorm,
        kR16G16_float,
        kR8_unorm -> true
        else -> false
    }

    /** True when this colour type can be the default destination for [SkBitmap]-style raster work. */
    public fun isValid(): Boolean = this != kUnknown
}
