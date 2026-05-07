package org.skia.foundation

import org.skia.core.SkColorSpaceXformSteps
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
     * Backing storage for [SkColorType.kARGB_4444] (Phase C5). One
     * `Short` per pixel : `[R:15..12 G:11..8 B:7..4 A:3..0]`. Pixels
     * are stored **premultiplied** — matches Skia's
     * `kPremul_SkAlphaType` default for ARGB_4444. The 4-bit channels
     * are stored as `(8-bit-channel + 8) >> 4` (round-to-nearest of
     * `b/17`), so a fully-opaque white pixel encodes to
     * `0xFFFF` and a fully-transparent pixel to `0`.
     */
    public val pixels4444: ShortArray =
        if (colorType == SkColorType.kARGB_4444) ShortArray(width * height) else ShortArray(0)

    /**
     * Legacy alias for [pixels8888]. Kept for source-compatibility with
     * pre-Phase-6 callers; equivalent to `pixels8888` and *only* meaningful
     * when [colorType] is [SkColorType.kRGBA_8888].
     */
    public val pixels: IntArray get() = pixels8888

    /**
     * Mirrors Skia's `SkBitmap::eraseColor(SkColor)`. The supplied [c]
     * is interpreted as an sRGB-encoded ARGB integer (Skia's [SkColor]
     * convention) and converted to this bitmap's [colorSpace] before
     * being stored. For sRGB destinations the conversion is an
     * identity no-op and we take the historical fast path; for
     * non-sRGB destinations (e.g. Rec.2020 — the format the GM test
     * harness renders into) we apply the [SkColorSpaceXformSteps]
     * pipeline so the encoded pixel value matches the destination
     * gamut.
     *
     * Pre-fix this method dropped the xform, so a `WHITE` background
     * filled into a Rec.2020 bitmap stored sRGB-encoded WHITE values
     * — visually-correct only because WHITE is a colour-space
     * invariant. Non-trivial backgrounds (e.g. `Crbug947055GM`,
     * `ClipDrawDrawGM`) would drift, capping their similarity scores.
     */
    public fun eraseColor(c: SkColor) {
        // Decode the SkColor as non-premul sRGB float `[0, 1]`.
        var r = SkColorGetR(c) / 255f
        var g = SkColorGetG(c) / 255f
        var b = SkColorGetB(c) / 255f
        val a = SkColorGetA(c) / 255f
        // Apply the sRGB → bitmap.colorSpace pipeline. Identity for
        // sRGB destinations (most callers); non-trivial only for
        // non-sRGB working spaces.
        if (!colorSpace.isSRGB()) {
            val rgba = floatArrayOf(r, g, b, a)
            xformedSrgbColor(rgba)
            r = rgba[0]; g = rgba[1]; b = rgba[2]
            // Note: alpha is unchanged by xformSteps (linearize/gamut/encode
            // only touch RGB), so we keep the original `a` value instead of
            // re-reading rgba[3] — saves one float access per fill.
        }
        when (colorType) {
            SkColorType.kRGBA_8888 -> {
                // Quantize the (potentially xformed) channels back to 8 bits
                // and pack in non-premul `0xAARRGGBB` form.
                val ai = (a * 255f + 0.5f).toInt().coerceIn(0, 255)
                val ri = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
                val gi = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
                val bi = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
                pixels8888.fill(SkColorSetARGB(ai, ri, gi, bi))
            }
            SkColorType.kRGBA_F16Norm -> {
                // Premultiply the (potentially xformed) channels and fill.
                val pr = (r * a).coerceIn(0f, 1f)
                val pg = (g * a).coerceIn(0f, 1f)
                val pb = (b * a).coerceIn(0f, 1f)
                val pa = a.coerceIn(0f, 1f)
                var i = 0
                val n = pixelsF16.size
                while (i < n) {
                    pixelsF16[i] = pr
                    pixelsF16[i + 1] = pg
                    pixelsF16[i + 2] = pb
                    pixelsF16[i + 3] = pa
                    i += 4
                }
            }
            SkColorType.kARGB_4444 -> {
                // Quantize to 4 bits per channel (premul) and pack.
                pixels4444.fill(packARGB4444Premul(a, r, g, b))
            }
            else -> error("SkBitmap.eraseColor unsupported for colorType=$colorType")
        }
    }

    /**
     * Apply the sRGB → [colorSpace] xform pipeline in place on a
     * `[r, g, b, a]` non-premul float vector. Lazy-init the steps the
     * first time we hit this branch — Rec.2020 GMs amortise the cost
     * over thousands of fills per render.
     */
    private fun xformedSrgbColor(rgba: FloatArray) {
        var steps = eraseColorXformCache
        if (steps == null) {
            // SkColorSpaceXformSteps lives in `org.skia.core` and uses its
            // own `core.SkAlphaType` enum — distinct from this package's
            // foundation enum (a known duplicate). Both have identical
            // variants; we resolve to the core one explicitly here.
            steps = SkColorSpaceXformSteps(
                SkColorSpace.makeSRGB(),
                org.skia.core.SkAlphaType.kUnpremul,
                colorSpace,
                org.skia.core.SkAlphaType.kUnpremul,
            )
            eraseColorXformCache = steps
        }
        steps.apply(rgba)
    }

    @Volatile
    private var eraseColorXformCache: SkColorSpaceXformSteps? = null

    public fun getPixel(x: Int, y: Int): SkColor {
        require(x in 0 until width && y in 0 until height) { "($x, $y) outside ${width}x$height" }
        return when (colorType) {
            SkColorType.kRGBA_8888 -> pixels8888[y * width + x]
            SkColorType.kARGB_4444 -> unpackARGB4444Premul(pixels4444[y * width + x])
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
            SkColorType.kARGB_4444 -> {
                val a = SkColorGetA(c) / 255f
                val r = SkColorGetR(c) / 255f
                val g = SkColorGetG(c) / 255f
                val b = SkColorGetB(c) / 255f
                pixels4444[y * width + x] = packARGB4444Premul(a, r, g, b)
            }
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
            SkColorType.kARGB_4444 -> {
                // Unpack 4-bit premul channels into [0, 1] floats. The
                // 4-bit value `v` round-trips to 8-bit via `v * 17` then
                // /255, which simplifies to v/15.
                val packed = pixels4444[y * width + x].toInt() and 0xFFFF
                out[0] = ((packed shr 12) and 0xF) / 15f
                out[1] = ((packed shr 8) and 0xF) / 15f
                out[2] = ((packed shr 4) and 0xF) / 15f
                out[3] = (packed and 0xF) / 15f
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
            SkColorType.kARGB_4444 -> {
                // Inputs are premul floats — quantise directly to 4 bits.
                val r4 = (r * 15f + 0.5f).toInt().coerceIn(0, 15)
                val g4 = (g * 15f + 0.5f).toInt().coerceIn(0, 15)
                val b4 = (b * 15f + 0.5f).toInt().coerceIn(0, 15)
                val a4 = (a * 15f + 0.5f).toInt().coerceIn(0, 15)
                pixels4444[y * width + x] = ((r4 shl 12) or (g4 shl 8) or (b4 shl 4) or a4).toShort()
            }
            else -> error("setPixelF16 unsupported for colorType=$colorType")
        }
    }

    public fun size(): SkISize = SkISize.Make(width, height)

    public fun asImage(): SkImage = SkImage.Make(this)

    /**
     * Mirrors Skia's `SkBitmap::makeShader(tmx, tmy, sampling, localMatrix)`.
     * Phase 5g — see [SkBitmapShader] for the sampling rules.
     */
    public fun makeShader(
        tileX: SkTileMode = SkTileMode.kClamp,
        tileY: SkTileMode = SkTileMode.kClamp,
        sampling: SkSamplingOptions = SkSamplingOptions.Default,
        localMatrix: org.skia.math.SkMatrix = org.skia.math.SkMatrix.Identity,
    ): SkShader = SkBitmapShader(asImage(), tileX, tileY, sampling, localMatrix)

    public companion object {
        public fun Make(w: Int, h: Int): SkBitmap = SkBitmap(w, h)
        public fun Make(w: Int, h: Int, colorSpace: SkColorSpace): SkBitmap =
            SkBitmap(w, h, colorSpace)
        public fun Make(w: Int, h: Int, colorSpace: SkColorSpace, colorType: SkColorType): SkBitmap =
            SkBitmap(w, h, colorSpace, colorType)

        // ─── ARGB_4444 helpers (Phase C5) ────────────────────────────

        /**
         * Pack non-premultiplied float channels `(r, g, b, a) ∈ [0, 1]`
         * into a 16-bit `Short` with bit layout
         * `[R:15..12 G:11..8 B:7..4 A:3..0]`. The output is
         * **premultiplied** (matching Skia's `kPremul_SkAlphaType`
         * default for ARGB_4444).
         *
         * Quantisation is round-to-nearest of `c * 15` per channel.
         * Inputs are clamped to `[0, 1]` before quantisation.
         */
        internal fun packARGB4444Premul(a: Float, r: Float, g: Float, b: Float): Short {
            val ac = a.coerceIn(0f, 1f)
            val rPm = (r * ac).coerceIn(0f, 1f)
            val gPm = (g * ac).coerceIn(0f, 1f)
            val bPm = (b * ac).coerceIn(0f, 1f)
            val a4 = (ac * 15f + 0.5f).toInt()
            val r4 = (rPm * 15f + 0.5f).toInt()
            val g4 = (gPm * 15f + 0.5f).toInt()
            val b4 = (bPm * 15f + 0.5f).toInt()
            return ((r4 shl 12) or (g4 shl 8) or (b4 shl 4) or a4).toShort()
        }

        /**
         * Unpack a packed ARGB_4444 `Short` (premul, 4 bits per
         * channel) to a non-premultiplied 8-bit ARGB [SkColor]. Mirrors
         * Skia's `SkColor4444::toSkColor()` behaviour : 4-bit channels
         * are widened to 8 bits via `v * 17` (so 15 → 255, 0 → 0), then
         * the premul values are unpremul'd by dividing the colour
         * channels by alpha.
         */
        internal fun unpackARGB4444Premul(packed: Short): SkColor {
            val p = packed.toInt() and 0xFFFF
            val r4 = (p shr 12) and 0xF
            val g4 = (p shr 8) and 0xF
            val b4 = (p shr 4) and 0xF
            val a4 = p and 0xF
            // Widen 4 → 8 bits via `v * 17` (= (v << 4) | v).
            val a8 = a4 * 17
            if (a8 == 0) return 0
            // Unpremul colour channels : the stored 4-bit channel is
            // premul ; we widen it to 8-bit then divide by `a8 / 255`
            // to recover the non-premul 8-bit channel value.
            val r8Pm = r4 * 17
            val g8Pm = g4 * 17
            val b8Pm = b4 * 17
            val r8 = ((r8Pm * 255 + a8 / 2) / a8).coerceIn(0, 255)
            val g8 = ((g8Pm * 255 + a8 / 2) / a8).coerceIn(0, 255)
            val b8 = ((b8Pm * 255 + a8 / 2) / a8).coerceIn(0, 255)
            return SkColorSetARGB(a8, r8, g8, b8)
        }
    }
}
