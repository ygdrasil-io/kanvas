package org.graphiks.math.color

/**
 * Packed 32-bit ARGB color (`alpha shl 24 | red shl 16 | green shl 8 | blue`).
 *
 * Iso-aligned port of Skia's `SkColor`
 * ([include/core/SkColor.h](https://github.com/google/skia/blob/main/include/core/SkColor.h)).
 * Channels are unpremultiplied. Use [ColorARGBCompanion] for named color constants.
 */
public typealias ColorARGB = Int

/**
 * Packs four byte channels (alpha, red, green, blue) into a [ColorARGB].
 * Channels are masked to 8 bits. Mirrors upstream `SkColorSetARGB`.
 */
public fun colorARGB(a: Int, r: Int, g: Int, b: Int): ColorARGB =
    ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

/**
 * Packs RGB channels with full opacity (alpha = 0xFF). Mirrors `SkColorSetRGB`.
 */
public fun colorRGB(r: Int, g: Int, b: Int): ColorARGB =
    colorARGB(0xFF, r, g, b)

/** Alpha component (0-255). */
public val ColorARGB.alpha: Int get() = (this shr 24) and 0xFF

/** Red component (0-255). */
public val ColorARGB.red: Int get() = (this shr 16) and 0xFF

/** Green component (0-255). */
public val ColorARGB.green: Int get() = (this shr 8) and 0xFF

/** Blue component (0-255). */
public val ColorARGB.blue: Int get() = this and 0xFF

/** Returns a copy of this color with the alpha channel replaced. */
public fun ColorARGB.withAlpha(a: Int): ColorARGB =
    ((a and 0xFF) shl 24) or (this and 0x00FFFFFF.toInt())

/**
 * Pseudo-companion for [ColorARGB], accessed via `ColorARGB.Companion`.
 * Provides named color constants.
 */
public val ColorARGB.Companion: ColorARGBCompanion get() = ColorARGBCompanion

/** Named [ColorARGB] constants. */
public object ColorARGBCompanion {
    /** Fully transparent (0x00000000). */
    public val Transparent: ColorARGB = 0x00000000
    /** Black (0xFF000000). */
    public val Black: ColorARGB = 0xFF000000.toInt()
    /** Dark gray (0xFF444444). */
    public val DarkGray: ColorARGB = 0xFF444444.toInt()
    /** Gray (0xFF888888). */
    public val Gray: ColorARGB = 0xFF888888.toInt()
    /** Light gray (0xFFCCCCCC). */
    public val LightGray: ColorARGB = 0xFFCCCCCC.toInt()
    /** White (0xFFFFFFFF). */
    public val White: ColorARGB = 0xFFFFFFFF.toInt()
    /** Red (0xFFFF0000). */
    public val Red: ColorARGB = 0xFFFF0000.toInt()
    /** Green (0xFF00FF00). */
    public val Green: ColorARGB = 0xFF00FF00.toInt()
    /** Blue (0xFF0000FF). */
    public val Blue: ColorARGB = 0xFF0000FF.toInt()
    /** Yellow (0xFFFFFF00). */
    public val Yellow: ColorARGB = 0xFFFFFF00.toInt()
    /** Cyan (0xFF00FFFF). */
    public val Cyan: ColorARGB = 0xFF00FFFF.toInt()
    /** Magenta (0xFFFF00FF). */
    public val Magenta: ColorARGB = 0xFFFF00FF.toInt()
}

/**
 * Premultiplies the given color channels. Mirrors upstream
 * `SkPreMultiplyARGB`.
 */
public fun premultiplyARGB(a: Int, r: Int, g: Int, b: Int): Int {
    if (a == 0xFF) return colorARGB(a, r, g, b)
    if (a == 0) return 0
    return colorARGB(a, (r * a + 127) / 255, (g * a + 127) / 255, (b * a + 127) / 255)
}

/** Premultiplies [color] and returns the result. */
public fun premultiplyColorARGB(color: ColorARGB): ColorARGB =
    premultiplyARGB(color.alpha, color.red, color.green, color.blue)

/** Returns the premultiplied form of this color. */
public fun ColorARGB.premultiplied(): ColorARGB = premultiplyColorARGB(this)

/**
 * Unpremultiplies [color]. Returns the original if alpha is 0 or 255.
 * Mirrors upstream `SkUnPreMultiply::PMColorToColor`.
 */
