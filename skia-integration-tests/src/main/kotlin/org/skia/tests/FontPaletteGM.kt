package org.skia.tests

import org.graphiks.math.SK_ColorGRAY
import org.graphiks.math.SK_ColorWHITE
import org.graphiks.math.SkISize
import org.skia.core.SkCanvas
import org.skia.foundation.SkFont
import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTypeface
import org.skia.tools.ToolUtils

/**
 * Port of Skia's
 * [`gm/palette.cpp::FontPaletteGM`](https://github.com/google/skia/blob/main/gm/palette.cpp).
 *
 * Exercises COLR v1 colour-glyph rendering under
 * [SkFontArguments.Palette] palette selection / palette overrides.
 * Two side-by-side draws of the same code-point sequence
 * (`U+F0E00`, `U+F0E01` — the "colour circles" test glyphs from
 * Google Fonts' `test_glyphs-glyf_colr_1.ttf`) :
 *
 *  - left column : the **default palette** (typeface as-loaded),
 *  - right column : the typeface re-instantiated with the variant's
 *    [SkFontArguments.Palette] override applied via two routes —
 *    once through `SkFontMgr::makeFromStream(stream, args)` (load-time
 *    palette resolution) and once through
 *    [SkTypeface.makeClone] (post-load palette resolution).
 *    Both routes should produce identical pixels — the GM cross-checks
 *    that contract.
 *
 * Upstream declares 5 `DEF_GM` instances, mapping a typeface palette /
 * override-list configuration onto a `font_palette_<name>` GM name :
 *
 *  - `default` : no overrides, index 0,
 *  - `light`   : palette index 2 (no per-entry overrides),
 *  - `dark`    : palette index 1 (no per-entry overrides),
 *  - `one`     : 1 per-entry override (replace palette entry 2 with cyan),
 *  - `all`     : 16 per-entry overrides forming a purple→pink gradient,
 *    including a repeat (entry 6, last-wins) and three out-of-bounds
 *    entries (indices 12 / 13 / -1, which should be silently ignored by
 *    a compliant `SkFontArguments::Palette` consumer).
 *
 * ## Port status — blocked by upstream fixtures / GM wiring
 *
 *  1. **`STUB.FIXTURE`** — `fonts/test_glyphs-glyf_colr_1.ttf` is not
 *     shipped under `kanvas-legacy/src/test/resources/fonts/`. Even with
 *     the fixture, this GM needs accepted references for the pure Kotlin
 *     COLRv1 renderer.
 *  2. **Load-time palette route** — the post-load
 *     [SkTypeface.makeClone] route is covered by unit fixtures; the
 *     upstream stream-load-with-args route still needs GM integration.
 *  3. **GM references** — reactivation is tracked in #1020 so this
 *     test does not imply FreeType/HarfBuzz/Fontations are mandatory.
 *
 * The body below is **the real upstream pipeline** — apply the palette
 * to a fresh [SkFontArguments], take both the "stream-load" and
 * "post-load clone" routes, iterate the two text-size rows
 * (matching upstream's `for (auto& typeface : {fTypefaceFromStream,
 * fTypefaceCloned})` loop), and emit each codepoint pair through
 * [SkCanvas.drawSimpleText] in [SkTextEncoding.kUTF32]. Today both
 * typeface resolutions return `null` and the GM short-circuits to a
 * white canvas (matching upstream's `errorMsg = "Did not recognize
 * COLR v1 test font format."` + `DrawResult::kSkip` branch).
 * [FontPaletteTest] remains disabled until #1020 supplies accepted
 * fixtures and references for the pure Kotlin renderer.
 *
 * ## Constructor parameters
 *
 * Mirrors upstream's
 * `FontPaletteGM(const char* test_name, const SkFontArguments::Palette& paletteOverride)`
 * ctor. The 5 named test variants are exposed through [Companion]
 * factories ([defaultPalette], [light], [dark], [one], [all]) ; the
 * no-arg constructor defaults to the `default` variant (matching
 * upstream's first `DEF_GM` line).
 */
