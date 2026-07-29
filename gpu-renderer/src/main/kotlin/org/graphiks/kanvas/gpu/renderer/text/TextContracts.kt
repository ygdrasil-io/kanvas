package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.glyph.gpu.GPUColorGlyphLayerPlan
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactGeneration
import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactReference
import org.graphiks.kanvas.glyph.gpu.GPUTextRefusalCodes

/** Text ordering token. */
@JvmInline
value class GPUTextOrderingToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUTextOrderingToken.value must not be blank" }
    }
}

/** Font key for referencing a specific typeface in the GPU text stack. */
@JvmInline
value class GPUFontKey(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUFontKey.value must not be blank" }
    }
}

/** Reference to a specific page within a text atlas. */
@JvmInline
value class GPUAtlasPageRef(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUAtlasPageRef.value must not be blank" }
    }
}

/** Per-component alpha modulation for subpixel LCD rendering. */
@JvmInline
value class GPUPerComponentAlphaModulation(val modulation: String) {
    init {
        require(modulation.isNotBlank()) { "GPUPerComponentAlphaModulation.modulation must not be blank" }
    }
}

/** Subpixel LCD WGSL module descriptor. */
data class GPUSubpixelLCDWGSL(
    val moduleId: String,
    val entryPoint: String,
)

/** Text run plan after pure Kotlin shaping/layout. */
data class GPUTextRunPlan(
    val layoutId: String,
    val glyphRunLabels: List<String>,
    val transformLabel: String,
    val clipLabel: String,
    val layerLabel: String,
    val colorLabel: String,
    val blendLabel: String,
    val subRuns: List<GPUTextSubRunPlan>,
    val diagnostics: List<GPUTextDiagnostic> = emptyList(),
)

/** Text sub-run plan. */
data class GPUTextSubRunPlan(
    val representation: String,
    val glyphRange: IntRange,
    val boundsLabel: String,
    val atlasRefs: List<String>,
    val instancePlan: GPUTextInstancePlan,
    val ordering: GPUTextOrderingToken,
)

/** Text route. */
sealed interface GPUTextRoute {
    /** A8 atlas route. */
    data class AtlasA8(val atlas: GPUTextAtlasPlan) : GPUTextRoute

    /** SDF atlas route. */
    data class AtlasSDF(val atlas: GPUTextAtlasPlan, val sdf: GPUTextSDFParams) : GPUTextRoute

    /** Outline glyph route. */
    data class Outline(val plan: OutlineGlyphPlan) : GPUTextRoute

    /** Color glyph route. */
    data class ColorGlyph(val plan: GPUColorGlyphLayerPlan) : GPUTextRoute

    /** Bitmap glyph route. */
    data class BitmapGlyph(val plan: BitmapGlyphPlan) : GPUTextRoute

    /** SVG glyph route, dependency-gated until real support lands. */
    data class SVGGlyph(val plan: SVGGlyphPlan) : GPUTextRoute

    /** Text route blocked by a dependency. */
    data class DependencyGated(val diagnostic: GPUTextDiagnostic) : GPUTextRoute

    /** Refused text route. */
    data class Refused(val diagnostic: GPUTextDiagnostic) : GPUTextRoute
}

/** Text render step contract. */
data class GPUTextRenderStep(
    val stepLabel: String,
    val routeLabel: String,
    val pipelineKeyHash: String,
)

/** Text atlas plan. */
data class GPUTextAtlasPlan(
    val atlasKind: String,
    val atlasKey: String,
    val pageCount: Int,
    val budgetClass: String,
)

/** Text binding plan. */
data class GPUTextBinding(
    val bindingLabel: String,
    val atlasKey: String,
    val samplerLabel: String,
)

/** Text instance plan. */
data class GPUTextInstancePlan(
    val instanceCount: Int,
    val instanceLayoutHash: String,
    val payloadHash: String,
)

/** SDF text parameters. */
data class GPUTextSDFParams(
    val radius: Float,
    val threshold: Float,
    val smoothing: Float,
)

/** Glyph atlas artifact descriptor. */
data class GlyphAtlasArtifact(
    val artifactKey: String,
    val atlasKind: String,
    val generation: Long,
    val lifetimeClass: String,
)

/** SDF glyph atlas artifact descriptor. */
data class SDFGlyphAtlasArtifact(
    val artifactKey: String,
    val sdfParams: GPUTextSDFParams,
    val generation: Long,
    val lifetimeClass: String,
)

