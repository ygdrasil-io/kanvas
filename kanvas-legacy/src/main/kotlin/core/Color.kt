package com.kanvas.core

/**
 * Represents a color in RGBA format, aligned with Skia's color concepts
 * @property red Red component (0-255)
 * @property green Green component (0-255)
 * @property blue Blue component (0-255)
 * @property alpha Alpha component (0-255, 0=transparent, 255=opaque)
 */
data class Color(val red: Int, val green: Int, val blue: Int, val alpha: Int = 255) {
    init {
        require(red in 0..255 && green in 0..255 && blue in 0..255 && alpha in 0..255) {
            "Color components must be in range 0-255"
        }
    }

    companion object {
        val TRANSPARENT = Color(0, 0, 0, 0)
        val BLACK = Color(0, 0, 0)
        val WHITE = Color(255, 255, 255)
        val RED = Color(255, 0, 0)
        val GREEN = Color(0, 255, 0)
        val BLUE = Color(0, 0, 255)
        
        // Additional Skia-aligned colors
        val YELLOW = Color(255, 255, 0)
        val CYAN = Color(0, 255, 255)
        val MAGENTA = Color(255, 0, 255)
        val GRAY = Color(128, 128, 128)
        val LIGHT_GRAY = Color(192, 192, 192)
        val DARK_GRAY = Color(64, 64, 64)
        
        /**
         * Convert from ARGB integer format
         */
        fun fromArgb(argb: Int): Color {
            return Color(
                (argb shr 16) and 0xFF,
                (argb shr 8) and 0xFF,
                argb and 0xFF,
                (argb shr 24) and 0xFF
            )
        }
    }

    /**
     * Convert to ARGB integer format (compatible with Android/Skia)
     */
    fun toArgb(): Int {
        return (alpha shl 24) or (red shl 16) or (green shl 8) or blue
    }



    /**
     * Apply a color filter to this color
     */
    fun withFilter(filter: ColorFilter): Color {
        return filter.apply(this)
    }

    /**
     * Create a ColorInfo object for this color
     */
    fun toColorInfo(colorSpace: com.kanvas.core.ColorSpace = com.kanvas.core.ColorSpace.SRGB): com.kanvas.core.ColorInfo {
        return com.kanvas.core.ColorInfo(
            colorType = com.kanvas.core.ColorType.RGBA_8888,
            alphaType = if (alpha == 255) com.kanvas.core.AlphaType.OPAQUE else com.kanvas.core.AlphaType.UNPREMUL,
            colorSpace = colorSpace
        )
    }

    /**
     * Premultiply alpha channel
     */
    fun premultiply(): Color {
        if (alpha == 0 || alpha == 255) return this
        
        val alphaRatio = alpha / 255f
        return Color(
            (red * alphaRatio).toInt(),
            (green * alphaRatio).toInt(),
            (blue * alphaRatio).toInt(),
            alpha
        )
    }

    /**
     * Unpremultiply alpha channel
     */
    fun unpremultiply(): Color {
        if (alpha == 0 || alpha == 255) return this
        
        val alphaRatio = 255f / alpha
        return Color(
            (red * alphaRatio).toInt().coerceAtMost(255),
            (green * alphaRatio).toInt().coerceAtMost(255),
            (blue * alphaRatio).toInt().coerceAtMost(255),
            alpha
        )
    }

    override fun toString(): String {
        return "Color(r=$red, g=$green, b=$blue, a=$alpha)"
    }
    
    /**
     * Linear interpolation between two colors
     * t = 0 returns this color, t = 1 returns other color
     */
    fun lerp(other: Color, t: Float): Color {
        require(t in 0f..1f) { "Interpolation factor must be between 0 and 1" }
        return Color(
            (red + (other.red - red) * t + 0.5f).toInt(),
            (green + (other.green - green) * t + 0.5f).toInt(),
            (blue + (other.blue - blue) * t + 0.5f).toInt(),
            (alpha + (other.alpha - alpha) * t + 0.5f).toInt()
        )
    }
}