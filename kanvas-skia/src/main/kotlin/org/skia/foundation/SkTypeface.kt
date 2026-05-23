package org.skia.foundation

import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar

/**
 * Mirrors Skia's `SkTypeface` (`include/core/SkTypeface.h`).
 *
 * In Skia a typeface is a polymorphic, ref-counted handle on a font
 * resource — `SkFontMgr` produces concrete subclasses (FreeType,
 * CoreText, DirectWrite). We keep the abstraction (open class) but
 * **the only concrete subclass shipped with `:kanvas-skia` is the AWT
 * one** (`org.skia.foundation.awt.AwtTypeface`), which is the unique
 * production-grade backend in T1-T5. See `archives/MIGRATION_PLAN_TEXT.md` §
 * "Contrainte de design".
 *
 * The base `SkTypeface` returned by [MakeEmpty] is a no-op stand-in
 * suitable for tests that need a typeface object but don't actually
 * render text. Its [measureText] / [getMetrics] hooks return zeroed
 * defaults; concrete subclasses override them.
 */
public open class SkTypeface protected constructor() {

    /**
     * Mirrors Skia's `SkTypeface::fontStyle()`. Default: [SkFontStyle.Normal].
     * Concrete subclasses override the backing field via the protected
     * setter — keeping it `open get` rather than `var` matches the C++
     * pattern where this is a `const` accessor.
     */
    public open val fontStyle: SkFontStyle = SkFontStyle.Normal()

    /**
     * Internal hook for [SkFont.measureText] — base class returns 0
     * (consistent with [MakeEmpty]'s no-op semantics). Concrete
     * typefaces (e.g. `AwtTypeface`) override this to delegate to their
     * native rasteriser.
     *
     * `internal` because callers should always go through [SkFont.measureText]
     * (the API surface that mirrors upstream).
     */
    public open fun measureTextInternal(
        text: String,
        byteLength: Int,
        encoding: SkTextEncoding,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
        bounds: SkRect?,
    ): SkScalar {
        bounds?.let {
            it.left = 0f; it.top = 0f; it.right = 0f; it.bottom = 0f
        }
        return 0f
    }

    /**
     * Internal hook for [org.skia.core.SkCanvas.drawString] — produces an
     * [SkPath] containing the outlines of every glyph in [text], pre-
     * positioned so that the text baseline aligns with `(x, y)` in the
     * caller's coordinate space. The canvas's CTM is **not** applied here;
     * `drawString` later routes the returned path through `drawPath` which
     * does the CTM mapping.
     *
     * Base class returns `null` (no glyphs to draw — the canvas treats
     * this as a no-op). Concrete typefaces (`AwtTypeface`) override.
     *
     * `internal` because the public API is [org.skia.core.SkCanvas.drawString].
     */
    public open fun makeTextPath(
        text: String,
        x: SkScalar,
        y: SkScalar,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
        isSubpixel: Boolean = false,
    ): SkPath? = null

    /**
     * Internal hook for [SkFont.getPath] — produces the outline of a
     * single glyph (identified by its font-local glyph ID) pre-scaled
     * to [size] and transformed by [scaleX] / [skewX]. Origin at
     * `(0, 0)` (caller is expected to translate as needed).
     *
     * Returns `null` if the glyph has no path (e.g. zero-width or the
     * base [MakeEmpty] typeface). Concrete typefaces override.
     */
    public open fun getGlyphPathInternal(
        glyphId: Int,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
    ): SkPath? = null

    /**
     * Internal hook for [SkFont.unicharsToGlyphs] — resolves each
     * Unicode code point in [unichars] to a font-local glyph ID.
     *
     * Base class fills [glyphs] with zeros (the upstream "missing
     * glyph" / `.notdef` index). Concrete typefaces override.
     */
    public open fun unicharsToGlyphsInternal(
        unichars: IntArray,
        count: Int,
        glyphs: ShortArray,
    ) {
        for (i in 0 until count) glyphs[i] = 0
    }

    /**
     * Internal hook for [SkFont.getWidth] — advance width of a single
     * glyph in source coords at the given size/scaleX/skewX. Base
     * class returns 0.
     */
    public open fun getGlyphWidthInternal(
        glyphId: Int,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
    ): SkScalar = 0f

    /**
     * Mirrors Skia's
     * [`SkGlyphID SkTypeface::unicharToGlyph(SkUnichar) const`](https://github.com/google/skia/blob/main/include/core/SkTypeface.h)
     * — resolves a single Unicode code point to a font-local glyph ID.
     *
     * Returns `0` (the `.notdef` glyph) if the typeface has no glyph for
     * [unichar]. Delegates to [unicharsToGlyphsInternal] with a single-element
     * array so concrete subclasses only need to override the array form.
     *
     * Used by `gm/typeface.cpp::draw_typeface_rendering_gm` to obtain the
     * glyph ID for `'A'` (typefacerendering) and `'O'`
     * (typefacerendering_pfa, typefacerendering_pfb).
     */
    public fun unicharToGlyph(unichar: Int): Int {
        val glyphs = ShortArray(1)
        unicharsToGlyphsInternal(IntArray(1) { unichar }, 1, glyphs)
        return glyphs[0].toInt() and 0xFFFF
    }

