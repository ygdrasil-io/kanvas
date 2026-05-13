package org.skia.foundation

import org.skia.core.SkColorSpaceXformSteps
import org.skia.math.SkIPoint
import org.skia.math.SkIRect
import org.skia.math.SkISize
import java.nio.ByteBuffer
import java.nio.ByteOrder

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
     * Backing storage for [SkColorType.kAlpha_8] (Phase G4a). One
     * unsigned byte per pixel interpreted as alpha; RGB is implicitly
     * `(0, 0, 0)` when read out as an [SkColor] (see [getPixel]).
     * Empty array for any other colour type.
     */
    public val pixelsA8: ByteArray =
        if (colorType == SkColorType.kAlpha_8) ByteArray(width * height) else ByteArray(0)

    /**
     * Backing storage for [SkColorType.kRGB_565] (Phase R1-C). One
     * `Short` per pixel : `[R:15..11 G:10..5 B:4..0]` per Skia's
     * `kRGB_565_SkColorType` layout (BGR data packed into a LE 16-bit
     * word, see `include/core/SkColorType.h`). 5-bit channels widen to
     * 8-bit via `v * 8 | v >> 2` (R, B) and `v * 4 | v >> 4` (G) on
     * read-out — matches Skia's `SkColor16to32` quantisation in
     * `src/core/SkBitmap.cpp`. Alpha is always 255 (`kOpaque`).
     *
     * Empty array for any other colour type.
     */
    public val pixels565: ShortArray =
        if (colorType == SkColorType.kRGB_565) ShortArray(width * height) else ShortArray(0)

    /**
     * Backing storage for [SkColorType.kGray_8] (Phase R1-C). One
     * unsigned byte per pixel interpreted as luminance ; on read-out
     * the byte is replicated to all three RGB channels and alpha is
     * forced to 255 (`kOpaque`). Mirrors Skia's `kGray_8_SkColorType`
     * (`include/core/SkColorType.h`) used by `gm/all_bitmap_configs.cpp`
     * and `gm/bitmapfilters.cpp`.
     *
     * Empty array for any other colour type.
     */
    public val pixelsGray8: ByteArray =
        if (colorType == SkColorType.kGray_8) ByteArray(width * height) else ByteArray(0)

    /**
     * Backing storage for [SkColorType.kBGRA_8888] (Phase G4b).
     *
     * Same shape as [pixels8888] : one `Int` per pixel, **non-premul**,
     * stored in Pascal-Argb (`0xAARRGGBB`) order — *identical* to the
     * `kRGBA_8888` representation. The `kBGRA_8888` colour type only
     * affects the *external byte order* (the order in which the channels
     * appear in a packed `uint32_t` on disk / on the wire — `A, R, G, B`
     * vs `A, B, G, R` on a little-endian host), which matters when
     * encoding to or decoding from a PNG / wire buffer. Internally, all
     * `:kanvas-skia` consumers ([SkBitmapShader], the raster device, the
     * colour-filter pipeline) read pixels via the colorType-aware
     * [getPixel] accessor (or the [SkImage.Make] snapshot, which itself
     * delegates to [getPixel] for non-8888 sources), so a single
     * Pascal-Argb backing store is correct for both colour types.
     *
     * Empty array for any other colour type.
     */
    public val pixelsBGRA8888: IntArray =
        if (colorType == SkColorType.kBGRA_8888) IntArray(width * height) else IntArray(0)

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
            SkColorType.kBGRA_8888 -> {
                // BGRA shares the same in-memory Pascal-Argb representation
                // as RGBA — only the external byte ordering differs (see
                // [pixelsBGRA8888]'s KDoc). Reuse the 8888 fast path.
                val ai = (a * 255f + 0.5f).toInt().coerceIn(0, 255)
                val ri = (r * 255f + 0.5f).toInt().coerceIn(0, 255)
                val gi = (g * 255f + 0.5f).toInt().coerceIn(0, 255)
                val bi = (b * 255f + 0.5f).toInt().coerceIn(0, 255)
                pixelsBGRA8888.fill(SkColorSetARGB(ai, ri, gi, bi))
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
            SkColorType.kAlpha_8 -> {
                // Alpha-only — RGB is dropped. `a` is in `[0, 1]` after
                // the (identity for sRGB → working) xform; quantize to 8
                // bits and broadcast.
                val ai = (a * 255f + 0.5f).toInt().coerceIn(0, 255)
                pixelsA8.fill(ai.toByte())
            }
            SkColorType.kRGB_565 -> {
                // Phase R1-C — 565 has no alpha channel ; we drop `a` and
                // quantise the (post-xform) RGB channels to 5/6/5 bits.
                pixels565.fill(packRGB565(r, g, b))
            }
            SkColorType.kGray_8 -> {
                // Phase R1-C — luminance from Rec.601 weights, then drop
                // alpha. Matches Skia's `SkColorToLuminance` (`src/core/SkColor.cpp`).
                val ly = (r * 0.299f + g * 0.587f + b * 0.114f).coerceIn(0f, 1f)
                val li = (ly * 255f + 0.5f).toInt().coerceIn(0, 255)
                pixelsGray8.fill(li.toByte())
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
            SkColorType.kBGRA_8888 -> pixelsBGRA8888[y * width + x]
            SkColorType.kARGB_4444 -> unpackARGB4444Premul(pixels4444[y * width + x])
            SkColorType.kRGBA_F16Norm -> {
                // Convert premul float → non-premul 8-bit ARGB SkColor. Use
                // **truncation** (`floor(f * 256)`) instead of round-to-nearest
                // to match the legacy `ushr 8` semantics the test-side PNG
                // loader used when materialising 16-bit references as 8-bit
                // bitmaps. Round-to-nearest would shift the gradient boundary
                // by one byte for pixels whose 16-bit value's low byte exceeds
                // 0x80, causing a regression on the previously 100 %-passing
                // `ShallowGradient*` GMs.
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
            SkColorType.kAlpha_8 -> {
                // Alpha-only — RGB forced to 0. The byte is unsigned 0..255.
                val a = pixelsA8[y * width + x].toInt() and 0xFF
                SkColorSetARGB(a, 0, 0, 0)
            }
            SkColorType.kRGB_565 -> {
                // Phase R1-C — unpack 5/6/5 bits to 8-bit RGB ; alpha = 255.
                unpackRGB565(pixels565[y * width + x])
            }
            SkColorType.kGray_8 -> {
                // Phase R1-C — replicate luminance to all 3 RGB channels ; alpha = 255.
                val l = pixelsGray8[y * width + x].toInt() and 0xFF
                SkColorSetARGB(0xFF, l, l, l)
            }
            else -> error("SkBitmap.getPixel unsupported for colorType=$colorType")
        }
    }

    public fun setPixel(x: Int, y: Int, c: SkColor) {
        if (x !in 0 until width || y !in 0 until height) return
        when (colorType) {
            SkColorType.kRGBA_8888 -> pixels8888[y * width + x] = c
            SkColorType.kBGRA_8888 -> pixelsBGRA8888[y * width + x] = c
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
            SkColorType.kAlpha_8 -> {
                // Alpha-only — RGB of `c` is discarded.
                pixelsA8[y * width + x] = SkColorGetA(c).toByte()
            }
            SkColorType.kRGB_565 -> {
                // Phase R1-C — drop alpha, quantise to 5/6/5 bits.
                pixels565[y * width + x] = packRGB565(
                    SkColorGetR(c) / 255f,
                    SkColorGetG(c) / 255f,
                    SkColorGetB(c) / 255f,
                )
            }
            SkColorType.kGray_8 -> {
                // Phase R1-C — Rec.601 luminance.
                val r = SkColorGetR(c)
                val g = SkColorGetG(c)
                val b = SkColorGetB(c)
                val l = ((r * 77 + g * 150 + b * 29) shr 8).coerceIn(0, 255)
                pixelsGray8[y * width + x] = l.toByte()
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
            SkColorType.kRGB_565 -> {
                // Phase R1-C — unpack 5/6/5 bits then divide ; alpha is
                // implicit opaque (premul or not — `1`).
                val p = pixels565[y * width + x].toInt() and 0xFFFF
                out[0] = ((p shr 11) and 0x1F) / 31f
                out[1] = ((p shr 5) and 0x3F) / 63f
                out[2] = (p and 0x1F) / 31f
                out[3] = 1f
            }
            SkColorType.kGray_8 -> {
                // Phase R1-C — single luminance channel replicated to RGB ;
                // alpha is opaque (1).
                val l = (pixelsGray8[y * width + x].toInt() and 0xFF) / 255f
                out[0] = l; out[1] = l; out[2] = l; out[3] = 1f
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

    // ─── Phase R2.11 — externally-managed pixel storage ───────────────

    /**
     * Optional [SkPixelRef] handle attached via [installPixels] /
     * [extractSubset]. `null` for bitmaps that own their typed-array
     * storage exclusively (the legacy default). When non-null,
     * [peekPixels] surfaces the ref's underlying [ByteBuffer] as a
     * non-owning [SkPixmap] view.
     *
     * **Storage divergence from upstream Skia** : upstream `SkBitmap`
     * always reads pixels *through* its `SkPixelRef`'s buffer. Kanvas-
     * skia's bitmaps keep their colour-typed backing arrays
     * ([pixels8888], [pixelsF16], …) as the authoritative storage and
     * use [_pixelRef] only to expose the buffer to API surfaces
     * ([peekPixels], `SkPixelRef::generationID`) and to advertise
     * "shared origin" semantics for [extractSubset]. The trade-off is
     * documented on the method-level KDoc — mutations made by the
     * caller into the buffer after [installPixels] *will not* be
     * reflected by [getPixel] (the install copies into the typed array
     * once). Use the typed arrays / [getPixel] / [setPixel] for the
     * authoritative pixel surface.
     */
    private var _pixelRef: SkPixelRef? = null

    /** Mirrors `SkBitmap::pixelRef()`. Returns `null` if none is attached. */
    public fun pixelRef(): SkPixelRef? = _pixelRef

    /**
     * Mirrors Skia's
     * `SkBitmap::installPixels(const SkImageInfo&, void*, size_t)`
     * ([SkBitmap.h:625](https://github.com/google/skia/blob/main/include/core/SkBitmap.h#L625)).
     *
     * Treats [pixels] as the new backing storage described by [info] at
     * row stride [rowBytes]. Validates :
     *  - [info] matches `this` bitmap's `width × height × colorType` ;
     *  - [rowBytes] ≥ `info.minRowBytes()` ;
     *  - [pixels] capacity is large enough to cover
     *    `(height - 1) * rowBytes + minRowBytes()`.
     *
     * On success, the buffer's bytes are decoded into the bitmap's typed
     * backing array (see KDoc on [_pixelRef] for why), and a fresh
     * [SkPixelRef] wrapping [pixels] is attached so consumers can probe
     * the original buffer via [pixelRef] / [peekPixels].
     *
     * Returns `false` (and leaves the bitmap unchanged) on any mismatch.
     */
    public fun installPixels(info: SkImageInfo, pixels: ByteBuffer, rowBytes: Int): Boolean {
        // Info must match this bitmap's geometry / colour type — kanvas-
        // skia's typed-array storage is allocated at construction, so the
        // upstream "free-form info swap" isn't supported.
        if (info.width != width || info.height != height || info.colorType != colorType) return false
        if (info.isEmpty()) return false
        if (rowBytes < info.minRowBytes()) return false
        val required = (height - 1).toLong() * rowBytes + info.minRowBytes()
        if (pixels.capacity() < required) return false

        // Decode the buffer into the typed backing array (the
        // authoritative store — see [_pixelRef]'s KDoc for the
        // rationale).
        val buf = pixels.duplicate().order(ByteOrder.LITTLE_ENDIAN)
        val bpp = info.bytesPerPixel()
        when (colorType) {
            SkColorType.kRGBA_8888 -> {
                for (y in 0 until height) {
                    val rowOff = y * rowBytes
                    for (x in 0 until width) {
                        val o = rowOff + x * bpp
                        val r = buf.get(o).toInt() and 0xFF
                        val g = buf.get(o + 1).toInt() and 0xFF
                        val b = buf.get(o + 2).toInt() and 0xFF
                        val a = buf.get(o + 3).toInt() and 0xFF
                        pixels8888[y * width + x] = SkColorSetARGB(a, r, g, b)
                    }
                }
            }
            SkColorType.kBGRA_8888 -> {
                for (y in 0 until height) {
                    val rowOff = y * rowBytes
                    for (x in 0 until width) {
                        val o = rowOff + x * bpp
                        val b = buf.get(o).toInt() and 0xFF
                        val g = buf.get(o + 1).toInt() and 0xFF
                        val r = buf.get(o + 2).toInt() and 0xFF
                        val a = buf.get(o + 3).toInt() and 0xFF
                        pixelsBGRA8888[y * width + x] = SkColorSetARGB(a, r, g, b)
                    }
                }
            }
            SkColorType.kAlpha_8 -> {
                for (y in 0 until height) {
                    val rowOff = y * rowBytes
                    for (x in 0 until width) {
                        pixelsA8[y * width + x] = buf.get(rowOff + x)
                    }
                }
            }
            SkColorType.kGray_8 -> {
                for (y in 0 until height) {
                    val rowOff = y * rowBytes
                    for (x in 0 until width) {
                        pixelsGray8[y * width + x] = buf.get(rowOff + x)
                    }
                }
            }
            SkColorType.kRGB_565 -> {
                for (y in 0 until height) {
                    val rowOff = y * rowBytes
                    for (x in 0 until width) {
                        pixels565[y * width + x] = buf.getShort(rowOff + x * bpp)
                    }
                }
            }
            SkColorType.kARGB_4444 -> {
                for (y in 0 until height) {
                    val rowOff = y * rowBytes
                    for (x in 0 until width) {
                        pixels4444[y * width + x] = buf.getShort(rowOff + x * bpp)
                    }
                }
            }
            SkColorType.kRGBA_F16Norm -> {
                // R-suivi.17 — F16 ingress. The buffer carries 8 bytes per
                // pixel : 4 IEEE 754 binary16 (half-float) channels in
                // R, G, B, A order, **premultiplied** (Skia's
                // `kRGBA_F16_SkColorType` default alpha type is `kPremul`).
                // Decode each half-float to a 32-bit float and write into
                // [pixelsF16] in the same R-G-B-A premul layout.
                for (y in 0 until height) {
                    val rowOff = y * rowBytes
                    val dstRow = y * width * 4
                    for (x in 0 until width) {
                        val src = rowOff + x * bpp
                        val dst = dstRow + x * 4
                        // ByteBuffer is little-endian (set above) — getShort
                        // reads the half-float bits as a signed 16-bit
                        // integer in the host order, which matches the
                        // upstream `uint16_t*` layout on a LE host.
                        val rh = buf.getShort(src)
                        val gh = buf.getShort(src + 2)
                        val bh = buf.getShort(src + 4)
                        val ah = buf.getShort(src + 6)
                        pixelsF16[dst] = halfToFloat(rh)
                        pixelsF16[dst + 1] = halfToFloat(gh)
                        pixelsF16[dst + 2] = halfToFloat(bh)
                        pixelsF16[dst + 3] = halfToFloat(ah)
                    }
                }
            }
            else -> return false
        }
        _pixelRef = SkPixelRef(width, height, pixels, rowBytes)
        return true
    }

    /**
     * Mirrors `SkBitmap::installPixels(const SkPixmap&)`
     * ([SkBitmap.h:644](https://github.com/google/skia/blob/main/include/core/SkBitmap.h#L644)).
     * Delegates to the three-arg overload using [pixmap]'s info, addr
     * and rowBytes.
     */
    public fun installPixels(pixmap: SkPixmap): Boolean =
        installPixels(pixmap.info(), pixmap.addr(), pixmap.rowBytes())

    /**
     * Mirrors `SkBitmap::extractSubset(SkBitmap*, const SkIRect&) const`
     * ([SkBitmap.h:979](https://github.com/google/skia/blob/main/include/core/SkBitmap.h#L979)).
     *
     * Re-binds [dst] to a fresh [SkBitmap] dimensioned to the
     * intersection of [subset] with this bitmap's bounds, with the same
     * `colorType` and `colorSpace`. Returns `false` if the intersection
     * is empty.
     *
     * **Storage sharing** : the returned bitmap *attaches the same
     * [SkPixelRef]* as the source (when one exists, e.g. after
     * [installPixels]) — matching upstream's
     * "extracted subset shares pixels with the parent" gen-id contract.
     *
     * **Pixel data** is *copied* from the source into [dst]'s typed
     * backing arrays. Subsequent mutations to either bitmap will not
     * propagate to the other — kanvas-skia bitmaps own their typed
     * arrays (see KDoc on [_pixelRef] for the rationale). Tests / GMs
     * that need true zero-copy sharing should operate on the
     * [SkPixmap.extractSubset] surface instead (`SkPixmap` is a true
     * non-owning view).
     *
     * Note that `dst` is bound *by reference* — the caller's local
     * variable is not rewired. Use the returned [SkBitmap] from
     * `bm.extractSubsetOrNull(rect)` (added below) when a value-style
     * API is preferred.
     */
    public fun extractSubset(dst: SkBitmap, subset: SkIRect): Boolean {
        val bounds = SkIRect.MakeWH(width, height)
        val isect = SkIRect.MakeLTRB(bounds.left, bounds.top, bounds.right, bounds.bottom)
        if (!isect.intersect(subset)) return false
        val sw = isect.width()
        val sh = isect.height()
        if (sw <= 0 || sh <= 0) return false
        // The dst handle is supplied by the caller — kanvas-skia's
        // [SkBitmap] is a value type so we can't rebind the variable.
        // We instead copy the subset's pixels into dst when dst has
        // matching geometry / colour type ; otherwise we surface this
        // as a precondition failure (false).
        if (dst.width != sw || dst.height != sh || dst.colorType != colorType) return false

        // Copy pixel-by-pixel — robust across colour types and avoids
        // typed-array vs typed-array juggling.
        for (y in 0 until sh) {
            for (x in 0 until sw) {
                val c = getPixel(isect.left + x, isect.top + y)
                dst.setPixel(x, y, c)
            }
        }
        // Propagate the SkPixelRef so gen-id linkage matches upstream.
        dst._pixelRef = _pixelRef
        return true
    }

    /**
     * Mirrors `SkBitmap::extractAlpha(SkBitmap*, const SkPaint*, SkIPoint*) const`
     * ([SkBitmap.h:1145](https://github.com/google/skia/blob/main/include/core/SkBitmap.h#L1145)).
     *
     * Re-binds [dst] to an alpha-only ([SkColorType.kAlpha_8]) copy of
     * `this`. [dst] must have `kAlpha_8` storage. Two sizing modes :
     *
     *  - **No [SkPaint.maskFilter]** : [dst] must be `width × height`.
     *    The source's alpha channel is copied into [dst]. [offset] (if
     *    non-`null`) is set to `(0, 0)` — origin unchanged.
     *
     *  - **With [SkPaint.maskFilter]** (R-suivi.18) : the source alpha
     *    is first dumped into a buffer expanded by the filter's
     *    [SkMaskFilter.margin] on every side, the filter widens it (e.g.
     *    Gaussian blur), and the result is copied into [dst]. [dst] may
     *    be sized either to the source dimensions (the filtered halo is
     *    cropped at the source bounds) or to the margin-expanded
     *    dimensions `width + 2·margin × height + 2·margin` (the full
     *    halo is preserved). [offset] is set to `(-margin, -margin)` —
     *    where [dst]'s `(0, 0)` lies relative to the source's `(0, 0)`
     *    — so callers compositing the alpha back over the original
     *    geometry can shift accordingly.
     */
    public fun extractAlpha(
        dst: SkBitmap,
        paint: SkPaint? = null,
        offset: SkIPoint? = null,
    ): Boolean {
        if (dst.colorType != SkColorType.kAlpha_8) return false

        val maskFilter = paint?.maskFilter
        if (maskFilter == null) {
            // Fast path — straight alpha-channel copy.
            if (dst.width != width || dst.height != height) return false
            for (y in 0 until height) {
                for (x in 0 until width) {
                    val a = SkColorGetA(getPixel(x, y))
                    dst.pixelsA8[y * width + x] = a.toByte()
                }
            }
            offset?.set(0, 0)
            return true
        }

        // R-suivi.18 — mask-filter path. Produce the source's A8 plane
        // in-line, run it through `SkMaskFilter.filterMask` (which
        // returns a margin-expanded bitmap), then blit the relevant
        // window into [dst]. [dst] may be sized to either the source
        // bounds (cropped halo) or the margin-expanded bounds (full
        // halo) — both shapes are valid in upstream's contract.
        val srcA8 = SkBitmap(width, height, colorSpace, SkColorType.kAlpha_8)
        for (y in 0 until height) {
            for (x in 0 until width) {
                srcA8.pixelsA8[y * width + x] = SkColorGetA(getPixel(x, y)).toByte()
            }
        }
        val mfOffset = SkIPoint()
        val filtered = maskFilter.filterMask(srcA8, offset = mfOffset)
        val m = -mfOffset.fX  // === maskFilter.margin()

        // Accept dst sized to source bounds OR to margin-expanded bounds.
        val expW = filtered.width
        val expH = filtered.height
        when {
            dst.width == expW && dst.height == expH -> {
                // Full halo : copy verbatim. Origin is at (-m, -m) of
                // the source.
                System.arraycopy(filtered.pixelsA8, 0, dst.pixelsA8, 0, expW * expH)
                offset?.set(-m, -m)
            }
            dst.width == width && dst.height == height -> {
                // Cropped halo : copy the central source-sized window.
                // Origin is still at (-m, -m) so callers know the halo
                // outside the source bounds was discarded (this matches
                // Skia's contract — `offset` denotes where the mask's
                // (0, 0) sits relative to the source).
                for (y in 0 until height) {
                    val srcRow = (y + m) * expW + m
                    val dstRow = y * width
                    System.arraycopy(filtered.pixelsA8, srcRow, dst.pixelsA8, dstRow, width)
                }
                offset?.set(-m, -m)
            }
            else -> return false
        }
        return true
    }

    /**
     * Mirrors `SkBitmap::peekPixels(SkPixmap*) const`
     * ([SkBitmap.h:1179](https://github.com/google/skia/blob/main/include/core/SkBitmap.h#L1179)).
     *
     * Fills [pixmap] with this bitmap's info / addr / rowBytes when an
     * externally-attached buffer is available ([installPixels] has been
     * called). Returns `false` when no buffer is attached — typed-array-
     * only bitmaps don't have a `void*` to hand out (kanvas-skia's
     * authoritative pixel store is the colour-typed array, not a byte
     * buffer).
     *
     * Callers that need byte-level access on every bitmap should use
     * [getPixel] / [getPixelF16] or build their own [SkPixmap] view over
     * the typed array.
     */
    public fun peekPixels(pixmap: SkPixmap): Boolean {
        val ref = _pixelRef ?: return false
        val info = SkImageInfo.Make(width, height, colorType,
            colorSpace = colorSpace)
        pixmap.reset(info, ref.pixels(), ref.rowBytes())
        return true
    }

    public companion object {
        public fun Make(w: Int, h: Int): SkBitmap = SkBitmap(w, h)
        public fun Make(w: Int, h: Int, colorSpace: SkColorSpace): SkBitmap =
            SkBitmap(w, h, colorSpace)
        public fun Make(w: Int, h: Int, colorSpace: SkColorSpace, colorType: SkColorType): SkBitmap =
            SkBitmap(w, h, colorSpace, colorType)

        /**
         * Mirrors Skia's `SkBitmap::allocPixels(const SkImageInfo&)`.
         *
         * Upstream `allocPixels` mutates an empty `SkBitmap` in place. Our
         * Kotlin `SkBitmap` binds its backing arrays at construction, so we
         * surface the same idiom as a factory : `SkBitmap.allocPixels(info)`
         * returns a fresh bitmap whose storage matches [info]. Use this when
         * porting C++ code that follows the `SkBitmap bm; bm.allocPixels(info);`
         * pattern (e.g. `gm/colorfilteralpha8.cpp`).
         */
        public fun allocPixels(info: SkImageInfo): SkBitmap =
            SkBitmap(info.width, info.height, info.colorSpace, info.colorType)

        // ─── F16 (half-float) helpers (R-suivi.17) ──────────────────
        //
        // IEEE 754 binary16 ↔ binary32 conversion. Java 21 ships
        // `Float.float16ToFloat` / `floatToFloat16` ; kanvas-skia targets
        // JDK 17, so we implement the bit-level dance here.
        //
        // Reference : the Skia / Halide / SkHalf implementation
        // (`include/private/SkHalf.h`). We follow the same fast-path
        // structure :
        //  - sign bit lifted to bit 31
        //  - exponent + mantissa shifted left 13 bits and re-biased by
        //    `(127 - 15) << 23` = `0x38000000`
        //  - subnormals (exp == 0, mantissa != 0) are renormalised so
        //    they survive the round-trip
        //  - infinities / NaN (exp == 0x1F) get the `0xFF` exponent so
        //    they round-trip as ±∞ / NaN.

        /**
         * Decode an IEEE 754 binary16 (half-float) bit pattern (held in
         * a `Short`) to a 32-bit `Float`. Subnormal halves are
         * renormalised ; ±∞ and NaN round-trip. Mirrors Skia's
         * `SkHalfToFloat` (`SkHalf.h`) — bit-exact for normals, matches
         * upstream's "force exp=0xFF for inf/NaN" branch.
         */
        internal fun halfToFloat(h: Short): Float {
            val bits = h.toInt() and 0xFFFF
            val sign = (bits and 0x8000) shl 16
            val exp = (bits ushr 10) and 0x1F
            val mant = bits and 0x3FF
            return when (exp) {
                0 -> {
                    // Zero or subnormal. Zero → ±0.0f ; subnormal →
                    // renormalise by stepping the exponent up while
                    // the leading mantissa bit is zero.
                    if (mant == 0) {
                        java.lang.Float.intBitsToFloat(sign)
                    } else {
                        var m = mant
                        var e = 1
                        while ((m and 0x400) == 0) {
                            m = m shl 1
                            e--
                        }
                        m = m and 0x3FF
                        // Re-bias the exponent : f32_exp = h_exp + (127 - 15).
                        val f32Exp = (e + (127 - 15)) shl 23
                        val f32Mant = m shl 13
                        java.lang.Float.intBitsToFloat(sign or f32Exp or f32Mant)
                    }
                }
                0x1F -> {
                    // Infinity (mant == 0) or NaN. Use the maximum
                    // exponent ; preserve the mantissa so NaN payloads
                    // survive (zero mantissa → ±∞).
                    val f32Exp = 0xFF shl 23
                    val f32Mant = mant shl 13
                    java.lang.Float.intBitsToFloat(sign or f32Exp or f32Mant)
                }
                else -> {
                    // Normal — re-bias exponent by `127 - 15` then shift
                    // the mantissa to fill the 23 bits of the binary32
                    // mantissa field.
                    val f32Exp = (exp + (127 - 15)) shl 23
                    val f32Mant = mant shl 13
                    java.lang.Float.intBitsToFloat(sign or f32Exp or f32Mant)
                }
            }
        }

        /**
         * Encode a 32-bit `Float` as an IEEE 754 binary16 bit pattern
         * (held in a `Short`). Inverse of [halfToFloat] — values whose
         * magnitude exceeds the half-float range saturate to ±∞ ;
         * subnormals at the binary32 → binary16 boundary flush to ±0.
         * Round-to-nearest-even (mantissa rounding via `+ 0x1000`).
         */
        internal fun floatToHalf(f: Float): Short {
            val bits = java.lang.Float.floatToRawIntBits(f)
            val sign = (bits ushr 16) and 0x8000
            val exp32 = (bits ushr 23) and 0xFF
            val mant32 = bits and 0x7FFFFF
            return when {
                // NaN or infinity.
                exp32 == 0xFF -> {
                    val mant16 = if (mant32 != 0) ((mant32 ushr 13) or 0x200) else 0
                    (sign or 0x7C00 or mant16).toShort()
                }
                // Overflow → saturate to ±inf. Half max exponent = 30 in
                // unbiased terms (15 + 15) ; in biased binary32 that's
                // `15 + 127 = 142` (i.e. exp32 - 127 > 15  →  exp32 > 142).
                exp32 > 142 -> (sign or 0x7C00).toShort()
                // Underflow → flush to ±0 (binary32 < smallest half
                // subnormal ≈ 2^-24 → exp32 < (127 - 24) = 103).
                exp32 < 103 -> sign.toShort()
                // Subnormal half : exp32 in [103, 113).
                exp32 < 113 -> {
                    // Re-emit as a subnormal half. The implicit 1 bit of
                    // the binary32 mantissa becomes part of the half
                    // mantissa ; we then right-shift by `1 + (113 - exp32)`
                    // bits and round-half-up.
                    val mantWithImplicit = mant32 or 0x800000
                    val shift = 14 + (113 - exp32)
                    val rounded = (mantWithImplicit + (1 shl (shift - 1))) ushr shift
                    (sign or rounded).toShort()
                }
                // Normal half — re-bias exponent by `127 - 15` and round
                // the mantissa to 10 bits. Round-half-up (`+ 0x1000`) ; the
                // round-half-to-even refinement is omitted (matches Skia's
                // `_mm_cvtps_ph` precision in the SSE path).
                else -> {
                    val expHalf = (exp32 - (127 - 15)) shl 10
                    val mantHalf = (mant32 + 0x1000) ushr 13
                    // If the rounding bumped the mantissa past 0x3FF we
                    // need to roll into the exponent.
                    if (mantHalf > 0x3FF) {
                        // Mantissa overflow lifts exponent by one.
                        (sign or (expHalf + (1 shl 10)) or (mantHalf and 0x3FF)).toShort()
                    } else {
                        (sign or expHalf or mantHalf).toShort()
                    }
                }
            }
        }

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
        // ─── RGB_565 helpers (Phase R1-C) ────────────────────────────

        /**
         * Pack non-premultiplied float channels `(r, g, b) ∈ [0, 1]` into
         * a 16-bit `Short` with bit layout `[R:15..11 G:10..5 B:4..0]`.
         * Round-to-nearest quantisation per channel ; inputs are clamped
         * to `[0, 1]` first. Alpha is implicit opaque (`SkAlphaType.kOpaque`).
         */
        internal fun packRGB565(r: Float, g: Float, b: Float): Short {
            val r5 = (r.coerceIn(0f, 1f) * 31f + 0.5f).toInt()
            val g6 = (g.coerceIn(0f, 1f) * 63f + 0.5f).toInt()
            val b5 = (b.coerceIn(0f, 1f) * 31f + 0.5f).toInt()
            return ((r5 shl 11) or (g6 shl 5) or b5).toShort()
        }

        /**
         * Unpack a 16-bit RGB_565 short to an opaque 8-bit `SkColor`.
         * Widens 5 → 8 bits via `(v << 3) | (v >> 2)` (R, B) and 6 → 8
         * via `(v << 2) | (v >> 4)` (G) — Skia's standard `SkColor16to32`
         * (`src/core/SkBitmap.cpp`).
         */
        internal fun unpackRGB565(packed: Short): SkColor {
            val p = packed.toInt() and 0xFFFF
            val r5 = (p shr 11) and 0x1F
            val g6 = (p shr 5) and 0x3F
            val b5 = p and 0x1F
            val r8 = (r5 shl 3) or (r5 shr 2)
            val g8 = (g6 shl 2) or (g6 shr 4)
            val b8 = (b5 shl 3) or (b5 shr 2)
            return SkColorSetARGB(0xFF, r8, g8, b8)
        }

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
