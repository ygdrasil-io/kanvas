package org.graphiks.kanvas.glyph

import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.glyph.gpu.GPUGlyphRunDescriptor

/**
 * Identifies the strike-specific inputs that affect glyph rasterization and cache reuse.
 *
 * @property typefaceId Stable identifier for the selected typeface.
 * @property sizePx requested strike size in pixels.
 * @property scaleX horizontal scale applied before glyph generation.
 * @property scaleY vertical scale applied before glyph generation.
 * @property subpixelX fractional x placement bucket.
 * @property subpixelY fractional y placement bucket.
 * @property variationCoordinates normalized variation axis coordinates keyed by axis tag.
 */
data class GlyphStrikeKey(
    val typefaceId: TypefaceID,
    val sizePx: Float,
    val scaleX: Float = 1f,
    val scaleY: Float = 1f,
    val subpixelX: Float = 0f,
    val subpixelY: Float = 0f,
    val variationCoordinates: Map<String, Float> = emptyMap(),
)

/**
 * Represents the selected intermediate representation for one glyph before final handoff to
 * GPU-facing artifact types.
 *
 * Implementations can carry outlines, alpha masks, signed-distance-field masks, or deferred color
 * glyph instructions while preserving a single cache and diagnostics surface.
 */
sealed interface GlyphRepresentation {
    /**
     * Glyph identifier represented by this planning result.
     */
    val glyphId: Int
}

/**
 * Plans the artifact route for a glyph run and strike.
 */
interface GlyphArtifactPlanner {
    /**
     * Builds a glyph artifact plan for a run at a specific strike.
     *
     * @param run shaped glyph run to route.
     * @param strikeKey strike inputs that affect rasterization and caching.
     * @return planned pure Kotlin glyph artifacts.
     */
    fun plan(run: GPUGlyphRunDescriptor, strikeKey: GlyphStrikeKey): GlyphArtifactPlan =
        TODO("Plan glyph artifact routes for the pure Kotlin font stack.")
}

/**
 * Records the pure Kotlin planning output for a glyph run.
 *
 * @property run source run used for planning.
 * @property strikeKey strike key used for generation and cache lookup.
 * @property representations per-glyph representations selected by the planner.
 * @property diagnostics non-fatal routing decisions and unsupported-route notes.
 */
data class GlyphArtifactPlan(
    val run: GPUGlyphRunDescriptor,
    val strikeKey: GlyphStrikeKey,
    val representations: List<GlyphRepresentation>,
    val diagnostics: List<GlyphRouteDiagnostic> = emptyList(),
)

/**
 * Describes an outline glyph route before any mask or atlas artifact is generated.
 *
 * @property glyphId glyph identifier for the outline.
 * @property pathCommands compact, renderer-neutral path command stream.
 * @property windingRule fill winding rule name used by downstream coverage generation.
 */
data class OutlineGlyphRepresentation(
    override val glyphId: Int,
    val pathCommands: List<String> = emptyList(),
    val windingRule: String = "nonZero",
) : GlyphRepresentation

/**
 * Generates alpha masks from outline glyph plans.
 */
interface GlyphMaskGenerator {
    /**
     * Rasterizes one outline plan into an A8 alpha mask.
     *
     * @param outline outline route to rasterize.
     * @param strikeKey strike inputs used for mask generation.
     * @return generated A8 glyph mask.
     */
    fun generate(outline: OutlineGlyphRepresentation, strikeKey: GlyphStrikeKey): A8GlyphMask =
        TODO("Generate A8 glyph masks from outline glyph representations.")
}

/**
 * Stores an 8-bit alpha glyph mask in pure Kotlin memory.
 *
 * @property glyphId glyph identifier represented by this mask.
 * @property width mask width in pixels.
 * @property height mask height in pixels.
 * @property left horizontal bearing of the mask origin.
 * @property top vertical bearing of the mask origin.
 * @property rowBytes number of bytes per mask row.
 * @property pixels Immutable alpha samples in row-major order, encoded as
 * integer byte values in the `0..255` range.
 */
data class A8GlyphMask(
    override val glyphId: Int,
    val width: Int,
    val height: Int,
    val left: Int = 0,
    val top: Int = 0,
    val rowBytes: Int = width,
    val pixels: List<Int> = List(rowBytes * height) { 0 },
) : GlyphRepresentation

/**
 * Generates signed-distance-field glyph masks for scalable text rendering.
 */
interface SDFGlyphGenerator {
    /**
     * Converts an outline plan into a signed-distance-field mask.
     *
     * @param outline outline route to convert.
     * @param strikeKey strike inputs used for SDF generation.
     * @return generated SDF glyph mask.
     */
    fun generate(outline: OutlineGlyphRepresentation, strikeKey: GlyphStrikeKey): SDFGlyphMask =
        TODO("Generate signed-distance-field glyph masks from outline glyph representations.")
}

