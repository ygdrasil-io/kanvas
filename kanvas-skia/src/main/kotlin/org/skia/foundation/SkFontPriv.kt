package org.skia.foundation

import org.graphiks.math.SkRect

/**
 * Iso-aligned port of upstream Skia's
 * [`SkFontPriv`](https://github.com/google/skia/blob/main/src/core/SkFontPriv.h)
 * — the private companion of [SkFont] that exposes a handful of
 * scaler-context-coupled helpers Skia does not expose on the public
 * `SkFont` surface.
 *
 * The Kotlin port lifts these to a `public object` because the
 * `private/internal` distinction upstream is a C++ build-system
 * artefact (the helpers are pure functions of [SkFont] state), and
 * direct ports of upstream `.cpp` GMs reach for them by name
 * (notably `gm/fontmgr.cpp::FontMgrBoundsGM::show_bounds` calling
 * `SkFontPriv::GetFontBounds(font)`).
 */
public object SkFontPriv {

    /**
     * Mirrors Skia's
     * [`SkRect SkFontPriv::GetFontBounds(const SkFont&)`](https://github.com/google/skia/blob/main/src/core/SkFontPriv.cpp)
     * — the **typographic** bbox of [font], derived from its
     * [SkFontMetrics] : the rectangle that would tightly enclose
     * every glyph the typeface could draw, plus a horizontal
     * estimate from `fXMin..fXMax`.
     *
     * Layout : `LTRB = (fXMin, fTop, fXMax, fBottom)` — the
     * inclusive vertical extent (Skia y-down so `fTop` is the
     * most-negative ascent, `fBottom` the most-positive descent).
     * When the typeface reports zero metrics (e.g.
     * [SkTypeface.MakeEmpty]), returns [SkRect.MakeEmpty].
     *
     * Concrete typefaces (`AwtTypeface`) populate the metrics from
     * AWT `LineMetrics` ; the resulting bbox matches Skia's
     * upstream within a 1-2 ulp drift on em-relative axes (cf.
     * `archives/MIGRATION_PLAN_TEXT.md` §T4).
     */
    public fun GetFontBounds(font: SkFont): SkRect {
        val metrics = SkFontMetrics()
        font.getMetrics(metrics)
        // Skia's reference impl multiplies `fXMin / fXMax` by `size *
        // scaleX` because the metrics fields are in em-relative
        // units upstream. Our AWT-backed [SkTypeface.getMetricsInternal]
        // already scales them to the requested point size, so we
        // consume them directly.
        return SkRect.MakeLTRB(
            metrics.fXMin,
            metrics.fTop,
            metrics.fXMax,
            metrics.fBottom,
        )
    }
}