    /**
     * Mirrors Skia's
     * [`SkTypeface::getKerningPairAdjustments`](https://github.com/google/skia/blob/main/include/core/SkTypeface.h)
     * — returns the OpenType `kern` table pair adjustments for a sequence
     * of [glyphs] (each adjustment is in source-space FUnits applied
     * between glyph `i` and glyph `i + 1`, so the returned array has
     * `glyphs.size - 1` entries).
     *
     * **R-final.7 status — JNI required, returns `null`** :
     *
     * AWT does not expose the OpenType `kern` table directly. Reading
     * pair adjustments would require parsing the raw font binary (the
     * `kern` and `GPOS` tables) — out of scope for the pure-JVM port
     * (FreeType / HarfBuzz handle this upstream). Returning `null`
     * matches Skia's contract for typefaces that don't carry a `kern`
     * table : the caller should fall back to advance-only positioning.
     *
     * Concrete subclasses backed by a font binary loader (eventual
     * Fontations / FreeType JNI bridge — see `STUB.FONTATIONS` in
     * `API_FINALIZATION_PLAN.md`) may override.
     */
    public open fun getKerningPairAdjustments(glyphs: ShortArray): IntArray? = null

    /**
     * Mirrors Skia's
     * [`SkTypeface::makeClone(const SkFontArguments&)`](https://github.com/google/skia/blob/main/include/core/SkTypeface.h).
     *
     * Returns a new typeface that's the result of applying [args] to
     * `this` — for variable fonts that means the clone's `fvar` axes
     * land at the design coordinates carried by
     * [SkFontArguments.variationDesignPosition], and (when supported)
     * the requested [SkFontArguments.collectionIndex] face is selected
     * from a `.ttc` collection.
     *
     * **Base-class default — no-op identity** : returns `this` unchanged.
     * That matches Skia's contract for typefaces backed by formats with
     * no variation tables (the kanvas-skia [MakeEmpty] sentinel falls
     * here ; concrete subclasses with a real font binary override).
     *
     * Concrete subclasses override the fast path in
     * `AwtTypeface.makeClone(...)`, which maps the standard
     * `wght` / `wdth` / `slnt` / `ital` axes onto AWT
     * `java.awt.font.TextAttribute` values via `Font.deriveFont(Map<...>)`.
     * Axes AWT cannot honour (`opsz`, `GRAD`, `XHGT`, custom) are
     * silently dropped — see the AWT-typeface KDoc for the per-axis
     * mapping table.
     *
     * Returns `null` if the clone cannot be produced (e.g. the underlying
     * font format rejects the requested arguments). The base default
     * never returns `null`.
     */
    public open fun makeClone(args: SkFontArguments): SkTypeface? = this

    /**
     * Mirrors Skia's
     * [`int SkTypeface::countGlyphs() const`](https://github.com/google/skia/blob/main/include/core/SkTypeface.h)
     * — number of glyphs this typeface carries (`maxp.numGlyphs` in
     * TrueType terms). Used by `gm/fontmgr.cpp::FontMgrBoundsGM` to
     * skip typefaces with 0 glyphs (e.g. degenerate AWT fallbacks) and
     * to bound the per-glyph bbox sweep at a thousand glyphs for
     * tractable runtimes.
     *
     * **Base-class default — 0** : the [MakeEmpty] sentinel carries no
     * glyphs. Concrete subclasses (notably `AwtTypeface`) override
     * with the underlying font's `numGlyphs`.
     */
    public open fun countGlyphs(): Int = 0

    /**
     * Mirrors Skia's
     * [`void SkTypeface::getFamilyName(SkString* name) const`](https://github.com/google/skia/blob/main/include/core/SkTypeface.h).
     * Appends the typeface's family name to [name].
     *
     * Kotlin idiom : we accept a [StringBuilder] out-param so direct
     * ports of upstream `.cpp` code drop in unchanged. Callers that
     * just want the value can use [getFamilyName] (the zero-arg
     * overload).
     *
     * **Base-class default — appends nothing** : the [MakeEmpty]
     * sentinel has no family name. Concrete subclasses override.
     */
    public open fun getFamilyName(name: StringBuilder) { /* default no-op */ }

    /**
     * Convenience zero-arg overload — returns the family name as a
     * fresh [String]. Implemented via [getFamilyName] (the out-param
     * form) so subclasses only need to override the latter.
     */
    public fun getFamilyName(): String {
        val sb = StringBuilder()
        getFamilyName(sb)
        return sb.toString()
    }

