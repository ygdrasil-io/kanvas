package org.graphiks.kanvas.glyph.gpu

import org.graphiks.kanvas.font.TypefaceID

/**
 * Stable identifier for a laid-out text result that can produce GPU text
 * artifact requests.
 *
 * The identifier names a layout result without carrying paragraph, shaping,
 * glyph cache, or renderer state. It lets diagnostics and bundles reference
 * text layout provenance while keeping the handoff surface dumpable.
 *
 * @property value Opaque layout result identifier suitable for logs,
 * snapshots, and cross-module handoff records.
 */
@JvmInline
value class GPUTextLayoutResultID(
    val value: String,
)

/**
 * Stable identifier for one glyph run within a laid-out text result.
 *
 * The identifier is owned by the text/font stack. It is not a renderer batch
 * key, material key, upload slot, or GPU command identifier.
 *
 * @property value Opaque glyph run identifier suitable for diagnostics and
 * serialized planning evidence.
 */
@JvmInline
value class GPUGlyphRunID(
    val value: String,
)

/**
 * Reference from a GPU text artifact back to the text layout and glyph run
 * that produced it.
 *
 * This reference is intentionally smaller than a full glyph run descriptor.
 * It exists for diagnostics, cache attribution, and PM evidence where the
 * full glyph positions are not needed.
 *
 * @property layoutResultID Stable identifier for the source text layout
 * result.
 * @property glyphRunID Stable identifier for the source glyph run.
 * @property runIndex Zero-based run index inside the layout result when the
 * producer exposes deterministic ordering.
 */
data class GPUTextLayoutRunReference(
    val layoutResultID: GPUTextLayoutResultID,
    val glyphRunID: GPUGlyphRunID,
    val runIndex: Int,
)

/**
 * Describes one shaped glyph run prepared for GPU text artifact planning.
 *
 * The descriptor is the public, renderer-neutral run handoff. It carries
 * positioned glyph identifiers and text provenance, but no parsed font table,
 * scaler object, glyph cache entry, texture, command encoder, or render step.
 *
 * @property runID Stable identifier for this glyph run.
 * @property layoutResultID Optional identifier for the layout result that
 * owns the run.
 * @property typefaceID Parsed typeface selected for the run when known.
 * @property glyphIDs Font-specific glyph identifiers in visual order.
 * @property advances Device-independent advances, one per glyph when known.
 * @property offsets Optional x/y offset pairs encoded as consecutive floats.
 * @property textRangeStart Inclusive UTF-16 source text start offset.
 * @property textRangeEnd Exclusive UTF-16 source text end offset.
 * @property script ISO 15924 script tag associated with the run.
 * @property bidiLevel Unicode Bidirectional Algorithm embedding level.
 */
data class GPUGlyphRunDescriptor(
    val runID: GPUGlyphRunID,
    val layoutResultID: GPUTextLayoutResultID? = null,
    val typefaceID: TypefaceID? = null,
    val glyphIDs: List<Int>,
    val advances: List<Float> = emptyList(),
    val offsets: List<Float> = emptyList(),
    val textRangeStart: Int = 0,
    val textRangeEnd: Int = glyphIDs.size,
    val script: String = "Zyyy",
    val bidiLevel: Int = 0,
)

/**
 * Stable identifier for a planned GPU text artifact.
 *
 * The identifier is an opaque value owned by the pure Kotlin font/text stack.
 * It names a logical artifact such as an atlas, upload payload, or glyph plan,
 * but it does not represent a GPU handle, font parser handle, shaped run, or
 * renderer batch key.
 *
 * @property value Dumpable identifier text suitable for logs, test fixtures,
 * and serialized planning snapshots.
 */
@JvmInline
value class GPUTextArtifactID(
    val value: String,
)

/**
 * Monotonic generation marker for a GPU text artifact.
 *
 * Generations allow caches and diagnostics to distinguish newer planning
 * results from older artifacts that share the same logical identifier. The
 * value is intentionally plain data so it can be serialized or dumped without
 * access to renderer state.
 *
 * @property value Non-negative generation number assigned by the text planning
 * layer.
 */
@JvmInline
value class GPUTextArtifactGeneration(
    val value: Int,
)

/**
 * Compound key for a pure Kotlin GPU text artifact.
 *
 * The key combines a stable artifact identifier, a generation, and a content
 * fingerprint. It is renderer-neutral: it can be used by atlas builders,
 * upload planners, diagnostics, and test fixtures without depending on any
 * concrete WebGPU or native resource.
 *
 * @property artifactID Stable logical identifier for the artifact family.
 * @property generation Generation of the artifact instance.
 * @property contentFingerprint Dumpable fingerprint for the planned content.
 */
