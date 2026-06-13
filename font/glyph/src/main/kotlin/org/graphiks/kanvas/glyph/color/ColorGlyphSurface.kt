package org.graphiks.kanvas.glyph.color

import org.graphiks.kanvas.glyph.GlyphRepresentation
import org.graphiks.kanvas.glyph.GlyphRouteDiagnostic
import org.graphiks.kanvas.glyph.GlyphStrikeKey
import org.graphiks.kanvas.glyph.gpu.GPUGlyphRunDescriptor

/**
 * Plans color glyph routes for emoji, COLR/CPAL, bitmap strikes, PNG glyphs, and SVG glyphs.
 */
interface ColorGlyphPlanner {
    /**
     * Plans color glyph representations for a shaped glyph run.
     *
     * @param run shaped glyph run to inspect.
     * @param strikeKey strike inputs used for color glyph selection.
     * @return color glyph planning result.
     */
    fun plan(run: GPUGlyphRunDescriptor, strikeKey: GlyphStrikeKey): ColorGlyphPlanningResult =
        TODO("Plan color glyph routes for the pure Kotlin font stack.")
}

/**
 * Plans COLR glyph paint graphs and palette usage.
 */
interface COLRGlyphPlanner {
    /**
     * Builds a COLR paint graph for one glyph.
     *
     * @param glyphId glyph identifier to plan.
     * @param palette palette selected for the glyph.
     * @return paint graph for the glyph.
     */
    fun plan(glyphId: Int, palette: CPALPalette): COLRPaintGraph =
        TODO("Plan COLR paint graphs for color glyphs.")
}

/**
 * Describes a renderer-neutral COLR paint graph.
 *
 * @property root root paint node for the graph.
 * @property nodes flattened node list for validation and traversal.
 */
data class COLRPaintGraph(
    val root: COLRPaintNode,
    val nodes: List<COLRPaintNode>,
)

/**
 * Represents one node in a COLR paint graph.
 *
 * @property id stable node identifier within a paint graph.
 * @property kind paint operation kind.
 * @property children child node identifiers.
 * @property paletteIndex optional CPAL palette index consumed by this node.
 */
data class COLRPaintNode(
    val id: Int,
    val kind: String,
    val children: List<Int> = emptyList(),
    val paletteIndex: Int? = null,
)

/**
 * Stores a CPAL palette in pure Kotlin form.
 *
 * @property index palette index selected from the font.
 * @property colors packed ARGB colors in palette order.
 * @property labels optional human-readable palette labels.
 */
data class CPALPalette(
    val index: Int,
    val colors: List<Int>,
    val labels: List<String> = emptyList(),
)

/**
 * Selects embedded bitmap strikes for color glyph alternate routes.
 */
interface BitmapStrikeSelector {
    /**
     * Selects the best bitmap strike for a glyph and requested size.
     *
     * @param glyphId glyph identifier to resolve.
     * @param requestedSizePx requested size in pixels.
     * @return selected bitmap strike or null when no suitable strike exists.
     */
    fun select(glyphId: Int, requestedSizePx: Float): BitmapStrikeSelection? =
        TODO("Select embedded bitmap strikes for color glyph rendering.")
}

/**
 * Describes a selected embedded bitmap strike.
 *
 * @property glyphId glyph identifier represented by the bitmap.
 * @property width bitmap width in pixels.
 * @property height bitmap height in pixels.
 * @property format source bitmap format label.
 */
data class BitmapStrikeSelection(
    val glyphId: Int,
    val width: Int,
    val height: Int,
    val format: String,
)

/**
 * Decodes PNG-backed glyph images into pure Kotlin byte buffers.
 */
interface PNGGlyphDecoder {
    /**
     * Decodes a PNG glyph payload.
     *
     * @param glyphId glyph identifier associated with the PNG payload.
     * @param bytes encoded PNG bytes.
     * @return decoded PNG glyph image.
     */
    fun decode(glyphId: Int, bytes: ByteArray): PNGGlyphImage =
        TODO("Decode PNG glyph payloads for color glyph rendering.")
}

/**
 * Stores a decoded PNG glyph image.
 *
 * @property glyphId glyph identifier represented by the image.
 * @property width image width in pixels.
 * @property height image height in pixels.
 * @property pixels Immutable decoded pixels in row-major order as packed ARGB
 * integers.
 */
