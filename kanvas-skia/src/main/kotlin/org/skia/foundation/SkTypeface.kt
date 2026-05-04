package org.skia.foundation

import org.skia.math.SkRect
import org.skia.math.SkScalar

/**
 * Mirrors Skia's `SkTypeface` (`include/core/SkTypeface.h`).
 *
 * In Skia a typeface is a polymorphic, ref-counted handle on a font
 * resource — `SkFontMgr` produces concrete subclasses (FreeType,
 * CoreText, DirectWrite). We keep the abstraction (open class) but
 * **the only concrete subclass shipped with `:kanvas-skia` is the AWT
 * one** (`org.skia.foundation.awt.AwtTypeface`), which is the unique
 * production-grade backend in T1-T5. See `MIGRATION_PLAN_TEXT.md` §
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
    internal open fun measureTextInternal(
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
    internal open fun makeTextPath(
        text: String,
        x: SkScalar,
        y: SkScalar,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
    ): SkPath? = null

    /**
     * Internal hook for [SkFont.getMetrics] — base class fills [metrics]
     * with zeros and returns 0 (the recommended line spacing).
     */
    internal open fun getMetricsInternal(metrics: SkFontMetrics, size: SkScalar): SkScalar {
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
