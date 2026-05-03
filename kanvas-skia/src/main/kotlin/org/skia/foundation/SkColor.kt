package org.skia.foundation

public typealias SkColor = Int

public fun SkColorSetARGB(a: Int, r: Int, g: Int, b: Int): SkColor =
    ((a and 0xFF) shl 24) or ((r and 0xFF) shl 16) or ((g and 0xFF) shl 8) or (b and 0xFF)

public fun SkColorSetRGB(r: Int, g: Int, b: Int): SkColor = SkColorSetARGB(0xFF, r, g, b)

public fun SkColorGetA(c: SkColor): Int = (c ushr 24) and 0xFF
public fun SkColorGetR(c: SkColor): Int = (c ushr 16) and 0xFF
public fun SkColorGetG(c: SkColor): Int = (c ushr 8) and 0xFF
public fun SkColorGetB(c: SkColor): Int = c and 0xFF

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
 * it as 0xFFRRGGBB. Mirrors Skia's `ToolUtils::color_to_565` used by SimpleRectGM.
 */
public fun colorToRGB565(c: SkColor): SkColor {
    val r = SkColorGetR(c) and 0xF8
    val g = SkColorGetG(c) and 0xFC
    val b = SkColorGetB(c) and 0xF8
    return SkColorSetARGB(0xFF, r or (r ushr 5), g or (g ushr 6), b or (b ushr 5))
}
