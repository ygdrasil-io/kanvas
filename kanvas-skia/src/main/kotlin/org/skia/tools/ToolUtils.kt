package org.skia.tools

import org.skia.codec.SkCodec
import org.skia.core.SkCanvas
import org.skia.foundation.SkBitmap
import org.skia.foundation.SkBlendMode
import org.skia.foundation.SkColor
import org.skia.foundation.SkColorGetB
import org.skia.foundation.SkColorGetG
import org.skia.foundation.SkColorGetR
import org.skia.foundation.SkColorSetARGB
import org.skia.foundation.SkData
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkImage
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTileMode
import org.skia.foundation.SkTypeface
import org.skia.foundation.awt.LiberationFontMgr
import org.skia.math.SkScalar
import kotlin.math.floor

/**
 * Bit-compatible ports of selected helpers from upstream Skia's
 * `tools/ToolUtils.{h,cpp}` and `include/core/SkColor.h` — the GMs that
 * we mirror call these directly to seed deterministic palettes that round-
 * trip through 16-bit RGB565 quantisation.
 *
 * Out of scope (no callers yet): `make_isize`, `create_checkerboard_shader`,
 * surface helpers, the font portability shims.
 */
public object ToolUtils {

    /**
     * Mirrors Skia's `SkHSVToColor(alpha, hsv)` (`src/core/SkColor.cpp`).
     * `hsv[0]` is hue in degrees `[0, 360)`, `hsv[1]` is saturation, `hsv[2]`
     * is value, both clamped to `[0, 1]`. Returns an opaque-by-default ARGB
     * `SkColor` with the requested `alpha` byte.
     *
     * Bit-compatible with upstream because:
     *  - the `v` byte is computed via [skScalarRoundToInt] (round-half-away-
     *    from-zero, exactly Skia's `SkScalarRoundToInt`);
     *  - the `p` / `q` / `t` intermediates use the same rounding;
     *  - the `(unsigned)hx` floor matches `SkScalarFloorToScalar`.
     */
    public fun skHSVToColor(hsv: FloatArray, alpha: Int = 0xFF): SkColor {
        require(hsv.size >= 3) { "skHSVToColor expects [h, s, v] — got ${hsv.size} elements" }
        val s = hsv[1].coerceIn(0f, 1f)
        val v = hsv[2].coerceIn(0f, 1f)
        val vByte = skScalarRoundToInt(v * 255f)

        if (kotlin.math.abs(s) < 1f / 4096f) {        // SkScalarNearlyZero
            return SkColorSetARGB(alpha, vByte, vByte, vByte)
        }
        val h = hsv[0]
        val hx = if (h < 0f || h >= 360f) 0f else h / 60f
        val w = floor(hx).toInt()                     // (unsigned)floor(hx) is in 0..5
        val f = hx - w

        val p = skScalarRoundToInt((1f - s) * v * 255f)
        val q = skScalarRoundToInt((1f - s * f) * v * 255f)
        val t = skScalarRoundToInt((1f - s * (1f - f)) * v * 255f)

        val (r, g, b) = when (w) {
            0 -> Triple(vByte, t, p)
            1 -> Triple(q, vByte, p)
            2 -> Triple(p, vByte, t)
            3 -> Triple(p, q, vByte)
            4 -> Triple(t, p, vByte)
            else -> Triple(vByte, p, q)
        }
        return SkColorSetARGB(alpha, r, g, b)
    }