/** Glyph upload plan. */
data class GlyphUploadPlan(
    val artifactKey: String,
    val glyphCount: Int,
    val uploadBudgetClass: String,
)

/** Outline glyph plan. */
data class OutlineGlyphPlan(
    val glyphIds: List<Int>,
    val pathArtifactKeys: List<String>,
    val fillRule: String,
)

/** Bitmap glyph plan. */
data class BitmapGlyphPlan(
    val glyphIds: List<Int>,
    val bitmapFormat: String,
    val uploadPlan: GlyphUploadPlan,
)

/** SVG glyph plan, dependency-gated. */
data class SVGGlyphPlan(
    val glyphIds: List<Int>,
    val svgDocumentKeys: List<String>,
    val dependencyGate: String,
)

/** Text diagnostic. */
data class GPUTextDiagnostic(
    val code: String,
    val layoutId: String? = null,
    val message: String,
    val terminal: Boolean,
)

/** Text route decision produced by the A8 route planner from a glyph run descriptor. */
sealed interface GPUTextRouteDecision {
    /** Accepted A8 atlas route. */
    data class Accepted(val route: GPUTextRoute.AtlasA8) : GPUTextRouteDecision

    /** Refused text route with a stable diagnostic. */
    data class Refused(val diagnostic: GPUTextDiagnostic) : GPUTextRouteDecision
}

/** Dumpable reference to one text-stack artifact plan consumed by GPU text routing. */
data class GPUTextArtifactRef(
    val artifactType: String,
    val artifactId: String,
    val artifactKeyHash: String,
    val generation: GPUTextArtifactGeneration,
    val routeHint: String? = null,
)

/**
 * Converts a pure Kotlin text-stack artifact reference into the renderer
 * command payload shape without importing font objects, bytes, or GPU handles.
 */
fun GPUTextArtifactReference.toRendererTextArtifactRef(
    routeHint: String? = null,
): GPUTextArtifactRef = GPUTextArtifactRef(
    artifactType = artifactName,
    artifactId = artifactID.value.toHexDashString(),
    artifactKeyHash = contentFingerprint,
    generation = generation,
    routeHint = routeHint,
)

