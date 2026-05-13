package org.skia.utils

import org.skia.core.SkDrawable
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTypeface
import org.skia.math.SkMatrix
import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * Mirrors Skia's
 * [`SkCustomTypefaceBuilder`](https://github.com/google/skia/blob/main/include/utils/SkCustomTypeface.h)
 * — a builder that assembles an [SkTypeface] from arbitrary
 * caller-supplied glyph data (paths or [SkDrawable]s) keyed by
 * Unicode code-point.
 *
 * Typical use case is testing or unusual font surfaces (vector emoji,
 * arbitrary mathematical glyphs, GMs that need a known-shape glyph
 * stream) where shipping a real `.ttf` would be overkill.
 *
 * **Lifecycle** : single-use. After [detach] returns, calling any
 * setter or [detach] again throws [IllegalStateException]. This
 * matches upstream's "consume the vector via `std::move`" pattern.
 *
 * **Key model** : upstream keys glyph storage by `SkGlyphID` (the
 * font-local glyph index). `:kanvas-skia` simplifies the contract by
 * keying directly on Unicode code-point — the produced typeface's
 * [SkTypeface.unicharsToGlyphsInternal] then maps `unichar -> glyphId`
 * where the glyph-id is the position of that code-point in the
 * insertion order of the [setGlyph] calls. Unknown code-points map to
 * `0` (the upstream `.notdef` sentinel).
 *
 * **Storage layout** : an insertion-ordered `LinkedHashMap` keyed by
 * the `unichar`. The first glyph inserted gets `glyphId = 0`, the
 * second `glyphId = 1`, etc. Re-setting the same `unichar` overwrites
 * the stored record but keeps its existing slot — so the glyph-id
 * assignment is stable across re-writes.
 *
 * **Bounds tracking** : on [detach] we compute the union of every
 * stored glyph's bounds (path bounds for path glyphs, [SkRect] bounds
 * for drawable glyphs) and write the result into the metrics' top /
 * bottom / xMin / xMax — mirroring `SkCustomTypefaceBuilder::detach`
 * in `src/utils/SkCustomTypeface.cpp:185-203`.
 */
public class SkCustomTypefaceBuilder public constructor() {

    /**
     * Internal glyph record. Either [path] is non-null (path glyph)
     * or [drawable] is non-null (drawable glyph) — never both.
     * Mirrors the union semantics of upstream's `GlyphRec` struct
     * (`include/utils/SkCustomTypeface.h:44-56`).
     */
    internal data class GlyphRec(
        val advance: SkScalar,
        val path: SkPath?,
        val drawable: SkDrawable?,
        val bounds: SkRect,
    ) {
        internal val isDrawable: Boolean get() = drawable != null
    }

    // Insertion-ordered storage : the iteration order is the assigned
    // glyph-id sequence (slot 0 = first insertion, etc.). LinkedHashMap
    // preserves insertion order *and* allows O(1) re-set of an existing
    // unichar without shifting its slot.
    private val recs: LinkedHashMap<Int, GlyphRec> = LinkedHashMap()
    private var metrics: SkFontMetrics = SkFontMetrics()
    private var style: SkFontStyle = SkFontStyle.Normal()
    private var consumed: Boolean = false

    /**
     * Add or overwrite the glyph for [unichar]. The glyph carries
     * an [advance] (horizontal advance in source units) and a [path]
     * outline. Mirrors
     * `SkCustomTypefaceBuilder::setGlyph(SkGlyphID, float, const SkPath&)`
     * (`include/utils/SkCustomTypeface.h:32`).
     */
    public fun setGlyph(unichar: Int, advance: SkScalar, path: SkPath): SkCustomTypefaceBuilder {
        ensureNotConsumed()
        recs[unichar] = GlyphRec(
            advance = advance,
            path = path,
            drawable = null,
            bounds = path.computeTightBounds(),
        )
        return this
    }

    /**
     * Add or overwrite the glyph for [unichar] as a drawable glyph
     * (a self-rendering [SkDrawable] with caller-supplied [bounds]).
     * Mirrors
     * `SkCustomTypefaceBuilder::setGlyph(SkGlyphID, float, sk_sp<SkDrawable>, const SkRect&)`
     * (`include/utils/SkCustomTypeface.h:33`).
     */
    public fun setGlyph(
        unichar: Int,
        advance: SkScalar,
        drawable: SkDrawable,
        bounds: SkRect,
    ): SkCustomTypefaceBuilder {
        ensureNotConsumed()
        recs[unichar] = GlyphRec(
            advance = advance,
            path = null,
            drawable = drawable,
            bounds = SkRect.MakeLTRB(bounds.left, bounds.top, bounds.right, bounds.bottom),
        )
        return this
    }

