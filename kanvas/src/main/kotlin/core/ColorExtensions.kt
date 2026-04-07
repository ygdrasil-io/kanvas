package com.kanvas.core

/**
 * Extension functions for Color class to align with Skia functionality
 */

/**
 * Blend two colors using different blend modes (simplified versions)
 */
fun Color.blendWith(other: Color, mode: com.kanvas.core.BlendMode = com.kanvas.core.BlendMode.SRC_OVER): Color {
    return when (mode) {
        com.kanvas.core.BlendMode.SRC_OVER -> blendSrcOver(other)
        com.kanvas.core.BlendMode.MULTIPLY -> blendMultiply(other)
        com.kanvas.core.BlendMode.SCREEN -> blendScreen(other)
        com.kanvas.core.BlendMode.PLUS -> blendAdd(other)
        else -> blendSrcOver(other) // Default to SRC_OVER for unsupported modes
    }
}

private fun Color.blendSrcOver(other: Color): Color {
    // SRC_OVER formula: result = src + dst * (1 - src_alpha)
    // This is the standard "source over destination" blending
    val srcAlphaRatio = this.alpha / 255f
    val invSrcAlphaRatio = 1 - srcAlphaRatio
    
    return Color(
        (this.red + other.red * invSrcAlphaRatio).toInt(),
        (this.green + other.green * invSrcAlphaRatio).toInt(),
        (this.blue + other.blue * invSrcAlphaRatio).toInt(),
        (this.alpha + other.alpha * invSrcAlphaRatio * 255).toInt().coerceAtMost(255)
    )
}

private fun Color.blendMultiply(other: Color): Color {
    return Color(
        (this.red * other.red / 255),
        (this.green * other.green / 255),
        (this.blue * other.blue / 255),
        (this.alpha * other.alpha / 255)
    )
}

private fun Color.blendScreen(other: Color): Color {
    return Color(
        255 - (255 - this.red) * (255 - other.red) / 255,
        255 - (255 - this.green) * (255 - other.green) / 255,
        255 - (255 - this.blue) * (255 - other.blue) / 255,
        (this.alpha + other.alpha).coerceAtMost(255)
    )
}

private fun Color.blendAdd(other: Color): Color {
    return Color(
        (this.red + other.red).coerceAtMost(255),
        (this.green + other.green).coerceAtMost(255),
        (this.blue + other.blue).coerceAtMost(255),
        (this.alpha + other.alpha).coerceAtMost(255)
    )
}

/**
 * Convert color to grayscale
 */
fun Color.toGrayscale(): Color {
    val luminance = (0.299f * red + 0.587f * green + 0.114f * blue).toInt()
    return Color(luminance, luminance, luminance, alpha)
}

/**
 * Invert color
 */
fun Color.invert(): Color {
    return Color(255 - red, 255 - green, 255 - blue, alpha)
}

/**
 * Adjust color brightness
 * @param factor Brightness factor (0.0 = black, 1.0 = original, >1.0 = brighter)
 */
fun Color.adjustBrightness(factor: Float): Color {
    require(factor >= 0) { "Brightness factor must be >= 0" }
    
    return Color(
        (red * factor).toInt().coerceIn(0, 255),
        (green * factor).toInt().coerceIn(0, 255),
        (blue * factor).toInt().coerceIn(0, 255),
        alpha
    )
}

/**
 * Adjust color saturation
 * @param factor Saturation factor (0.0 = grayscale, 1.0 = original, >1.0 = more saturated)
 */
fun Color.adjustSaturation(factor: Float): Color {
    val gray = toGrayscale()
    val invFactor = 1.0f - factor
    
    return Color(
        (gray.red * invFactor + red * factor).toInt(),
        (gray.green * invFactor + green * factor).toInt(),
        (gray.blue * invFactor + blue * factor).toInt(),
        alpha
    )
}

/**
 * Create a color from HSL values (Hue, Saturation, Lightness)
 */
fun Color.Companion.fromHSL(h: Float, s: Float, l: Float, alpha: Int = 255): Color {
    require(h in 0f..360f && s in 0f..1f && l in 0f..1f) { "HSL values out of range" }
    
    val c = (1 - Math.abs(2 * l - 1)) * s
    val x = c * (1 - Math.abs((h / 60) % 2 - 1))
    val m = l - c / 2
    
    val (r, g, b) = when (h) {
        in 0f..60f -> Triple(c, x, 0f)
        in 60f..120f -> Triple(x, c, 0f)
        in 120f..180f -> Triple(0f, c, x)
        in 180f..240f -> Triple(0f, x, c)
        in 240f..300f -> Triple(x, 0f, c)
        else -> Triple(c, 0f, x)
    }
    
    return Color(
        ((r + m) * 255).toInt(),
        ((g + m) * 255).toInt(),
        ((b + m) * 255).toInt(),
        alpha
    )
}



/**
 * Convert color to HSL values
 */
fun Color.toHSL(): Triple<Float, Float, Float> {
    val r = red / 255f
    val g = green / 255f
    val b = blue / 255f
    
    val max = maxOf(r, g, b)
    val min = minOf(r, g, b)
    val delta = max - min
    
    val l = (max + min) / 2
    
    val s = if (delta == 0f) 0f else delta / (1 - Math.abs(2 * l - 1))
    
    val h = when {
        delta == 0f -> 0f
        max == r -> 60 * (((g - b) / delta) % 6)
        max == g -> 60 * ((b - r) / delta + 2)
        else -> 60 * ((r - g) / delta + 4)
    }
    
    return Triple(h, s, l)
}