/** Stable diagnostic codes for GPU text route selection and refusals. */
object GPUTextDiagnosticCodes {
    const val PAYLOAD_NONDUMPABLE: String = GPUTextRefusalCodes.PAYLOAD_NONDUMPABLE
    const val SK_TYPE_LEAKED: String = GPUTextRefusalCodes.SK_TYPE_LEAKED
    const val ARTIFACT_UNREGISTERED: String = GPUTextRefusalCodes.ARTIFACT_UNREGISTERED
    const val ARTIFACT_KEY_NONDETERMINISTIC: String =
        GPUTextRefusalCodes.ARTIFACT_KEY_NONDETERMINISTIC
    const val ARTIFACT_GENERATION_STALE: String =
        GPUTextRefusalCodes.ARTIFACT_GENERATION_STALE
    const val ARTIFACT_BUDGET_EXCEEDED: String =
        GPUTextRefusalCodes.ARTIFACT_BUDGET_EXCEEDED
    const val UPLOAD_PLAN_MISSING: String = GPUTextRefusalCodes.UPLOAD_PLAN_MISSING
    const val UPLOAD_BUDGET_EXCEEDED: String = GPUTextRefusalCodes.UPLOAD_BUDGET_EXCEEDED
    const val UPLOAD_FAILED: String = GPUTextRefusalCodes.UPLOAD_FAILED
    const val ATLAS_DESCRIPTOR_UNACCEPTED: String =
        GPUTextRefusalCodes.ATLAS_DESCRIPTOR_UNACCEPTED
    const val ATLAS_PAGE_UNAVAILABLE: String = GPUTextRefusalCodes.ATLAS_PAGE_UNAVAILABLE
    const val ATLAS_ENTRY_MISSING: String = GPUTextRefusalCodes.ATLAS_ENTRY_MISSING
    const val ATLAS_GENERATION_STALE: String = GPUTextRefusalCodes.ATLAS_GENERATION_STALE
    const val A8_ATLAS_ROUTE_UNAVAILABLE: String =
        GPUTextRefusalCodes.A8_ATLAS_ROUTE_UNAVAILABLE
    const val SDF_ROUTE_UNAVAILABLE: String = GPUTextRefusalCodes.SDF_ROUTE_UNAVAILABLE
    const val SDF_PARAMS_MISSING: String = GPUTextRefusalCodes.SDF_PARAMS_MISSING
    const val SDF_TRANSFORM_UNSUPPORTED: String = GPUTextRefusalCodes.SDF_TRANSFORM_UNSUPPORTED
    const val OUTLINE_ROUTE_UNAVAILABLE: String = GPUTextRefusalCodes.OUTLINE_ROUTE_UNAVAILABLE
    const val COLOR_PLAN_UNSUPPORTED: String = GPUTextRefusalCodes.COLOR_PLAN_UNSUPPORTED
    const val COLOR_COMPOSITE_UNSUPPORTED: String =
        GPUTextRefusalCodes.COLOR_COMPOSITE_UNSUPPORTED
    const val COLOR_FONT_FORMAT_UNAVAILABLE: String =
        GPUTextRefusalCodes.COLOR_FONT_FORMAT_UNAVAILABLE
    const val COLOR_FONT_LAYER_COUNT_EXCEEDED: String =
        GPUTextRefusalCodes.COLOR_FONT_LAYER_COUNT_EXCEEDED
    const val BITMAP_ROUTE_UNSUPPORTED: String = GPUTextRefusalCodes.BITMAP_ROUTE_UNSUPPORTED
    const val SVG_PLAN_UNSUPPORTED: String = GPUTextRefusalCodes.SVG_PLAN_UNSUPPORTED
    const val EMOJI_COLOR_GLYPH_UNAVAILABLE: String =
        GPUTextRefusalCodes.EMOJI_COLOR_GLYPH_UNAVAILABLE
    const val LCD_FUTURE_RESEARCH: String = GPUTextRefusalCodes.LCD_FUTURE_RESEARCH
    const val INSTANCE_BUFFER_BUDGET_EXCEEDED: String =
        GPUTextRefusalCodes.INSTANCE_BUFFER_BUDGET_EXCEEDED
    const val BINDING_LAYOUT_UNAVAILABLE: String =
        GPUTextRefusalCodes.BINDING_LAYOUT_UNAVAILABLE
    const val DESTINATION_READ_UNACCEPTED: String =
        GPUTextRefusalCodes.DESTINATION_READ_UNACCEPTED
    const val CLIP_ROUTE_UNACCEPTED: String = GPUTextRefusalCodes.CLIP_ROUTE_UNACCEPTED
    const val CPU_RENDERED_TEXTURE_FORBIDDEN: String =
        GPUTextRefusalCodes.CPU_RENDERED_TEXTURE_FORBIDDEN
    const val SUBPIXEL_PIXEL_GEOMETRY: String =
        GPUTextRefusalCodes.SUBPIXEL_PIXEL_GEOMETRY
    const val SUBPIXEL_TARGET_FORMAT: String = GPUTextRefusalCodes.SUBPIXEL_TARGET_FORMAT
    const val FALLBACK_EXHAUSTED: String = GPUTextRefusalCodes.FALLBACK_EXHAUSTED

    val all: List<String> = listOf(
        PAYLOAD_NONDUMPABLE,
        SK_TYPE_LEAKED,
        ARTIFACT_UNREGISTERED,
        ARTIFACT_KEY_NONDETERMINISTIC,
        ARTIFACT_GENERATION_STALE,
        ARTIFACT_BUDGET_EXCEEDED,
        UPLOAD_PLAN_MISSING,
        UPLOAD_BUDGET_EXCEEDED,
        UPLOAD_FAILED,
        ATLAS_DESCRIPTOR_UNACCEPTED,
        ATLAS_PAGE_UNAVAILABLE,
        ATLAS_ENTRY_MISSING,
        ATLAS_GENERATION_STALE,
        A8_ATLAS_ROUTE_UNAVAILABLE,
        SDF_ROUTE_UNAVAILABLE,
        SDF_PARAMS_MISSING,
        SDF_TRANSFORM_UNSUPPORTED,
        OUTLINE_ROUTE_UNAVAILABLE,
        COLOR_PLAN_UNSUPPORTED,
        COLOR_COMPOSITE_UNSUPPORTED,
        COLOR_FONT_FORMAT_UNAVAILABLE,
        COLOR_FONT_LAYER_COUNT_EXCEEDED,
        BITMAP_ROUTE_UNSUPPORTED,
        SVG_PLAN_UNSUPPORTED,
        EMOJI_COLOR_GLYPH_UNAVAILABLE,
        LCD_FUTURE_RESEARCH,
        INSTANCE_BUFFER_BUDGET_EXCEEDED,
        BINDING_LAYOUT_UNAVAILABLE,
        DESTINATION_READ_UNACCEPTED,
        CLIP_ROUTE_UNACCEPTED,
        CPU_RENDERED_TEXTURE_FORBIDDEN,
        SUBPIXEL_PIXEL_GEOMETRY,
        SUBPIXEL_TARGET_FORMAT,
        FALLBACK_EXHAUSTED,
    )
}

