@file:Suppress("DEPRECATION")

package org.skia.foundation.awt

import org.skia.foundation.SkFontArguments
import org.skia.foundation.SkFontMetrics
import org.skia.foundation.SkFontStyle
import org.skia.foundation.SkFontVariation
import org.skia.foundation.SkPath
import org.skia.foundation.SkPathBuilder
import org.skia.foundation.SkTextEncoding
import org.skia.foundation.SkTypeface
import org.graphiks.math.SkRect
import org.graphiks.math.SkScalar
import java.awt.Font
import java.awt.font.FontRenderContext
import java.awt.font.TextAttribute
import java.awt.geom.AffineTransform
import kotlin.math.floor

/**
 * **Legacy optional JVM/AWT backend** — Ce fichier expose la surface API Skia
 * (`SkTypeface` / `SkFont` / `SkFontMetrics` / …) mais l'implémentation
 * sous-jacente repose sur **`java.awt.Font` + `java.awt.font.GlyphVector`**.
 * Les chemins fonts portables doivent utiliser
 * [org.skia.foundation.opentype.OpenTypeTypeface] et le manager
 * [org.skia.foundation.LiberationFontMgr].
 *
 * Conséquences :
 *  - Les métriques peuvent diverger de 1-2 ulps des valeurs upstream.
 *  - Le rasterizer AA des glyphes est celui d'AWT (relayé via SkPath →
 *    notre scanline-fill 4×4), pas le rasterizer FreeType de Skia. Pas
 *    encore exercé en T2 — `drawString` reste no-op jusqu'à T3.
 *  - `SkFont.Edging.kSubpixelAntiAlias` est dégradé silencieusement vers
 *    `kAntiAlias` (cf. archives/MIGRATION_PLAN_TEXT.md §R3).
 *
 * Si un jour on remplace AWT par FreeType+JNI ou par un rasterizer custom,
 * **seul ce fichier (et ses pairs `Awt*.kt`) doit changer** — l'API publique
 * reste figée sur la signature Skia.
 */
/**
 * Phase I4.2 — output of [AwtTypeface.shapeAwtRun]. Carries shaped
 * glyph IDs + run-local positions + char-cluster mapping for one
 * single-direction text segment.
 *
 * @property glyphIds      per-glyph font-local IDs.
 * @property positions     `(x, y)` per glyph in run-local coords,
 *                         length `n * 2 + 2` — index `n*2` holds the
 *                         pen position after the last glyph (the run's
 *                         total advance).
 * @property charClusters  UTF-16 char index *relative to the run
 *                         start* that each glyph derives from. For RTL
 *                         runs, indices descend monotonically (last
 *                         char in the range produces the first glyph).
 * @property advanceX      total run advance — same value as
 *                         `positions[n * 2]` for convenience.
 */
@Deprecated(
    message = "AWT shaping is a legacy optional JVM surface. Prefer portable OpenType text paths when complex JVM/AWT shaping is not required.",
)
public data class AwtShapedRun(
    val glyphIds: IntArray,
    val positions: FloatArray,
    val charClusters: IntArray,
    val advanceX: Float,
) {
    // Identity equals/hashCode — these arrays are owned by the producer
    // and reused ; data-class semantics on byte-equal arrays would be
    // expensive to no benefit.
    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}

