package org.graphiks.kanvas.glyph.gpu

import org.graphiks.kanvas.font.TypefaceID
import kotlin.uuid.Uuid

/**
 * Stable identifier for a laid-out text result that can produce GPU text
 * artifact requests.
 *
 * The identifier names a layout result without carrying paragraph, shaping,
 * glyph cache, or renderer state. It lets diagnostics and bundles reference
 * text layout provenance while keeping the handoff surface dumpable. The value
 * is a Kotlin 2.4 `Uuid`, not a renderer handle.
 *
 * @property value Opaque layout result UUID suitable for logs, snapshots, and
 * cross-module handoff records.
 */
@JvmInline
value class GPUTextLayoutResultID(
    val value: Uuid,
)

/**
 * Stable identifier for one glyph run within a laid-out text result.
 *
 * The identifier is owned by the text/font stack. It is not a renderer batch
 * key, material key, upload slot, or GPU command identifier. The value is a
 * Kotlin 2.4 `Uuid` so it can be serialized consistently across targets.
 *
 * @property value Opaque glyph run UUID suitable for diagnostics and serialized
 * planning evidence.
 */
@JvmInline
value class GPUGlyphRunID(
    val value: Uuid,
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
 * renderer batch key. The UUID is separate from the content fingerprint so
 * callers can keep stable identity and content change detection distinct.
 *
 * @property value Dumpable identifier UUID suitable for logs, test fixtures,
 * and serialized planning snapshots.
 */
@JvmInline
value class GPUTextArtifactID(
    val value: Uuid,
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
 * Dumpable reference to a typed GPU text artifact plan.
 *
 * The reference names the artifact plan type and its stable artifact key facts
 * without carrying the plan object, renderer state, GPU handles, font parser
 * state, or parsed glyph payloads. It is intentionally richer than a compact
 * content fingerprint string so diagnostics can preserve identity, generation,
 * content, and source provenance independently.
 *
 * @property artifactName Public artifact plan type name.
 * @property artifactID Stable logical artifact identifier.
 * @property generation Artifact generation associated with the planned
 * content.
 * @property contentFingerprint Dumpable content fingerprint or compact key
 * hash for the artifact content.
 * @property sourceLabel Bundle or plan field that produced the reference.
 * @property artifactType Route-facing artifact kind. This currently matches
 * [artifactName] for registered text artifact plans.
 * @property artifactKeyHash Stable compact hash used by route diagnostics. In
 * this contract slice it is the local [contentFingerprint].
 * @property invalidationFacts Stable facts that make the reference stale when
 * changed.
 * @property diagnostics Stable diagnostic facts scoped to this artifact
 * reference.
 */
data class GPUTextArtifactReference(
    val artifactName: String,
    val artifactID: GPUTextArtifactID,
    val generation: GPUTextArtifactGeneration,
    val contentFingerprint: String,
    val sourceLabel: String,
    val artifactType: String = artifactName,
    val artifactKeyHash: String = contentFingerprint,
    val invalidationFacts: List<String> = artifactReferenceInvalidationFacts(artifactName),
    val diagnostics: List<String> = emptyList(),
) {
    init {
        require(artifactType.isNotBlank()) { "artifactType must not be blank." }
        require(invalidationFacts.all { fact -> fact.isNotBlank() }) {
            "invalidationFacts must not contain blank entries."
        }
        require(diagnostics.all { diagnostic -> diagnostic.isNotBlank() }) {
            "diagnostics must not contain blank entries."
        }
    }
}

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
 * Validates upload ranges against this plan's declared payload size.
 *
 * The method returns diagnostics instead of throwing so artifact planners can
 * include invalid payload evidence in PM bundles or route refusals without
 * needing renderer state.
 */
fun GPUTextUploadPlan.validateRanges(): List<GPUTextArtifactDiagnostic> {
    val diagnostics = mutableListOf<GPUTextArtifactDiagnostic>()
    if (byteSize < 0) {
        diagnostics += GPUTextArtifactDiagnostic(
            code = GPUTextArtifactDiagnosticCode.INVALID_UPLOAD_RANGE,
            message = "Upload payload byteSize $byteSize is negative.",
            artifactKey = artifactKey,
        )
        return diagnostics
    }

    ranges.forEach { range ->
        val start = range.offset.toLong()
        val end = start + range.size.toLong()
        when {
            range.offset < 0 || range.size < 0 -> diagnostics += GPUTextArtifactDiagnostic(
                code = GPUTextArtifactDiagnosticCode.INVALID_UPLOAD_RANGE,
                message = "Upload range ${range.label} has negative offset ${range.offset} or size ${range.size}.",
                artifactKey = artifactKey,
            )
            end > byteSize.toLong() -> diagnostics += GPUTextArtifactDiagnostic(
                code = GPUTextArtifactDiagnosticCode.INVALID_UPLOAD_RANGE,
                message = "Upload range ${range.label} [$start, $end) exceeds payload byteSize $byteSize.",
                artifactKey = artifactKey,
            )
        }
    }
    return diagnostics
}

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
 * Returns a dumpable reference to this glyph atlas artifact.
 */
fun GlyphAtlasArtifact.artifactReference(
    sourceLabel: String = "GlyphAtlasArtifact",
    diagnostics: List<String> = emptyList(),
): GPUTextArtifactReference = artifactKey.toArtifactReference(
    artifactName = "GlyphAtlasArtifact",
    sourceLabel = sourceLabel,
    diagnostics = diagnostics,
)

/**
 * Returns a dumpable reference to this SDF glyph atlas artifact.
 */
fun SDFGlyphAtlasArtifact.artifactReference(
    sourceLabel: String = "SDFGlyphAtlasArtifact",
    diagnostics: List<String> = emptyList(),
): GPUTextArtifactReference = atlas.artifactKey.toArtifactReference(
    artifactName = "SDFGlyphAtlasArtifact",
    sourceLabel = sourceLabel,
    diagnostics = diagnostics,
)

/**
 * Returns a dumpable reference to this glyph upload plan.
 */
fun GlyphUploadPlan.artifactReference(
    sourceLabel: String = "GlyphUploadPlan",
    diagnostics: List<String> = emptyList(),
): GPUTextArtifactReference = artifactKey.toArtifactReference(
    artifactName = "GlyphUploadPlan",
    sourceLabel = sourceLabel,
    diagnostics = diagnostics,
)

/**
 * Returns a dumpable reference to this outline glyph plan.
 */
fun OutlineGlyphPlan.artifactReference(
    sourceLabel: String = "OutlineGlyphPlan",
    diagnostics: List<String> = emptyList(),
): GPUTextArtifactReference = artifactKey.toArtifactReference(
    artifactName = "OutlineGlyphPlan",
    sourceLabel = sourceLabel,
    diagnostics = diagnostics,
)

/**
 * Returns a dumpable reference to this color glyph plan.
 */
fun ColorGlyphPlan.artifactReference(
    sourceLabel: String = "ColorGlyphPlan",
    diagnostics: List<String> = emptyList(),
): GPUTextArtifactReference = artifactKey.toArtifactReference(
    artifactName = "ColorGlyphPlan",
    sourceLabel = sourceLabel,
    diagnostics = diagnostics,
)

/**
 * Returns a dumpable reference to this bitmap glyph plan.
 */
fun BitmapGlyphPlan.artifactReference(
    sourceLabel: String = "BitmapGlyphPlan",
    diagnostics: List<String> = emptyList(),
): GPUTextArtifactReference = artifactKey.toArtifactReference(
    artifactName = "BitmapGlyphPlan",
    sourceLabel = sourceLabel,
    diagnostics = diagnostics,
)

/**
 * Returns a dumpable reference to this SVG glyph plan.
 */
fun SVGGlyphPlan.artifactReference(
    sourceLabel: String = "SVGGlyphPlan",
    diagnostics: List<String> = emptyList(),
): GPUTextArtifactReference = artifactKey.toArtifactReference(
    artifactName = "SVGGlyphPlan",
    sourceLabel = sourceLabel,
    diagnostics = diagnostics,
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

/**
 * Returns all typed artifact references in a stable handoff order.
 *
 * The order is category-first and then preserves each bundle list's order:
 * `GlyphAtlasArtifact`, `SDFGlyphAtlasArtifact`, `GlyphUploadPlan`,
 * `OutlineGlyphPlan`, `ColorGlyphPlan`, `BitmapGlyphPlan`, and `SVGGlyphPlan`.
 * Generic byte upload plans are intentionally not surfaced as a separate
 * category because the typed `GlyphUploadPlan` owns the handoff reference.
 */
fun TextGPUArtifactBundle.artifactReferences(): List<GPUTextArtifactReference> {
    val references = mutableListOf<GPUTextArtifactReference>()
    atlases.mapTo(references) {
        it.artifactReference(
            sourceLabel = "TextGPUArtifactBundle.atlases",
            diagnostics = diagnostics.referenceDiagnosticsFor(it.artifactKey),
        )
    }
    sdfAtlases.mapTo(references) {
        it.artifactReference(
            sourceLabel = "TextGPUArtifactBundle.sdfAtlases",
            diagnostics = diagnostics.referenceDiagnosticsFor(it.atlas.artifactKey),
        )
    }
    glyphUploadPlans.mapTo(references) {
        it.artifactReference(
            sourceLabel = "TextGPUArtifactBundle.glyphUploadPlans",
            diagnostics = diagnostics.referenceDiagnosticsFor(it.artifactKey),
        )
    }
    outlineGlyphPlans.mapTo(references) {
        it.artifactReference(
            sourceLabel = "TextGPUArtifactBundle.outlineGlyphPlans",
            diagnostics = diagnostics.referenceDiagnosticsFor(it.artifactKey),
        )
    }
    colorGlyphPlans.mapTo(references) {
        it.artifactReference(
            sourceLabel = "TextGPUArtifactBundle.colorGlyphPlans",
            diagnostics = diagnostics.referenceDiagnosticsFor(it.artifactKey),
        )
    }
    bitmapGlyphPlans.mapTo(references) {
        it.artifactReference(
            sourceLabel = "TextGPUArtifactBundle.bitmapGlyphPlans",
            diagnostics = diagnostics.referenceDiagnosticsFor(it.artifactKey),
        )
    }
    svgGlyphPlans.mapTo(references) {
        it.artifactReference(
            sourceLabel = "TextGPUArtifactBundle.svgGlyphPlans",
            diagnostics = diagnostics.referenceDiagnosticsFor(it.artifactKey),
        )
    }
    return references
}

/**
 * Validates the concrete artifact bundle surface against the no-Sk handoff
 * rule using explicit real-field facts instead of a hand-written fixture list.
 */
fun TextGPUArtifactBundle.noSkLeakageReport(): TextPayloadLeakReport = validateGPUTextNoSkLeakage(
    payloadKind = "TextGPUArtifactBundle",
    fields = textPayloadLeakageFields(),
)

private fun GPUTextArtifactKey.toArtifactReference(
    artifactName: String,
    sourceLabel: String,
    diagnostics: List<String>,
): GPUTextArtifactReference = GPUTextArtifactReference(
    artifactName = artifactName,
    artifactID = artifactID,
    generation = generation,
    contentFingerprint = contentFingerprint,
    sourceLabel = sourceLabel,
    artifactType = artifactName,
    artifactKeyHash = contentFingerprint,
    invalidationFacts = artifactReferenceInvalidationFacts(artifactName),
    diagnostics = diagnostics.toList(),
)

private fun artifactReferenceInvalidationFacts(artifactName: String): List<String> =
    defaultTextGPUArtifactRegistry()
        .descriptor(artifactName)
        ?.invalidationFacts
        ?.toList()
        ?: listOf("generation", "contentFingerprint")

private fun GPUTextRouteDiagnostics.referenceDiagnosticsFor(
    artifactKey: GPUTextArtifactKey,
): List<String> =
    diagnostics
        .filter { diagnostic -> diagnostic.artifactKey == artifactKey }
        .map { diagnostic -> diagnostic.toArtifactReferenceDiagnostic() }

private fun GPUTextArtifactDiagnostic.toArtifactReferenceDiagnostic(): String =
    "${code.name}:$message"

private fun TextGPUArtifactBundle.textPayloadLeakageFields(): List<TextPayloadField> = buildList {
    addArtifactKeyFields("artifactKey", artifactKey)
    add(TextPayloadField("uploadPlans", "List<GPUTextUploadPlan>"))
    uploadPlans.forEachIndexed { index, uploadPlan ->
        addUploadPlanFields("uploadPlans[$index]", uploadPlan)
    }
    add(TextPayloadField("glyphUploadPlans", "List<GlyphUploadPlan>"))
    glyphUploadPlans.forEachIndexed { index, glyphUploadPlan ->
        addGlyphUploadPlanFields("glyphUploadPlans[$index]", glyphUploadPlan)
    }
    add(TextPayloadField("outlineGlyphPlans", "List<OutlineGlyphPlan>"))
    outlineGlyphPlans.forEachIndexed { index, outlineGlyphPlan ->
        addArtifactKeyFields("outlineGlyphPlans[$index].artifactKey", outlineGlyphPlan.artifactKey)
        addUIntListFields("outlineGlyphPlans[$index].glyphIDs", outlineGlyphPlan.glyphIDs)
        add(TextPayloadField("outlineGlyphPlans[$index].windingRule", "String", outlineGlyphPlan.windingRule))
    }
    add(TextPayloadField("colorGlyphPlans", "List<ColorGlyphPlan>"))
    colorGlyphPlans.forEachIndexed { index, colorGlyphPlan ->
        addArtifactKeyFields("colorGlyphPlans[$index].artifactKey", colorGlyphPlan.artifactKey)
        addUIntListFields("colorGlyphPlans[$index].glyphIDs", colorGlyphPlan.glyphIDs)
        add(TextPayloadField("colorGlyphPlans[$index].layerCount", "Int", colorGlyphPlan.layerCount.toString()))
    }
    add(TextPayloadField("bitmapGlyphPlans", "List<BitmapGlyphPlan>"))
    bitmapGlyphPlans.forEachIndexed { index, bitmapGlyphPlan ->
        addArtifactKeyFields("bitmapGlyphPlans[$index].artifactKey", bitmapGlyphPlan.artifactKey)
        addUIntListFields("bitmapGlyphPlans[$index].glyphIDs", bitmapGlyphPlan.glyphIDs)
        add(TextPayloadField("bitmapGlyphPlans[$index].colorFormat", "String", bitmapGlyphPlan.colorFormat))
    }
    add(TextPayloadField("svgGlyphPlans", "List<SVGGlyphPlan>"))
    svgGlyphPlans.forEachIndexed { index, svgGlyphPlan ->
        addArtifactKeyFields("svgGlyphPlans[$index].artifactKey", svgGlyphPlan.artifactKey)
        addUIntListFields("svgGlyphPlans[$index].glyphIDs", svgGlyphPlan.glyphIDs)
        add(TextPayloadField("svgGlyphPlans[$index].documentCount", "Int", svgGlyphPlan.documentCount.toString()))
    }
    add(TextPayloadField("atlases", "List<GlyphAtlasArtifact>"))
    atlases.forEachIndexed { index, atlas ->
        addGlyphAtlasFields("atlases[$index]", atlas)
    }
    add(TextPayloadField("sdfAtlases", "List<SDFGlyphAtlasArtifact>"))
    sdfAtlases.forEachIndexed { index, sdfAtlas ->
        addGlyphAtlasFields("sdfAtlases[$index].atlas", sdfAtlas.atlas)
        add(TextPayloadField("sdfAtlases[$index].distanceRange", "Float", sdfAtlas.distanceRange.toString()))
    }
    addRouteDiagnosticsFields("diagnostics", diagnostics)
    add(TextPayloadField("artifactReferences", "List<GPUTextArtifactReference>"))
    artifactReferences().forEachIndexed { index, reference ->
        addArtifactReferenceFields("artifactReferences[$index]", reference)
    }
}

private fun MutableList<TextPayloadField>.addArtifactKeyFields(
    fieldPrefix: String,
    artifactKey: GPUTextArtifactKey,
) {
    add(TextPayloadField("$fieldPrefix.artifactID", "GPUTextArtifactID", artifactKey.artifactID.value.toString()))
    add(TextPayloadField("$fieldPrefix.generation", "GPUTextArtifactGeneration", artifactKey.generation.value.toString()))
    add(TextPayloadField("$fieldPrefix.contentFingerprint", "String", artifactKey.contentFingerprint))
}

private fun MutableList<TextPayloadField>.addUploadPlanFields(
    fieldPrefix: String,
    uploadPlan: GPUTextUploadPlan,
) {
    addArtifactKeyFields("$fieldPrefix.artifactKey", uploadPlan.artifactKey)
    add(TextPayloadField("$fieldPrefix.ranges", "List<GPUTextUploadRange>"))
    uploadPlan.ranges.forEachIndexed { index, range ->
        add(TextPayloadField("$fieldPrefix.ranges[$index].offset", "Int", range.offset.toString()))
        add(TextPayloadField("$fieldPrefix.ranges[$index].size", "Int", range.size.toString()))
        add(TextPayloadField("$fieldPrefix.ranges[$index].label", "String", range.label))
    }
    add(TextPayloadField("$fieldPrefix.byteSize", "Int", uploadPlan.byteSize.toString()))
}

private fun MutableList<TextPayloadField>.addGlyphUploadPlanFields(
    fieldPrefix: String,
    glyphUploadPlan: GlyphUploadPlan,
) {
    addArtifactKeyFields("$fieldPrefix.artifactKey", glyphUploadPlan.artifactKey)
    addUploadPlanFields("$fieldPrefix.uploadPlan", glyphUploadPlan.uploadPlan)
    addUIntListFields("$fieldPrefix.glyphIDs", glyphUploadPlan.glyphIDs)
}

private fun MutableList<TextPayloadField>.addGlyphAtlasFields(
    fieldPrefix: String,
    atlas: GlyphAtlasArtifact,
) {
    addArtifactKeyFields("$fieldPrefix.artifactKey", atlas.artifactKey)
    add(TextPayloadField("$fieldPrefix.width", "Int", atlas.width.toString()))
    add(TextPayloadField("$fieldPrefix.height", "Int", atlas.height.toString()))
    add(TextPayloadField("$fieldPrefix.format", "String", atlas.format))
}

private fun MutableList<TextPayloadField>.addRouteDiagnosticsFields(
    fieldPrefix: String,
    routeDiagnostics: GPUTextRouteDiagnostics,
) {
    add(TextPayloadField("$fieldPrefix.diagnostics", "List<GPUTextArtifactDiagnostic>"))
    routeDiagnostics.diagnostics.forEachIndexed { index, diagnostic ->
        add(TextPayloadField("$fieldPrefix.diagnostics[$index].code", "GPUTextArtifactDiagnosticCode", diagnostic.code.name))
        add(TextPayloadField("$fieldPrefix.diagnostics[$index].message", "String", diagnostic.message))
        diagnostic.artifactKey?.let { artifactKey ->
            addArtifactKeyFields("$fieldPrefix.diagnostics[$index].artifactKey", artifactKey)
        }
    }
    add(TextPayloadField("$fieldPrefix.refusalRequired", "Boolean", routeDiagnostics.refusalRequired.toString()))
}

private fun MutableList<TextPayloadField>.addArtifactReferenceFields(
    fieldPrefix: String,
    reference: GPUTextArtifactReference,
) {
    add(TextPayloadField("$fieldPrefix.artifactName", "String", reference.artifactName))
    add(TextPayloadField("$fieldPrefix.artifactType", "String", reference.artifactType))
    add(TextPayloadField("$fieldPrefix.artifactID", "GPUTextArtifactID", reference.artifactID.value.toString()))
    add(TextPayloadField("$fieldPrefix.generation", "GPUTextArtifactGeneration", reference.generation.value.toString()))
    add(TextPayloadField("$fieldPrefix.contentFingerprint", "String", reference.contentFingerprint))
    add(TextPayloadField("$fieldPrefix.artifactKeyHash", "String", reference.artifactKeyHash))
    addStringListFields("$fieldPrefix.invalidationFacts", reference.invalidationFacts)
    addStringListFields("$fieldPrefix.diagnostics", reference.diagnostics)
    add(TextPayloadField("$fieldPrefix.sourceLabel", "String", reference.sourceLabel))
}

private fun MutableList<TextPayloadField>.addUIntListFields(
    fieldPrefix: String,
    values: List<UInt>,
) {
    add(TextPayloadField(fieldPrefix, "List<UInt>"))
    values.forEachIndexed { index, value ->
        add(TextPayloadField("$fieldPrefix[$index]", "UInt", value.toString()))
    }
}

private fun MutableList<TextPayloadField>.addStringListFields(
    fieldPrefix: String,
    values: List<String>,
) {
    add(TextPayloadField(fieldPrefix, "List<String>"))
    values.forEachIndexed { index, value ->
        add(TextPayloadField("$fieldPrefix[$index]", "String", value))
    }
}
