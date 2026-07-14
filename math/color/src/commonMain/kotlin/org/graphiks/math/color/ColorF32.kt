package org.graphiks.math.color

import org.graphiks.math.scalar.clamp

/**
 * Mutable RGBA color with float components in [0, 1].
 *
 */
@ConsistentCopyVisibility
public data class ColorF32 internal constructor(
    public var red: Float,
    public var green: Float,
    public var blue: Float,
    public var alpha: Float = 1f
) {
    /** `true` if alpha >= 1. */
    public val isOpaque: Boolean get() = alpha >= 1f

    /** Returns components as `[red, green, blue, alpha]`. */
    public fun vec(): FloatArray = floatArrayOf(red, green, blue, alpha)

    /** Alias for [vec]. */
    public fun array(): FloatArray = vec()

    /** Indexed access: 0 → red, 1 → green, 2 → blue, 3 → alpha. */
    public operator fun get(index: Int): Float = when (index) {
        0 -> red; 1 -> green; 2 -> blue; 3 -> alpha
        else -> throw IndexOutOfBoundsException("ColorF32 index $index outside [0, 3]")
    }

    /** Converts to a packed [ColorARGB]. Clamps each channel to [0, 1] first. */
    public fun toColorARGB(): ColorARGB {
        val a = (clamp(alpha, 0f, 1f) * 255f + 0.5f).toInt()
        val r = (clamp(red, 0f, 1f) * 255f + 0.5f).toInt()
        val g = (clamp(green, 0f, 1f) * 255f + 0.5f).toInt()
        val b = (clamp(blue, 0f, 1f) * 255f + 0.5f).toInt()
        return ColorARGB.of(a, r, g, b)
    }

    /** Returns the premultiplied form of this color. */
    public fun premultiplied(): ColorF32 = ColorF32(red * alpha, green * alpha, blue * alpha, alpha)

    /** Returns the unpremultiplied form of this color. */
    public fun unpremultiplied(): ColorF32 {
        val a = alpha
        if (a == 0f) return ColorF32(0f, 0f, 0f, 0f)
        if (a == 1f) return this
        val invA = 1f / a
        return ColorF32(red * invA, green * invA, blue * invA, a)
    }

    /** Clamps all channels to [0, 1]. */
    public fun clampToFit(): ColorF32 = ColorF32(
        clamp(red, 0f, 1f), clamp(green, 0f, 1f), clamp(blue, 0f, 1f), clamp(alpha, 0f, 1f)
    )

    /** Returns a copy with alpha = 1. */
    public fun makeOpaque(): ColorF32 = ColorF32(red, green, blue, 1f)

    /** Returns a copy with alpha clamped to [0, 1]. */
    public fun pinAlpha(): ColorF32 = ColorF32(red, green, blue, alpha.coerceIn(0f, 1f))

    /** Returns a copy with the given alpha. */
    public fun withAlpha(a: Float): ColorF32 = ColorF32(red, green, blue, a)

    /** Returns a copy with alpha from a byte (0-255). */
    public fun withAlphaByte(a: Int): ColorF32 = ColorF32(red, green, blue, (a and 0xFF) / 255f)

    /** Uniform scaling of all channels. */
    public operator fun times(s: Float): ColorF32 = ColorF32(red * s, green * s, blue * s, alpha * s)

    /** Component-wise multiplication. */
    public operator fun times(c: ColorF32): ColorF32 = ColorF32(red * c.red, green * c.green, blue * c.blue, alpha * c.alpha)

    /** Component-wise addition. */
    public operator fun plus(c: ColorF32): ColorF32 = ColorF32(red + c.red, green + c.green, blue + c.blue, alpha + c.alpha)

    /** Packs channels as RGBA bytes (alpha in the most-significant byte). */
    public fun toPackedRGBA(): Int =
        (channelToByte(alpha) shl 24) or
            (channelToByte(blue) shl 16) or
            (channelToByte(green) shl 8) or
            channelToByte(red)

    public companion object {
        /** Fully transparent black. */
        public val Transparent: ColorF32 get() = ColorF32(0f, 0f, 0f, 0f)
        /** Opaque black. */
        public val Black: ColorF32 get() = ColorF32(0f, 0f, 0f)
        /** Opaque white. */
        public val White: ColorF32 get() = ColorF32(1f, 1f, 1f)
        /** Opaque red. */
        public val Red: ColorF32 get() = ColorF32(1f, 0f, 0f)
        /** Opaque green. */
        public val Green: ColorF32 get() = ColorF32(0f, 1f, 0f)
        /** Opaque blue. */
        public val Blue: ColorF32 get() = ColorF32(0f, 0f, 1f)

        /** Creates a [ColorF32] from float components. */
        public fun of(red: Float, green: Float, blue: Float, alpha: Float = 1f): ColorF32 =
            ColorF32(red, green, blue, alpha)

        /** Creates a [ColorF32] from RGBA byte values. */
        public fun fromBytesRGBA(r: Byte, g: Byte, b: Byte, a: Byte = (-1).toByte()): ColorF32 =
            ColorF32(
                (r.toInt() and 0xFF) / 255f,
                (g.toInt() and 0xFF) / 255f,
                (b.toInt() and 0xFF) / 255f,
                (a.toInt() and 0xFF) / 255f
            )

        /** Creates a [ColorF32] from a [ColorARGB]. */
        public fun fromColorARGB(color: ColorARGB): ColorF32 = ColorF32(
            color.red / 255f, color.green / 255f, color.blue / 255f, color.alpha / 255f
        )

        /** Creates a [ColorF32] from a packed RGBA integer. */
        public fun fromPackedRGBA(rgba: Int): ColorF32 = ColorF32(
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

/** Scalar-times-color: `s * c`. */
public operator fun Float.times(c: ColorF32): ColorF32 = c * this