    /**
     * Mirrors `ToolUtils::color_to_565` (`tools/ToolUtils.cpp`): round-trip
     * an opaque SkColor through 16-bit RGB565 quantisation. Used by GMs
     * (notably `manycircles`, `manyrrects`, `ovals`, `roundrects`) to make
     * their random colours match the 565 backbuffer the references were
     * captured from.
     *
     * For `alpha = 0xFF` inputs (the only ones the GMs use), `SkPreMultiplyColor`
     * is the identity, so we skip that step. For each channel, the round-trip
     * `(c >> n) << n | (c >> n2)` replicates the high bits of the surviving
     * 5- or 6-bit field into the low bits — the same operation Skia's
     * `SkR16ToR32` / `SkG16ToG32` / `SkB16ToB32` macros perform.
     */
    public fun colorTo565(color: SkColor): SkColor {
        val r = SkColorGetR(color)
        val g = SkColorGetG(color)
        val b = SkColorGetB(color)
        // 5-6-5 quantise then re-expand with top-bit replication.
        val r5 = r ushr 3
        val g6 = g ushr 2
        val b5 = b ushr 3
        val r8 = ((r5 shl 3) or (r5 ushr 2)) and 0xFF
        val g8 = ((g6 shl 2) or (g6 ushr 4)) and 0xFF
        val b8 = ((b5 shl 3) or (b5 ushr 2)) and 0xFF
        return SkColorSetARGB(0xFF, r8, g8, b8)
    }

    /**
     * Mirrors Skia's `SkScalarRoundToInt`: round-half-away-from-zero. We
     * can't use Kotlin's `Math.round` directly because it lives on `Double`
     * and would drag in a single-precision → double promotion that isn't
     * bit-equivalent to upstream's `(int)floorf(x + 0.5f)`.
     */
    private fun skScalarRoundToInt(x: Float): Int =
        floor(x + 0.5f).toInt()

    /**
     * Mirrors `ToolUtils::CreatePortableTypeface(name, style)`
     * (`tools/fonts/FontToolUtils.cpp:203`).
     *
     * Resolves a portable typeface by family name (substring match
     * upstream — `"ono"` → Mono, `"ans"` → Sans, `"erif"` → Serif —
     * with default Sans for `null` or unrecognised names) and style
     * (collapsed to one of Liberation's 4 styles per family).
     *
     * Identical resolution rules to upstream's
     * `MakePortableFontMgr()->legacyMakeTypeface(name, style)` for the
     * portable family set (Mono / Sans / Serif). Aliases like "Arial",
     * "Courier New", "Times" map to the closest Liberation family
     * (Sans / Mono / Serif respectively).
     */
    public fun CreatePortableTypeface(name: String?, style: SkFontStyle): SkTypeface =
        LiberationFontMgr.matchFamilyStyle(name, style)

    /**
     * Mirrors `ToolUtils::DefaultPortableTypeface()`
     * (`tools/fonts/FontToolUtils.cpp`).
     *
     * Upstream returns the typeface at index `gDefaultFontIndex = 4` in
     * `tools/fonts/test_font_index.inc` — that's **Liberation Sans Regular**
     * (the `.inc` was generated from the upstream Liberation TTF by
     * `tools/fonts/create_test_font.cpp`).
     *
     * **T4 (option A — currently active)**: we ship the Liberation TTFs
     * as classpath resources under `kanvas-skia/src/main/resources/fonts/
     * liberation/` and load them through AWT (`Font.createFont(TRUETYPE_FONT,
     * …)`), routed via [LiberationFontMgr]. Glyph outlines are identical
     * to upstream; pixel-level fidelity still drifts on AA edges because
     * AWT's scaler/hinting differs from FreeType (~1-2 ulp).
     *
     * **Tx future (option B — deferred)**: port the `test_font_*.inc` data
     * to Kotlin and build an `SkTestTypeface` that iterates the embedded
     * points/verbs directly, bypassing AWT for outline lookup. That would
     * make outlines bit-exact upstream. Triggered by `bigtext`-family GMs.
     * See `archives/MIGRATION_PLAN_TEXT.md` §T4 for details.
     */
    public fun DefaultPortableTypeface(): SkTypeface = LiberationFontMgr.getDefault()

    /**
     * Mirrors `ToolUtils::DefaultPortableFont()`. Convenience wrapper —
     * a [SkFont] using [DefaultPortableTypeface] at the requested size
     * (default 12pt, matching upstream). Edging defaults to `kAntiAlias`.
     */
    public fun DefaultPortableFont(size: SkScalar = 12f): SkFont =
        SkFont(DefaultPortableTypeface(), size)

