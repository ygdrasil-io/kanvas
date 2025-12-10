package com.kanvas.core

/**
 * Represents a color space, aligned with Skia's SkColorSpace concept
 */
class ColorSpace {
    companion object {
        val SRGB = ColorSpace()
        val DISPLAY_P3 = ColorSpace()
        val ADOBE_RGB = ColorSpace()
    }
}

/**
 * Color information, aligned with Skia's SkColorInfo
 */
data class ColorInfo(
    val colorType: ColorType,
    val alphaType: AlphaType,
    val colorSpace: ColorSpace
)

enum class ColorType {
    ALPHA_8,    // 8-bit alpha only
    RGB_565,    // 16-bit RGB
    ARGB_4444,  // 16-bit ARGB
    RGBA_8888,  // 32-bit RGBA (most common)
    RGBA_F16,   // 64-bit float RGBA
    RGBA_F32,   // 128-bit float RGBA
    UNKNOWN
}

enum class AlphaType {
    OPAQUE,     // No alpha channel
    PREMUL,     // Pre-multiplied alpha
    UNPREMUL,   // Un-premultiplied alpha
    UNKNOWN
}