package org.graphiks.kanvas.glyph

import org.graphiks.kanvas.font.TypefaceID
import org.graphiks.kanvas.glyph.gpu.GPUGlyphRunDescriptor
import java.security.MessageDigest
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.roundToInt
import kotlin.math.sqrt

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
interface GlyphRepresentation {
    /**
     * Glyph identifier represented by this planning result.
     */
    val glyphId: Int
}

/**
 * Names the renderer-neutral representation routes supported by the module-local glyph planner.
 *
 * The route is deliberately narrower than a GPU artifact type. It only says which pure Kotlin
 * representation should be handed to the next pipeline stage, leaving atlas packing, upload
 * planning, and renderer policy outside this module.
 *
 * @property diagnosticName stable lowercase route label used in diagnostics and PM evidence.
 */
enum class GlyphArtifactRoute(
    val diagnosticName: String,
) {
    /**
     * Keeps the glyph as vector outline commands for downstream coverage or GPU path handling.
     */
    OUTLINE("outline"),

    /**
     * Uses a single-channel 8-bit alpha mask representation.
     */
    A8("a8"),

    /**
     * Uses a signed-distance-field mask representation for scale-tolerant text rendering.
     */
    SDF("sdf"),
}

/**
 * Describes the route preference and already-available representations for one planning pass.
 *
 * The planner is intentionally limited to choosing from supplied pure Kotlin representations. It
 * does not parse font tables, invoke a scaler, rasterize outlines, allocate atlas space, or create
 * GPU artifacts. Callers that can produce multiple representations list all of them by glyph ID,
 * and the planner selects the first representation whose route appears in [preferredRoutes].
 *
 * @property preferredRoutes ordered route preference. Earlier routes are selected before later
 * routes when multiple representations are available for the same glyph.
 * @property availableRepresentations candidate representations keyed by glyph identifier. Entries
 * for glyphs not present in the current run are ignored.
 */
data class GlyphArtifactRouteRequest(
    val preferredRoutes: List<GlyphArtifactRoute>,
    val availableRepresentations: Map<Int, List<GlyphRepresentation>> = emptyMap(),
)

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
    fun plan(run: GPUGlyphRunDescriptor, strikeKey: GlyphStrikeKey): GlyphArtifactPlan
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
        rasterizeLinearOutlineToA8(outline, strikeKey)
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
        generateLinearOutlineSDF(outline, strikeKey)
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
 * Selects pure Kotlin glyph representations for a run from the caller's available route request.
 *
 * The planner is deterministic: glyphs are considered in run order, routes are considered in the
 * order supplied by [GlyphArtifactRouteRequest.preferredRoutes], and candidates are considered in
 * their original list order. Selected representations can be mirrored into an optional
 * module-local [cache], but route selection itself stays renderer-independent and never allocates
 * GPU resources.
 *
 * @property request route preferences and available representations for this planning pass.
 * @property cache optional cache that receives selected representations and can satisfy a glyph
 * when the current request does not include an in-memory candidate for an accepted route.
 */
class GlyphArtifactRoutePlanner(
    private val request: GlyphArtifactRouteRequest,
    private val cache: GlyphCache? = null,
) : GlyphArtifactPlanner {
    /**
     * Builds a route plan by selecting one representation per glyph when a requested route exists.
     *
     * @param run shaped glyph run whose glyph IDs define planning order.
     * @param strikeKey strike inputs used for optional cache lookup and storage.
     * @return route plan containing selected representations and non-fatal diagnostics for glyphs
     * that could not satisfy the requested route set.
     */
    override fun plan(run: GPUGlyphRunDescriptor, strikeKey: GlyphStrikeKey): GlyphArtifactPlan {
        val representations = mutableListOf<GlyphRepresentation>()
        val diagnostics = mutableListOf<GlyphRouteDiagnostic>()

        run.glyphIDs.forEach { glyphId ->
            val selected = request.selectRepresentation(glyphId, cache?.get(strikeKey, glyphId))

            if (selected == null) {
                diagnostics += request.unsupportedDiagnostic(
                    glyphId = glyphId,
                    availableRepresentations = request.availableRepresentations[glyphId].orEmpty(),
                )
            } else {
                representations += selected
                cache?.put(strikeKey, selected)
            }
        }

        return GlyphArtifactPlan(
            run = run,
            strikeKey = strikeKey,
            representations = representations.toList(),
            diagnostics = diagnostics.toList(),
        )
    }
}

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
    fun pack(masks: List<A8GlyphMask>): List<GlyphAtlasPlacement>
}

/**
 * Deterministic row-based atlas packer for pure Kotlin glyph masks.
 *
 * Masks are placed in input order. Each row starts at the configured padding, and masks wrap to the
 * next row when the next placement would exceed the configured atlas width.
 *
 * @property atlasWidth maximum row width in pixels.
 * @property padding empty pixels reserved before the first row/column and between placements.
 */
class RowGlyphAtlasPacker(
    private val atlasWidth: Int = 2048,
    private val padding: Int = 1,
) : GlyphAtlasPacker {
    init {
        require(atlasWidth > 0) { "atlasWidth must be positive." }
        require(padding >= 0) { "padding must be non-negative." }
    }

    /**
     * Packs A8 masks into stable row-major atlas placements.
     *
     * @param masks masks to place in input order.
     * @return deterministic atlas placements for the supplied masks.
     */
    override fun pack(masks: List<A8GlyphMask>): List<GlyphAtlasPlacement> =
        packAtlasItems(
            masks.map { mask ->
                mask.requireValidA8Pixels()
                AtlasItem(mask.glyphId, mask.width, mask.height)
            },
            atlasWidth = atlasWidth,
            padding = padding,
        )
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
    fun build(masks: List<A8GlyphMask>, placements: List<GlyphAtlasPlacement>): GlyphAtlasBuildResult
}

/**
 * Builds pure Kotlin A8 atlas upload inputs from packed glyph masks.
 */
class KotlinGlyphAtlasArtifactBuilder : GlyphAtlasArtifactBuilder {
    /**
     * Composes source mask pixels into a zero-filled A8 atlas using the supplied placements.
     *
     * @param masks source A8 masks keyed by glyph identifier.
     * @param placements atlas placements that select and position masks.
     * @return composed atlas pixels with the original placements preserved.
     */
    override fun build(masks: List<A8GlyphMask>, placements: List<GlyphAtlasPlacement>): GlyphAtlasBuildResult {
        val masksByGlyphId = masks.associateUniqueA8Masks()
        val width = placements.atlasWidth()
        val height = placements.atlasHeight()
        val pixels = MutableList(checkedAtlasPixelCount(width, height, "A8")) { 0 }

        placements.forEach { placement ->
            placement.requireValidPlacement()
            val mask = requireNotNull(masksByGlyphId[placement.glyphId]) {
                "Missing A8 mask for glyph ${placement.glyphId}."
            }
            mask.requireValidA8Pixels()
            require(placement.width == mask.width && placement.height == mask.height) {
                "Placement dimensions must match A8 mask dimensions for glyph ${placement.glyphId}."
            }
            copyA8Mask(mask, placement, width, pixels)
        }

        return GlyphAtlasBuildResult(
            width = width,
            height = height,
            placements = placements.toList(),
            pixels = pixels.toList(),
        )
    }
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
    fun build(masks: List<SDFGlyphMask>): SDFGlyphAtlasBuildResult
}