    /**
     * Mirrors `ToolUtils::add_to_text_blob(builder, text, font, x, y)`
     * (`tools/ToolUtils.cpp` line ~1080). Resolves [text] (UTF-8) into
     * glyph IDs via [SkFont.unicharsToGlyphs] and appends a single
     * `allocRun`-style run to [builder] anchored at `(x, y)`. The
     * font's per-glyph advance widths drive intra-run positioning at
     * draw time.
     *
     * No-op when [text] is empty (matches upstream's `count < 1` guard).
     */
    public fun addToTextBlob(
        builder: org.skia.foundation.SkTextBlobBuilder,
        text: String,
        font: SkFont,
        x: SkScalar,
        y: SkScalar,
    ) {
        if (text.isEmpty()) return
        val codepoints = text.codePoints().toArray()
        val n = codepoints.size
        if (n == 0) return
        val rec = builder.allocRun(font, n, x, y)
        val glyphsShort = ShortArray(n)
        font.unicharsToGlyphs(codepoints, n, glyphsShort)
        for (i in 0 until n) {
            rec.glyphs[i] = glyphsShort[i].toInt() and 0xFFFF
        }
    }

    // ─── Resource loading (mirrors tools/Resources.{h,cpp} + tools/DecodeUtils.h) ──
    //
    // Upstream `Resources.cpp` reads from a filesystem path baked at build
    // time (`-Dresources_dir=...`) or via `SetResourcePath`. The Kotlin port
    // pulls bytes from the JVM classpath instead — every GM-test resource
    // lives under `src/test/resources/` and is published to the test
    // classloader, so a relative path like `"images/mandrill_512.png"`
    // resolves identically across local runs and CI without any
    // configuration plumbing.

    /**
     * Mirrors `sk_sp<SkData> GetResourceAsData(const char* resource)`
     * (`tools/Resources.cpp:42`). Reads the entire classpath resource at
     * [path] into a freshly-allocated [SkData] via [SkData.MakeWithCopy].
     * Returns `null` when no such resource is on the classpath — matches
     * upstream's `nullptr` return on `SkStream::MakeFromFile` failure.
     *
     * Resource paths are relative to the classpath root, e.g. for a file
     * at `kanvas/src/test/resources/images/mandrill_512.png` (shared with
     * `:kanvas-skia` via the build script's `resources.srcDir(...)`) the
     * caller passes `"images/mandrill_512.png"`.
     */
    public fun GetResourceAsData(path: String): SkData? {
        val stream = ToolUtils::class.java.classLoader.getResourceAsStream(path)
            ?: return null
        val bytes = stream.use { it.readBytes() }
        return SkData.MakeWithCopy(bytes)
    }

    /**
     * Mirrors `sk_sp<SkImage> ToolUtils::GetResourceAsImage(const char*)`
     * (`tools/DecodeUtils.h:31`). Loads the encoded bytes via
     * [GetResourceAsData], dispatches them through [SkCodec.MakeFromData],
     * and returns the decoded [SkImage]. Returns `null` if the resource
     * is missing, no codec recognises its container format, or the decode
     * fails — mirrors upstream's `nullptr` short-circuits.
     *
     * The Kotlin port collapses upstream's `SkImages::DeferredFromEncodedData`
     * → lazy decode pipeline into an eager decode through [SkCodec.getImage].
     * `:kanvas-skia` has no GPU upload path that would benefit from lazy
     * decoding, and eager-decode keeps every test deterministic regardless
     * of how many times the caller samples the image.
     */
    public fun GetResourceAsImage(path: String): SkImage? {
        val data = GetResourceAsData(path) ?: return null
        val codec = SkCodec.MakeFromData(data.toByteArray()) ?: return null
        val (bitmap, result) = codec.getImage()
        if (result != SkCodec.Result.kSuccess || bitmap == null) return null
        return bitmap.asImage()
    }

