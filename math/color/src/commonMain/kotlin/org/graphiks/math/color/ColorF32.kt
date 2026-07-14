package org.graphiks.math.color

import org.graphiks.math.scalar.clamp

@ConsistentCopyVisibility
public data class ColorF32 internal constructor(
    public var red: Float,
    public var green: Float,
    public var blue: Float,
    public var alpha: Float = 1f
) {
    public val isOpaque: Boolean get() = alpha >= 1f

    public fun vec(): FloatArray = floatArrayOf(red, green, blue, alpha)

    public fun array(): FloatArray = vec()

    public operator fun get(index: Int): Float = when (index) {
        0 -> red; 1 -> green; 2 -> blue; 3 -> alpha
        else -> throw IndexOutOfBoundsException("ColorF32 index $index outside [0, 3]")
    }

    public fun toColorARGB(): ColorARGB {
        val a = (clamp(alpha, 0f, 1f) * 255f + 0.5f).toInt()
        val r = (clamp(red, 0f, 1f) * 255f + 0.5f).toInt()
        val g = (clamp(green, 0f, 1f) * 255f + 0.5f).toInt()
        val b = (clamp(blue, 0f, 1f) * 255f + 0.5f).toInt()
        return colorARGB(a, r, g, b)
    }

    public fun premultiplied(): ColorF32 = ColorF32(red * alpha, green * alpha, blue * alpha, alpha)

    public fun unpremultiplied(): ColorF32 {
        val a = alpha
        if (a == 0f) return ColorF32(0f, 0f, 0f, 0f)
        if (a == 1f) return this
        val invA = 1f / a
        return ColorF32(red * invA, green * invA, blue * invA, a)
    }

    public fun clampToFit(): ColorF32 = ColorF32(
        clamp(red, 0f, 1f), clamp(green, 0f, 1f), clamp(blue, 0f, 1f), clamp(alpha, 0f, 1f)
    )

    public fun makeOpaque(): ColorF32 = ColorF32(red, green, blue, 1f)

    public fun pinAlpha(): ColorF32 = ColorF32(red, green, blue, alpha.coerceIn(0f, 1f))

    public fun withAlpha(a: Float): ColorF32 = ColorF32(red, green, blue, a)

    public fun withAlphaByte(a: Int): ColorF32 = ColorF32(red, green, blue, (a and 0xFF) / 255f)

    public operator fun times(s: Float): ColorF32 = ColorF32(red * s, green * s, blue * s, alpha * s)
    public operator fun times(c: ColorF32): ColorF32 = ColorF32(red * c.red, green * c.green, blue * c.blue, alpha * c.alpha)
    public operator fun plus(c: ColorF32): ColorF32 = ColorF32(red + c.red, green + c.green, blue + c.blue, alpha + c.alpha)

    public fun toBytes_RGBA(): Int =
        (channelToByte(alpha) shl 24) or
            (channelToByte(blue) shl 16) or
            (channelToByte(green) shl 8) or
            channelToByte(red)

    public companion object {
        public val Transparent: ColorF32 = ColorF32(0f, 0f, 0f, 0f)
        public val Black: ColorF32 = ColorF32(0f, 0f, 0f)
        public val White: ColorF32 = ColorF32(1f, 1f, 1f)
        public val Red: ColorF32 = ColorF32(1f, 0f, 0f)
        public val Green: ColorF32 = ColorF32(0f, 1f, 0f)
        public val Blue: ColorF32 = ColorF32(0f, 0f, 1f)

        public fun of(red: Float, green: Float, blue: Float, alpha: Float = 1f): ColorF32 =
            ColorF32(red, green, blue, alpha)

        public fun fromBytesRGBA(r: Byte, g: Byte, b: Byte, a: Byte = (-1).toByte()): ColorF32 =
            ColorF32(
                (r.toInt() and 0xFF) / 255f,
                (g.toInt() and 0xFF) / 255f,
                (b.toInt() and 0xFF) / 255f,
                (a.toInt() and 0xFF) / 255f
            )

        public fun fromColorARGB(color: ColorARGB): ColorF32 = ColorF32(
            color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f
        )

        public fun fromBytes_RGBA(rgba: Int): ColorF32 = ColorF32(
            red = (rgba and 0xFF) / 255f,
            green = ((rgba ushr 8) and 0xFF) / 255f,
            blue = ((rgba ushr 16) and 0xFF) / 255f,
            alpha = ((rgba ushr 24) and 0xFF) / 255f,
        )

        private fun channelToByte(v: Float): Int {
            val clamped = when {
                v <= 0f -> 0f
                v >= 1f -> 1f
                else -> v
            }
            val rounded = (clamped * 255f + 0.5f).toInt()
            return if (rounded < 0) 0 else if (rounded > 255) 255 else rounded
        }
    }
}

public operator fun Float.times(c: ColorF32): ColorF32 = c * this
