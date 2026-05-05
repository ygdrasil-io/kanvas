package org.skia.foundation

import org.skia.math.SkRect
import org.skia.math.SkScalar

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
    internal fun makeTextPath(text: String, x: SkScalar, y: SkScalar): SkPath? =
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
}