    /**
     * Mirrors `bool ToolUtils::GetResourceAsBitmap(const char* resource,
     * SkBitmap* dst)` (`tools/DecodeUtils.h:23`). Decodes the resource
     * into a caller-provided [dst] bitmap whose [SkBitmap.width] /
     * [SkBitmap.height] / [SkBitmap.colorType] must match the codec's
     * default [org.skia.foundation.SkImageInfo]. Returns `true` on
     * success ; `false` if the resource is missing, no codec matched,
     * or the decode failed.
     *
     * Upstream's signature takes a `SkBitmap*` that the function
     * `allocPixels`-resizes on success ; our [SkBitmap] is constructed
     * with fixed dimensions and the caller is responsible for sizing
     * [dst] to the codec's [SkCodec.getInfo] (typically queried via
     * [GetResourceAsData] + [SkCodec.MakeFromData] for the dimensions
     * before allocating the bitmap).
     */
    public fun GetResourceAsBitmap(path: String, dst: SkBitmap): Boolean {
        val data = GetResourceAsData(path) ?: return false
        val codec = SkCodec.MakeFromData(data.toByteArray()) ?: return false
        return codec.getPixels(dst) == SkCodec.Result.kSuccess
    }

    /**
     * Mirrors `sk_sp<SkImage> ToolUtils::MakeTextureImage(SkCanvas*,
     * sk_sp<SkImage>)` (`tools/GpuToolUtils.h:32`). Upstream uploads
     * [image] to the [canvas]'s GPU recording context when one is
     * available (Ganesh or Graphite), falling back to the original
     * raster image otherwise.
     *
     * `:kanvas-skia` is raster-only — there is no GPU backend to upload
     * to — so this helper short-circuits to the identity. GMs that call
     * `MakeTextureImage` to opt into GPU upload still render correctly,
     * just through the raster path. Returns `null` iff [image] is `null`,
     * matching upstream.
     */
    public fun MakeTextureImage(canvas: SkCanvas?, image: SkImage?): SkImage? = image

    /**
     * Mirrors `ToolUtils::draw_checkerboard(SkCanvas*, SkColor, SkColor,
     * int)` (`tools/ToolUtils.cpp:176`). Paints a [size]-pixel checker
     * pattern alternating between [c1] and [c2] across the canvas's
     * current clip, using [SkBlendMode.kSrc] so the existing pixels are
     * replaced (not blended).
     *
     * Upstream constructs a 2×[size] tiled bitmap shader via
     * `create_checkerboard_shader` and `drawPaint`s it ; the Kotlin
     * port follows the same recipe — a `2*size × 2*size` 8888 bitmap
     * with `(c1, c2)` in opposite quadrants, wrapped in a [SkTileMode.kRepeat]
     * shader. The shader path keeps the cost O(1) regardless of clip
     * size, matches upstream visually, and re-uses the already-tested
     * raster bitmap-shader pipeline.
     *
     * `size` must be positive — a zero or negative tile would produce
     * an empty shader and upstream's `create_checkerboard_shader` would
     * crash on the underlying `allocPixels(2*size, 2*size)`. We mirror
     * the implicit contract.
     */
    public fun draw_checkerboard(canvas: SkCanvas, c1: SkColor, c2: SkColor, size: Int) {
        require(size > 0) { "draw_checkerboard size must be > 0 ; got $size" }
        val tile = SkBitmap(2 * size, 2 * size)
        tile.eraseColor(c1)
        // Fill the off-diagonal quadrants with c2 (matches upstream's
        // `eraseArea(LTRB(0, 0, size, size), c2)` +
        // `eraseArea(LTRB(size, size, 2*size, 2*size), c2)`).
        for (y in 0 until size) {
            for (x in 0 until size) {
                tile.setPixel(x, y, c2)
                tile.setPixel(x + size, y + size, c2)
            }
        }
        val paint = SkPaint()
        paint.shader = tile.makeShader(SkTileMode.kRepeat, SkTileMode.kRepeat)
        paint.blendMode = SkBlendMode.kSrc
        canvas.drawPaint(paint)
    }
}
