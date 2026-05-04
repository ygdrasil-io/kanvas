package org.skia.foundation.awt

import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPath
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTypeface
import org.skia.math.SkRect
import org.skia.math.SkScalar
import java.awt.Font
import java.awt.geom.AffineTransform
import java.awt.font.FontRenderContext

/**
 * **NOTE D'IMPLÉMENTATION** — Ce fichier expose la surface API Skia
 * (`SkTypeface` / `SkFont` / `SkFontMetrics` / …) mais l'implémentation
 * sous-jacente repose sur **`java.awt.Font` + `java.awt.font.GlyphVector`**,
 * pas sur le moteur de fontes natif Skia (FreeType + SkScalerContext).
 *
 * Conséquences :
 *  - Les métriques peuvent diverger de 1-2 ulps des valeurs upstream.
 *  - Le rasterizer AA des glyphes est celui d'AWT (relayé via SkPath →
 *    notre scanline-fill 4×4), pas le rasterizer FreeType de Skia. Pas
 *    encore exercé en T2 — `drawString` reste no-op jusqu'à T3.
 *  - `SkFont.Edging.kSubpixelAntiAlias` est dégradé silencieusement vers
 *    `kAntiAlias` (cf. MIGRATION_PLAN_TEXT.md §R3).
 *
 * Si un jour on remplace AWT par FreeType+JNI ou par un rasterizer custom,
 * **seul ce fichier (et ses pairs `Awt*.kt`) doit changer** — l'API publique
 * reste figée sur la signature Skia.
 */
