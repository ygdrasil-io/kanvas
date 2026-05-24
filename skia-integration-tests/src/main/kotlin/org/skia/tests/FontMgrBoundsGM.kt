package org.skia.tests

import org.graphiks.math.SK_ColorBLUE
import org.graphiks.math.SK_ColorRED
import org.graphiks.math.SK_ScalarInfinity
import org.graphiks.math.SK_ScalarNegativeInfinity
import org.graphiks.math.SkColor
import org.graphiks.math.SkISize
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar
import org.skia.core.SkCanvas
import org.skia.core.withSave
import org.skia.foundation.SkDashPathEffect
import org.skia.foundation.SkFont
import org.skia.foundation.LiberationFontMgr
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkFontMgr
import org.skia.foundation.SkFontPriv
import org.skia.foundation.SkFontStyleSet
import org.skia.foundation.SkPaint
import org.skia.foundation.SkTextEncoding
import org.skia.tools.ToolUtils

/**
 * Port of upstream Skia's
 * [`gm/fontmgr.cpp::FontMgrBoundsGM`](https://github.com/google/skia/blob/main/gm/fontmgr.cpp)
 * — three registrations from the same C++ class :
 *  - `fontmgr_bounds`            : `FontMgrBoundsGM(1, 0)`
 *  - `fontmgr_bounds_0.75_0`     : `FontMgrBoundsGM(0.75, 0)`
 *  - `fontmgr_bounds_1_-0.25`    : `FontMgrBoundsGM(1, -0.25)`
 *
 * For each font family enumerated by the default [SkFontMgr],
 * pick up to 3 styles whose glyph count is in `(0, 1000)` (i.e.
 * skip dingbat / CJK fonts that would dominate the runtime), draw
 * the union of every glyph's [SkFont.getBounds] in a dashed
 * rectangle and the typographic bbox (via [SkFontPriv.GetFontBounds])
 * in a solid rectangle. Stop once 32 typefaces have been visited
 * or 700 px of vertical space is consumed.
 *
 * **Font-set divergence** : same caveat as the other two
 * `fontmgr.cpp` GMs — this port uses the deterministic
 * Liberation portable set exposed by [LiberationFontMgr.Make], not
 * the host system font catalog.
 *
 * **API gap vs upstream — `SkMetaData` controls** : upstream
 * exposes a `Label Bounds` toggle on the GM panel ; the kanvas-skia
 * GM framework has no analogous control surface, so `fLabelBounds`
 * is hard-coded `false` (matching the reference PNGs upstream
 * captures with the default off).
 */
