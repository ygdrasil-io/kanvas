package org.skia.foundation

import org.skia.math.SkISize

/**
 * Non-premultiplied 8-bit-per-channel ARGB raster buffer. Pixels are stored
 * as Int in 0xAARRGGBB format, row-major, no row padding.
 *
 * The color space describes what the encoded RGB triplets mean. By default
 * it is sRGB (the same convention as `SkColor`). Setting it to a wide-gamut
 * profile means the bitmap stores values that need the inverse transform
 * (Rec.2020 → sRGB) to be displayed correctly on a sRGB monitor — which is
 * exactly how Skia DM stores its reference PNGs.
 */
public class SkBitmap(
    public val width: Int,
    public val height: Int,
    public val colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
) {
    public val pixels: IntArray = IntArray(width * height)

    public fun eraseColor(c: SkColor) {
        pixels.fill(c)
    }

    public fun getPixel(x: Int, y: Int): SkColor {
        require(x in 0 until width && y in 0 until height) { "($x, $y) outside ${width}x$height" }
        return pixels[y * width + x]
    }

    public fun setPixel(x: Int, y: Int, c: SkColor) {
        if (x !in 0 until width || y !in 0 until height) return
        pixels[y * width + x] = c
    }

    public fun size(): SkISize = SkISize.Make(width, height)

    public companion object {
        public fun Make(w: Int, h: Int): SkBitmap = SkBitmap(w, h)
        public fun Make(w: Int, h: Int, colorSpace: SkColorSpace): SkBitmap =
            SkBitmap(w, h, colorSpace)
    }
}
