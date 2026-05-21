package org.graphiks.math

/**
 * Iso-aligned port of Skia's `SkColor`
 * ([include/core/SkColor.h](https://github.com/google/skia/blob/main/include/core/SkColor.h)).
 *
 * Packed 32-bit ARGB color (alpha << 24 | red << 16 | green << 8 | blue),
 * unpremultiplied. Use [SkPMColor] for the premultiplied variant.
 */

public typealias SkColor = Int

/** 8-bit alpha component, `0` = transparent .. `255` = opaque. */
public typealias SkAlpha = Int

/** Premultiplied ARGB packed color. Identical layout to [SkColor]. */
public typealias SkPMColor = Int

/** Fully transparent alpha value (`0x00`). Mirrors Skia's `SK_AlphaTRANSPARENT`. */
public const val SK_AlphaTRANSPARENT: SkAlpha = 0x00

/** Fully opaque alpha value (`0xFF`). Mirrors Skia's `SK_AlphaOPAQUE`. */
public const val SK_AlphaOPAQUE: SkAlpha = 0xFF

/**
 * Packs four byte channels (alpha, red, green, blue) into an [SkColor] (ARGB8888).
 * Channels are `and`-masked to 8 bits — callers don't need to pre-clamp. Mirrors
 * upstream `SkColorSetARGB` (`include/core/SkColor.h`).
 */
public fun SkColorSetARGB(a: Int, r: Int, g: Int, b: Int): SkColor =
    ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

/** Packs RGB with opaque alpha (`0xFF`). Mirrors Skia's `SkColorSetRGB` macro. */
public fun SkColorSetRGB(r: Int, g: Int, b: Int): SkColor = SkColorSetARGB(0xFF, r, g, b)

/** Returns the alpha byte of [c]. Mirrors Skia's `SkColorGetA` macro. */
public fun SkColorGetA(c: SkColor): Int = (c ushr 24) and 0xFF

/** Returns the red byte of [c]. Mirrors Skia's `SkColorGetR` macro. */
public fun SkColorGetR(c: SkColor): Int = (c ushr 16) and 0xFF

/** Returns the green byte of [c]. Mirrors Skia's `SkColorGetG` macro. */
public fun SkColorGetG(c: SkColor): Int = (c ushr 8) and 0xFF

/** Returns the blue byte of [c]. Mirrors Skia's `SkColorGetB` macro. */
public fun SkColorGetB(c: SkColor): Int = c and 0xFF

/** Replaces only the alpha byte of [c], keeping its RGB components. */
public fun SkColorSetA(c: SkColor, a: Int): SkColor = (c and 0x00FFFFFF) or ((a and 0xFF) shl 24)

public const val SK_ColorTRANSPARENT: SkColor = 0x00000000
public const val SK_ColorBLACK: SkColor = 0xFF000000.toInt()
public const val SK_ColorDKGRAY: SkColor = 0xFF444444.toInt()
public const val SK_ColorGRAY: SkColor = 0xFF888888.toInt()
public const val SK_ColorLTGRAY: SkColor = 0xFFCCCCCC.toInt()
public const val SK_ColorWHITE: SkColor = 0xFFFFFFFF.toInt()
public const val SK_ColorRED: SkColor = 0xFFFF0000.toInt()
public const val SK_ColorGREEN: SkColor = 0xFF00FF00.toInt()
public const val SK_ColorBLUE: SkColor = 0xFF0000FF.toInt()
public const val SK_ColorYELLOW: SkColor = 0xFFFFFF00.toInt()
public const val SK_ColorCYAN: SkColor = 0xFF00FFFF.toInt()
public const val SK_ColorMAGENTA: SkColor = 0xFFFF00FF.toInt()

/**
 * Quantize an opaque ARGB color to RGB565 precision (5-6-5 bits) and return
 * it as 0xFFRRGGBB. Mirrors Skia's `ToolUtils::color_to_565`.
 */
public fun colorToRGB565(c: SkColor): SkColor {
    val r = SkColorGetR(c) and 0xF8
    val g = SkColorGetG(c) and 0xFC
    val b = SkColorGetB(c) and 0xF8
    return SkColorSetARGB(0xFF, r or (r ushr 5), g or (g ushr 6), b or (b ushr 5))
}

// ─── HSV conversions ─────────────────────────────────────────────────────

/**
 * Convert RGB byte values to HSV. Mirrors Skia's [`SkRGBToHSV`](https://github.com/google/skia/blob/main/src/core/SkColor.cpp).
 *
 * - `hsv[0]` = hue in `[0, 360)`
 * - `hsv[1]` = saturation in `[0, 1]`
 * - `hsv[2]` = value in `[0, 1]`
 */
