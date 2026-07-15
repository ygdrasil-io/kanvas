package org.graphiks.math.color

import kotlin.jvm.JvmInline

/**
 * Packed 32-bit ARGB color.
 *
 * Bits are laid out as `AARRGGBB` — alpha in the most significant byte,
 * followed by red, green, blue.
 *
 * Use [ColorARGB.of] to construct from components, or refer to the
 * [companion][ColorARGB.Companion] constants for named colors.
 *
 * Colors are stored in non-premultiplied form. Use [premultiplied] and
 * [unpremultiplied] to convert.
 */
@JvmInline
public value class ColorARGB internal constructor(public val value: UInt) {

    /** Alpha component in `[0, 255]`. */
    public val alpha: Int get() = (value shr 24).toInt()
    /** Red component in `[0, 255]`. */
    public val red: Int get() = (value shr 16 and 0xFFu).toInt()
    /** Green component in `[0, 255]`. */
    public val green: Int get() = (value shr 8 and 0xFFu).toInt()
    /** Blue component in `[0, 255]`. */
    public val blue: Int get() = (value and 0xFFu).toInt()

    /** Returns a new color with the given alpha, preserving RGB. */
    public fun withAlpha(a: Int): ColorARGB =
        ColorARGB(((a and 0xFF) shl 24).toUInt() or (value and 0x00FFFFFFu))

    /** Returns this color with RGB premultiplied by alpha. */
    public fun premultiplied(): ColorARGB {
        val a = alpha
        if (a == 0xFF) return this
        if (a == 0) return ColorARGB.Transparent
        return of(a, (red * a + 127) / 255, (green * a + 127) / 255, (blue * a + 127) / 255)
    }

    /** Returns this color with RGB divided by alpha (unpremultiplication). */
    public fun unpremultiplied(): ColorARGB {
        val a = alpha
        if (a == 0 || a == 0xFF) return this
        val r = (red * 255 + a / 2) / a
        val g = (green * 255 + a / 2) / a
        val b = (blue * 255 + a / 2) / a
        return of(a, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    public companion object {
        /** Fully transparent black. */
        public val Transparent: ColorARGB = ColorARGB(0x00000000u)
        /** Opaque black. */
        public val Black: ColorARGB = ColorARGB(0xFF000000u)
        /** Dark gray. */
        public val DarkGray: ColorARGB = ColorARGB(0xFF444444u)
        /** Medium gray. */
        public val Gray: ColorARGB = ColorARGB(0xFF888888u)
        /** Light gray. */
        public val LightGray: ColorARGB = ColorARGB(0xFFCCCCCCu)
        /** Opaque white. */
        public val White: ColorARGB = ColorARGB(0xFFFFFFFFu)
        /** Opaque red. */
        public val Red: ColorARGB = ColorARGB(0xFFFF0000u)
        /** Opaque green. */
        public val Green: ColorARGB = ColorARGB(0xFF00FF00u)
        /** Opaque blue. */
        public val Blue: ColorARGB = ColorARGB(0xFF0000FFu)
        /** Opaque yellow. */
        public val Yellow: ColorARGB = ColorARGB(0xFFFFFF00u)
        /** Opaque cyan. */
        public val Cyan: ColorARGB = ColorARGB(0xFF00FFFFu)
        /** Opaque magenta. */
        public val Magenta: ColorARGB = ColorARGB(0xFFFF00FFu)

        /** Creates a color from ARGB components (each in `[0, 255]`). */
        public fun of(alpha: Int, red: Int, green: Int, blue: Int): ColorARGB =
            ColorARGB(
                (((alpha and 0xFF) shl 24) or ((red and 0xFF) shl 16) or ((green and 0xFF) shl 8) or (blue and 0xFF)).toUInt()
            )

        /** Creates an opaque color from RGB components (each in `[0, 255]`). */
        public fun of(red: Int, green: Int, blue: Int): ColorARGB =
            of(0xFF, red, green, blue)
    }
}

/**
 * Returns a premultiplied [ColorARGB] from individual ARGB components.
 *
 * Each component should be in `[0, 255]`. Alpha `0xFF` returns the identity;
 * alpha `0` returns [ColorARGB.Transparent].
 */
public fun premultiplyARGB(a: Int, r: Int, g: Int, b: Int): ColorARGB {
    if (a == 0xFF) return ColorARGB.of(a, r, g, b)
    if (a == 0) return ColorARGB.Transparent
    return ColorARGB.of(a, (r * a + 127) / 255, (g * a + 127) / 255, (b * a + 127) / 255)
}

/** Premultiplies [color]. Convenience overload of [premultiplyARGB]. */
public fun premultiplyColorARGB(color: ColorARGB): ColorARGB = color.premultiplied()

/** Unpremultiplies [color]. */
public fun unpremultiplyColorARGB(color: ColorARGB): ColorARGB = color.unpremultiplied()

/**
 * Multiplies alpha value `a` by `scale`, dividing by 255.
 *
 * Both arguments and the result are in `[0, 255]`.
 */
public fun multiplyAlpha255(a: Int, scale: Int): Int = (a * scale + 127) / 255

/**
 * Multiplies alpha value `a` by `scale`, dividing by 65535.
 *
 * Both arguments and the result are in `[0, 65535]` (16-bit range).
 */
public fun multiplyAlpha32(a: Int, scale: Int): Int =
    ((a.toLong() * scale.toLong() + 0x7FFF) / 0xFFFF).toInt()

/**
 * Converts HSV to an opaque [ColorARGB].
 *
 * @param h hue in degrees `[0, 360)`.
 * @param s saturation `[0, 1]`.
 * @param v value `[0, 1]`.
 */
public fun hsvToColor(h: Float, s: Float, v: Float): ColorARGB {
    val sClamped = s.coerceIn(0f, 1f)
    val vClamped = v.coerceIn(0f, 1f)

    if (sClamped <= 0f) {
        val gray = (vClamped * 255f + 0.5f).toInt().coerceIn(0, 255)
        return ColorARGB.of(0xFF, gray, gray, gray)
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
    return ColorARGB.of(0xFF, rb, gb, bb)
}

/**
 * Converts HSV to a [ColorARGB] with explicit alpha.
 *
 * @param alpha alpha in `[0, 255]`.
 * @param h hue in degrees `[0, 360)`.
 * @param s saturation `[0, 1]`.
 * @param v value `[0, 1]`.
 */
public fun hsvToColor(alpha: Int, h: Float, s: Float, v: Float): ColorARGB {
    val sClamped = s.coerceIn(0f, 1f)
    val vClamped = v.coerceIn(0f, 1f)

    if (sClamped <= 0f) {
        val gray = (vClamped * 255f + 0.5f).toInt().coerceIn(0, 255)
        return ColorARGB.of(alpha and 0xFF, gray, gray, gray)
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
    return ColorARGB.of(alpha and 0xFF, rb, gb, bb)
}

/**
 * Converts [color] to HSV, writing the result into [hsv].
 *
 * @param hsv output array of at least 3 elements — `[hue, saturation, value]`.
 *            Hue is in degrees `[0, 360)`, saturation and value in `[0, 1]`.
 * @return the [hsv] array for call chaining.
 */
public fun colorToHSV(color: ColorARGB, hsv: FloatArray): FloatArray {
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
    return hsv
}

/**
 * Converts a non-premultiplied canvas color to its premultiplied RGB565
 * form, preserving alpha at `0xFF`.
 */
public fun colorToRGB565(c: ColorARGB): ColorARGB {
    val r = c.red and 0xF8
    val g = c.green and 0xFC
    val b = c.blue and 0xF8
    return ColorARGB.of(0xFF, r or (r ushr 5), g or (g ushr 6), b or (b ushr 5))
}