public class FontMgrBoundsGM(
    private val fScaleX: SkScalar,
    private val fSkewX: SkScalar,
) : GM() {

    /** Convenience constructor for the default `(1, 0)` registration. */
    public constructor() : this(1f, 0f)

    override fun getName(): String =
        if (fScaleX != 1f || fSkewX != 0f) {
            "fontmgr_bounds_${fmtScalar(fScaleX)}_${fmtScalar(fSkewX)}"
        } else {
            "fontmgr_bounds"
        }

    override fun getISize(): SkISize = SkISize.Make(1024, 850)

    private val fLabelBounds: Boolean = false

    override fun onDraw(canvas: SkCanvas?) {
        if (canvas == null) return
        val font = SkFont(ToolUtils.DefaultPortableTypeface()).apply {
            edging = SkFont.Edging.kAntiAlias
            isSubpixel = true
            size = 100f
            scaleX = fScaleX
            skewX = fSkewX
        }

        val boundsColors = intArrayOf(SK_ColorRED, SK_ColorBLUE)

        val fm: SkFontMgr = LiberationFontMgr.Make()
        val count = fm.countFamilies()
        if (count == 0) {
            // Upstream returns `DrawResult::kSkip` here.
            return
        }

        var index = 0
        var x: SkScalar = 0f
        var y: SkScalar = 0f

        canvas.translate(10f, 120f)

        var typefacesVisited = 0
        var i = 0
        while (i < count && typefacesVisited < 32) {
            val set: SkFontStyleSet = fm.createStyleSet(i)
            var stylesVisited = 0
            var j = 0
            while (j < set.count() && typefacesVisited < 32 && stylesVisited < 3) {
                val tf = set.createTypeface(j)
                if (tf != null) {
                    font.typeface = tf
                    val ng = tf.countGlyphs()
                    if (ng in 1..999) {
                        typefacesVisited++
                        stylesVisited++

                        val color: SkColor = boundsColors[index and 1]
                        val drawBounds = showBounds(canvas, font, x, y, color, fLabelBounds)
                        x += drawBounds.width() + 20f
                        index += 1
                        if (x > 900f) {
                            x = 0f
                            y += 160f
                        }
                        if (y >= 700f) {
                            return
                        }
                    }
                }
                j++
            }
            i++
        }
    }

    public companion object {

        /**
         * Mirrors upstream's `show_bounds(canvas, font, x, y,
         * boundsColor, labelBounds)` — sweeps every glyph in
         * [font]'s typeface to compute the visual-bbox union, joins
         * with [SkFontPriv.GetFontBounds], draws the dashed glyph
         * union + solid font-metrics rectangle, optionally
         * underline / strikeout overlays, then draws the four
         * extremum glyphs (leftmost / rightmost / topmost /
         * bottommost) at the centres of `min`'s edges.
         *
         * The label-bounds overlay (family name + glyph-ID labels)
         * is gated on [labelBounds] — false in our reference run,
         * matching the upstream PNGs.
         */
        internal fun showBounds(
            canvas: SkCanvas,
            font: SkFont,
            x: SkScalar, y: SkScalar,
            boundsColor: SkColor,
            labelBounds: Boolean,
        ): SkRect {
            // Per-glyph visual-bbox sweep — capture the
            // {leftmost, rightmost, topmost, bottommost} glyph IDs
            // for the optional label/draw pass.
            var leftGlyph = 0
            var rightGlyph = 0
            var topGlyph = 0
            var bottomGlyph = 0
            val min = SkRect.MakeLTRB(
                SK_ScalarInfinity, SK_ScalarInfinity,
                SK_ScalarNegativeInfinity, SK_ScalarNegativeInfinity,
            )
            val numGlyphs = font.typeface.countGlyphs()
            for (g in 0 until numGlyphs) {
                val cur = font.getBounds(g, null)
                if (cur.left < min.left)    { min.left   = cur.left;   leftGlyph   = g }
                if (cur.top  < min.top)     { min.top    = cur.top;    topGlyph    = g }
                if (min.right  < cur.right) { min.right  = cur.right;  rightGlyph  = g }
                if (min.bottom < cur.bottom){ min.bottom = cur.bottom; bottomGlyph = g }
            }

            val fontBounds = SkFontPriv.GetFontBounds(font)

            val drawBounds = SkRect.MakeLTRB(min.left, min.top, min.right, min.bottom)
            drawBounds.join(fontBounds)

            canvas.withSave {
                translate(x - drawBounds.left, y)

                val boundsPaint = SkPaint().apply {
                    isAntiAlias = true
                    color = boundsColor
                    style = SkPaint.Style.kStroke_Style
                }
                drawRect(fontBounds, boundsPaint)

                // Dashed overlay for the per-glyph union.
                val intervals = floatArrayOf(10f, 10f)
                boundsPaint.pathEffect = SkDashPathEffect.Make(intervals, 0f)
                drawRect(min, boundsPaint)

                // Underline + strikeout overlays (fill, 25% alpha) —
                // only when the typeface advertises the corresponding
                // metric-valid flag.
                val fm = SkFontMetrics()
                font.getMetrics(fm)
                val metricsPaint = SkPaint().apply {
                    isAntiAlias = true
                    color = boundsColor
                    style = SkPaint.Style.kFill_Style
                    alphaf = 0.25f
                }
                if ((fm.fFlags and SkFontMetrics.kUnderlinePositionIsValid_Flag) != 0 &&
                    (fm.fFlags and SkFontMetrics.kUnderlineThicknessIsValid_Flag) != 0
                ) {
                    val underline = SkRect.MakeLTRB(
                        min.left, fm.fUnderlinePosition,
                        min.right, fm.fUnderlinePosition + fm.fUnderlineThickness,
                    )
                    drawRect(underline, metricsPaint)
                }
                if ((fm.fFlags and SkFontMetrics.kStrikeoutPositionIsValid_Flag) != 0 &&
                    (fm.fFlags and SkFontMetrics.kStrikeoutThicknessIsValid_Flag) != 0
                ) {
                    val strikeout = SkRect.MakeLTRB(
                        min.left, fm.fStrikeoutPosition - fm.fStrikeoutThickness,
                        min.right, fm.fStrikeoutPosition,
                    )
                    drawRect(strikeout, metricsPaint)
                }

                // Four extremum glyphs — drawn at centres of `min`'s
                // edges (filled if their outline is empty, stroked
                // otherwise — mirrors upstream's branch on
                // `path.isEmpty()`).
                val glyphsToDraw = arrayOf(
                    GlyphToDraw(leftGlyph,   min.left,    min.centerY(), 270f),
                    GlyphToDraw(rightGlyph,  min.right,   min.centerY(),  90f),
                    GlyphToDraw(topGlyph,    min.centerX(), min.top,       0f),
                    GlyphToDraw(bottomGlyph, min.centerX(), min.bottom,  180f),
                )

                val labelFont = SkFont(ToolUtils.DefaultPortableTypeface()).apply {
                    edging = SkFont.Edging.kAntiAlias
                }

                if (labelBounds) {
                    val name = font.typeface.getFamilyName()
                    drawString(name, min.left, min.bottom, labelFont, SkPaint())
                }
                for (g in glyphsToDraw) {
                    val path = font.getPath(g.id)
                    val style = if (path == null || path.isEmpty()) {
                        SkPaint.Style.kFill_Style
                    } else {
                        SkPaint.Style.kStroke_Style
                    }
                    val glyphPaint = SkPaint().apply { this.style = style }
                    // drawSimpleText with kGlyphID encoding — feed the
                    // glyph ID directly so we don't go through the
                    // typeface's char->glyph map.
                    val glyphStr = String(charArrayOf(g.id.toChar()))
                    drawSimpleText(
                        glyphStr,
                        glyphStr.length,
                        SkTextEncoding.kGlyphID,
                        0f, 0f, font, glyphPaint,
                    )

                    if (labelBounds) {
                        withSave {
                            translate(g.x, g.y)
                            rotate(g.rotation)
                            drawString(g.id.toString(), 0f, 0f, labelFont, SkPaint())
                        }
                    }
                }
            }

            return drawBounds
        }

        /** Upstream uses `%g`; Kotlin needs explicit `Locale.ROOT`-free formatting. */
        private fun fmtScalar(v: SkScalar): String {
            // Upstream `%g` produces e.g. "0.75", "1", "-0.25". Use
            // Kotlin's `toString` for a float, which matches for the
            // three concrete values we register (1, 0.75, -0.25).
            val s = v.toString()
            // Strip trailing ".0" for integer-valued floats so "1.0"
            // becomes "1" (matches `%g`'s elision).
            return if (s.endsWith(".0")) s.dropLast(2) else s
        }

        private data class GlyphToDraw(
            val id: Int,
            val x: SkScalar,
            val y: SkScalar,
            val rotation: SkScalar,
        )
    }
}
