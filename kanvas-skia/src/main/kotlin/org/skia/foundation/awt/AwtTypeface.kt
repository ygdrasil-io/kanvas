package org.skia.foundation.awt

import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTypeface
import org.skia.math.SkRect
import org.skia.math.SkScalar
import java.awt.Font
import java.awt.geom.AffineTransform
import java.awt.font.FontRenderContext
import kotlin.math.floor

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
     * T5 — per-typeface cache of single-glyph outline paths keyed by
     * `(glyphId, size, scaleX, skewX)`. Populated lazily by [makeTextPath]
     * and [getGlyphPathInternal]. Internal-visibility for tests that
     * verify hit/miss counts.
     */
    internal val glyphPathCache: GlyphPathCache = GlyphPathCache()

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
     * T5 — build an [SkPath] containing every glyph in [text] via the
     * **glyph cache** at [glyphPathCache]. Compared to the T3 single-
     * GV-shape route, this version:
     *  1. builds a kerning-aware GV for [text] **only to read positions
     *     and glyph IDs** — the outline shape is not extracted from the
     *     full GV;
     *  2. for each glyph, looks up the cached single-glyph [SkPath]
     *     (origin `(0, 0)`) keyed by `(glyphId, size, scaleX, skewX)`;
     *     a cache miss builds it via the same single-glyph GV route as
     *     [getGlyphPathInternal];
     *  3. emits each cached path translated to its baseline-relative
     *     position, with the text origin `(x, y)` snapped to integer
     *     coords when [isSubpixel] is `false` (mirrors Skia's
     *     `font.isSubpixel = false` semantics).
     *
     * Notes on font.edging / font.hinting — neither is consulted here;
     * see `MIGRATION_PLAN_TEXT.md` §R3 / [SkFontHinting] for the
     * "stored, not consulted" pattern. The path-fill rasteriser does
     * coverage AA only.
     *
     * Returns `null` for empty input — callers (notably
     * `SkCanvas.drawString`) treat that as a fast-path no-op.
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
        val font = derivedFont(size, scaleX, skewX)
        val gv = font.createGlyphVector(FRC, text)
        val numGlyphs = gv.numGlyphs
        if (numGlyphs == 0) return null

        // T5 subpixel snap: when font.isSubpixel = false, Skia snaps
        // each glyph's emit position to the integer pixel grid. We
        // apply the same rule here — the cached single-glyph paths are
        // at origin (0, 0), so the snap is just on the per-glyph emit
        // offset (x + glyphPos.x, y + glyphPos.y).
        val builder = SkPathBuilder()
        for (i in 0 until numGlyphs) {
            val glyphId = gv.getGlyphCode(i)
            val pos = gv.getGlyphPosition(i)
            val rawX = x + pos.x.toFloat()
            val rawY = y + pos.y.toFloat()
            val ex = if (isSubpixel) rawX else floor(rawX + 0.5f)
            val ey = if (isSubpixel) rawY else floor(rawY + 0.5f)
            val glyphPath = glyphPathCache.getOrBuild(
                glyphId, size, scaleX, skewX,
            ) { buildGlyphPath(glyphId, size, scaleX, skewX) }
            if (!glyphPath.isEmpty()) {
                builder.addPathOffset(glyphPath, ex, ey)
            }
        }
        val out = builder.detach()
        return if (out.isEmpty()) null else out
    }

    /**
     * Derive an AWT `Font` instance at the requested size with optional
     * scaleX / skewX shear, mirroring the rule used by both
     * [makeTextPath] and [getGlyphPathInternal] (skewX → AWT
     * `AffineTransform.shear(shx, 0)`).
     */
    private fun derivedFont(size: SkScalar, scaleX: SkScalar, skewX: SkScalar): Font {
        val sized = baseFont.deriveFont(size)
        if (scaleX == 1f && skewX == 0f) return sized
        val tx = AffineTransform()
        if (scaleX != 1f) tx.scale(scaleX.toDouble(), 1.0)
        if (skewX != 0f) tx.shear(skewX.toDouble(), 0.0)
        return sized.deriveFont(tx)
    }

    /**
     * Build a single-glyph outline path at origin `(0, 0)` — the
     * builder used by both [glyphPathCache] and [getGlyphPathInternal].
     */
    private fun buildGlyphPath(
        glyphId: Int,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
    ): SkPath {
        val font = derivedFont(size, scaleX, skewX)
        val gv = font.createGlyphVector(FRC, intArrayOf(glyphId))
        val outline = gv.getOutline(0f, 0f)
        return AwtPathConverter.shapeToSkPath(outline)
    }

    /**
     * Single-glyph variant of [makeTextPath] — returns the outline of
     * one glyph (identified by AWT glyph code) at origin `(0, 0)` in
     * the configured size/scaleX/skewX. Used by [SkFont.getPath].
     *
     * AWT `Font.createGlyphVector(frc, glyphCodes: int[])` accepts
     * font-local glyph IDs directly — same convention as Skia's
     * `SkGlyphID` for TrueType-backed typefaces.
     */
    override fun getGlyphPathInternal(
        glyphId: Int,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
    ): SkPath? {
        // T5: route single-glyph lookups through the same cache that
        // [makeTextPath] populates. Cache hits between drawString and
        // getPath callers (rare in practice but free).
        return glyphPathCache.getOrBuild(glyphId, size, scaleX, skewX) {
            buildGlyphPath(glyphId, size, scaleX, skewX)
        }
    }

    /**
     * AWT char→glyph resolution: build a String from the Unicode
     * code points and ask the AWT Font to lay it out, then read the
     * glyph indices. Limitation: BMP-only; supplementary plane code
     * points (≥ U+10000) would need surrogate-pair conversion which
     * upstream Skia handles via SkUTF::ToUTF16. We don't have a
     * non-BMP GM in scope yet, so the simple BMP path is sufficient.
     */
    override fun unicharsToGlyphsInternal(
        unichars: IntArray,
        count: Int,
        glyphs: ShortArray,
    ) {
        if (count == 0) return
        // Build a String from the code points. CharArray + Char(int)
        // works for BMP; non-BMP would need 2 UTF-16 code units per
        // code point, which we'd need to resolve back to single
        // glyphs after the fact (not supported here — see KDoc).
        val chars = CharArray(count) { i -> unichars[i].toChar() }
        val str = String(chars)
        val gv = baseFont.createGlyphVector(FRC, str)
        for (i in 0 until count) {
            // GlyphVector.getGlyphCode returns the AWT Font's notion
            // of the glyph index, which matches the TrueType glyph
            // ID for our Liberation TTFs.
            glyphs[i] = gv.getGlyphCode(i).toShort()
        }
    }

    /**
     * AWT advance width for a single glyph. Reads
     * `GlyphVector.getGlyphMetrics(0).advance` rather than measuring
     * a string, because we may be asked about a specific glyph ID
     * that's not the default glyph for any code point (e.g. a stylistic
     * alternate).
     */
    override fun getGlyphWidthInternal(
        glyphId: Int,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
    ): SkScalar {
        val sized = baseFont.deriveFont(size)
        val font = if (scaleX == 1f && skewX == 0f) {
            sized
        } else {
            val tx = AffineTransform()
            if (scaleX != 1f) tx.scale(scaleX.toDouble(), 1.0)
            if (skewX != 0f) tx.shear(skewX.toDouble(), 0.0)
            sized.deriveFont(tx)
        }
        val gv = font.createGlyphVector(FRC, intArrayOf(glyphId))
        return gv.getGlyphMetrics(0).advance
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