@Deprecated(
    message = "AwtTypeface is a legacy optional JVM backend. Use OpenTypeTypeface for portable fonts, or opt in through cpu-raster AWT APIs only when required.",
)
public class AwtTypeface internal constructor(
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
     * R-suivi.43 — does this typeface carry a glyph for the Unicode
     * code point [cp]? Routes through AWT `Font.canDisplay(int)` which
     * answers true when the font has a non-`.notdef` glyph for the
     * code point. Kept for the legacy AWT typeface surface until the
     * JavaTextLayout shaper is replaced by #927.
     */
    internal fun canDisplayCodepoint(cp: Int): Boolean = baseFont.canDisplay(cp)

    /**
     * Mirrors [SkTypeface.countGlyphs] — AWT exposes the underlying
     * font's `maxp.numGlyphs` directly via `java.awt.Font.getNumGlyphs()`.
     * Skia's contract is "number of distinct glyphs in this typeface",
     * which is exactly what AWT returns for TrueType-backed fonts.
     */
    override fun countGlyphs(): Int = baseFont.numGlyphs

    /**
     * Mirrors [SkTypeface.getFamilyName] — appends the AWT
     * `Font.getFamily(Locale.ROOT)` to [name]. `Locale.ROOT` keeps
     * the result deterministic across JVM locales (otherwise AWT
     * would translate canonical family names like "Liberation Sans"
     * into the user's locale).
     */
    override fun getFamilyName(name: StringBuilder) {
        name.append(baseFont.getFamily(java.util.Locale.ROOT))
    }

    /**
     * Mirrors [SkTypeface.getGlyphBoundsInternal] — tight visual bbox
     * of a single glyph at the requested `size / scaleX / skewX`.
     * Uses the same `GlyphVector.getVisualBounds()` route as
     * [measureTextInternal] (the multi-glyph path), but scoped to a
     * single glyph ID via `Font.createGlyphVector(frc, int[])`.
     *
     * The bbox is in baseline-relative coordinates (Skia y-down :
     * negative `top` = above baseline, positive `bottom` = below).
     */
    override fun getGlyphBoundsInternal(
        glyphId: Int,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
    ): SkRect {
        val font = derivedFont(size, scaleX, skewX)
        val gv = font.createGlyphVector(FRC, intArrayOf(glyphId))
        val vb = gv.getGlyphVisualBounds(0).bounds2D
        return SkRect(
            vb.x.toFloat(),
            vb.y.toFloat(),
            (vb.x + vb.width).toFloat(),
            (vb.y + vb.height).toFloat(),
        )
    }

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
     * see `archives/MIGRATION_PLAN_TEXT.md` §R3 / [SkFontHinting] for the
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

    /**
     * Phase I4.2 — shape one bidi run via `Font.layoutGlyphVector(...,
     * Font.LAYOUT_LEFT_TO_RIGHT | LAYOUT_RIGHT_TO_LEFT)`, AWT's bidi-
     * aware shaping entry point. AWT performs basic kerning + ligature
     * substitution + glyph reordering for the requested direction
     * within the supplied UTF-16 character range.
     *
     * @param chars        input UTF-16 character buffer.
     * @param start        run start, inclusive (UTF-16 index into [chars]).
     * @param limit        run end, exclusive.
     * @param leftToRight  `true` → LTR layout ; `false` → RTL layout.
     * @return one [AwtShapedRun] containing per-glyph IDs, `(x, y)`
     *         positions (run-local coords, origin = run start), and
     *         the UTF-16 char index into [chars] each glyph derives
     *         from. The run advance is `glyphPositions[2*n]` (AWT
     *         appends the post-last-glyph pen position).
     */
    public fun shapeAwtRun(
        chars: CharArray,
        start: Int,
        limit: Int,
        leftToRight: Boolean,
        size: SkScalar,
        scaleX: SkScalar,
        skewX: SkScalar,
    ): AwtShapedRun {
        val font = derivedFont(size, scaleX, skewX)
        val flags = if (leftToRight) Font.LAYOUT_LEFT_TO_RIGHT else Font.LAYOUT_RIGHT_TO_LEFT
        val gv = font.layoutGlyphVector(FRC, chars, start, limit, flags)
        val n = gv.numGlyphs
        val glyphIds = IntArray(n) { gv.getGlyphCode(it) }
        val positions = FloatArray(n * 2 + 2)
        for (i in 0 until n) {
            val pos = gv.getGlyphPosition(i)
            positions[i * 2] = pos.x.toFloat()
            positions[i * 2 + 1] = pos.y.toFloat()
        }
        // AWT appends the pen position after the last glyph at index n.
        val end = gv.getGlyphPosition(n)
        positions[n * 2] = end.x.toFloat()
        positions[n * 2 + 1] = end.y.toFloat()
        // Glyph→char map, indices RELATIVE TO `start` (so the caller
        // can lift them to UTF-16 by adding `start` back, or to UTF-8
        // via a precomputed prefix-sum table).
        val charClusters = IntArray(n) { gv.getGlyphCharIndex(it) }
        return AwtShapedRun(
            glyphIds = glyphIds,
            positions = positions,
            charClusters = charClusters,
            advanceX = end.x.toFloat(),
        )
    }

    /**
     * R-final.9 — apply [args] to this AWT-backed typeface.
     *
     * **Variation axes** (`SkFontArguments::VariationPosition`) are
     * mapped onto AWT [TextAttribute] values via `Font.deriveFont(Map)` :
     *
     *  | Skia tag  | AWT attribute            | Conversion                                 |
     *  |-----------|--------------------------|--------------------------------------------|
     *  | `wght`    | [TextAttribute.WEIGHT]   | linear remap `[100..900] → [0.5f..2.5f]`   |
     *  | `wdth`    | [TextAttribute.WIDTH]    | linear remap `[50..200] → [0.5f..1.5f]`    |
     *  | `slnt`    | [TextAttribute.POSTURE]  | `slnt-degrees / -90f` (Skia degrees CCW    |
     *  |           |                          |  → AWT shear ratio)                        |
     *  | `ital`    | [TextAttribute.POSTURE]  | `0` → `0f`, `1` → `0.2f` (AWT italic)      |
     *  | `opsz`    | (none)                   | dropped — AWT has no optical-size knob     |
     *  | `GRAD`    | (none)                   | dropped — vendor grade extension           |
     *  | `XHGT`    | (none)                   | dropped — design x-height axis             |
     *  | other     | (none)                   | dropped — unknown to AWT                   |
     *
     * Multiple coordinates on the same axis : the **last** wins, which
     * matches Skia's deterministic-iteration semantics. Axes AWT can't
     * model are silently ignored — the returned typeface still honours
     * the axes that mapped successfully (matching Skia's "best effort"
     * contract for backends with limited variation support).
     *
     * **Collection index** (`SkFontArguments::fCollectionIndex`) is
     * dropped — AWT's `Font` always exposes face 0 of a `.ttc` file ;
     * we keep `this`'s baseFont (already-loaded face 0) regardless.
     *
     * **Palette** (`SkFontArguments::Palette`) is ignored — COLR v0/v1
     * support is gated on `STUB.COLR_V1` (see `API_FINALIZATION_PLAN.md`).
     *
     * Returns a new [AwtTypeface] sharing the same [SkFontStyle] as the
     * source. Never returns `null` — even if every axis is unmappable
     * the result is a clone of the base font (an identity transform).
     */
    override fun makeClone(args: SkFontArguments): SkTypeface {
        val coords = args.variationDesignPosition.coordinates
        if (coords.isEmpty()) {
            // Identity clone — return a fresh wrapper around the same
            // baseFont so caller-side identity comparisons don't alias
            // (`Font.deriveFont(0f).hashCode() != original.hashCode()`),
            // matching Skia's "always a fresh sk_sp" contract.
            return AwtTypeface(baseFont.deriveFont(baseFont.size2D), fontStyle)
        }
        val attrs = HashMap<TextAttribute, Any>()
        for (coord in coords) {
            when (SkFontVariation.Tag(coord.axis).toString()) {
                "wght" -> {
                    // Skia design value spans roughly 100..900 (typographic
                    // weight). AWT's [TextAttribute.WEIGHT] uses 0.5f..2.5f
                    // (REGULAR = 1.0). Linear remap centred on REGULAR/400.
                    val w = coord.value.coerceIn(1f, 1000f)
                    attrs[TextAttribute.WEIGHT] = ((w - 400f) / 500f) + 1f
                }
                "wdth" -> {
                    // Skia: 50..200 (% of normal). AWT: 0.5f..1.5f.
                    val w = coord.value.coerceIn(50f, 200f)
                    attrs[TextAttribute.WIDTH] = w / 100f
                }
                "slnt" -> {
                    // Skia: degrees counterclockwise (negative = forward).
                    // AWT [TextAttribute.POSTURE] is a shear ratio (0 =
                    // upright, 0.2 ≈ standard italic). Approximate via
                    // `-degrees/90` so a +14° forward slant lands near
                    // AWT's 0.156 (close to ITALIC = 0.2).
                    attrs[TextAttribute.POSTURE] = coord.value / -90f
                }
                "ital" -> {
                    // ital is a binary (0 or 1) ; map onto AWT's POSTURE
                    // standard italic ratio 0.2 (matches REGULAR vs ITALIC).
                    attrs[TextAttribute.POSTURE] =
                        if (coord.value >= 0.5f) TextAttribute.POSTURE_OBLIQUE else TextAttribute.POSTURE_REGULAR
                }
                else -> {
                    // opsz, GRAD, XHGT, XOPQ, YOPQ, custom — AWT has no
                    // matching TextAttribute. Drop silently. A future JNI
                    // backend (FreeType / Fontations) can honour these by
                    // overriding `makeClone` itself ; meanwhile we keep
                    // the GM compiling without the axis applied.
                }
            }
        }
        if (attrs.isEmpty()) {
            // Every coordinate was unmappable — return a fresh wrapper
            // (same as the empty-coordinates branch above).
            return AwtTypeface(baseFont.deriveFont(baseFont.size2D), fontStyle)
        }
        return AwtTypeface(baseFont.deriveFont(attrs), fontStyle)
    }

    public companion object {
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
        public val DEFAULT: AwtTypeface = AwtTypeface(makeDefaultBase())

        /**
         * R-final.7 — build an AWT-backed typeface from raw TTF/OTF
         * bytes. Mirrors upstream's `SkTypeface::MakeFromStream` /
         * `SkFontMgr::makeFromData` path. Returns `null` if AWT can't
         * parse the bytes (corrupt or unsupported font format — AWT only
         * grokes TrueType / OpenType outlines, not bitmap CBDT/Sbix or
         * COLRv1).
         */
        fun createFromBytes(bytes: ByteArray, style: SkFontStyle = SkFontStyle.Normal()): AwtTypeface? {
            val baseFont = try {
                Font.createFont(Font.TRUETYPE_FONT, bytes.inputStream())
            } catch (e: Exception) {
                return null
            }
            return AwtTypeface(baseFont, style)
        }
    }
}
