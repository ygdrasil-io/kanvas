package org.skia.foundation

import org.graphiks.math.SkPoint
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar

/**
 * Mirrors Skia's `SkFont` (`include/core/SkFont.h`).
 *
 * A drawable typeface configuration: a [SkTypeface] handle plus size /
 * scaleX / skewX / edging / hinting flags. Mutable, like upstream — a
 * single `SkFont` instance is reconfigured between draws.
 *
 * **API fidelity (`archives/MIGRATION_PLAN_TEXT.md` §"Contrainte de design")**:
 * we expose `var typeface` / `var size` / `var edging` / `var isSubpixel`
 * etc. rather than explicit `setSize(s)` / `getSize()` methods because
 * Kotlin `var` compiles to identical JVM bytecode (`getX()`/`setX(X)`),
 * and the local convention (cf. [SkPaint]) already uses this idiom.
 *
 * **T1 / T2 scope**: ctors, properties, [measureText], [getMetrics] are
 * wired through to the underlying typeface; rendering ([SkCanvas.drawString])
 * remains a no-op until T3 (see plan).
 */
public class SkFont(
    public var typeface: SkTypeface = SkTypeface.MakeEmpty(),
    public var size: SkScalar = 12f,
    public var scaleX: SkScalar = 1f,
    public var skewX: SkScalar = 0f,
) {
    /**
     * Mirrors `SkFont::Edging`. T1-T4 honour [kAlias] / [kAntiAlias]
     * (kAntiAlias is the default — same as upstream) but downgrade
     * [kSubpixelAntiAlias] silently to [kAntiAlias] (cf. plan §R3).
     */
    public enum class Edging { kAlias, kAntiAlias, kSubpixelAntiAlias }

    public var edging: Edging = Edging.kAntiAlias
    public var isSubpixel: Boolean = false
    public var isLinearMetrics: Boolean = false
    public var isEmbolden: Boolean = false
    public var isBaselineSnap: Boolean = false
    public var isForceAutoHinting: Boolean = false
    public var isEmbeddedBitmaps: Boolean = false

    /**
     * Mirrors `SkFont::setHinting(SkFontHinting)`. The AWT backend
     * ignores this value silently — see [SkFontHinting] for rationale.
     * Stored on [SkFont] so direct ports of upstream `.cpp` code that
     * call `font.setHinting(...)` compile and round-trip correctly.
     */
    public var hinting: SkFontHinting = SkFontHinting.kNormal

    /**
     * Variable-font design position — mirrors what Skia carries via
     * `SkFontArguments::VariationPosition` on a typeface clone (Phase
     * I2.2 light). The current AWT-backed scaler does **not** consume
     * this list yet ; it is exposed today so direct ports that
     * propagate variations through their pipelines compile and
     * round-trip. See [SkFontVariation] for axis semantics.
     */
    public var variations: List<SkFontVariation> = emptyList()

    /** Copy ctor mirroring `SkFont::SkFont(const SkFont&)`. */
    public constructor(other: SkFont) : this(other.typeface, other.size, other.scaleX, other.skewX) {
        edging = other.edging
        isSubpixel = other.isSubpixel
        isLinearMetrics = other.isLinearMetrics
        isEmbolden = other.isEmbolden
        isBaselineSnap = other.isBaselineSnap
        isForceAutoHinting = other.isForceAutoHinting
        isEmbeddedBitmaps = other.isEmbeddedBitmaps
        hinting = other.hinting
        variations = other.variations
    }

    /** Convenience ctor — typeface only, default size 12. */
    public constructor(typeface: SkTypeface) : this(typeface, 12f, 1f, 0f)

    /** Convenience ctor — typeface + size. */
    public constructor(typeface: SkTypeface, size: SkScalar) : this(typeface, size, 1f, 0f)

    /**
     * Mirrors Skia's `SkFont::measureText(const void*, size_t, SkTextEncoding,
     * SkRect* bounds, const SkPaint* paint)` — minus the optional `paint`
     * (irrelevant for us until paint-aware advance widths land).
     *
     * @param text       the string to measure.
     * @param byteLength number of bytes (or wchars) to consider; defaults
     *                   to the full string length.
     * @param encoding   byte/word interpretation. T2: only [SkTextEncoding.kUTF8]
     *                   is honoured by the AWT backend.
     * @param bounds     optional out-param — populated with the tight bounding
     *                   box of the rendered glyph run, in text-local coords.
     * @return the advance width.
     */
    public fun measureText(
        text: String,
        byteLength: Int = text.length,
        encoding: SkTextEncoding = SkTextEncoding.kUTF8,
        bounds: SkRect? = null,
    ): SkScalar = typeface.measureTextInternal(text, byteLength, encoding, size, scaleX, skewX, bounds)

    /**
     * Mirrors Skia's `SkScalar SkFont::getMetrics(SkFontMetrics*) const`.
     * Populates [metrics] in place; returns the recommended line spacing
     * (= ascent + descent + leading, all positive).
     */
    public fun getMetrics(metrics: SkFontMetrics): SkScalar =
        typeface.getMetricsInternal(metrics, size)

    /**
     * **Internal** entry point for [org.skia.core.SkCanvas.drawString].
     * Delegates to the typeface, threading the [isSubpixel] flag so
     * concrete backends can honour it (e.g. snap glyph origins to
     * integer device coords when `false`).
     */
    public fun makeTextPath(text: String, x: SkScalar, y: SkScalar): SkPath? =
        typeface.makeTextPath(text, x, y, size, scaleX, skewX, isSubpixel)

    /**
     * Mirrors Skia's `bool SkFont::getPath(SkGlyphID, SkPath*)`
     * (`SkFont.h` ~line 360). Upstream takes an out-param; we return
     * the path (or `null`) for clean Kotlin idiom — direct ports that
     * use the upstream form should call this and copy into a builder
     * if a mutable path is needed.
     *
     * Returns `null` when the typeface has no path for [glyphId] (e.g.
     * an empty typeface, or a glyph ID outside the font's range).
     */
    public fun getPath(glyphId: Int): SkPath? =
        typeface.getGlyphPathInternal(glyphId, size, scaleX, skewX)

    /**
     * Mirrors Skia's
     * [`SkRect SkFont::getBounds(SkGlyphID, const SkPaint*) const`](https://github.com/google/skia/blob/main/include/core/SkFont.h)
     * — tight visual bounding box of a single glyph at the configured
     * size / scaleX / skewX, in text-local coords.
     *
     * The optional [paint] mirrors upstream's `paint` parameter
     * (used to widen the bbox for stroked text). The Kotlin port
     * currently ignores it — stroked-text glyph bounds need the
     * per-glyph outline measured against the stroker which we don't
     * yet thread through the font path. Direct ports of upstream
     * `.cpp` test code (e.g. `gm/fontmgr.cpp::FontMgrBoundsGM`) pass
     * `nullptr` here anyway.
     *
     * Delegates to [SkTypeface.getGlyphBoundsInternal] — base
     * typeface ([SkTypeface.MakeEmpty]) returns the empty rect ;
     * concrete subclasses (`AwtTypeface`) compute the outline bbox.
     */
    public fun getBounds(glyphId: Int, paint: SkPaint? = null): SkRect {
        @Suppress("UNUSED_PARAMETER") val unused = paint
        return typeface.getGlyphBoundsInternal(glyphId, size, scaleX, skewX)
    }

    /**
     * Mirrors Skia's `void SkFont::unicharsToGlyphs(SkSpan<const SkUnichar>,
     * SkSpan<SkGlyphID>)` (`SkFont.h` ~line 313).
     *
     * Resolves each Unicode code point in [unichars] to a font-local
     * glyph ID, written into [glyphs]. Both arrays must hold at least
     * [count] elements; we don't bounds-check beyond [count] to match
     * upstream's span-based contract.
     *
     * Glyphs absent from the font map to 0 (the upstream "missing
     * glyph" `.notdef` index).
     */
    public fun unicharsToGlyphs(unichars: IntArray, count: Int, glyphs: ShortArray) {
        require(unichars.size >= count) { "unichars too small: ${unichars.size} < $count" }
        require(glyphs.size >= count) { "glyphs too small: ${glyphs.size} < $count" }
        typeface.unicharsToGlyphsInternal(unichars, count, glyphs)
    }

    /**
     * Mirrors Skia's `SkScalar SkFont::getWidth(SkGlyphID)` (`SkFont.h`
     * ~line 295). Single-glyph advance width at the configured size /
     * scaleX / skewX. Returns 0 for unknown glyph IDs.
     */
    public fun getWidth(glyphId: Int): SkScalar =
        typeface.getGlyphWidthInternal(glyphId, size, scaleX, skewX)

    /** Mirrors `SkFont::getSpacing()`. */
    public fun getSpacing(): SkScalar = getMetrics(SkFontMetrics())

    public fun copy(): SkFont = SkFont(this)

    // ───────────────────────── S7-B helpers ──────────────────────────────
    //
    // The four entry points below were previously inlined in every
    // text-bearing GM port (`GlyphPosGM`, `StrokeTextGM`, `TextEffectsGM`,
    // `UserFontGM`, `DrawGlyphsGM`, …) — the same recipe each time:
    // `text.codePoints()` → [unicharsToGlyphs] → cumulative widths via
    // [getWidth]. Promoting them to first-class [SkFont] methods mirrors
    // upstream Skia's `SkFont::textToGlyphs / getPos / getXPos / getWidths`
    // (`SkFont.h` ~lines 245-310) and lets the GM ports drop ~6 LOC each.

    /**
     * Mirrors Skia's
     * [`int SkFont::textToGlyphs(const void* text, size_t byteLength, SkTextEncoding,
     *  SkGlyphID glyphs[], int maxGlyphCount)`](https://github.com/google/skia/blob/main/include/core/SkFont.h)
     * (`SkFont.h` ~line 245), simplified to return a freshly-allocated
     * `IntArray` for clean Kotlin idiom (callers don't pre-size).
     *
     * Walks each Unicode code point in [text] and resolves it to a
     * font-local glyph ID via the typeface's char-to-glyph map. Code
     * points absent from the font map to `0` (the upstream `.notdef`
     * sentinel).
     *
     * Only [SkTextEncoding.kUTF8] / [SkTextEncoding.kUTF16] / [SkTextEncoding.kUTF32]
     * are honoured — [SkTextEncoding.kGlyphID] short-circuits to a no-op
     * "text already is glyph IDs" passthrough by interpreting [text]'s
     * UTF-16 char codes as raw glyph IDs (mirrors upstream's
     * `kGlyphID_SkTextEncoding` shortcut). For our AWT-backed pipeline
     * the three Unicode encodings collapse to the same `String.codePoints()`
     * walk because Kotlin `String` is already Unicode internally.
     *
     * Returned array length equals the input code-point count (≤
     * `text.length`, less for surrogate pairs in [SkTextEncoding.kUTF16]).
     */
    public fun textToGlyphs(
        text: String,
        encoding: SkTextEncoding = SkTextEncoding.kUTF8,
    ): IntArray {
        if (text.isEmpty()) return IntArray(0)
        if (encoding == SkTextEncoding.kGlyphID) {
            // text's char codes are already glyph IDs — direct copy.
            return IntArray(text.length) { text[it].code and 0xFFFF }
        }
        val codepoints = text.codePoints().toArray()
        val n = codepoints.size
        val glyphsShort = ShortArray(n)
        typeface.unicharsToGlyphsInternal(codepoints, n, glyphsShort)
        return IntArray(n) { glyphsShort[it].toInt() and 0xFFFF }
    }

    /**
     * Mirrors Skia's
     * [`void SkFont::getPos(const SkGlyphID glyphs[], int count, SkPoint pos[],
     *  SkPoint origin = {0, 0})`](https://github.com/google/skia/blob/main/include/core/SkFont.h)
     * (`SkFont.h` ~line 290) — returns a freshly-allocated array for
     * Kotlin ergonomics rather than the upstream out-param.
     *
     * Per-glyph baseline-aligned positions: each glyph's `(x, y)` is
     * `(origin.x + cumulative-advance, origin.y)` where the cumulative
     * advance accumulates [getWidth] for each preceding glyph. The
     * `[i]`th `SkPoint` is the origin for glyph `[i]`.
     *
     * Empty input returns an empty array.
     */
    public fun getPos(
        glyphs: IntArray,
        origin: SkPoint = SkPoint(0f, 0f),
    ): Array<SkPoint> {
        if (glyphs.isEmpty()) return emptyArray()
        val out = Array(glyphs.size) { SkPoint(0f, 0f) }
        var x = origin.fX
        for (i in glyphs.indices) {
            out[i] = SkPoint(x, origin.fY)
            x += typeface.getGlyphWidthInternal(glyphs[i], size, scaleX, skewX)
        }
        return out
    }

    /**
     * Mirrors Skia's
     * [`void SkFont::getXPos(const SkGlyphID glyphs[], int count, SkScalar xpos[],
     *  SkScalar origin = 0)`](https://github.com/google/skia/blob/main/include/core/SkFont.h)
     * (`SkFont.h` ~line 297). Cheaper variant of [getPos] when the caller
     * only needs X coordinates (constant baseline Y).
     *
     * Returns `[origin, origin + w0, origin + w0 + w1, …]` of length
     * `glyphs.size`.
     */
    public fun getXPos(glyphs: IntArray, origin: SkScalar = 0f): FloatArray {
        if (glyphs.isEmpty()) return FloatArray(0)
        val out = FloatArray(glyphs.size)
        var x = origin
        for (i in glyphs.indices) {
            out[i] = x
            x += typeface.getGlyphWidthInternal(glyphs[i], size, scaleX, skewX)
        }
        return out
    }

    /**
     * Mirrors Skia's
     * [`void SkFont::getWidths(const SkGlyphID glyphs[], int count, SkScalar widths[])`](https://github.com/google/skia/blob/main/include/core/SkFont.h)
     * (`SkFont.h` ~line 273). Returns a per-glyph advance-width array of
     * the same length as [glyphs] — cheaper than [getXPos] when the
     * caller wants per-glyph deltas rather than cumulative origins.
     */
    public fun getWidths(glyphs: IntArray): FloatArray {
        if (glyphs.isEmpty()) return FloatArray(0)
        return FloatArray(glyphs.size) { i ->
            typeface.getGlyphWidthInternal(glyphs[i], size, scaleX, skewX)
        }
    }
}
