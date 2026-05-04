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
 * **API fidelity (`MIGRATION_PLAN_TEXT.md` §"Contrainte de design")**:
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

    /** Copy ctor mirroring `SkFont::SkFont(const SkFont&)`. */
    public constructor(other: SkFont) : this(other.typeface, other.size, other.scaleX, other.skewX) {
        edging = other.edging
        isSubpixel = other.isSubpixel
        isLinearMetrics = other.isLinearMetrics
        isEmbolden = other.isEmbolden
        isBaselineSnap = other.isBaselineSnap
        isForceAutoHinting = other.isForceAutoHinting
        isEmbeddedBitmaps = other.isEmbeddedBitmaps
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

    /** Mirrors `SkFont::getSpacing()`. */
    public fun getSpacing(): SkScalar = getMetrics(SkFontMetrics())

    public fun copy(): SkFont = SkFont(this)
}