/**
 * Stores a signed-distance-field glyph mask in pure Kotlin memory.
 *
 * @property glyphId glyph identifier represented by this mask.
 * @property width mask width in pixels.
 * @property height mask height in pixels.
 * @property distanceRange distance range encoded by one byte of mask data.
 * @property pixels Immutable signed-distance samples in row-major order,
 * encoded as integer byte values in the `0..255` range.
 */
data class SDFGlyphMask(
    override val glyphId: Int,
    val width: Int,
    val height: Int,
    val distanceRange: Float,
    val pixels: List<Int> = List(width * height) { 0 },
) : GlyphRepresentation

/**
 * Packs generated glyph masks into atlas coordinates before GPU artifact construction.
 */
interface GlyphAtlasPacker {
    /**
     * Packs A8 masks into atlas placements.
     *
     * @param masks masks to place in an atlas.
     * @return placement records for each packed mask.
     */
    fun pack(masks: List<A8GlyphMask>): List<GlyphAtlasPlacement> =
        TODO("Pack A8 glyph masks into a glyph atlas.")
}

/**
 * Describes the atlas rectangle assigned to one glyph mask.
 *
 * @property glyphId glyph identifier assigned to this placement.
 * @property x left atlas coordinate in pixels.
 * @property y top atlas coordinate in pixels.
 * @property width placement width in pixels.
 * @property height placement height in pixels.
 */
data class GlyphAtlasPlacement(
    val glyphId: Int,
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/**
 * Internal builder for A8 atlas upload inputs before the public GPU handoff artifact is produced
 * by the GPU API module.
 */
interface GlyphAtlasArtifactBuilder {
    /**
     * Builds a module-local atlas build result from masks and placements.
     *
     * @param masks source masks included in the atlas.
     * @param placements atlas placements assigned to source masks.
     * @return module-local build result for downstream GPU API adaptation.
     */
    fun build(masks: List<A8GlyphMask>, placements: List<GlyphAtlasPlacement>): GlyphAtlasBuildResult =
        TODO("Build module-local A8 glyph atlas artifact inputs.")
}

/**
 * Internal builder for SDF atlas upload inputs before the public GPU handoff artifact is produced
 * by the GPU API module.
 */
interface SDFGlyphAtlasArtifactBuilder {
    /**
     * Builds a module-local SDF atlas build result from masks.
     *
     * @param masks source SDF masks included in the atlas.
     * @return module-local build result for downstream GPU API adaptation.
     */
    fun build(masks: List<SDFGlyphMask>): SDFGlyphAtlasBuildResult =
        TODO("Build module-local SDF glyph atlas artifact inputs.")
}

/**
 * Module-local A8 atlas build result that avoids duplicating public GPU API artifact classes.
 *
 * @property width atlas width in pixels.
 * @property height atlas height in pixels.
 * @property placements packed glyph placements.
 * @property pixels Immutable atlas alpha pixels in row-major order, encoded as
 * integer byte values in the `0..255` range.
 */
data class GlyphAtlasBuildResult(
    val width: Int,
    val height: Int,
    val placements: List<GlyphAtlasPlacement>,
    val pixels: List<Int>,
)

/**
 * Module-local SDF atlas build result that avoids duplicating public GPU API artifact classes.
 *
 * @property width atlas width in pixels.
 * @property height atlas height in pixels.
 * @property distanceRange distance range encoded by atlas samples.
 * @property pixels Immutable atlas signed-distance samples in row-major order,
 * encoded as integer byte values in the `0..255` range.
 */
data class SDFGlyphAtlasBuildResult(
    val width: Int,
    val height: Int,
    val distanceRange: Float,
    val pixels: List<Int>,
)

/**
 * Caches glyph plans and generated representations under a strike-aware budget.
 */
interface GlyphCache {
    /**
     * Looks up a cached representation.
     *
     * @param strikeKey strike key for the cached entry.
     * @param glyphId glyph identifier for the cached entry.
     * @return cached representation or null when absent.
     */
    fun get(strikeKey: GlyphStrikeKey, glyphId: Int): GlyphRepresentation? =
        TODO("Look up glyph representations in the glyph cache.")

    /**
     * Stores a representation in the cache.
     *
     * @param strikeKey strike key for the cached entry.
     * @param representation representation to store.
     */
    fun put(strikeKey: GlyphStrikeKey, representation: GlyphRepresentation) {
        TODO("Store glyph representations in the glyph cache.")
    }
}

/**
 * Defines memory and entry-count limits for glyph cache implementations.
 *
 * @property maxBytes maximum approximate cache memory budget.
 * @property maxEntries maximum number of glyph entries.
 */
data class GlyphCacheBudget(
    val maxBytes: Long,
    val maxEntries: Int,
)

/**
 * Describes a routing decision, alternate route, or unsupported glyph condition observed while planning.
 *
 * @property glyphId glyph identifier associated with the diagnostic when available.
 * @property route selected route, such as outline, A8, SDF, bitmap, or SVG.
 * @property message human-readable diagnostic message.
 * @property severity severity label for logs and PM evidence.
 */
data class GlyphRouteDiagnostic(
    val glyphId: Int?,
    val route: String,
    val message: String,
    val severity: String = "info",
)