/** Classifies a color glyph route refusal into its stable category. */
enum class ColorGlyphRefusalKind {
    FORMAT_UNAVAILABLE,
    LAYER_COUNT_EXCEEDED,
}

/** Decision produced by the color glyph route planner. */
sealed interface GPUColorGlyphRouteDecision {
    /** COLRv0 color route accepted. */
    data class Accepted(val route: GPUTextRoute.ColorGlyph) : GPUColorGlyphRouteDecision

    /** Color route refused with a stable diagnostic and refusal category. */
    data class Refused(
        val diagnostic: GPUTextDiagnostic,
        val refusalKind: ColorGlyphRefusalKind,
    ) : GPUColorGlyphRouteDecision
}

/** Dependency gate for one text representation that is visible to route diagnostics and PM dumps. */
data class GPUTextRepresentationGate(
    val representation: String,
    val diagnosticCode: String,
    val legacyGates: List<String>,
    val promoted: Boolean = false,
) {
    /** Deterministic evidence line for reports and tests. */
    fun dumpLine(): String =
        listOf(
            representation,
            diagnosticCode,
            legacyGates.joinToString(","),
            if (promoted) "promoted" else "not-promoted",
        ).joinToString("|")
}

/** Current text representation refusal matrix; it is evidence, not route promotion. */
object GPUTextRepresentationGateMatrix {
    private val gates: List<GPUTextRepresentationGate> = listOf(
        GPUTextRepresentationGate(
            representation = "A8MaskAtlas",
            diagnosticCode = GPUTextDiagnosticCodes.A8_ATLAS_ROUTE_UNAVAILABLE,
            legacyGates = listOf("dftext"),
        ),
        GPUTextRepresentationGate(
            representation = "SDFMaskAtlas",
            diagnosticCode = GPUTextDiagnosticCodes.SDF_ROUTE_UNAVAILABLE,
            legacyGates = listOf("dftext"),
        ),
        GPUTextRepresentationGate(
            representation = "COLRColorGlyph",
            diagnosticCode = GPUTextDiagnosticCodes.COLOR_PLAN_UNSUPPORTED,
            legacyGates = listOf("coloremoji_blendmodes"),
            promoted = true,
        ),
        GPUTextRepresentationGate(
            representation = "BitmapGlyph",
            diagnosticCode = GPUTextDiagnosticCodes.BITMAP_ROUTE_UNSUPPORTED,
            legacyGates = listOf("scaledemoji_rendering"),
        ),
        GPUTextRepresentationGate(
            representation = "SVGGlyph",
            diagnosticCode = GPUTextDiagnosticCodes.SVG_PLAN_UNSUPPORTED,
            legacyGates = listOf("scaledemoji_rendering"),
        ),
        GPUTextRepresentationGate(
            representation = "EmojiColorGlyph",
            diagnosticCode = GPUTextDiagnosticCodes.EMOJI_COLOR_GLYPH_UNAVAILABLE,
            legacyGates = listOf("scaledemoji_rendering", "coloremoji_blendmodes"),
        ),
        GPUTextRepresentationGate(
            representation = "LCDMask",
            diagnosticCode = GPUTextDiagnosticCodes.LCD_FUTURE_RESEARCH,
            legacyGates = listOf("dftext"),
        ),
        GPUTextRepresentationGate(
            representation = "CPURenderedTextTexture",
            diagnosticCode = GPUTextDiagnosticCodes.CPU_RENDERED_TEXTURE_FORBIDDEN,
            legacyGates = listOf("dftext", "scaledemoji_rendering", "coloremoji_blendmodes"),
        ),
    )

    /** Matrix keyed by representation name in deterministic insertion order. */
    fun byRepresentation(): Map<String, GPUTextRepresentationGate> =
        gates.associateBy { gate -> gate.representation }

    /** Deterministic refusal dump for report evidence. */
    fun dumpLines(): List<String> = gates.map { gate -> gate.dumpLine() }
}
