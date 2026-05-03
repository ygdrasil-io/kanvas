package org.skia.foundation

import org.skia.math.SkISize

/**
 * Premultiplied ARGB8888 raster buffer. Pixels are stored as Int in
 * 0xAARRGGBB format, row-major, no row padding.
 */
public class SkBitmap(public val width: Int, public val height: Int) {
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
    }
}