public fun unpremultiplyColorARGB(color: ColorARGB): ColorARGB {
    val a = color.alpha
    if (a == 0 || a == 0xFF) return color
    val r = (color.red * 255 + a / 2) / a
    val g = (color.green * 255 + a / 2) / a
    val b = (color.blue * 255 + a / 2) / a
    return colorARGB(a, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
}

/** Returns the unpremultiplied form of this color. */
public fun ColorARGB.unpremultiplied(): ColorARGB = unpremultiplyColorARGB(this)

/**
 * Multiplies an alpha value by [scale] with rounding. Mirrors
 * `SkMultiplyAlpha255` / `SkAlphaMul`.
 */
public fun multiplyAlpha255(a: Int, scale: Int): Int = (a * scale + 127) / 255

/**
 * Multiplies an alpha value by [scale] with rounding for 16-bit alpha.
 * Mirrors `SkAlphaMulQ`.
 */
public fun multiplyAlpha32(a: Int, scale: Int): Int = (a * scale + 0x7FFF) / 0xFFFF

/**
 * Converts HSV (hue, saturation, value) to a [ColorARGB] with full opacity.
 * Hue is [0, 360), saturation and value are [0, 1].
 */
public fun hsvToColor(h: Float, s: Float, v: Float): ColorARGB {
    val sClamped = s.coerceIn(0f, 1f)
    val vClamped = v.coerceIn(0f, 1f)

    if (sClamped <= 0f) {
        val gray = (vClamped * 255f + 0.5f).toInt().coerceIn(0, 255)
        return colorARGB(0xFF, gray, gray, gray)
    }

    val hue = if (h < 0f || h >= 360f) 0f else h / 60f
    val sector = hue.toInt()
    val frac = hue - sector

    val p = vClamped * (1f - sClamped)
    val q = vClamped * (1f - sClamped * frac)
    val t = vClamped * (1f - sClamped * (1f - frac))

    val (r, g, b) = when (sector) {
        0 -> Triple(vClamped, t, p)
        1 -> Triple(q, vClamped, p)
        2 -> Triple(p, vClamped, t)
        3 -> Triple(p, q, vClamped)
        4 -> Triple(t, p, vClamped)
        else -> Triple(vClamped, p, q)
    }
    val rb = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
    val gb = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
    val bb = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
    return colorARGB(0xFF, rb, gb, bb)
}

/**
 * Converts HSV to a [ColorARGB] with the given alpha channel.
 */
public fun hsvToColor(alpha: Int, h: Float, s: Float, v: Float): ColorARGB {
    val sClamped = s.coerceIn(0f, 1f)
    val vClamped = v.coerceIn(0f, 1f)

    if (sClamped <= 0f) {
        val gray = (vClamped * 255f + 0.5f).toInt().coerceIn(0, 255)
        return colorARGB(alpha and 0xFF, gray, gray, gray)
    }

    val hue = if (h < 0f || h >= 360f) 0f else h / 60f
    val sector = hue.toInt()
    val frac = hue - sector

    val p = vClamped * (1f - sClamped)
    val q = vClamped * (1f - sClamped * frac)
    val t = vClamped * (1f - sClamped * (1f - frac))

    val (r, g, b) = when (sector) {
        0 -> Triple(vClamped, t, p)
        1 -> Triple(q, vClamped, p)
        2 -> Triple(p, vClamped, t)
        3 -> Triple(p, q, vClamped)
        4 -> Triple(t, p, vClamped)
        else -> Triple(vClamped, p, q)
    }
    val rb = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
    val gb = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
    val bb = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
    return colorARGB(alpha and 0xFF, rb, gb, bb)
}

/**
 * Converts a [ColorARGB] to HSV. Writes into [hsv]: `hsv[0]` = hue,
 * `hsv[1]` = saturation, `hsv[2]` = value. Mirrors `SkColorToHSV`.
 */
public fun colorToHSV(color: ColorARGB, hsv: FloatArray = FloatArray(3)) {
    require(hsv.size >= 3) { "hsv must have at least 3 elements" }
    val r = color.red.toFloat()
    val g = color.green.toFloat()
    val b = color.blue.toFloat()
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min

    val v = max / 255f
    val s = if (max == 0f) 0f else delta / max

    var h = if (delta == 0f) 0f else when {
        max == r -> (g - b) / delta
        max == g -> 2f + (b - r) / delta
        else -> 4f + (r - g) / delta
    }
    h *= 60f
    if (h < 0f) h += 360f

    hsv[0] = h
    hsv[1] = s
    hsv[2] = v
}

/**
 * Converts a [ColorARGB] to RGB565 with replicated low bits, keeping alpha.
 * Mirrors `SkPMColorToRGB16` / `SkPixel16ToPixel32`.
 */
public fun colorToRGB565(c: ColorARGB): ColorARGB {
    val r = c.red and 0xF8
    val g = c.green and 0xFC
    val b = c.blue and 0xF8
    return colorARGB(0xFF, r or (r ushr 5), g or (g ushr 6), b or (b ushr 5))
}
