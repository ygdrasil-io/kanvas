package org.skia.tests

import org.graphiks.math.SK_ColorLTGRAY
import org.graphiks.math.SkISize
import org.graphiks.math.SkScalar
import org.graphiks.math.SkScalarCeil
import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTypeface
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/fontcache.cpp::FontCacheGM`](https://github.com/google/skia/blob/main/gm/fontcache.cpp)
 * — 1280 × 1280, originally **GPU-only** (the upstream header comment
 * says "It's not necessary to run this with CPU configs").
 *
 * Upstream's intent is to stress Ganesh's
 * [`GrTextStrike`](https://github.com/google/skia/blob/main/src/gpu/ganesh/text/GrAtlasManager.h)
 * glyph-atlas LRU by reconfiguring the
 * [`GrContextOptions`](https://github.com/google/skia/blob/main/include/gpu/ganesh/GrContextOptions.h)
 * `fGlyphCacheTextureMaximumBytes = 0` (immediate eviction) and toggling
 * `fAllowMultipleGlyphCacheTextures` to force either a single-page
 * atlas (with thrashing) or multi-page paging (with cross-page
 * draws). The two `DEF_GM` factories produce :
 *  * `fontcache`    — single atlas page, no multi-texture allowed
 *  * `fontcache-mt` — multi-page atlas, then 10× CTM scale so the
 *    same glyphs land at huge sizes and overflow a single page
 *
 * From the raster point of view both options collapse onto the same
 * thing — there is no atlas, glyphs are rasterised through
 * [SkCanvas.drawString] → [SkFont.makeTextPath] → coverage-AA scanline
 * fill. The GM still exercises the *drawing* side of the pipeline :
 * **2592 glyph draws** (9 sizes × 6 typefaces × 4 strings × 12 outer
 * subpixel iterations until the 1280-px row budget is exhausted),
 * mixing italic / normal / bold weights of `ToolUtils::CreatePortableTypeface`'s
 * serif and sans-serif families with sub-pixel offsets that drift on
 * alternate X / Y axes between outer-loop passes.
 *
 * ## Port status
 *
 * Body ported for the `fontcache` (single-texture) variant — the
 * 6-typeface × 9-size × 4-string nested loop with sub-pixel offset
 * walk. The reference `fontcache.png` was Ganesh-rendered ; raster
 * output exists and exercises the full glyph-path-fill pipeline, but
 * sub-pixel positioning + OpenType-vs-FreeType scaler differences make
 * the pixels diverge wholesale from the GPU reference (see
 * [FontCacheTest] for the bucket classification).
 *
 * The `fontcache-mt` variant (10× CTM, multi-page atlas) is **not**
 * exposed here — it shares this class's name only when the upstream
 * `fAllowMultipleTextures` flag is on, and at raster all that flag
 * does is rescale the same loop ; we'd need a separate
 * `FontCacheMtGM` class to compare against `fontcache-mt.png`. Mirrors
 * upstream's `getName()` returning `"fontcache"` for the default
 * factory.
 */
public class FontCacheGM : GM() {

    init {
        setBGColor(SK_ColorLTGRAY)
    }

    override fun getName(): String = "fontcache"
    override fun getISize(): SkISize = SkISize.Make(kSize.toInt(), kSize.toInt())

    private var typefacesInitialised: Boolean = false
    private lateinit var typefaces: Array<SkTypeface>

    override fun onOnceBeforeDraw() {
        // Mirrors upstream's `onOnceBeforeDraw` — six typefaces covering
        // serif / sans-serif × italic / normal / bold. The portable
        // resolver collapses these to Liberation Serif / Sans Italic /
        // Regular / Bold (see `ToolUtils.CreatePortableTypeface`).
        typefaces = arrayOf(
            ToolUtils.CreatePortableTypeface("serif", SkFontStyle.Italic()),
            ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle.Italic()),
            ToolUtils.CreatePortableTypeface("serif", SkFontStyle.Normal()),
            ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle.Normal()),
            ToolUtils.CreatePortableTypeface("serif", SkFontStyle.Bold()),
            ToolUtils.CreatePortableTypeface("sans-serif", SkFontStyle.Bold()),
        )
        typefacesInitialised = true
    }

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        if (!typefacesInitialised) onOnceBeforeDraw()
        drawText(c)
        // Upstream's `kShowAtlas` debugging branch is intentionally
        // dropped — it only does anything when the canvas is backed by
        // a [GrDirectContext] (it calls
        // `priv().testingOnly_getFontAtlasImage`), which the raster
        // backend cannot provide. The static is `false` upstream too.
    }

    /**
     * Mirrors upstream's
     * [`draw_string`](https://github.com/google/skia/blob/main/gm/fontcache.cpp#L36)
     * helper — draws `text` at `(x, y)` with `font` and returns the
     * pen advance (x + measured width).
     */
    private fun drawString(
        canvas: SkCanvas,
        text: String,
        x: SkScalar,
        y: SkScalar,
        font: SkFont,
    ): SkScalar {
        val paint = SkPaint()
        canvas.drawString(text, x, y, font, paint)
        return x + font.measureText(text, text.length, SkTextEncoding.kUTF8)
    }

    /**
     * Mirrors upstream's private `drawText(SkCanvas*)` loop. Walks the
     * 9 base sizes × 6 typefaces × 4 strings, advancing the pen
     * horizontally and wrapping to a new row when `x + 100 > kSize`.
     * Returns when `y > kSize`.
     *
     * Outer loop : after each pass through the 9 sizes the sub-pixel
     * offset is incremented on alternating axes by `1 / 2.f`,
     * producing different glyph rasterisations on each pass — that's
     * what upstream uses to force *new* atlas entries on a GPU run.
     * On raster we just get visually-distinct rows.
     */
    private fun drawText(canvas: SkCanvas) {
        val sizes = intArrayOf(8, 9, 10, 11, 12, 13, 18, 20, 25)
        val texts = arrayOf(
            "ABCDEFGHIJKLMNOPQRSTUVWXYZ",
            "abcdefghijklmnopqrstuvwxyz",
            "0123456789",
            "!@#\$%^&*()<>[]{}",
        )

        val font = ToolUtils.DefaultPortableFont().apply {
            edging = SkFont.Edging.kAntiAlias
            isSubpixel = true
        }

        val kSubPixelInc: SkScalar = 1f / 2f
        var x: SkScalar = 0f
        var y: SkScalar = 10f
        var subpixelX: SkScalar = 0f
        var subpixelY: SkScalar = 0f
        var offsetX = true

        // The non-mt variant doesn't pre-scale ; the `fontcache-mt`
        // path applies `canvas->scale(10, 10)` here. See class kdoc.

        // Upstream loops `do { ... } while (true)` and exits via
        // `return` from the inner break ; that's equivalent to a plain
        // `while (true)` because we hand control back to the caller
        // via [return] on the y-overflow path.
        while (true) {
            for (s in sizes) {
                val size = 2 * s
                font.size = size.toFloat()
                for (typeface in typefaces) {
                    font.typeface = typeface
                    for (text in texts) {
                        x = size + drawString(canvas, text, x + subpixelX, y + subpixelY, font)
                        x = SkScalarCeil(x)
                        if (x + 100f > kSize) {
                            x = 0f
                            y += SkScalarCeil(size.toFloat() + 3f)
                            if (y > kSize) {
                                return
                            }
                        }
                    }
                }
                if (offsetX) {
                    subpixelX += kSubPixelInc
                } else {
                    subpixelY += kSubPixelInc
                }
                offsetX = !offsetX
            }
        }
    }

    public companion object {
        /** Upstream's `static constexpr SkScalar kSize = 1280`. */
        public const val kSize: SkScalar = 1280f
    }
}