public class FontPaletteGM(
    private val testName: String,
    private val paletteOverride: SkFontArguments.Palette,
) : GM() {

    /** No-arg ctor — defaults to the `default` variant (no overrides). */
    public constructor() : this("default", SkFontArguments.Palette())

    private var typefaceDefault: SkTypeface? = null
    private var typefaceFromStream: SkTypeface? = null
    private var typefaceCloned: SkTypeface? = null

    override fun onOnceBeforeDraw() {
        // Mirrors upstream's onOnceBeforeDraw — three typeface handles
        // resolved through two routes :
        //   1) default-args resource load (left column),
        //   2) clone-with-args of the default load (right column, route A),
        //   3) palette-args resource load (right column, route B).
        // The two right-column handles should agree pixel-for-pixel once
        // #1020 wires the GM fixture/reference path.
        val paletteArgs = SkFontArguments().setPalette(paletteOverride)

        typefaceDefault = makeTypefaceFromResource(K_COLR_CPAL_TEST_FONT_PATH, SkFontArguments())
        typefaceCloned = typefaceDefault?.makeClone(paletteArgs)
        typefaceFromStream = makeTypefaceFromResource(K_COLR_CPAL_TEST_FONT_PATH, paletteArgs)
    }

    override fun getName(): String = "font_palette_$testName"

    override fun getISize(): SkISize = SkISize.Make(1000, 400)

    override fun onDraw(canvas: SkCanvas?) {
        val c = canvas ?: return
        c.drawColor(SK_ColorWHITE)
        val paint = SkPaint()

        c.translate(10f, 20f)

        // Mirror upstream's `kSkip` branch. Until #1020 wires the GM
        // fixture/reference path, the two palette-aware handles are null
        // and there is nothing to draw beyond the white background.
        if (typefaceCloned == null || typefaceFromStream == null) {
            // errorMsg = "Did not recognize COLR v1 test font format."
            return
        }

        val metrics = SkFontMetrics()
        var y = 0f
        val textSize = 200f
        for (typeface in listOf(typefaceFromStream, typefaceCloned)) {
            val defaultFont = SkFont(typefaceDefault ?: continue)
            val paletteFont = SkFont(typeface ?: continue)
            defaultFont.size = textSize
            paletteFont.size = textSize

            defaultFont.getMetrics(metrics)
            y += -metrics.fAscent
            // Set a recognizable foreground color which is not to be overridden.
            paint.color = SK_ColorGRAY
            // Draw the default palette on the left, for COLRv0 and COLRv1.
            drawColorCircles(c, x = 0f, y = y, font = defaultFont, paint = paint)
            // Draw the overridden palette on the right.
            drawColorCircles(c, x = 440f, y = y, font = paletteFont, paint = paint)
            y += metrics.fDescent + metrics.fLeading
        }
    }

    /**
     * Renders the two-codepoint `colorCirclesPalette` sequence at
     * `(x, y)` through [SkCanvas.drawSimpleText] in UTF-32 encoding —
     * matches upstream's pair of `canvas->drawSimpleText(...)` calls.
     */
    private fun drawColorCircles(
        canvas: SkCanvas,
        x: Float,
        y: Float,
        font: SkFont,
        paint: SkPaint,
    ) {
        // codepoints { 0xf0e00, 0xf0e01 } — the "colour circles" pair
        // exposed by Google Fonts' COLR v1 synthetic test font.
        val text = String(Character.toChars(COLOR_CIRCLES_PALETTE[0])) +
            String(Character.toChars(COLOR_CIRCLES_PALETTE[1]))
        // Upstream passes `sizeof(uint32_t) * count` as the byte length
        // (= 8 bytes for the 2-codepoint pair). The drawSimpleText
        // shim in :kanvas-skia treats `byteLength` as an upper bound on
        // the String-side substring (see SkCanvas.kt line ~2188) — pass
        // the codepoint-count to keep the contract sane on the kUTF32
        // branch.
        canvas.drawSimpleText(
            text = text,
            byteLength = text.length,
            encoding = SkTextEncoding.kUTF32,
            x = x,
            y = y,
            font = font,
            paint = paint,
        )
    }

    /**
     * Mirrors upstream's
     * `sk_sp<SkTypeface> MakeTypefaceFromResource(const char* resource, const SkFontArguments& args)`
     * — load the COLR v1 test font and apply [args] at load time.
     *
     * Today routes through [ToolUtils.CreateTypefaceFromResource]. The
     * pure Kotlin renderer covers palette clones; #1020 tracks the
     * remaining stream-load-with-args GM route and reference images.
     */
    @Suppress("UNUSED_PARAMETER")
    private fun makeTypefaceFromResource(resource: String, args: SkFontArguments): SkTypeface? {
        // `args` is the compile-pinned palette surface. #1020 tracks
        // routing this load-time palette path through accepted fixtures.
        return ToolUtils.CreateTypefaceFromResource(resource)
    }

    public companion object {
        /** Upstream `kColrCpalTestFontPath` (palette.cpp line 41). */
        public const val K_COLR_CPAL_TEST_FONT_PATH: String = "fonts/test_glyphs-glyf_colr_1.ttf"

        /**
         * Mirrors upstream's `ColrV1TestDefinitions::color_circles_palette`
         * — the two synthetic glyphs that drive the palette swatch test.
         * Copied from Google Fonts' colour-circles markdown, see the
         * cpp header comment.
         */
        public val COLOR_CIRCLES_PALETTE: IntArray = intArrayOf(0xf0e00, 0xf0e01)

        // ─── Per-variant palette ctor arguments (cpp lines 43..77) ──

        /**
         * Mirrors upstream's
         * `kColorOverridesAll[]` — gradient of dark→light purple/pink
         * for the circle palette test glyph, randomly ordered (upstream
         * comment: "Randomly ordered with `shuf`"), plus a repeat
         * (entry 6 — later overrides override earlier overrides) and
         * three out-of-bounds entries (12 / 13 / -1 ; the font only
         * has 12 palette entries).
         *
         * Public so [FontPaletteTest] / sibling unit tests can pin the
         * canonical contract.
         */
        public val K_COLOR_OVERRIDES_ALL: List<SkFontArguments.Palette.Override> = listOf(
            SkFontArguments.Palette.Override(index = 6, color = 0xffffff00.toInt()),
            SkFontArguments.Palette.Override(index = 2, color = 0xff76078f.toInt()),
            SkFontArguments.Palette.Override(index = 4, color = 0xffb404c4.toInt()),
            SkFontArguments.Palette.Override(index = 1, color = 0xff510970.toInt()),
            SkFontArguments.Palette.Override(index = 6, color = 0xfffa00ff.toInt()),
            SkFontArguments.Palette.Override(index = 8, color = 0xff888888.toInt()),
            SkFontArguments.Palette.Override(index = 10, color = 0xff888888.toInt()),
            SkFontArguments.Palette.Override(index = 9, color = 0xff888888.toInt()),
            SkFontArguments.Palette.Override(index = 7, color = 0xff888888.toInt()),
            SkFontArguments.Palette.Override(index = 11, color = 0xff888888.toInt()),
            SkFontArguments.Palette.Override(index = 0, color = 0xff310b55.toInt()),
            SkFontArguments.Palette.Override(index = 3, color = 0xff9606aa.toInt()),
            SkFontArguments.Palette.Override(index = 5, color = 0xffd802e2.toInt()),
            SkFontArguments.Palette.Override(index = 13, color = 0xff00ffff.toInt()),
            SkFontArguments.Palette.Override(index = 12, color = 0xff00ffff.toInt()),
            // Upstream casts `static_cast<uint16_t>(-1)` — the unsigned
            // wrap reads back as 0xffff = 65535 (still out of bounds for
            // a 12-entry font, expected to be silently ignored).
            SkFontArguments.Palette.Override(index = 0xffff, color = 0xff00ff00.toInt()),
        )

        /** Mirrors upstream's `kColorOverridesOne[]`. */
        public val K_COLOR_OVERRIDES_ONE: List<SkFontArguments.Palette.Override> = listOf(
            SkFontArguments.Palette.Override(index = 2, color = 0xff02dfe2.toInt()),
        )

        // ─── DEF_GM factory methods (cpp lines 166..170) ────────────

        /** `DEF_GM(return new FontPaletteGM("default", SkFontArguments::Palette()))`. */
        public fun defaultPalette(): FontPaletteGM = FontPaletteGM(
            testName = "default",
            paletteOverride = SkFontArguments.Palette(),
        )

        /** `DEF_GM(return new FontPaletteGM("light", kLightPaletteOverride))`. */
        public fun light(): FontPaletteGM = FontPaletteGM(
            testName = "light",
            paletteOverride = SkFontArguments.Palette().apply { index = 2 },
        )

        /** `DEF_GM(return new FontPaletteGM("dark", kDarkPaletteOverride))`. */
        public fun dark(): FontPaletteGM = FontPaletteGM(
            testName = "dark",
            paletteOverride = SkFontArguments.Palette().apply { index = 1 },
        )

        /** `DEF_GM(return new FontPaletteGM("one", kOnePaletteOverride))`. */
        public fun one(): FontPaletteGM = FontPaletteGM(
            testName = "one",
            paletteOverride = SkFontArguments.Palette().apply {
                index = 0
                overrides = K_COLOR_OVERRIDES_ONE
            },
        )

        /** `DEF_GM(return new FontPaletteGM("all", kAllPaletteOverride))`. */
        public fun all(): FontPaletteGM = FontPaletteGM(
            testName = "all",
            paletteOverride = SkFontArguments.Palette().apply {
                index = 0
                overrides = K_COLOR_OVERRIDES_ALL
            },
        )
    }
}