    /**
     * Set the typeface's [SkFontMetrics], optionally pre-scaling every
     * field by [scale] (sx and sy are equal here — upstream's
     * `scale_fontmetrics` in `SkCustomTypeface.cpp:58-85` actually
     * allows independent x / y scaling, but the public API only
     * exposes a single scalar).
     */
    public fun setMetrics(metrics: SkFontMetrics, scale: SkScalar = 1f): SkCustomTypefaceBuilder {
        ensureNotConsumed()
        this.metrics = scaleMetrics(metrics, scale, scale)
        return this
    }

    /** Set the produced typeface's [SkFontStyle]. */
    public fun setFontStyle(style: SkFontStyle): SkCustomTypefaceBuilder {
        ensureNotConsumed()
        this.style = style
        return this
    }

    /**
     * Materialise the typeface and consume the builder. Subsequent
     * calls on this builder throw [IllegalStateException]. Mirrors
     * upstream's "return nullptr if empty, otherwise own the recs"
     * behaviour — except we throw rather than return `null` for the
     * empty case (Kotlin-idiomatic, callers can guard up-front).
     *
     * Mirrors `SkCustomTypefaceBuilder::detach` (`src/utils/SkCustomTypeface.cpp:185`).
     */
    public fun detach(): SkTypeface {
        ensureNotConsumed()
        check(recs.isNotEmpty()) {
            "SkCustomTypefaceBuilder.detach: no glyphs registered"
        }

        // Compute union bounds across every glyph (path or drawable)
        // and bake them into the metrics, exactly like upstream.
        var xMin = Float.POSITIVE_INFINITY
        var yMin = Float.POSITIVE_INFINITY
        var xMax = Float.NEGATIVE_INFINITY
        var yMax = Float.NEGATIVE_INFINITY
        for (r in recs.values) {
            val b = r.bounds
            if (b.left < xMin) xMin = b.left
            if (b.top < yMin) yMin = b.top
            if (b.right > xMax) xMax = b.right
            if (b.bottom > yMax) yMax = b.bottom
        }
        val finalMetrics = SkFontMetrics().also { m ->
            // Copy from the user-supplied metrics first.
            m.fFlags = metrics.fFlags
            m.fTop = yMin
            m.fAscent = metrics.fAscent
            m.fDescent = metrics.fDescent
            m.fBottom = yMax
            m.fLeading = metrics.fLeading
            m.fAvgCharWidth = metrics.fAvgCharWidth
            m.fMaxCharWidth = metrics.fMaxCharWidth
            m.fXMin = xMin
            m.fXMax = xMax
            m.fXHeight = metrics.fXHeight
            m.fCapHeight = metrics.fCapHeight
            m.fUnderlineThickness = metrics.fUnderlineThickness
            m.fUnderlinePosition = metrics.fUnderlinePosition
            m.fStrikeoutThickness = metrics.fStrikeoutThickness
            m.fStrikeoutPosition = metrics.fStrikeoutPosition
        }

        // Build slot list + unichar -> glyphId map in insertion order.
        val slots: List<GlyphRec> = recs.values.toList()
        val unicharToGlyphId: Map<Int, Int> = recs.keys.withIndex()
            .associate { (idx, uc) -> uc to idx }
        val familyName = "Custom-${nextSerial.getAndIncrement()}"

        consumed = true
        return SkUserTypeface(style, finalMetrics, slots, unicharToGlyphId, familyName)
    }

    private fun ensureNotConsumed() {
        check(!consumed) {
            "SkCustomTypefaceBuilder has already been consumed by detach()"
        }
    }

    private fun scaleMetrics(src: SkFontMetrics, sx: SkScalar, sy: SkScalar): SkFontMetrics {
        return SkFontMetrics().also { d ->
            d.fFlags = src.fFlags
            d.fAvgCharWidth = src.fAvgCharWidth * sx
            d.fMaxCharWidth = src.fMaxCharWidth * sx
            d.fXMin = src.fXMin * sx
            d.fXMax = src.fXMax * sx
            d.fTop = src.fTop * sy
            d.fAscent = src.fAscent * sy
            d.fDescent = src.fDescent * sy
            d.fBottom = src.fBottom * sy
            d.fLeading = src.fLeading * sy
            d.fXHeight = src.fXHeight * sy
            d.fCapHeight = src.fCapHeight * sy
            d.fUnderlineThickness = src.fUnderlineThickness * sy
            d.fUnderlinePosition = src.fUnderlinePosition * sy
            d.fStrikeoutThickness = src.fStrikeoutThickness * sy
            d.fStrikeoutPosition = src.fStrikeoutPosition * sy
        }
    }

