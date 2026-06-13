package org.graphiks.kanvas.font.scaler

import org.graphiks.kanvas.font.sfnt.OpenTypeFaceData

/**
 * Scales glyph outlines and metrics from parsed font data into requested design positions.
 */
interface GlyphScaler {
    /**
     * Produces an outline for one glyph at a variation position.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variable-font axis position to apply before scaling.
     * @return Scaled glyph outline.
     */
    fun outline(glyphId: UInt, position: VariationPosition = VariationPosition()): GlyphOutline =
        TODO("Implement glyph outline scaling.")

    /**
     * Produces metrics for one glyph at a variation position.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variable-font axis position to apply before measuring.
     * @return Scaled glyph metrics.
     */
    fun metrics(glyphId: UInt, position: VariationPosition = VariationPosition()): GlyphMetrics =
        TODO("Implement glyph metric scaling.")
}

/**
 * Scaled outline geometry for one glyph.
 *
 * @property glyphId Font-specific glyph identifier.
 * @property contours Opaque contour commands until the geometry command model is finalized.
 */
data class GlyphOutline(
    val glyphId: UInt,
    val contours: List<String> = emptyList(),
)

/**
 * Scaled metrics for a glyph.
 *
 * @property advanceX Horizontal advance in user-independent font units.
 * @property advanceY Vertical advance in user-independent font units.
 * @property bounds Tight glyph bounds when available.
 */
data class GlyphMetrics(
    val advanceX: Double,
    val advanceY: Double,
    val bounds: GlyphBounds,
)

/**
 * Axis-aligned glyph bounds.
 *
 * @property left Minimum x coordinate.
 * @property top Minimum y coordinate.
 * @property right Maximum x coordinate.
 * @property bottom Maximum y coordinate.
 */
data class GlyphBounds(
    val left: Double,
    val top: Double,
    val right: Double,
    val bottom: Double,
)

/**
 * TrueType `glyf` table scaler.
 *
 * @property face Parsed OpenType face data containing TrueType outline tables.
 */
class TrueTypeGlyfScaler(
    private val face: OpenTypeFaceData,
) : GlyphScaler {
    /**
     * Produces a TrueType glyph outline.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variable-font axis position.
     * @return Scaled TrueType glyph outline.
     */
    override fun outline(glyphId: UInt, position: VariationPosition): GlyphOutline =
        TODO("Implement TrueType glyf outline scaling for ${face.id.value}.")

    /**
     * Produces TrueType glyph metrics.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variable-font axis position.
     * @return Scaled TrueType glyph metrics.
     */
    override fun metrics(glyphId: UInt, position: VariationPosition): GlyphMetrics =
        TODO("Implement TrueType glyf metric scaling for ${face.id.value}.")
}

/**
 * Compact Font Format `CFF ` scaler.
 *
 * @property face Parsed OpenType face data containing CFF outlines.
 */
class CFFScaler(
    private val face: OpenTypeFaceData,
) : GlyphScaler {
    /**
     * Produces a CFF glyph outline.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variation position for synthetic or blended metrics when applicable.
     * @return Scaled CFF glyph outline.
     */
    override fun outline(glyphId: UInt, position: VariationPosition): GlyphOutline =
        TODO("Implement CFF outline scaling for ${face.id.value}.")

    /**
     * Produces CFF glyph metrics.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variation position for synthetic or blended metrics when applicable.
     * @return Scaled CFF glyph metrics.
     */
    override fun metrics(glyphId: UInt, position: VariationPosition): GlyphMetrics =
        TODO("Implement CFF metric scaling for ${face.id.value}.")
}

/**
 * Compact Font Format 2 scaler for variable CFF outlines.
 *
 * @property face Parsed OpenType face data containing CFF2 outlines.
 */
class CFF2Scaler(
    private val face: OpenTypeFaceData,
) : GlyphScaler {
    /**
     * Produces a CFF2 glyph outline.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variable-font axis position.
     * @return Scaled CFF2 glyph outline.
     */
    override fun outline(glyphId: UInt, position: VariationPosition): GlyphOutline =
        TODO("Implement CFF2 outline scaling for ${face.id.value}.")

    /**
     * Produces CFF2 glyph metrics.
     *
     * @param glyphId Font-specific glyph identifier.
     * @param position Variable-font axis position.
     * @return Scaled CFF2 glyph metrics.
     */
    override fun metrics(glyphId: UInt, position: VariationPosition): GlyphMetrics =
        TODO("Implement CFF2 metric scaling for ${face.id.value}.")
}

/**
 * Interpreter boundary for CFF and CFF2 charstrings.
 */
interface CFFCharStringInterpreter {
    /**
     * Interprets a raw charstring into an opaque outline command list.
     *
     * @param charString Raw Type 2 or CFF2 charstring bytes.
     * @param position Variation position used by CFF2 blend operators.
     * @return Opaque outline commands until the geometry command model is finalized.
     */
    fun interpret(charString: ByteArray, position: VariationPosition = VariationPosition()): List<String> =
        TODO("Implement CFF charstring interpretation.")
}

/**
 * User-space position in a variable font design space.
 *
 * @property axes Axis values keyed by four-character OpenType axis tags.
 */
data class VariationPosition(
    val axes: Map<String, Double> = emptyMap(),
)

/**
 * Normalizes user-space variation positions into the scalar domain expected by glyph variation tables.
 */
interface VariationNormalizer {
    /**
     * Normalizes a variation position.
     *
     * @param position User-space variation position.
     * @return Normalized axis values keyed by axis tag.
     */
    fun normalize(position: VariationPosition): Map<String, Double> = TODO("Implement variation normalization.")
}
