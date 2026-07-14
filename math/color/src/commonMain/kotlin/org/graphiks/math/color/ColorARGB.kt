package org.graphiks.math.color

@JvmInline
public value class ColorARGB internal constructor(public val value: Int) {

    public val alpha: Int get() = (value shr 24) and 0xFF
    public val red: Int get() = (value shr 16) and 0xFF
    public val green: Int get() = (value shr 8) and 0xFF
    public val blue: Int get() = value and 0xFF

    public fun withAlpha(a: Int): ColorARGB =
        ColorARGB(((a and 0xFF) shl 24) or (value and 0x00FFFFFF.toInt()))

    public fun premultiplied(): ColorARGB {
        val a = alpha
        if (a == 0xFF) return this
        if (a == 0) return ColorARGB.Transparent
        return of(a, (red * a + 127) / 255, (green * a + 127) / 255, (blue * a + 127) / 255)
    }

    public fun unpremultiplied(): ColorARGB {
        val a = alpha
        if (a == 0 || a == 0xFF) return this
        val r = (red * 255 + a / 2) / a
        val g = (green * 255 + a / 2) / a
        val b = (blue * 255 + a / 2) / a
        return of(a, r.coerceIn(0, 255), g.coerceIn(0, 255), b.coerceIn(0, 255))
    }

    public companion object {
        public val Transparent: ColorARGB = ColorARGB(0x00000000)
        public val Black: ColorARGB = ColorARGB(0xFF000000.toInt())
        public val DarkGray: ColorARGB = ColorARGB(0xFF444444.toInt())
        public val Gray: ColorARGB = ColorARGB(0xFF888888.toInt())
        public val LightGray: ColorARGB = ColorARGB(0xFFCCCCCC.toInt())
        public val White: ColorARGB = ColorARGB(0xFFFFFFFF.toInt())
        public val Red: ColorARGB = ColorARGB(0xFFFF0000.toInt())
        public val Green: ColorARGB = ColorARGB(0xFF00FF00.toInt())
        public val Blue: ColorARGB = ColorARGB(0xFF0000FF.toInt())
        public val Yellow: ColorARGB = ColorARGB(0xFFFFFF00.toInt())
        public val Cyan: ColorARGB = ColorARGB(0xFF00FFFF.toInt())
        public val Magenta: ColorARGB = ColorARGB(0xFFFF00FF.toInt())

        public fun of(alpha: Int, red: Int, green: Int, blue: Int): ColorARGB =
            ColorARGB(((alpha and 0xFF) shl 24) or ((red and 0xFF) shl 16) or ((green and 0xFF) shl 8) or (blue and 0xFF))

        public fun of(red: Int, green: Int, blue: Int): ColorARGB =
            of(0xFF, red, green, blue)
    }
}

public fun premultiplyARGB(a: Int, r: Int, g: Int, b: Int): ColorARGB {
    if (a == 0xFF) return ColorARGB.of(a, r, g, b)
    if (a == 0) return ColorARGB.Transparent
    return ColorARGB.of(a, (r * a + 127) / 255, (g * a + 127) / 255, (b * a + 127) / 255)
}

public fun premultiplyColorARGB(color: ColorARGB): ColorARGB = color.premultiplied()

public fun unpremultiplyColorARGB(color: ColorARGB): ColorARGB = color.unpremultiplied()

public fun multiplyAlpha255(a: Int, scale: Int): Int = (a * scale + 127) / 255

public fun multiplyAlpha32(a: Int, scale: Int): Int = (a * scale + 0x7FFF) / 0xFFFF

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

public fun colorToRGB565(c: ColorARGB): ColorARGB {
    val r = c.red and 0xF8
    val g = c.green and 0xFC
    val b = c.blue and 0xF8
    return ColorARGB.of(0xFF, r or (r ushr 5), g or (g ushr 6), b or (b ushr 5))
}