    public companion object {
        // Process-wide counter — yields stable, distinct family names
        // ("Custom-0", "Custom-1", …) per produced typeface. Mirrors
        // upstream's `Custom-{hash}` naming in spirit ; we use a
        // monotonic counter rather than a hash so collisions never
        // occur within a process.
        private val nextSerial: java.util.concurrent.atomic.AtomicLong =
            java.util.concurrent.atomic.AtomicLong(0)
    }
}

/**
 * Concrete [SkTypeface] produced by [SkCustomTypefaceBuilder.detach].
 * Mirrors upstream's private `SkUserTypeface` class
 * (`src/utils/SkCustomTypeface.cpp:87-147`).
 *
 * Internal because it must not be sub-classed outside the builder —
 * the contract (insertion-order glyph-id assignment, bound metrics)
 * only holds for instances [SkCustomTypefaceBuilder] creates itself.
 */
internal class SkUserTypeface internal constructor(
    style: SkFontStyle,
    private val metrics: SkFontMetrics,
    private val slots: List<SkCustomTypefaceBuilder.GlyphRec>,
    private val unicharToGlyphId: Map<Int, Int>,
    public val familyName: String,
) : SkTypeface() {

    override val fontStyle: SkFontStyle = style

    /** Mirrors `SkUserTypeface::glyphCount` (`SkCustomTypeface.cpp:144-146`). */
    public fun glyphCount(): Int = slots.size

    /**
     * Glyph-id of the slot storing [unichar], or `-1` if [unichar]
     * was never registered. Exposed for tests / advanced callers ;
     * normal text-rendering goes through [unicharsToGlyphsInternal].
     */
    public fun glyphIdForUnichar(unichar: Int): Int =
        unicharToGlyphId[unichar] ?: -1

    /**
     * Horizontal advance of the glyph at index [glyphId], or 0 if
     * out of range. Used by future `SkFont.getWidth` integration.
     */
    public fun advanceForGlyph(glyphId: Int): SkScalar =
        slots.getOrNull(glyphId)?.advance ?: 0f

    /** Outline path of the glyph at [glyphId], or `null` if none / out of range. */
    public fun pathForGlyph(glyphId: Int): SkPath? =
        slots.getOrNull(glyphId)?.path

    /** [SkDrawable] of the glyph at [glyphId], or `null` if none / out of range. */
    public fun drawableForGlyph(glyphId: Int): SkDrawable? =
        slots.getOrNull(glyphId)?.drawable

    /** Cached glyph bounds — useful for callers that need the per-glyph rect. */
    public fun boundsForGlyph(glyphId: Int): SkRect? =
        slots.getOrNull(glyphId)?.bounds

    // ---- SkTypeface hooks ----------------------------------------------------

    override fun unicharsToGlyphsInternal(
        unichars: IntArray,
        count: Int,
        glyphs: ShortArray,
    ) {
        for (i in 0 until count) {
            val id = unicharToGlyphId[unichars[i]] ?: 0
            glyphs[i] = id.toShort()
        }
    }

    override fun getGlyphWidthInternal(
        glyphId: Int,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
    ): SkScalar {
        // Custom typefaces store advances in 1-unit space (source units
        // match the path coordinates). The base typeface contract is
        // "advance in source coords at the given size" — emulate by
        // scaling by size (in lieu of a real upem-based normalisation,
        // which would need a font-stable units-per-em ; upstream
        // hard-codes 2048 in `SkUserTypeface::onGetUPEM`).
        return advanceForGlyph(glyphId) * size * scaleX
    }

    override fun getGlyphPathInternal(
        glyphId: Int,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
    ): SkPath? {
        // Return the raw stored path — base contract says "pre-scaled to
        // [size]" but custom typefaces have no canonical upem so callers
        // composing them through real `SkFont` will need to handle the
        // scale themselves. Documented in CONTEXT.md / API plan §1.7.
        return pathForGlyph(glyphId)
    }

    /**
     * Internal hook used by [org.skia.core.SkCanvas.drawString] →
     * [org.skia.foundation.SkFont.makeTextPath]. Routes each character
     * of [text] through the stored glyph table : for each code-point
     *  1. resolve `unichar → glyphId` (unknown → 0 = `.notdef`) ;
     *  2. fetch the stored [SkPath] (drawable glyphs are skipped — they
     *     don't have an outline to fold into the path-fill pipeline) ;
     *  3. scale the source-units path by `(size * scaleX, size)` and
     *     translate it by the cursor position, accumulating advances
     *     in source units along the way.
     *
     * Mirrors the path-rendering branch of upstream's
     * `SkUserTypeface::SkUserScalerContext::generatePath` (returns the
     * stored `rec.fPath.makeTransform(fMatrix)` where `fMatrix` carries
     * the size scale) — minus the upem normalisation, because
     * `:kanvas-skia` keeps user paths in 1-unit source space.
     *
     * Returns `null` for empty input or when every code-point resolved
     * to an empty / drawable glyph (no outline to render).
     */
    override fun makeTextPath(
        text: String,
        x: SkScalar,
        y: SkScalar,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
        isSubpixel: Boolean,
    ): SkPath? {
        if (text.isEmpty()) return null
        // skewX is folded into the scale matrix as an X-shear ; mirrors
        // AwtTypeface.derivedFont where `tx.shear(shx, 0)` is composed
        // after the `scale(scaleX, 1)`.
        val sx = size * scaleX
        val sy = size
        val builder = SkPathBuilder()
        var cursorAdvance = 0f
        var anyGlyph = false
        var i = 0
        while (i < text.length) {
            val cp = text.codePointAt(i)
            val gid = unicharToGlyphId[cp] ?: 0
            val slot = slots.getOrNull(gid)
            if (slot != null && slot.path != null && !slot.path.isEmpty()) {
                // Compose the per-glyph transform :
                //   1. scale source units → device (sx, sy) ;
                //   2. translate to baseline cursor (x + advance*sx, y) ;
                //   3. fold skewX as an X-shear (kx column = sy * skewX,
                //      matching upstream's `AffineTransform.shear(shx, 0)`
                //      composed after scale).
                // Y-axis isn't scaled by scaleX (only X dimension is).
                val emitX = x + cursorAdvance * sx
                val m = SkMatrix.MakeAll(
                    sx, sy * skewX, emitX,
                    0f, sy, y,
                    0f, 0f, 1f,
                )
                builder.addPath(slot.path, m)
                anyGlyph = true
            }
            // Advance the cursor whether or not the slot has an outline
            // (drawable glyphs still advance ; missing glyphs use slot 0
            // advance per the .notdef contract).
            if (slot != null) {
                cursorAdvance += slot.advance
            }
            i += Character.charCount(cp)
        }
        if (!anyGlyph) return null
        val out = builder.detach()
        return if (out.isEmpty()) null else out
    }

    override fun measureTextInternal(
        text: String,
        byteLength: Int,
        encoding: SkTextEncoding,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
        bounds: SkRect?,
    ): SkScalar {
        var advanceTotal = 0f
        var minX = Float.POSITIVE_INFINITY
        var minY = Float.POSITIVE_INFINITY
        var maxX = Float.NEGATIVE_INFINITY
        var maxY = Float.NEGATIVE_INFINITY
        var i = 0
        val ch = text.toCharArray()
        while (i < ch.size) {
            val cp = ch[i].code
            val gid = unicharToGlyphId[cp] ?: 0
            val slot = slots.getOrNull(gid)
            if (slot != null) {
                advanceTotal += slot.advance
                val b = slot.bounds
                if (b.left < minX) minX = b.left
                if (b.top < minY) minY = b.top
                if (b.right > maxX) maxX = b.right
                if (b.bottom > maxY) maxY = b.bottom
            }
            i++
        }
        if (bounds != null) {
            if (minX == Float.POSITIVE_INFINITY) {
                bounds.left = 0f; bounds.top = 0f; bounds.right = 0f; bounds.bottom = 0f
            } else {
                bounds.left = minX * size * scaleX
                bounds.top = minY * size
                bounds.right = maxX * size * scaleX
                bounds.bottom = maxY * size
            }
        }
        return advanceTotal * size * scaleX
    }

    override fun getMetricsInternal(metrics: SkFontMetrics, size: SkScalar): SkScalar {
        metrics.fFlags = this.metrics.fFlags
        metrics.fTop = this.metrics.fTop * size
        metrics.fAscent = this.metrics.fAscent * size
        metrics.fDescent = this.metrics.fDescent * size
        metrics.fBottom = this.metrics.fBottom * size
        metrics.fLeading = this.metrics.fLeading * size
        metrics.fAvgCharWidth = this.metrics.fAvgCharWidth * size
        metrics.fMaxCharWidth = this.metrics.fMaxCharWidth * size
        metrics.fXMin = this.metrics.fXMin * size
        metrics.fXMax = this.metrics.fXMax * size
        metrics.fXHeight = this.metrics.fXHeight * size
        metrics.fCapHeight = this.metrics.fCapHeight * size
        metrics.fUnderlineThickness = this.metrics.fUnderlineThickness * size
        metrics.fUnderlinePosition = this.metrics.fUnderlinePosition * size
        metrics.fStrikeoutThickness = this.metrics.fStrikeoutThickness * size
        metrics.fStrikeoutPosition = this.metrics.fStrikeoutPosition * size
        return (this.metrics.fDescent - this.metrics.fAscent + this.metrics.fLeading) * size
    }
}