public fun SkRGBToHSV(red: Int, green: Int, blue: Int, hsv: FloatArray) {
    require(hsv.size >= 3) { "hsv must have at least 3 elements" }
    val r = red and 0xFF
    val g = green and 0xFF
    val b = blue and 0xFF
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val v = max / 255f
    val s = if (max == 0) 0f else delta.toFloat() / max.toFloat()

    var h = if (delta == 0) 0f else when (max) {
        r -> (g - b).toFloat() / delta.toFloat()
        g -> 2f + (b - r).toFloat() / delta.toFloat()
        else -> 4f + (r - g).toFloat() / delta.toFloat()
    }
    h *= 60f
    if (h < 0f) h += 360f

    hsv[0] = h
    hsv[1] = s
    hsv[2] = v
}

public fun SkColorToHSV(color: SkColor, hsv: FloatArray) {
    SkRGBToHSV(SkColorGetR(color), SkColorGetG(color), SkColorGetB(color), hsv)
}

/**
 * Convert HSV components to an opaque ARGB color. Out-of-range values are
 * pinned (`hue` mod 360, `s` and `v` clamped to `[0, 1]`).
 */
public fun SkHSVToColor(alpha: Int, hsv: FloatArray): SkColor {
    require(hsv.size >= 3) { "hsv must have at least 3 elements" }
    val s = hsv[1].coerceIn(0f, 1f)
    val v = hsv[2].coerceIn(0f, 1f)

    if (s <= 0f) {
        val gray = (v * 255f + 0.5f).toInt().coerceIn(0, 255)
        return SkColorSetARGB(alpha and 0xFF, gray, gray, gray)
    }

    var h = hsv[0] % 360f
    if (h < 0f) h += 360f
    h /= 60f
    val sector = h.toInt()
    val frac = h - sector

    val p = v * (1f - s)
    val q = v * (1f - s * frac)
    val t = v * (1f - s * (1f - frac))

    val (r, g, bl) = when (sector) {
        0 -> Triple(v, t, p)
        1 -> Triple(q, v, p)
        2 -> Triple(p, v, t)
        3 -> Triple(p, q, v)
        4 -> Triple(t, p, v)
        else -> Triple(v, p, q)
    }
    val rb = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
    val gb = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
    val bb = (bl * 255f + 0.5f).toInt().coerceIn(0, 255)
    return SkColorSetARGB(alpha and 0xFF, rb, gb, bb)
}

public fun SkHSVToColor(hsv: FloatArray): SkColor = SkHSVToColor(0xFF, hsv)

// ─── Premultiplication ───────────────────────────────────────────────────

/**
 * Premultiply RGB by alpha, returning an [SkPMColor]. Mirrors Skia's
 * [`SkPreMultiplyARGB`](https://github.com/google/skia/blob/main/src/core/SkColor.cpp).
 *
 * Note: Skia's `SkPMColor` byte order is platform-dependent
 * (`kBGRA_8888_SkColorType` on most), but the Kotlin port keeps the same
 * ARGB layout as [SkColor] for portability — callers using PMColor
 * shouldn't depend on byte order beyond `SkColorGet*` accessors.
 */
public fun SkPreMultiplyARGB(a: Int, r: Int, g: Int, b: Int): SkPMColor {
    val ab = a and 0xFF
    return SkColorSetARGB(
        ab,
        ((r and 0xFF) * ab + 127) / 255,
        ((g and 0xFF) * ab + 127) / 255,
        ((b and 0xFF) * ab + 127) / 255,
    )
}

public fun SkPreMultiplyColor(c: SkColor): SkPMColor =
    SkPreMultiplyARGB(SkColorGetA(c), SkColorGetR(c), SkColorGetG(c), SkColorGetB(c))

// ─── Channel enums ───────────────────────────────────────────────────────

public enum class SkColorChannel { kR, kG, kB, kA }

/** Bit flags for color-channel masks. Matches Skia's `SkColorChannelFlag`. */
public object SkColorChannelFlag {
    public const val kRed_SkColorChannelFlag: Int = 1 shl 0
    public const val kGreen_SkColorChannelFlag: Int = 1 shl 1
    public const val kBlue_SkColorChannelFlag: Int = 1 shl 2
    public const val kAlpha_SkColorChannelFlag: Int = 1 shl 3
    public const val kGray_SkColorChannelFlag: Int = 0x10
    public const val kGrayAlpha_SkColorChannelFlags: Int =
        kGray_SkColorChannelFlag or kAlpha_SkColorChannelFlag
    public const val kRG_SkColorChannelFlags: Int = kRed_SkColorChannelFlag or kGreen_SkColorChannelFlag
    public const val kRGB_SkColorChannelFlags: Int = kRG_SkColorChannelFlags or kBlue_SkColorChannelFlag
    public const val kRGBA_SkColorChannelFlags: Int = kRGB_SkColorChannelFlags or kAlpha_SkColorChannelFlag
}