data class GPUTextArtifactKey(
    val artifactID: GPUTextArtifactID,
    val generation: GPUTextArtifactGeneration,
    val contentFingerprint: String,
)

/**
 * Byte range that should be uploaded for a text artifact payload.
 *
 * The range describes an offset and length in a CPU-side payload. It does not
 * bind to a GPU buffer, staging resource, command encoder, or renderer upload
 * route.
 *
 * @property offset Byte offset from the start of the payload.
 * @property size Number of bytes covered by this range.
 * @property label Human-readable range label for dumps and diagnostics.
 */
data class GPUTextUploadRange(
    val offset: Int,
    val size: Int,
    val label: String,
)

/**
 * CPU-side upload plan for a GPU text artifact.
 *
 * The plan groups upload ranges for one artifact key. It describes the data
 * shape that a renderer may consume later, while intentionally avoiding any
 * renderer-owned binding, batch, render step, route, or GPU handle.
 *
 * @property artifactKey Artifact instance that owns the upload payload.
 * @property ranges Ordered byte ranges that should be transferred.
 * @property byteSize Total payload size in bytes.
 */
data class GPUTextUploadPlan(
    val artifactKey: GPUTextArtifactKey,
    val ranges: List<GPUTextUploadRange>,
    val byteSize: Int,
)

/**
 * Diagnostic emitted while building GPU text artifacts.
 *
 * Diagnostics are plain data so they can be stored in PM evidence, test
 * snapshots, or route reports without pulling in renderer or font-parser
 * dependencies.
 *
 * @property code Stable diagnostic category.
 * @property message Human-readable diagnostic detail.
 * @property artifactKey Optional artifact key related to the diagnostic.
 */
data class GPUTextArtifactDiagnostic(
    val code: GPUTextArtifactDiagnosticCode,
    val message: String,
    val artifactKey: GPUTextArtifactKey? = null,
)

/**
 * Stable diagnostic codes for GPU text artifact planning.
 *
 * These codes describe planning and explicit refusal conditions only. They do not
 * describe renderer command routing or GPU execution failures.
 */
enum class GPUTextArtifactDiagnosticCode {
    /**
     * A glyph required by the text artifact could not be represented by the
     * current pure Kotlin plan.
     */
    MISSING_GLYPH,

    /**
     * The requested glyph representation is not supported by the current
     * artifact planner.
     */
    UNSUPPORTED_GLYPH_FORMAT,

    /**
     * The planned atlas dimensions or capacity cannot hold the requested
     * glyph content.
     */
    ATLAS_CAPACITY_EXCEEDED,

    /**
     * An upload range is malformed or inconsistent with the declared payload
     * size.
     */
    INVALID_UPLOAD_RANGE,

    /**
     * The planner cannot produce a GPU text artifact and the caller must
     * refuse the route or select an explicitly named non-GPU text path.
     */
    EXPLICIT_REFUSAL_REQUIRED,
}

/**
 * Aggregate diagnostics for a text artifact route decision.
 *
 * This class reports whether planned GPU text artifacts require an explicit
 * refusal or alternate route. It never authorizes hidden CPU rendering and it
 * does not encode the renderer-owned route itself.
 *
 * @property diagnostics Ordered planning diagnostics.
 * @property refusalRequired True when the planner cannot emit the requested
 * GPU text artifact and the caller must refuse or choose a named alternate
 * route.
 */
data class GPUTextRouteDiagnostics(
    val diagnostics: List<GPUTextArtifactDiagnostic>,
    val refusalRequired: Boolean,
) {
    /**
     * True when there are no diagnostics and no refusal requirement.
     */
    val isEmpty: Boolean
        get() = diagnostics.isEmpty() && !refusalRequired
}

/**
 * Planned glyph atlas artifact.
 *
 * The artifact describes atlas dimensions and texel format as dumpable values.
 * It is not a GPU texture, texture view, sampler, bind group, or renderer atlas
 * allocation.
 *
 * @property artifactKey Artifact key for this atlas description.
 * @property width Atlas width in texels.
 * @property height Atlas height in texels.
 * @property format Dumpable texel format name such as "r8" or "rgba8888".
 */
data class GlyphAtlasArtifact(
    val artifactKey: GPUTextArtifactKey,
    val width: Int,
    val height: Int,
    val format: String,
)

/**
 * Signed-distance-field atlas artifact.
 *
 * The SDF artifact wraps a generic glyph atlas description with the distance
 * range used when generating or interpreting SDF texels. It remains pure data
 * and has no dependency on the rasterizer or renderer.
 *
 * @property atlas Underlying glyph atlas description.
 * @property distanceRange Distance range represented by the SDF texels.
 */
