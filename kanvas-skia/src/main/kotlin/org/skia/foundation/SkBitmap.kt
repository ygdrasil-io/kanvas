package org.skia.foundation

import org.skia.math.SkISize

/**
 * Raster pixel buffer.
 *
 * Two storage formats are supported (Phase 6 — F16 working-space rasterizer):
 *
 *  - **`SkColorType.kRGBA_8888`** *(default, legacy)* — 8 bits per channel,
 *    packed `0xAARRGGBB` Int per pixel, **not premultiplied**. Storage:
 *    [pixels8888]. Fast for solid-colour rasterization, but every blend
 *    quantizes to 1/255 — accumulates drift on translucent stacks and on
 *    multi-stop gradients.
 *
 *  - **`SkColorType.kRGBA_F16Norm`** — 4 × 32-bit float per pixel,
 *    **premultiplied**, components normalized to `[0, 1]`. Storage:
 *    [pixelsF16] (length `4 × width × height`, R-G-B-A interleaved).
 *    Skia upstream uses true 16-bit half-floats via SIMD; we use 32-bit
 *    floats to keep the JVM simple — same precision when bounded to
 *    `[0, 1]`. PNG output writes 16-bit-per-channel; reference loading
 *    preserves the 16-bit data verbatim. This is the format that matches
 *    upstream's reference rendering precision.
 *
 * The colour space describes what the encoded RGB values mean. Default is
 * sRGB; tests render into Rec.2020 to match upstream DM output.
 */