data class PNGGlyphImage(
    val glyphId: Int,
    val width: Int,
    val height: Int,
    val pixels: List<Int>,
)

/**
 * Renders parsed SVG glyph documents into pure Kotlin image buffers or vector plans.
 */
interface SVGGlyphRenderer {
    /**
     * Renders a parsed SVG glyph document at a requested size.
     *
     * @param document parsed SVG glyph document.
     * @param sizePx requested render size in pixels.
     * @return rendered SVG glyph image.
     */
    fun render(document: SVGGlyphDocument, sizePx: Float): SVGGlyphImage =
        TODO("Render SVG glyph documents for color glyph rendering.")
}

/**
 * Parses SVG glyph payloads into renderer-neutral documents.
 */
interface SVGGlyphParser {
    /**
     * Parses an SVG glyph payload.
     *
     * @param glyphId glyph identifier associated with the SVG payload.
     * @param text UTF-8 decoded SVG text.
     * @return parsed SVG glyph document.
     */
    fun parse(glyphId: Int, text: String): SVGGlyphDocument =
        TODO("Parse SVG glyph payloads for color glyph rendering.")
}

/**
 * Stores a parsed SVG glyph document.
 *
 * @property glyphId glyph identifier represented by the document.
 * @property viewBox SVG viewBox values encoded as minX, minY, width, and height.
 * @property elements renderer-neutral element descriptions.
 */
data class SVGGlyphDocument(
    val glyphId: Int,
    val viewBox: List<Float> = emptyList(),
    val elements: List<String> = emptyList(),
)

/**
 * Stores a rendered SVG glyph image.
 *
 * @property glyphId glyph identifier represented by the image.
 * @property width image width in pixels.
 * @property height image height in pixels.
 * @property pixels Immutable rendered pixels in row-major order as packed ARGB
 * integers.
 */
data class SVGGlyphImage(
    val glyphId: Int,
    val width: Int,
    val height: Int,
    val pixels: List<Int>,
)

/**
 * Dispatches emoji glyphs to the best available color glyph route.
 */
interface EmojiGlyphDispatcher {
    /**
     * Selects a color glyph route for an emoji glyph.
     *
     * @param glyphId glyph identifier to dispatch.
     * @param strikeKey strike inputs used for route selection.
     * @return dispatch result for the emoji glyph.
     */
    fun dispatch(glyphId: Int, strikeKey: GlyphStrikeKey): EmojiGlyphDispatch =
        TODO("Dispatch emoji glyphs to COLR, bitmap, PNG, SVG, or outline alternate routes.")
}

/**
 * Records one emoji dispatch decision.
 *
 * @property glyphId glyph identifier being dispatched.
 * @property route selected color glyph route.
 * @property diagnostics diagnostics emitted while selecting the route.
 */
data class EmojiGlyphDispatch(
    val glyphId: Int,
    val route: String,
    val diagnostics: List<ColorGlyphDiagnostic> = emptyList(),
)

/**
 * Describes a color glyph planning result without duplicating public GPU API plan classes.
 *
 * @property representations glyph representations selected by color planning.
 * @property diagnostics color-specific diagnostics.
 */
data class ColorGlyphPlanningResult(
    val representations: List<GlyphRepresentation>,
    val diagnostics: List<ColorGlyphDiagnostic> = emptyList(),
)

/**
 * Describes a color glyph routing decision, alternate route, or unsupported source condition.
 *
 * @property glyphId glyph identifier associated with the diagnostic when available.
 * @property route selected or attempted color route.
 * @property message human-readable diagnostic message.
 * @property severity severity label for logs and PM evidence.
 */
data class ColorGlyphDiagnostic(
    val glyphId: Int?,
    val route: String,
    val message: String,
    val severity: String = "info",
) {
    /**
     * Converts this color diagnostic into a generic glyph route diagnostic.
     *
     * @return generic glyph route diagnostic with the same core fields.
     */
    fun toGlyphRouteDiagnostic(): GlyphRouteDiagnostic =
        GlyphRouteDiagnostic(
            glyphId = glyphId,
            route = route,
            message = message,
            severity = severity,
        )
}