    /**
     * Mirrors Skia's
     * [`bool SkTypeface::getPostScriptName(SkString*) const`](https://github.com/google/skia/blob/main/include/core/SkTypeface.h).
     * Returns the PostScript name of this typeface if one is present in
     * the OpenType `name` table (nameID 6), or `null` otherwise.
     *
     * **STUB.FONTATIONS** — Accessing nameID entries requires raw OpenType
     * name-table parsing (`getTableData(SkSetFourByteTag('n','a','m','e'), …)`)
     * which is not implemented in the pure-JVM / AWT backend. This method
     * always throws [NotImplementedError] with the `STUB.FONTATIONS` tag.
     * Callers must annotate their tests `@Disabled("STUB.FONTATIONS")`.
     * See [SkTypeface_Fontations] and `API_FINALIZATION_PLAN.md`.
     */
    public open fun getPostScriptName(): String? = TODO(
        "STUB.FONTATIONS: getPostScriptName() requires raw OpenType name-table access " +
            "(nameID 6) — not available in the pure-JVM AWT backend. " +
            "See API_FINALIZATION_PLAN.md § STUB.FONTATIONS.",
    )

    /**
     * Mirrors Skia's
     * [`SkTypeface::LocalizedStrings* SkTypeface::createFamilyNameIterator() const`](https://github.com/google/skia/blob/main/include/core/SkTypeface.h).
     * Returns an iterator over all localised family names stored in the
     * OpenType `name` table (nameIDs 1 + 4, all language/platform entries).
     * Each element is a `(name: String, language: String)` pair.
     *
     * **STUB.FONTATIONS** — Same blocker as [getPostScriptName]: raw
     * name-table parsing is unavailable in the pure-JVM backend. This
     * method always throws [NotImplementedError]. Callers must annotate
     * their tests `@Disabled("STUB.FONTATIONS")`.
     * See [SkTypeface_Fontations] and `API_FINALIZATION_PLAN.md`.
     */
    public open fun createFamilyNameIterator(): Iterator<LocalizedString> = TODO(
        "STUB.FONTATIONS: createFamilyNameIterator() requires raw OpenType name-table access " +
            "— not available in the pure-JVM AWT backend. " +
            "See API_FINALIZATION_PLAN.md § STUB.FONTATIONS.",
    )

    /**
     * Mirrors Skia's `SkTypeface::LocalizedString` — a `(fString, fLanguage)`
     * pair returned by [createFamilyNameIterator].
     *
     * @property fString   the localised family name string.
     * @property fLanguage the BCP-47 language tag (e.g. `"en"`, `"ja"`).
     */
    public data class LocalizedString(val fString: String, val fLanguage: String)

    /**
     * Internal hook for [SkFont.getBounds] — single-glyph tight visual
     * bbox at the configured `size` / `scaleX` / `skewX`. The default
     * returns the empty rect; concrete subclasses (notably `AwtTypeface`)
     * compute the glyph outline's actual bounds.
     *
     * Mirrors the per-glyph branch of upstream's
     * `SkScalerContext::getMetrics(...)` (`src/core/SkScalerContext.cpp`)
     * — the `SkRect` Skia stores in `SkGlyph::fImageBounds`.
     */
    public open fun getGlyphBoundsInternal(
        glyphId: Int,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
    ): SkRect = SkRect.MakeEmpty()

    /**
     * Internal hook for [SkFont.getMetrics] — base class fills [metrics]
     * with zeros and returns 0 (the recommended line spacing).
     */
    public open fun getMetricsInternal(metrics: SkFontMetrics, size: SkScalar): SkScalar {
        metrics.fFlags = 0
        metrics.fTop = 0f
        metrics.fAscent = 0f
        metrics.fDescent = 0f
        metrics.fBottom = 0f
        metrics.fLeading = 0f
        metrics.fAvgCharWidth = 0f
        metrics.fMaxCharWidth = 0f
        metrics.fXMin = 0f
        metrics.fXMax = 0f
        metrics.fXHeight = 0f
        metrics.fCapHeight = 0f
        metrics.fUnderlineThickness = 0f
        metrics.fUnderlinePosition = 0f
        metrics.fStrikeoutThickness = 0f
        metrics.fStrikeoutPosition = 0f
        return 0f
    }

    public companion object {
        /**
         * Mirrors Skia's `SkTypeface::MakeEmpty()`. Returns a singleton
         * no-op typeface — fine for unit tests, but won't actually render
         * glyphs. Production callers should use
         * [org.skia.tools.ToolUtils.DefaultPortableTypeface] which returns
         * a real AWT-backed typeface.
         */
        public fun MakeEmpty(): SkTypeface = EMPTY

        private val EMPTY: SkTypeface = object : SkTypeface() {}
    }
}