public class SkBitmap(
    public val width: Int,
    public val height: Int,
    public val colorSpace: SkColorSpace = SkColorSpace.makeSRGB(),
    public val colorType: SkColorType = SkColorType.kRGBA_8888,
) {
    /**
     * Backing storage for [SkColorType.kRGBA_8888]. Empty array for F16
     * bitmaps — never mutated when [colorType] is `kRGBA_F16Norm`.
     */
    public val pixels8888: IntArray =
        if (colorType == SkColorType.kRGBA_8888) IntArray(width * height) else IntArray(0)

    /**
     * Backing storage for [SkColorType.kRGBA_F16Norm]. 4 floats per pixel
     * (R, G, B, A in `[0, 1]`, **premultiplied**, row-major). Empty array
     * for 8888 bitmaps.
     */
    public val pixelsF16: FloatArray =
        if (colorType == SkColorType.kRGBA_F16Norm) FloatArray(width * height * 4) else FloatArray(0)

    /**
     * Legacy alias for [pixels8888]. Kept for source-compatibility with
     * pre-Phase-6 callers; equivalent to `pixels8888` and *only* meaningful
     * when [colorType] is [SkColorType.kRGBA_8888].
     */
    public val pixels: IntArray get() = pixels8888

    public fun eraseColor(c: SkColor) {
        when (colorType) {
            SkColorType.kRGBA_8888 -> pixels8888.fill(c)
            SkColorType.kRGBA_F16Norm -> {
                // Convert non-premul SkColor → premul float and fill.
                val a = SkColorGetA(c) / 255f
                val r = SkColorGetR(c) / 255f * a
                val g = SkColorGetG(c) / 255f * a
                val b = SkColorGetB(c) / 255f * a
                var i = 0
                val n = pixelsF16.size
                while (i < n) {
                    pixelsF16[i] = r
                    pixelsF16[i + 1] = g
                    pixelsF16[i + 2] = b
                    pixelsF16[i + 3] = a
                    i += 4
                }
            }
            else -> error("SkBitmap.eraseColor unsupported for colorType=$colorType")
        }
    }

    public fun getPixel(x: Int, y: Int): SkColor {
        require(x in 0 until width && y in 0 until height) { "($x, $y) outside ${width}x$height" }
        return when (colorType) {
            SkColorType.kRGBA_8888 -> pixels8888[y * width + x]
            SkColorType.kRGBA_F16Norm -> {
                // Convert premul float → non-premul 8-bit ARGB SkColor. Use
                // **truncation** (`floor(f * 256)`) instead of round-to-nearest
                // to match the legacy `ushr 8` semantics that
                // `bufferedImageToBitmap` used when loading 16-bit PNGs into
                // 8-bit bitmaps. Round-to-nearest would shift the gradient
                // boundary by one byte for pixels whose 16-bit value's low
                // byte exceeds 0x80, causing a regression on the previously
                // 100 %-passing `ShallowGradient*` GMs.
                val i = (y * width + x) * 4
                val pr = pixelsF16[i]
                val pg = pixelsF16[i + 1]
                val pb = pixelsF16[i + 2]
                val pa = pixelsF16[i + 3]
                val a = (pa * 256f).toInt().coerceIn(0, 255)
                if (a == 0) return 0
                val invA = 1f / pa
                val r = (pr * invA * 256f).toInt().coerceIn(0, 255)
                val g = (pg * invA * 256f).toInt().coerceIn(0, 255)
                val b = (pb * invA * 256f).toInt().coerceIn(0, 255)
                SkColorSetARGB(a, r, g, b)
            }
            else -> error("SkBitmap.getPixel unsupported for colorType=$colorType")
        }
    }

    public fun setPixel(x: Int, y: Int, c: SkColor) {
        if (x !in 0 until width || y !in 0 until height) return
        when (colorType) {
            SkColorType.kRGBA_8888 -> pixels8888[y * width + x] = c
            SkColorType.kRGBA_F16Norm -> {
                val i = (y * width + x) * 4
                val a = SkColorGetA(c) / 255f
                pixelsF16[i] = SkColorGetR(c) / 255f * a
                pixelsF16[i + 1] = SkColorGetG(c) / 255f * a
                pixelsF16[i + 2] = SkColorGetB(c) / 255f * a
                pixelsF16[i + 3] = a
            }
            else -> error("SkBitmap.setPixel unsupported for colorType=$colorType")
        }
    }

    /**
     * Read a pixel as 4 premultiplied floats (R, G, B, A in `[0, 1]`).
     * For 8888 bitmaps the values are converted on the fly.
     */
    public fun getPixelF16(x: Int, y: Int, out: FloatArray) {
        require(out.size >= 4)
        require(x in 0 until width && y in 0 until height) { "($x, $y) outside ${width}x$height" }
        when (colorType) {
            SkColorType.kRGBA_F16Norm -> {
                val i = (y * width + x) * 4
                out[0] = pixelsF16[i]
                out[1] = pixelsF16[i + 1]
                out[2] = pixelsF16[i + 2]
                out[3] = pixelsF16[i + 3]
            }
            SkColorType.kRGBA_8888 -> {
                val c = pixels8888[y * width + x]
                val a = SkColorGetA(c) / 255f
                out[0] = SkColorGetR(c) / 255f * a
                out[1] = SkColorGetG(c) / 255f * a
                out[2] = SkColorGetB(c) / 255f * a
                out[3] = a
            }
            else -> error("getPixelF16 unsupported for colorType=$colorType")
        }
    }

    /**
     * Write a pixel from 4 premultiplied floats. Caller's responsibility to
     * ensure the values are in `[0, 1]` and consistent (rgb ≤ a). For 8888
     * bitmaps the values are quantized to 8 bits per channel and stored
     * non-premultiplied.
     */
    public fun setPixelF16(x: Int, y: Int, r: Float, g: Float, b: Float, a: Float) {
        if (x !in 0 until width || y !in 0 until height) return
        when (colorType) {
            SkColorType.kRGBA_F16Norm -> {
                val i = (y * width + x) * 4
                pixelsF16[i] = r
                pixelsF16[i + 1] = g
                pixelsF16[i + 2] = b
                pixelsF16[i + 3] = a
            }
            SkColorType.kRGBA_8888 -> {
                val ai = (a * 255f + 0.5f).toInt().coerceIn(0, 255)
                val ri: Int; val gi: Int; val bi: Int
                if (a > 0f) {
                    val invA = 1f / a
                    ri = (r * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
                    gi = (g * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
                    bi = (b * invA * 255f + 0.5f).toInt().coerceIn(0, 255)
                } else { ri = 0; gi = 0; bi = 0 }
                pixels8888[y * width + x] = SkColorSetARGB(ai, ri, gi, bi)
            }
            else -> error("setPixelF16 unsupported for colorType=$colorType")
        }
    }

    public fun size(): SkISize = SkISize.Make(width, height)

    public fun asImage(): SkImage = SkImage.Make(this)

    public companion object {
        public fun Make(w: Int, h: Int): SkBitmap = SkBitmap(w, h)
        public fun Make(w: Int, h: Int, colorSpace: SkColorSpace): SkBitmap =
            SkBitmap(w, h, colorSpace)
        public fun Make(w: Int, h: Int, colorSpace: SkColorSpace, colorType: SkColorType): SkBitmap =
            SkBitmap(w, h, colorSpace, colorType)
    }
}