internal class AwtTypeface internal constructor(
    private val baseFont: Font,
    public override val fontStyle: SkFontStyle = SkFontStyle.Normal(),
) : SkTypeface() {

    /**
     * Mirrors `SkFont::measureText` — returns advance width, optionally
     * fills [bounds] with the **tight glyph visual bbox** (matching Skia,
     * which returns visual not logical bounds). The visual-bbox path
     * builds a `GlyphVector`; if you only need the advance width pass
     * `bounds = null` to skip that allocation.
     */
    override fun measureTextInternal(
        text: String,
        byteLength: Int,
        encoding: SkTextEncoding,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
        bounds: SkRect?,
    ): SkScalar {
        if (text.isEmpty() || byteLength == 0) {
            bounds?.let { it.left = 0f; it.top = 0f; it.right = 0f; it.bottom = 0f }
            return 0f
        }
        // Encoding: T2 only honours kUTF8 / kUTF16 / kUTF32 (all three end
        // up as Java `String` characters since Kotlin Strings are UTF-16
        // internally). kGlyphID is treated as kUTF8 — see plan note.
        val sub = if (byteLength >= text.length) text else text.substring(0, byteLength)

        val font = baseFont.deriveFont(size)
        val frc = FRC

        val advance = font.getStringBounds(sub, frc).width.toFloat()

        if (bounds != null) {
            // Tight glyph bbox via GlyphVector. More expensive but matches
            // Skia's `bounds` semantics (Skia returns the union of glyph
            // image bboxes, not the typographic line box).
            val gv = font.createGlyphVector(frc, sub)
            val vb = gv.visualBounds
            bounds.left = vb.x.toFloat()
            bounds.top = vb.y.toFloat()
            bounds.right = (vb.x + vb.width).toFloat()
            bounds.bottom = (vb.y + vb.height).toFloat()
        }
        return advance
    }

    /**
     * Mirrors the path that [org.skia.core.SkCanvas.drawString] takes
     * (T3): build an [SkPath] containing every glyph in [text], pre-
     * positioned so the baseline lands at `(x, y)` in source coords. The
     * returned path is then routed through `drawPath`, which applies the
     * canvas CTM and the existing AA scanline-fill + blend-mode pipeline.
     *
     * Implementation:
     *  1. derive the AWT `Font` at the requested [size] / [scaleX] /
     *     [skewX] (skewX = horizontal shear);
     *  2. `Font.createGlyphVector(FRC, text)` lays out glyphs on the
     *     baseline;
     *  3. `GlyphVector.getOutline(x, y)` returns a `Shape` already
     *     translated to baseline `(x, y)`;
     *  4. [AwtPathConverter.shapeToSkPath] walks the AWT `PathIterator`
     *     and emits the equivalent `moveTo` / `lineTo` / `quadTo` /
     *     `cubicTo` / `close` verb stream into an [SkPath].
     *
     * Returns `null` for empty input — callers (notably `SkCanvas.drawString`)
     * treat that as a fast-path no-op.
     */
    override fun makeTextPath(
        text: String,
        x: SkScalar,
        y: SkScalar,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
    ): SkPath? {
        if (text.isEmpty()) return null
        val sized = baseFont.deriveFont(size)
        // scaleX scales the x-axis only; skewX horizontally shears
        // (glyph x ← x + skewX·y). Skia's convention matches AWT's
        // `AffineTransform.shear(shx, shy)`.
        val font = if (scaleX == 1f && skewX == 0f) {
            sized
        } else {
            val tx = AffineTransform()
            if (scaleX != 1f) tx.scale(scaleX.toDouble(), 1.0)
            if (skewX != 0f) tx.shear(skewX.toDouble(), 0.0)
            sized.deriveFont(tx)
        }
        val gv = font.createGlyphVector(FRC, text)
        val outline = gv.getOutline(x, y)
        return AwtPathConverter.shapeToSkPath(outline)
    }

    /**
     * Mirrors `SkScalar SkFont::getMetrics(SkFontMetrics*)` — fills the
     * out-param and returns recommended line spacing (= ascent + descent
     * + leading, in upstream's positive-magnitude convention).
     *
     * AWT `LineMetrics`/`FontMetrics` map to Skia as follows (Skia is
     * y-down, AWT reports magnitudes; sign flip applies to top/ascent
     * because Skia treats values above baseline as negative):
     *
     *  | Skia             | AWT source                              |
     *  |------------------|------------------------------------------|
     *  | fAscent          | -lm.ascent                              |
     *  | fTop             | -lm.ascent (no separate "top" in AWT)   |
     *  | fDescent         | +lm.descent                             |
     *  | fBottom          | +lm.descent                             |
     *  | fLeading         | lm.leading                              |
     *  | fXHeight         | -font.size * 0.5 (AWT has no x-height)  |
     *  | fCapHeight       | -font.size * 0.7 (AWT has no cap-h)     |
     *  | fAvgCharWidth    | fm.charWidth('x')                       |
     *  | fMaxCharWidth    | fm.maxAdvance                           |
     *  | underline / strikeout | from LineMetrics (always present)  |
     */
    override fun getMetricsInternal(metrics: SkFontMetrics, size: SkScalar): SkScalar {
        val font = baseFont.deriveFont(size)
        val frc = FRC
        // Use a representative string for line metrics — the choice of
        // string doesn't affect ascent/descent/leading for fixed AWT
        // fonts but does affect the x-height/cap-height heuristic below
        // if we ever switch to a string-driven measurement.
        val lm = font.getLineMetrics("Hxg", frc)

        metrics.fAscent = -lm.ascent
        metrics.fDescent = lm.descent
        metrics.fTop = -lm.ascent
        metrics.fBottom = lm.descent
        metrics.fLeading = lm.leading

        // AWT exposes no native x-height or cap-height. Use a heuristic
        // (50% / 70% of em-size) — the GMs in our scope don't read these
        // for layout. T4 with portable TTFs can refine via OS/2 table.
        metrics.fXHeight = -size * 0.5f
        metrics.fCapHeight = -size * 0.7f

        // Avg char width: width of 'x' at this size — a common Skia
        // approximation when the font has no `OS/2.xAvgCharWidth` table.
        metrics.fAvgCharWidth = font.getStringBounds("x", frc).width.toFloat()
        metrics.fMaxCharWidth = size * 1.0f       // ~ em-size upper bound
        metrics.fXMin = 0f
        metrics.fXMax = size

        // Underline & strikeout — AWT exposes both via LineMetrics.
        metrics.fUnderlineThickness = lm.underlineThickness
        metrics.fUnderlinePosition = lm.underlineOffset
        metrics.fStrikeoutThickness = lm.strikethroughThickness
        metrics.fStrikeoutPosition = lm.strikethroughOffset
        metrics.fFlags =
            SkFontMetrics.kUnderlineThicknessIsValid_Flag or
                SkFontMetrics.kUnderlinePositionIsValid_Flag or
                SkFontMetrics.kStrikeoutThicknessIsValid_Flag or
                SkFontMetrics.kStrikeoutPositionIsValid_Flag

        return lm.height
    }

    internal companion object {
        /**
         * Single shared `FontRenderContext` — `(null transform, AA on,
         * fractional metrics on)`. Matches what we'll feed to the actual
         * raster path in T3 so measurements stay consistent with rendering.
         */
        internal val FRC: FontRenderContext = FontRenderContext(null, true, true)

        /**
         * Default platform sans-serif at 1pt — derived to the requested
         * size via `deriveFont(size)` per call. We pin `Font.PLAIN` here;
         * `SkFont` doesn't model boldness independently of the typeface
         * (boldness comes from the `SkTypeface.fontStyle` weight).
         */
        internal fun makeDefaultBase(): Font = Font(Font.SANS_SERIF, Font.PLAIN, 1)

        /** Singleton AWT-backed default typeface. */
        internal val DEFAULT: AwtTypeface = AwtTypeface(makeDefaultBase())
    }
}