/**
 * Builds pure Kotlin SDF atlas upload inputs with deterministic row packing.
 *
 * @property atlasWidth maximum row width in pixels.
 * @property padding empty pixels reserved before the first row/column and between placements.
 */
class KotlinSDFGlyphAtlasArtifactBuilder(
    private val atlasWidth: Int = 2048,
    private val padding: Int = 1,
) : SDFGlyphAtlasArtifactBuilder {
    init {
        require(atlasWidth > 0) { "atlasWidth must be positive." }
        require(padding >= 0) { "padding must be non-negative." }
    }

    /**
     * Packs and composes SDF masks into a zero-filled atlas.
     *
     * @param masks source SDF masks. All masks must share one distance range.
     * @return composed SDF atlas pixels and the shared distance range.
     */
    override fun build(masks: List<SDFGlyphMask>): SDFGlyphAtlasBuildResult {
        if (masks.isEmpty()) {
            return SDFGlyphAtlasBuildResult(
                width = 0,
                height = 0,
                distanceRange = 0f,
                pixels = emptyList(),
                placements = emptyList(),
            )
        }

        val distanceRange = masks.first().distanceRange
        require(masks.all { it.distanceRange == distanceRange }) {
            "All SDF masks in one atlas must share the same distanceRange."
        }

        val placements = packAtlasItems(
            masks.map { mask ->
                mask.requireValidSDFPixels()
                AtlasItem(mask.glyphId, mask.width, mask.height)
            },
            atlasWidth = atlasWidth,
            padding = padding,
        )
        val masksByGlyphId = masks.associateUniqueSDFMasks()
        val width = placements.atlasWidth()
        val height = placements.atlasHeight()
        val pixels = MutableList(checkedAtlasPixelCount(width, height, "SDF")) { 0 }

        placements.forEach { placement ->
            val mask = requireNotNull(masksByGlyphId[placement.glyphId]) {
                "Missing SDF mask for glyph ${placement.glyphId}."
            }
            copySDFMask(mask, placement, width, pixels)
        }

        return SDFGlyphAtlasBuildResult(
            width = width,
            height = height,
            distanceRange = distanceRange,
            pixels = pixels.toList(),
            placements = placements.toList(),
        )
    }
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
 * @property placements packed glyph placements used to compose the SDF atlas.
 */
data class SDFGlyphAtlasBuildResult(
    val width: Int,
    val height: Int,
    val distanceRange: Float,
    val pixels: List<Int>,
    val placements: List<GlyphAtlasPlacement> = emptyList(),
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
    fun get(strikeKey: GlyphStrikeKey, glyphId: Int): GlyphRepresentation?

    /**
     * Stores a representation in the cache.
     *
     * @param strikeKey strike key for the cached entry.
     * @param representation representation to store.
     */
    fun put(strikeKey: GlyphStrikeKey, representation: GlyphRepresentation)
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
) {
    init {
        require(maxBytes >= 0L) { "maxBytes must be non-negative." }
        require(maxEntries >= 0) { "maxEntries must be non-negative." }
    }
}

/**
 * Describes one shaped glyph observed while building a renderer-neutral cache inventory.
 *
 * This input type carries only deterministic, pure Kotlin facts that a shaping or cache build
 * step already knows. It deliberately excludes font objects, path objects, canvas state, GPU
 * handles, native pointers, and rasterizer callbacks so the resulting inventory can be used as
 * stable debug evidence across renderers.
 *
 * @property index zero-based position of the glyph in the shaped run.
 * @property codePoint Unicode scalar value associated with this glyph position.
 * @property glyphId font-specific glyph identifier selected for [codePoint].
 * @property key stable cache key that identifies the strike-specific glyph record.
 * @property advance horizontal advance in pixels or caller-defined run units.
 * @property x horizontal glyph origin in pixels or caller-defined run units.
 * @property maskSummary renderer-neutral summary of the cached mask artifact, or
 * [GlyphMaskSummary.Empty] when no mask pixels are present.
 * @property diagnostic optional diagnostic code attached to this glyph position.
 */
data class GlyphRunCacheBuildGlyph(
    val index: Int,
    val codePoint: Int,
    val glyphId: Int,
    val key: String,
    val advance: Float,
    val x: Float,
    val maskSummary: GlyphMaskSummary = GlyphMaskSummary.Empty,
    val diagnostic: String? = null,
)

/**
 * Records one glyph position in a stable renderer-neutral cache inventory.
 *
 * Items preserve shaped-run order and are intentionally lighter than cache records: each item
 * points at a cache [key] and keeps the per-position facts needed to audit glyph selection,
 * advance accumulation, placement, and missing-glyph diagnostics.
 *
 * @property index zero-based position of the glyph in the shaped run.
 * @property codePoint Unicode scalar value associated with this glyph position.
 * @property glyphId font-specific glyph identifier selected for [codePoint].
 * @property key stable cache key for the deduplicated glyph record used by this item.
 * @property advance horizontal advance in pixels or caller-defined run units.
 * @property x horizontal glyph origin in pixels or caller-defined run units.
 * @property diagnostic optional diagnostic code attached to this glyph position.
 */
data class GlyphRunCacheInventoryItem(
    val index: Int,
    val codePoint: Int,
    val glyphId: Int,
    val key: String,
    val advance: Float,
    val x: Float,
    val diagnostic: String?,
)

/**
 * Summarizes a cached glyph mask without retaining renderer or rasterizer-specific objects.
 *
 * The summary is suitable for deterministic debug dumps: dimensions and origin describe the mask
 * rectangle, [rowBytes] preserves source row stride, [nonZeroPixels] captures coverage density,
 * and [sha256] identifies the addressable samples without embedding the full pixel payload.
 *
 * @property left horizontal mask origin relative to glyph origin.
 * @property top vertical mask origin relative to glyph origin.
 * @property width addressable mask width in pixels.
 * @property height addressable mask height in pixels.
 * @property rowBytes source row stride in samples.
 * @property nonZeroPixels count of non-zero addressable samples.
 * @property sha256 SHA-256 digest of addressable samples in row-major order.
 */
data class GlyphMaskSummary(
    val left: Int,
    val top: Int,
    val width: Int,
    val height: Int,
    val rowBytes: Int,
    val nonZeroPixels: Int,
    val sha256: String,
) {
    /**
     * Shared constructors and constants for renderer-neutral glyph mask summaries.
     */
    companion object {
        /**
         * SHA-256 digest of an empty byte array.
         */
        const val EmptyHash: String = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"

        /**
         * Empty mask summary used for whitespace, missing outlines, and deferred mask generation.
         */
        val Empty: GlyphMaskSummary = GlyphMaskSummary(
            left = 0,
            top = 0,
            width = 0,
            height = 0,
            rowBytes = 0,
            nonZeroPixels = 0,
            sha256 = EmptyHash,
        )

        /**
         * Builds a deterministic summary from an in-memory A8 mask.
         *
         * Only addressable samples inside [A8GlyphMask.width] by [A8GlyphMask.height] are hashed;
         * row padding beyond [A8GlyphMask.width] is ignored so callers can compare masks with the
         * same visible pixels but different backing stride.
         *
         * @param mask source A8 glyph mask to summarize.
         * @return renderer-neutral mask summary for debug evidence and cache records.
         */
        fun fromA8Mask(mask: A8GlyphMask): GlyphMaskSummary {
            mask.requireValidA8Pixels()
            if (mask.width == 0 || mask.height == 0) {
                return Empty
            }

            val samples = ByteArray(mask.width * mask.height)
            var targetIndex = 0
            var nonZeroPixels = 0
            for (row in 0 until mask.height) {
                for (column in 0 until mask.width) {
                    val sample = mask.pixels[row * mask.rowBytes + column]
                    if (sample != 0) {
                        nonZeroPixels += 1
                    }
                    samples[targetIndex] = sample.toByte()
                    targetIndex += 1
                }
            }

            return GlyphMaskSummary(
                left = mask.left,
                top = mask.top,
                width = mask.width,
                height = mask.height,
                rowBytes = mask.rowBytes,
                nonZeroPixels = nonZeroPixels,
                sha256 = glyphSha256(samples),
            )
        }
    }
}

/**
 * Describes one deduplicated glyph cache record in a renderer-neutral run inventory.
 *
 * Records are grouped by stable [key] in first-seen run order. They aggregate the Unicode code
 * points that selected the same cached glyph artifact while preserving the first record's glyph
 * identity, advance, mask summary, and diagnostic.
 *
 * @property key stable cache key shared by all run items represented by this record.
 * @property glyphId font-specific glyph identifier represented by the cache record.
 * @property codePoints distinct Unicode scalar values that mapped to this record, in first-seen
 * order.
 * @property advance horizontal advance in pixels or caller-defined run units.
 * @property maskSummary renderer-neutral summary of the cached mask artifact.
 * @property diagnostic optional diagnostic code attached to the record.
 */
data class GlyphCacheRecord(
    val key: String,
    val glyphId: Int,
    val codePoints: List<Int>,
    val advance: Float,
    val maskSummary: GlyphMaskSummary,
    val diagnostic: String?,
)

/**
 * Stable debug artifact for auditing one shaped glyph run and its deduplicated cache records.
 *
 * This inventory is renderer-neutral: it does not retain font engines, path objects, canvases,
 * GPU resources, native handles, or rasterizer state. Callers provide already-known glyph facts,
 * and [build] converts them into deterministic per-position [items], deduplicated [records],
 * ordered [diagnostics], canonical JSON, and a SHA-256 dump hash.
 *
 * @property runId stable caller-defined identifier for the shaped run or evidence scope.
 * @property representation stable representation label, such as [AlphaMaskRepresentation].
 * @property items glyph positions in shaped-run order.
 * @property records deduplicated cache records in first-seen key order.
 * @property diagnostics distinct diagnostic codes in first-seen order.
 */
data class GlyphRunCacheInventory(
    val runId: String,
    val representation: String,
    val items: List<GlyphRunCacheInventoryItem>,
    val records: List<GlyphCacheRecord>,
    val diagnostics: List<String>,
) {
    /**
     * SHA-256 digest of [toCanonicalJson] content with the `dumpSha256` field omitted.
     */
    val dumpSha256: String by lazy {
        glyphSha256(canonicalJson(includeDumpSha256 = false).toByteArray(Charsets.UTF_8))
    }

    /**
     * Serializes this inventory into stable human-readable JSON.
     *
     * The serializer is intentionally local and deterministic: field order, array order, float
     * formatting, Unicode escaping, and trailing newline are fixed so PM evidence and debug
     * artifacts can be compared by text diff or [dumpSha256].
     *
     * @return canonical JSON dump ending with a newline.
     */
    fun toCanonicalJson(): String = canonicalJson(includeDumpSha256 = true)

    /**
     * Shared constructors and constants for run cache inventories.
     */
    companion object {
        /**
         * Stable representation label for 8-bit alpha mask glyph cache records.
         */
        const val AlphaMaskRepresentation: String = "font.glyph.alpha-mask"

        /**
         * Builds a deterministic inventory from shaped glyph cache observations.
         *
         * Items are preserved in input order. Records are deduplicated by [GlyphRunCacheBuildGlyph.key]
         * in first-seen order, and record code points are distinct in first-seen order. Reusing a
         * key with a different glyph ID, advance, or mask summary is rejected because that would
         * make the cache evidence ambiguous.
         *
         * @param runId stable caller-defined identifier for the shaped run or evidence scope.
         * @param representation stable representation label for the cache artifact type.
         * @param glyphs shaped glyph observations to inventory.
         * @return deterministic renderer-neutral cache inventory.
         */
        fun build(
            runId: String,
            representation: String,
            glyphs: List<GlyphRunCacheBuildGlyph>,
        ): GlyphRunCacheInventory {
            val items = ArrayList<GlyphRunCacheInventoryItem>(glyphs.size)
            val recordsByKey = linkedMapOf<String, MutableGlyphCacheRecord>()
            val diagnostics = linkedSetOf<String>()

            glyphs.forEach { glyph ->
                glyph.diagnostic?.let { diagnostic -> diagnostics += diagnostic }
                items += GlyphRunCacheInventoryItem(
                    index = glyph.index,
                    codePoint = glyph.codePoint,
                    glyphId = glyph.glyphId,
                    key = glyph.key,
                    advance = glyph.advance,
                    x = glyph.x,
                    diagnostic = glyph.diagnostic,
                )

                val record = recordsByKey.getOrPut(glyph.key) {
                    MutableGlyphCacheRecord(
                        key = glyph.key,
                        glyphId = glyph.glyphId,
                        codePoints = linkedSetOf(),
                        advance = glyph.advance,
                        maskSummary = glyph.maskSummary,
                        diagnostic = glyph.diagnostic,
                    )
                }
                record.requireCompatible(glyph)
                record.codePoints += glyph.codePoint
                if (record.diagnostic == null && glyph.diagnostic != null) {
                    record.diagnostic = glyph.diagnostic
                }
            }

            return GlyphRunCacheInventory(
                runId = runId,
                representation = representation,
                items = items.toList(),
                records = recordsByKey.values.map { record -> record.freeze() },
                diagnostics = diagnostics.toList(),
            )
        }
    }

    /**
     * Serializes this inventory with optional dump hash inclusion.
     */
    private fun canonicalJson(includeDumpSha256: Boolean): String = buildString {
        append("{\n")
        appendGlyphJsonField("runId", runId, comma = true)
        appendGlyphJsonField("representation", representation, comma = true)
        appendGlyphJsonField("itemCount", items.size, comma = true)
        appendGlyphJsonField("recordCount", records.size, comma = true)
        append("  \"diagnostics\": [")
        append(diagnostics.joinToString(", ") { diagnostic -> glyphJsonString(diagnostic) })
        append("],\n")
        append("  \"items\": [\n")
        append(items.joinToString(",\n") { item -> item.toCanonicalJson().prependIndent("    ") })
        append("\n  ],\n")
        append("  \"records\": [\n")
        append(records.joinToString(",\n") { record -> record.toCanonicalJson().prependIndent("    ") })
        append("\n  ]")
        if (includeDumpSha256) {
            append(",\n")
            appendGlyphJsonField("dumpSha256", dumpSha256, comma = false)
        } else {
            append("\n")
        }
        append("}\n")
    }
}

/**
 * Deterministic in-memory implementation of [GlyphCache] for module-local glyph planning.
 *
 * The cache keys entries by every strike input that can affect representation reuse: typeface,
 * glyph ID, pixel size, scale, subpixel buckets, and variation coordinates. Variation coordinates
 * are normalized by axis tag so maps with the same values but different iteration order address
 * the same entry. Eviction is deterministic FIFO by insertion order; replacing an existing entry
 * moves it to the newest position.
 *
 * The byte budget is approximate and intentionally renderer-independent. It counts addressable
 * mask samples and path command text with a small fixed overhead so tests and PM evidence can
 * exercise budget behavior without relying on platform object-size introspection.
 *
 * @property budget maximum approximate byte count and entry count retained by this cache.
 */
class InMemoryGlyphCache(
    private val budget: GlyphCacheBudget,
) : GlyphCache {
    private val entries = linkedMapOf<GlyphCacheKey, GlyphCacheEntry>()
    private var currentBytes = 0L

    /**
     * Looks up a representation by normalized strike and glyph identity.
     *
     * @param strikeKey strike key whose variation coordinates are normalized for lookup.
     * @param glyphId glyph identifier for the cached entry.
     * @return cached representation, or null when the entry was never stored or has been evicted.
     */
    override fun get(strikeKey: GlyphStrikeKey, glyphId: Int): GlyphRepresentation? =
        entries[GlyphCacheKey.from(strikeKey, glyphId)]?.representation

    /**
     * Stores a representation unless it cannot fit within the configured budget.
     *
     * @param strikeKey strike key whose rasterization-affecting fields become part of the cache
     * key.
     * @param representation representation to store under its glyph identifier.
     */
    override fun put(strikeKey: GlyphStrikeKey, representation: GlyphRepresentation) {
        val key = GlyphCacheKey.from(strikeKey, representation.glyphId)
        val byteSize = representation.approximateCacheBytes()
        val previousEntry = entries.remove(key)
        if (previousEntry != null) {
            currentBytes -= previousEntry.byteSize
        }

        if (budget.maxEntries == 0 || budget.maxBytes == 0L || byteSize > budget.maxBytes) {
            return
        }

        entries[key] = GlyphCacheEntry(
            representation = representation,
            byteSize = byteSize,
        )
        currentBytes += byteSize
        evictUntilWithinBudget()
    }

    /**
     * Removes oldest entries until both byte and entry budgets are satisfied.
     */
    private fun evictUntilWithinBudget() {
        val iterator = entries.iterator()
        while ((entries.size > budget.maxEntries || currentBytes > budget.maxBytes) && iterator.hasNext()) {
            val entry = iterator.next()
            currentBytes -= entry.value.byteSize
            iterator.remove()
        }
    }
}

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

private const val MaxGlyphPathCommands = 4_096
private const val MaxGeneratedA8MaskPixels = 16_777_216L
private const val MaxGeneratedSDFMaskPixels = 16_777_216L
private const val DefaultSDFDistanceRange = 4f

/**
 * Mutable accumulator for one cache record while building a run inventory.
 *
 * @property key stable cache key for the record.
 * @property glyphId glyph identifier associated with [key].
 * @property codePoints distinct code points that map to [key].
 * @property advance advance associated with [key].
 * @property maskSummary renderer-neutral mask summary associated with [key].
 * @property diagnostic optional diagnostic associated with [key].
 */
private data class MutableGlyphCacheRecord(
    val key: String,
    val glyphId: Int,
    val codePoints: LinkedHashSet<Int>,
    val advance: Float,
    val maskSummary: GlyphMaskSummary,
    var diagnostic: String?,
) {
    /**
     * Rejects ambiguous reuse of the same cache key for different glyph facts.
     */
    fun requireCompatible(glyph: GlyphRunCacheBuildGlyph) {
        require(glyphId == glyph.glyphId) {
            "Glyph cache key '${glyph.key}' maps to glyph $glyphId and glyph ${glyph.glyphId}."
        }
        require(advance == glyph.advance) {
            "Glyph cache key '${glyph.key}' maps to multiple advances."
        }
        require(maskSummary == glyph.maskSummary) {
            "Glyph cache key '${glyph.key}' maps to multiple mask summaries."
        }
    }

    /**
     * Freezes this accumulator into an immutable public record.
     */
    fun freeze(): GlyphCacheRecord = GlyphCacheRecord(
        key = key,
        glyphId = glyphId,
        codePoints = codePoints.toList(),
        advance = advance,
        maskSummary = maskSummary,
        diagnostic = diagnostic,
    )
}

/**
 * Rectangle-like input used by shared atlas packing logic.
 *
 * @property glyphId glyph identifier assigned to the packed item.
 * @property width item width in pixels.
 * @property height item height in pixels.
 */
private data class AtlasItem(
    val glyphId: Int,
    val width: Int,
    val height: Int,
)

/**
 * Normalized cache key for one strike-specific glyph representation.
 *
 * @property typefaceId parsed or virtual typeface identity.
 * @property glyphId font-specific glyph identifier.
 * @property sizePx strike pixel size.
 * @property scaleX horizontal scale applied before glyph generation.
 * @property scaleY vertical scale applied before glyph generation.
 * @property subpixelX fractional x placement bucket.
 * @property subpixelY fractional y placement bucket.
 * @property variationCoordinates variation coordinates sorted by axis tag.
 */
private data class GlyphCacheKey(
    val typefaceId: TypefaceID,
    val glyphId: Int,
    val sizePx: Float,
    val scaleX: Float,
    val scaleY: Float,
    val subpixelX: Float,
    val subpixelY: Float,
    val variationCoordinates: List<GlyphVariationCoordinate>,
) {
    companion object {
        /**
         * Builds a normalized cache key from public strike inputs and glyph identity.
         *
         * @param strikeKey public strike key supplied by the caller.
         * @param glyphId glyph identifier associated with the cached representation.
         * @return normalized cache key suitable for deterministic map lookup.
         */
        fun from(strikeKey: GlyphStrikeKey, glyphId: Int): GlyphCacheKey =
            GlyphCacheKey(
                typefaceId = strikeKey.typefaceId,
                glyphId = glyphId,
                sizePx = strikeKey.sizePx,
                scaleX = strikeKey.scaleX,
                scaleY = strikeKey.scaleY,
                subpixelX = strikeKey.subpixelX,
                subpixelY = strikeKey.subpixelY,
                variationCoordinates = strikeKey.variationCoordinates.toSortedMap().map { (axisTag, value) ->
                    GlyphVariationCoordinate(axisTag = axisTag, value = value)
                },
            )
    }
}

/**
 * One normalized variation coordinate stored in [GlyphCacheKey].
 *
 * @property axisTag OpenType variation axis tag.
 * @property value normalized coordinate value for that axis.
 */
private data class GlyphVariationCoordinate(
    val axisTag: String,
    val value: Float,
)

/**
 * Cache value plus approximate memory cost used for deterministic budget enforcement.
 *
 * @property representation cached glyph representation.
 * @property byteSize approximate bytes charged to the cache budget.
 */
private data class GlyphCacheEntry(
    val representation: GlyphRepresentation,
    val byteSize: Long,
)

/**
 * Point in transformed pixel-space outline coordinates.
 */
private data class GlyphPathPoint(
    val x: Double,
    val y: Double,
)

/**
 * Parses and rasterizes a small deterministic line-only outline command subset to A8.
 */
private fun rasterizeLinearOutlineToA8(
    outline: OutlineGlyphRepresentation,
    strikeKey: GlyphStrikeKey,
): A8GlyphMask {
    val contours = outline.parseLinearContours(strikeKey)
    if (contours.isEmpty()) {
        return A8GlyphMask(
            glyphId = outline.glyphId,
            width = 0,
            height = 0,
            pixels = emptyList(),
        )
    }

    require(outline.windingRule == "nonZero") {
        "Unsupported glyph winding rule '${outline.windingRule}' for glyph ${outline.glyphId}; only nonZero is supported."
    }

    val points = contours.flatten()
    val left = floor(points.minOf { point -> point.x }).toInt()
    val top = floor(points.minOf { point -> point.y }).toInt()
    val right = ceil(points.maxOf { point -> point.x }).toInt()
    val bottom = ceil(points.maxOf { point -> point.y }).toInt()
    val width = right - left
    val height = bottom - top
    if (width <= 0 || height <= 0) {
        return A8GlyphMask(
            glyphId = outline.glyphId,
            width = 0,
            height = 0,
            left = left,
            top = top,
            pixels = emptyList(),
        )
    }

    val pixelCount = width.toLong() * height.toLong()
    require(pixelCount <= MaxGeneratedA8MaskPixels) {
        "Generated A8 mask pixel count $pixelCount exceeds limit $MaxGeneratedA8MaskPixels for glyph ${outline.glyphId}."
    }

    val pixels = MutableList(pixelCount.toInt()) { 0 }
    for (row in 0 until height) {
        val sampleY = top + row + 0.5
        for (column in 0 until width) {
            val sampleX = left + column + 0.5
            if (contours.nonZeroContains(sampleX, sampleY)) {
                pixels[row * width + column] = 255
            }
        }
    }

    return A8GlyphMask(
        glyphId = outline.glyphId,
        width = width,
        height = height,
        left = left,
        top = top,
        rowBytes = width,
        pixels = pixels.toList(),
    )
}

/**
 * Generates a deterministic SDF for a small line-only outline command subset.
 */
private fun generateLinearOutlineSDF(
    outline: OutlineGlyphRepresentation,
    strikeKey: GlyphStrikeKey,
): SDFGlyphMask {
    val contours = outline.parseLinearContours(strikeKey)
    if (contours.isEmpty()) {
        return SDFGlyphMask(
            glyphId = outline.glyphId,
            width = 0,
            height = 0,
            distanceRange = DefaultSDFDistanceRange,
            pixels = emptyList(),
        )
    }

    require(outline.windingRule == "nonZero") {
        "Unsupported glyph winding rule '${outline.windingRule}' for glyph ${outline.glyphId}; only nonZero is supported."
    }

    val points = contours.flatten()
    val left = floor(points.minOf { point -> point.x }).toInt() - 1
    val top = floor(points.minOf { point -> point.y }).toInt() - 1
    val right = ceil(points.maxOf { point -> point.x }).toInt() + 1
    val bottom = ceil(points.maxOf { point -> point.y }).toInt() + 1
    val width = right - left
    val height = bottom - top
    if (width <= 0 || height <= 0) {
        return SDFGlyphMask(
            glyphId = outline.glyphId,
            width = 0,
            height = 0,
            distanceRange = DefaultSDFDistanceRange,
            pixels = emptyList(),
        )
    }

    val pixelCount = width.toLong() * height.toLong()
    require(pixelCount <= MaxGeneratedSDFMaskPixels) {
        "Generated SDF mask pixel count $pixelCount exceeds limit $MaxGeneratedSDFMaskPixels for glyph ${outline.glyphId}."
    }

    val pixels = MutableList(pixelCount.toInt()) { 0 }
    for (row in 0 until height) {
        val sampleY = top + row.toDouble()
        for (column in 0 until width) {
            val sampleX = left + column.toDouble()
            val distance = contours.distanceToNearestSegment(sampleX, sampleY)
            val signedDistance = if (contours.nonZeroContains(sampleX, sampleY)) distance else -distance
            pixels[row * width + column] = signedDistance.encodeSDFSample(DefaultSDFDistanceRange)
        }
    }

    return SDFGlyphMask(
        glyphId = outline.glyphId,
        width = width,
        height = height,
        distanceRange = DefaultSDFDistanceRange,
        pixels = pixels.toList(),
    )
}

/**
 * Parses supported path commands into closed linear contours in strike pixel space.
 */
private fun OutlineGlyphRepresentation.parseLinearContours(strikeKey: GlyphStrikeKey): List<List<GlyphPathPoint>> {
    require(pathCommands.size <= MaxGlyphPathCommands) {
        "Glyph $glyphId path command count ${pathCommands.size} exceeds limit $MaxGlyphPathCommands."
    }
    require(strikeKey.scaleX.isFinite() && strikeKey.scaleY.isFinite()) {
        "Glyph $glyphId strike scale must be finite."
    }
    require(strikeKey.subpixelX.isFinite() && strikeKey.subpixelY.isFinite()) {
        "Glyph $glyphId strike subpixel buckets must be finite."
    }

    val contours = mutableListOf<List<GlyphPathPoint>>()
    var current = mutableListOf<GlyphPathPoint>()
    var start: GlyphPathPoint? = null

    pathCommands.forEach { commandText ->
        val tokens = commandText.pathCommandTokens()
        if (tokens.isEmpty()) {
            return@forEach
        }

        when (val command = tokens.first()) {
            "M" -> {
                require(tokens.size == 3) { "Glyph $glyphId M command must have exactly two coordinates: '$commandText'." }
                current.finishContourInto(contours)
                val point = parseGlyphPathPoint(tokens, commandText, strikeKey)
                current = mutableListOf(point)
                start = point
            }
            "L" -> {
                require(tokens.size == 3) { "Glyph $glyphId L command must have exactly two coordinates: '$commandText'." }
                require(current.isNotEmpty()) { "Glyph $glyphId L command appears before M: '$commandText'." }
                current += parseGlyphPathPoint(tokens, commandText, strikeKey)
            }
            "Z" -> {
                require(tokens.size == 1) { "Glyph $glyphId Z command must not have coordinates: '$commandText'." }
                val contourStart = start
                if (contourStart != null && current.lastOrNull() != contourStart) {
                    current += contourStart
                }
                current.finishContourInto(contours)
                current = mutableListOf()
                start = null
            }
            "Q", "C" -> throw UnsupportedOperationException(
                "Glyph $glyphId command '$command' is not supported by the minimal line-only glyph generator.",
            )
            else -> throw IllegalArgumentException("Unsupported glyph $glyphId path command '$command' in '$commandText'.")
        }
    }

    current.finishContourInto(contours)
    return contours.toList()
}

/**
 * Splits a compact glyph path command on whitespace and commas.
 */
private fun String.pathCommandTokens(): List<String> =
    trim()
        .split(Regex("[\\s,]+"))
        .filter { token -> token.isNotEmpty() }

/**
 * Parses one command endpoint and applies strike placement transforms.
 */
private fun OutlineGlyphRepresentation.parseGlyphPathPoint(
    tokens: List<String>,
    commandText: String,
    strikeKey: GlyphStrikeKey,
): GlyphPathPoint {
    val x = tokens[1].toDoubleOrNull()
    val y = tokens[2].toDoubleOrNull()
    require(x != null && y != null && x.isFinite() && y.isFinite()) {
        "Glyph $glyphId path command has non-finite coordinates: '$commandText'."
    }

    return GlyphPathPoint(
        x = x * strikeKey.scaleX + strikeKey.subpixelX,
        y = y * strikeKey.scaleY + strikeKey.subpixelY,
    )
}

/**
 * Adds a contour when it has enough distinct points to enclose area.
 */
private fun MutableList<GlyphPathPoint>.finishContourInto(contours: MutableList<List<GlyphPathPoint>>) {
    if (distinct().size >= 3) {
        contours += toList()
    }
}

/**
 * Evaluates non-zero winding containment for line-only contours.
 */
private fun List<List<GlyphPathPoint>>.nonZeroContains(x: Double, y: Double): Boolean {
    var winding = 0
    forEach { contour ->
        for (index in 0 until contour.lastIndex) {
            val start = contour[index]
            val end = contour[index + 1]
            if ((start.y <= y && end.y > y) || (start.y > y && end.y <= y)) {
                val intersectionX = start.x + (y - start.y) * (end.x - start.x) / (end.y - start.y)
                if (intersectionX > x) {
                    winding += if (end.y > start.y) 1 else -1
                }
            }
        }
    }
    return winding != 0
}

/**
 * Computes the shortest Euclidean distance from a sample to any contour segment.
 */
private fun List<List<GlyphPathPoint>>.distanceToNearestSegment(x: Double, y: Double): Double {
    var minDistanceSquared = Double.POSITIVE_INFINITY
    forEach { contour ->
        for (index in 0 until contour.lastIndex) {
            minDistanceSquared = minOf(
                minDistanceSquared,
                squaredDistanceToSegment(x, y, contour[index], contour[index + 1]),
            )
        }
    }
    return sqrt(minDistanceSquared)
}

/**
 * Computes squared distance from a sample point to one finite line segment.
 */
private fun squaredDistanceToSegment(
    x: Double,
    y: Double,
    start: GlyphPathPoint,
    end: GlyphPathPoint,
): Double {
    val dx = end.x - start.x
    val dy = end.y - start.y
    val lengthSquared = dx * dx + dy * dy
    if (lengthSquared == 0.0) {
        val pointDx = x - start.x
        val pointDy = y - start.y
        return pointDx * pointDx + pointDy * pointDy
    }

    val t = (((x - start.x) * dx + (y - start.y) * dy) / lengthSquared).coerceIn(0.0, 1.0)
    val projectionX = start.x + t * dx
    val projectionY = start.y + t * dy
    val projectionDx = x - projectionX
    val projectionDy = y - projectionY
    return projectionDx * projectionDx + projectionDy * projectionDy
}

/**
 * Encodes signed distance into an 8-bit SDF sample with 128 at the edge.
 */
private fun Double.encodeSDFSample(distanceRange: Float): Int {
    val normalized = (this / distanceRange.toDouble()).coerceIn(-1.0, 1.0)
    return (128.0 + normalized * 127.0).roundToInt().coerceIn(0, 255)
}

/**
 * Packs rectangle items into rows with deterministic padding.
 *
 * @param items rectangles to place in input order.
 * @param atlasWidth maximum row width in pixels.
 * @param padding empty pixels reserved around and between placements.
 * @return atlas placements in the same order as the input items.
 */
private fun packAtlasItems(
    items: List<AtlasItem>,
    atlasWidth: Int,
    padding: Int,
): List<GlyphAtlasPlacement> {
    val placements = mutableListOf<GlyphAtlasPlacement>()
    var x = padding
    var y = padding
    var rowHeight = 0

    items.forEach { item ->
        require(item.width >= 0) { "Glyph ${item.glyphId} width must be non-negative." }
        require(item.height >= 0) { "Glyph ${item.glyphId} height must be non-negative." }
        require(item.width + padding * 2 <= atlasWidth) {
            "Glyph ${item.glyphId} width plus padding exceeds atlas width."
        }

        if (x != padding && x + item.width + padding > atlasWidth) {
            x = padding
            y += rowHeight + padding
            rowHeight = 0
        }

        placements += GlyphAtlasPlacement(
            glyphId = item.glyphId,
            x = x,
            y = y,
            width = item.width,
            height = item.height,
        )
        x += item.width + padding
        rowHeight = maxOf(rowHeight, item.height)
    }

    return placements
}

/**
 * Computes the minimal atlas width that contains all placements.
 *
 * @return maximum placement right edge, or zero for an empty placement list.
 */
private fun List<GlyphAtlasPlacement>.atlasWidth(): Int =
    maxOfOrNull { placement ->
        placement.requireValidPlacement()
        placement.checkedRightEdge()
    } ?: 0

/**
 * Computes the minimal atlas height that contains all placements.
 *
 * @return maximum placement bottom edge, or zero for an empty placement list.
 */
private fun List<GlyphAtlasPlacement>.atlasHeight(): Int =
    maxOfOrNull { placement ->
        placement.requireValidPlacement()
        placement.checkedBottomEdge()
    } ?: 0

/**
 * Computes the atlas pixel count with overflow and allocation bounds checked.
 */
private fun checkedAtlasPixelCount(width: Int, height: Int, label: String): Int {
    val pixelCount = width.toLong() * height.toLong()
    require(pixelCount <= Int.MAX_VALUE.toLong()) {
        "$label atlas pixel count $pixelCount exceeds Int.MAX_VALUE."
    }
    return pixelCount.toInt()
}

/**
 * Validates that a placement describes a non-negative atlas rectangle.
 */
private fun GlyphAtlasPlacement.requireValidPlacement() {
    require(x >= 0) { "Placement x must be non-negative for glyph $glyphId." }
    require(y >= 0) { "Placement y must be non-negative for glyph $glyphId." }
    require(width >= 0) { "Placement width must be non-negative for glyph $glyphId." }
    require(height >= 0) { "Placement height must be non-negative for glyph $glyphId." }
}

private fun GlyphAtlasPlacement.checkedRightEdge(): Int {
    val right = x.toLong() + width.toLong()
    require(right <= Int.MAX_VALUE.toLong()) {
        "Placement right edge $right exceeds Int.MAX_VALUE for glyph $glyphId."
    }
    return right.toInt()
}

private fun GlyphAtlasPlacement.checkedBottomEdge(): Int {
    val bottom = y.toLong() + height.toLong()
    require(bottom <= Int.MAX_VALUE.toLong()) {
        "Placement bottom edge $bottom exceeds Int.MAX_VALUE for glyph $glyphId."
    }
    return bottom.toInt()
}

/**
 * Builds a glyph-id map for A8 masks while rejecting ambiguous duplicates.
 *
 * @return masks keyed by glyph identifier.
 */
private fun List<A8GlyphMask>.associateUniqueA8Masks(): Map<Int, A8GlyphMask> {
    val masksByGlyphId = mutableMapOf<Int, A8GlyphMask>()
    forEach { mask ->
        require(masksByGlyphId.put(mask.glyphId, mask) == null) {
            "Duplicate A8 mask for glyph ${mask.glyphId}."
        }
    }
    return masksByGlyphId
}

/**
 * Builds a glyph-id map for SDF masks while rejecting ambiguous duplicates.
 *
 * @return masks keyed by glyph identifier.
 */
private fun List<SDFGlyphMask>.associateUniqueSDFMasks(): Map<Int, SDFGlyphMask> {
    val masksByGlyphId = mutableMapOf<Int, SDFGlyphMask>()
    forEach { mask ->
        require(masksByGlyphId.put(mask.glyphId, mask) == null) {
            "Duplicate SDF mask for glyph ${mask.glyphId}."
        }
    }
    return masksByGlyphId
}

/**
 * Validates dimensions, backing pixel count, and addressable sample values for an A8 mask.
 */
private fun A8GlyphMask.requireValidA8Pixels() {
    require(width >= 0) { "A8 mask width must be non-negative for glyph $glyphId." }
    require(height >= 0) { "A8 mask height must be non-negative for glyph $glyphId." }
    require(rowBytes >= width) { "A8 mask rowBytes must be at least width for glyph $glyphId." }
    val expectedPixelCount = rowBytes.toLong() * height.toLong()
    require(pixels.size.toLong() >= expectedPixelCount) {
        "A8 mask pixel count is smaller than rowBytes * height for glyph $glyphId."
    }

    for (row in 0 until height) {
        for (column in 0 until width) {
            val value = pixels[row * rowBytes + column]
            require(value in 0..255) {
                "A8 mask pixel value $value is outside 0..255 for glyph $glyphId."
            }
        }
    }
}

/**
 * Validates dimensions, backing pixel count, and sample values for an SDF mask.
 */
private fun SDFGlyphMask.requireValidSDFPixels() {
    require(width >= 0) { "SDF mask width must be non-negative for glyph $glyphId." }
    require(height >= 0) { "SDF mask height must be non-negative for glyph $glyphId." }
    val expectedPixelCount = width.toLong() * height.toLong()
    require(pixels.size.toLong() >= expectedPixelCount) {
        "SDF mask pixel count is smaller than width * height for glyph $glyphId."
    }

    for (index in 0 until expectedPixelCount.toInt()) {
        val value = pixels[index]
        require(value in 0..255) {
            "SDF mask pixel value $value is outside 0..255 for glyph $glyphId."
        }
    }
}

/**
 * Copies one A8 mask into its atlas position.
 *
 * @param mask source mask.
 * @param placement destination atlas rectangle.
 * @param atlasWidth atlas row width in pixels.
 * @param atlasPixels mutable atlas pixels to receive source samples.
 */
private fun copyA8Mask(
    mask: A8GlyphMask,
    placement: GlyphAtlasPlacement,
    atlasWidth: Int,
    atlasPixels: MutableList<Int>,
) {
    for (row in 0 until mask.height) {
        for (column in 0 until mask.width) {
            val sourceIndex = row * mask.rowBytes + column
            val targetIndex = (placement.y + row) * atlasWidth + placement.x + column
            atlasPixels[targetIndex] = mask.pixels[sourceIndex]
        }
    }
}

/**
 * Copies one SDF mask into its atlas position.
 *
 * @param mask source mask.
 * @param placement destination atlas rectangle.
 * @param atlasWidth atlas row width in pixels.
 * @param atlasPixels mutable atlas pixels to receive source samples.
 */
private fun copySDFMask(
    mask: SDFGlyphMask,
    placement: GlyphAtlasPlacement,
    atlasWidth: Int,
    atlasPixels: MutableList<Int>,
) {
    for (row in 0 until mask.height) {
        for (column in 0 until mask.width) {
            val sourceIndex = row * mask.width + column
            val targetIndex = (placement.y + row) * atlasWidth + placement.x + column
            atlasPixels[targetIndex] = mask.pixels[sourceIndex]
        }
    }
}

/**
 * Selects the first available representation that satisfies the request's route order.
 *
 * @param glyphId glyph identifier being planned.
 * @return selected representation, or null when no requested route exists for the glyph.
 */
private fun GlyphArtifactRouteRequest.selectRepresentation(
    glyphId: Int,
    cached: GlyphRepresentation?,
): GlyphRepresentation? {
    val candidates = availableRepresentations[glyphId].orEmpty()
    preferredRoutes.forEach { route ->
        candidates.firstOrNull { representation -> route.matches(representation) }?.let { representation ->
            return representation
        }
        if (cached != null && route.matches(cached)) {
            return cached
        }
    }
    return null
}

/**
 * Builds a stable diagnostic for a glyph whose requested routes cannot be satisfied.
 *
 * @param glyphId glyph identifier that could not be planned.
 * @param availableRepresentations supplied candidate representations for that glyph.
 * @return warning diagnostic describing requested and available routes.
 */
private fun GlyphArtifactRouteRequest.unsupportedDiagnostic(
    glyphId: Int,
    availableRepresentations: List<GlyphRepresentation>,
): GlyphRouteDiagnostic {
    val requestedRoutes = preferredRoutes.diagnosticLabel()
    val availableRoutes = availableRepresentations
        .map { representation -> representation.diagnosticRouteName() }
        .distinct()
        .joinToString(separator = "|")
        .ifEmpty { "none" }

    return GlyphRouteDiagnostic(
        glyphId = glyphId,
        route = requestedRoutes,
        message = "No requested glyph representation is available for glyph $glyphId; " +
            "requested $requestedRoutes, available $availableRoutes.",
        severity = "warning",
    )
}

/**
 * Formats route preferences for deterministic diagnostics.
 *
 * @return pipe-delimited route label, or "none" when no route was requested.
 */
private fun List<GlyphArtifactRoute>.diagnosticLabel(): String =
    joinToString(separator = "|") { route -> route.diagnosticName }.ifEmpty { "none" }

/**
 * Determines whether this route can carry the supplied representation.
 *
 * @param representation candidate pure Kotlin glyph representation.
 * @return true when the representation has this route's artifact shape.
 */
private fun GlyphArtifactRoute.matches(representation: GlyphRepresentation): Boolean =
    when (this) {
        GlyphArtifactRoute.OUTLINE -> representation is OutlineGlyphRepresentation
        GlyphArtifactRoute.A8 -> representation is A8GlyphMask
        GlyphArtifactRoute.SDF -> representation is SDFGlyphMask
    }

/**
 * Returns the diagnostic route represented by a concrete glyph representation.
 *
 * @return stable lowercase route label for known glyph representations.
 */
private fun GlyphRepresentation.diagnosticRouteName(): String =
    when (this) {
        is OutlineGlyphRepresentation -> GlyphArtifactRoute.OUTLINE.diagnosticName
        is A8GlyphMask -> GlyphArtifactRoute.A8.diagnosticName
        is SDFGlyphMask -> GlyphArtifactRoute.SDF.diagnosticName
        else -> this::class.simpleName
            ?.removeSuffix("GlyphRepresentation")
            ?.lowercase()
            ?.ifEmpty { "unknown" } ?: "unknown"
    }

/**
 * Estimates cache memory for a representation without depending on JVM object layout.
 *
 * @return approximate byte cost charged to [GlyphCacheBudget.maxBytes].
 */
private fun GlyphRepresentation.approximateCacheBytes(): Long {
    val fixedOverheadBytes = 64L
    return fixedOverheadBytes + when (this) {
        is OutlineGlyphRepresentation -> pathCommands.sumOf { command -> command.length.toLong() * 2L } +
            windingRule.length.toLong() * 2L
        is A8GlyphMask -> rowBytes.nonNegativeProduct(height, glyphId = glyphId, label = "A8 mask")
        is SDFGlyphMask -> width.nonNegativeProduct(height, glyphId = glyphId, label = "SDF mask")
        else -> 0L
    }
}

/**
 * Computes a non-negative Int product as Long for cache-size estimates.
 *
 * @param other second multiplicand.
 * @param glyphId glyph identifier used in validation errors.
 * @param label representation label used in validation errors.
 * @return non-negative product as a Long.
 */
private fun Int.nonNegativeProduct(other: Int, glyphId: Int, label: String): Long {
    require(this >= 0) { "$label dimension must be non-negative for glyph $glyphId." }
    require(other >= 0) { "$label dimension must be non-negative for glyph $glyphId." }
    return toLong() * other.toLong()
}

/**
 * Serializes an inventory item into stable JSON.
 */
private fun GlyphRunCacheInventoryItem.toCanonicalJson(): String = buildString {
    append("{\n")
    appendGlyphJsonField("index", index, comma = true)
    appendGlyphJsonField("codePoint", glyphCodePointLabel(codePoint), comma = true)
    appendGlyphJsonField("codePointValue", codePoint, comma = true)
    appendGlyphJsonField("glyphId", glyphId, comma = true)
    appendGlyphJsonField("key", key, comma = true)
    appendGlyphJsonField("advance", advance, comma = true)
    appendGlyphJsonField("x", x, comma = true)
    appendGlyphJsonNullableField("diagnostic", diagnostic, comma = false)
    append("\n}")
}

/**
 * Serializes a cache record into stable JSON.
 */
private fun GlyphCacheRecord.toCanonicalJson(): String = buildString {
    append("{\n")
    appendGlyphJsonField("key", key, comma = true)
    appendGlyphJsonField("glyphId", glyphId, comma = true)
    append("  \"codePoints\": [")
    append(codePoints.joinToString(", ") { codePoint -> glyphJsonString(glyphCodePointLabel(codePoint)) })
    append("],\n")
    appendGlyphJsonField("advance", advance, comma = true)
    append("  \"maskSummary\": ")
    append(maskSummary.toCanonicalJson())
    append(",\n")
    appendGlyphJsonNullableField("diagnostic", diagnostic, comma = false)
    append("\n}")
}

/**
 * Serializes a mask summary as a single-line stable JSON object.
 */
private fun GlyphMaskSummary.toCanonicalJson(): String = buildString {
    append("{")
    append(glyphJsonString("left")).append(": ").append(left).append(", ")
    append(glyphJsonString("top")).append(": ").append(top).append(", ")
    append(glyphJsonString("width")).append(": ").append(width).append(", ")
    append(glyphJsonString("height")).append(": ").append(height).append(", ")
    append(glyphJsonString("rowBytes")).append(": ").append(rowBytes).append(", ")
    append(glyphJsonString("nonZeroPixels")).append(": ").append(nonZeroPixels).append(", ")
    append(glyphJsonString("sha256")).append(": ").append(glyphJsonString(sha256))
    append("}")
}

/**
 * Appends a stable string JSON field.
 */
private fun StringBuilder.appendGlyphJsonField(name: String, value: String, comma: Boolean) {
    append("  ").append(glyphJsonString(name)).append(": ").append(glyphJsonString(value))
    if (comma) append(",")
    append("\n")
}

/**
 * Appends a stable nullable string JSON field.
 */
private fun StringBuilder.appendGlyphJsonNullableField(name: String, value: String?, comma: Boolean) {
    append("  ").append(glyphJsonString(name)).append(": ")
    append(if (value == null) "null" else glyphJsonString(value))
    if (comma) append(",")
}

/**
 * Appends a stable integer JSON field.
 */
private fun StringBuilder.appendGlyphJsonField(name: String, value: Int, comma: Boolean) {
    append("  ").append(glyphJsonString(name)).append(": ").append(value)
    if (comma) append(",")
    append("\n")
}

/**
 * Appends a stable float JSON field.
 */
private fun StringBuilder.appendGlyphJsonField(name: String, value: Float, comma: Boolean) {
    append("  ").append(glyphJsonString(name)).append(": ").append(glyphFloatToken(value))
    if (comma) append(",")
    append("\n")
}

/**
 * Escapes a string as deterministic ASCII JSON.
 */
private fun glyphJsonString(value: String): String = buildString {
    append('"')
    for (ch in value) {
        when (ch) {
            '\\' -> append("\\\\")
            '"' -> append("\\\"")
            '\n' -> append("\\n")
            '\r' -> append("\\r")
            '\t' -> append("\\t")
            else -> {
                if (ch.code < 0x20 || ch.code > 0x7E) {
                    append("\\u")
                    append(ch.code.toString(16).padStart(4, '0'))
                } else {
                    append(ch)
                }
            }
        }
    }
    append('"')
}

/**
 * Formats a Unicode scalar value as a stable `U+XXXX` label.
 */
private fun glyphCodePointLabel(codePoint: Int): String =
    "U+${codePoint.toString(16).uppercase().padStart(4, '0')}"

/**
 * Formats a finite float using stable US-locale decimal syntax.
 */
private fun glyphFloatToken(value: Float): String {
    require(value.isFinite()) { "Glyph inventory float values must be finite." }
    return String.format(Locale.US, "%.6f", value).trimEnd('0').trimEnd('.').ifEmpty { "0" }
}

/**
 * Computes a lowercase SHA-256 digest for deterministic evidence identifiers.
 */
private fun glyphSha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { byte ->
        "%02x".format(byte.toInt() and 0xFF)
    }