data class SDFGlyphAtlasArtifact(
    val atlas: GlyphAtlasArtifact,
    val distanceRange: Float,
)

/**
 * Upload plan for glyph payloads associated with a text artifact.
 *
 * The plan connects glyph identifiers to a CPU-side upload payload. Glyph IDs
 * are opaque numeric IDs from the text planning layer and are not tied to a
 * font parser dependency.
 *
 * @property artifactKey Artifact instance that owns the glyph upload plan.
 * @property uploadPlan Byte upload plan for the glyph payload.
 * @property glyphIDs Ordered glyph identifiers included in the upload.
 */
data class GlyphUploadPlan(
    val artifactKey: GPUTextArtifactKey,
    val uploadPlan: GPUTextUploadPlan,
    val glyphIDs: List<UInt>,
)

/**
 * Planned outline glyph representation.
 *
 * The plan describes that glyphs should be represented as outlines and records
 * the winding rule as plain text. It intentionally does not carry parsed paths,
 * scaler handles, or renderer draw steps.
 *
 * @property artifactKey Artifact instance for the outline plan.
 * @property glyphIDs Ordered glyph identifiers represented by outlines.
 * @property windingRule Dumpable winding rule label.
 */
data class OutlineGlyphPlan(
    val artifactKey: GPUTextArtifactKey,
    val glyphIDs: List<UInt>,
    val windingRule: String,
)

/**
 * Planned color glyph representation.
 *
 * The plan records that glyphs have layered color content, without depending
 * on a color font parser, SVG parser, bitmap decoder, or renderer route.
 *
 * @property artifactKey Artifact instance for the color glyph plan.
 * @property glyphIDs Ordered glyph identifiers represented by color layers.
 * @property layerCount Number of planned color layers.
 */
data class ColorGlyphPlan(
    val artifactKey: GPUTextArtifactKey,
    val glyphIDs: List<UInt>,
    val layerCount: Int,
)

/**
 * Planned bitmap glyph representation.
 *
 * The plan names bitmap-backed glyph content and its dumpable color format,
 * while avoiding ownership of decoded pixels, codec state, or GPU textures.
 *
 * @property artifactKey Artifact instance for the bitmap glyph plan.
 * @property glyphIDs Ordered glyph identifiers represented by bitmaps.
 * @property colorFormat Dumpable bitmap color format label.
 */
data class BitmapGlyphPlan(
    val artifactKey: GPUTextArtifactKey,
    val glyphIDs: List<UInt>,
    val colorFormat: String,
)

/**
 * Planned SVG glyph representation.
 *
 * The plan records SVG glyph availability as plain counts and identifiers. It
 * does not parse SVG documents, own XML data, or bind to renderer resources.
 *
 * @property artifactKey Artifact instance for the SVG glyph plan.
 * @property glyphIDs Ordered glyph identifiers represented by SVG documents.
 * @property documentCount Number of SVG documents represented by the plan.
 */
data class SVGGlyphPlan(
    val artifactKey: GPUTextArtifactKey,
    val glyphIDs: List<UInt>,
    val documentCount: Int,
)

/**
 * Complete bundle of pure Kotlin GPU text artifacts.
 *
 * The bundle is the handoff object from font/text planning toward a future
 * renderer integration. It contains only dumpable value objects and excludes
 * renderer-owned run plans, bindings, atlas plans, routes, render steps, and
 * batch keys.
 *
 * @property artifactKey Primary artifact key for the bundle.
 * @property uploadPlans CPU-side upload plans in dependency order.
 * @property glyphUploadPlans Glyph payload upload plans.
 * @property outlineGlyphPlans Planned outline glyph representations.
 * @property colorGlyphPlans Planned color glyph representations.
 * @property bitmapGlyphPlans Planned bitmap glyph representations.
 * @property svgGlyphPlans Planned SVG glyph representations.
 * @property atlases Planned glyph atlas artifacts.
 * @property sdfAtlases Planned SDF atlas artifacts.
 * @property diagnostics Planning diagnostics and refusal summary.
 */
data class TextGPUArtifactBundle(
    val artifactKey: GPUTextArtifactKey,
    val uploadPlans: List<GPUTextUploadPlan>,
    val glyphUploadPlans: List<GlyphUploadPlan>,
    val outlineGlyphPlans: List<OutlineGlyphPlan>,
    val colorGlyphPlans: List<ColorGlyphPlan>,
    val bitmapGlyphPlans: List<BitmapGlyphPlan>,
    val svgGlyphPlans: List<SVGGlyphPlan>,
    val atlases: List<GlyphAtlasArtifact>,
    val sdfAtlases: List<SDFGlyphAtlasArtifact>,
    val diagnostics: GPUTextRouteDiagnostics,
)
