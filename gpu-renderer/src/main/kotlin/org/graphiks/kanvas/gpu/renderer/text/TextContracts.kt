package org.graphiks.kanvas.gpu.renderer.text

import org.graphiks.kanvas.glyph.gpu.GPUTextArtifactReference

/** Text ordering token. */
@JvmInline
value class GPUTextOrderingToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUTextOrderingToken.value must not be blank" }
    }
}

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
    data class ColorGlyph(val plan: ColorGlyphPlan) : GPUTextRoute

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

/** Color glyph plan. */
data class ColorGlyphPlan(
    val glyphIds: List<Int>,
    val paletteLabel: String,
    val layerCount: Int,
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

/** Dumpable reference to one text-stack artifact plan consumed by GPU text routing. */
data class GPUTextArtifactRef(
    val artifactType: String,
    val artifactId: String,
    val artifactKeyHash: String,
    val generationToken: String,
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
    generationToken = generation.value.toString(),
    routeHint = routeHint,
)

/** Stable diagnostic codes for GPU text route selection and refusals. */
object GPUTextDiagnosticCodes {
    const val PAYLOAD_NONDUMPABLE: String = "unsupported.text.payload_nondumpable"
    const val SK_TYPE_LEAKED: String = "unsupported.text.sk_type_leaked"
    const val ARTIFACT_UNREGISTERED: String = "unsupported.text.artifact_unregistered"
    const val ARTIFACT_KEY_NONDETERMINISTIC: String = "unsupported.text.artifact_key_nondeterministic"
    const val ARTIFACT_GENERATION_STALE: String = "unsupported.text.artifact_generation_stale"
    const val ARTIFACT_BUDGET_EXCEEDED: String = "unsupported.text.artifact_budget_exceeded"
    const val UPLOAD_PLAN_MISSING: String = "unsupported.text.upload_plan_missing"
    const val UPLOAD_BUDGET_EXCEEDED: String = "unsupported.text.upload_budget_exceeded"
    const val UPLOAD_FAILED: String = "unsupported.text.upload_failed"
    const val ATLAS_DESCRIPTOR_UNACCEPTED: String = "unsupported.text.atlas_descriptor_unaccepted"
    const val ATLAS_PAGE_UNAVAILABLE: String = "unsupported.text.atlas_page_unavailable"
    const val ATLAS_ENTRY_MISSING: String = "unsupported.text.atlas_entry_missing"
    const val ATLAS_GENERATION_STALE: String = "unsupported.text.atlas_generation_stale"
    const val A8_ATLAS_ROUTE_UNAVAILABLE: String = "unsupported.text.a8_atlas_route_unavailable"
    const val SDF_ROUTE_UNAVAILABLE: String = "unsupported.text.sdf_route_unavailable"
    const val SDF_PARAMS_MISSING: String = "unsupported.text.sdf_params_missing"
    const val SDF_TRANSFORM_UNSUPPORTED: String = "unsupported.text.sdf_transform_unsupported"
    const val OUTLINE_ROUTE_UNAVAILABLE: String = "unsupported.text.outline_route_unavailable"
    const val COLOR_PLAN_UNSUPPORTED: String = "unsupported.text.color_plan_unsupported"
    const val COLOR_COMPOSITE_UNSUPPORTED: String = "unsupported.text.color_composite_unsupported"
    const val BITMAP_ROUTE_UNSUPPORTED: String = "unsupported.text.bitmap_route_unsupported"
    const val SVG_PLAN_UNSUPPORTED: String = "unsupported.text.svg_plan_unsupported"
    const val EMOJI_COLOR_GLYPH_UNAVAILABLE: String = "dependency.text.emoji_color_glyph_unavailable"
    const val LCD_FUTURE_RESEARCH: String = "unsupported.text.lcd_future_research"
    const val INSTANCE_BUFFER_BUDGET_EXCEEDED: String = "unsupported.text.instance_buffer_budget_exceeded"
    const val BINDING_LAYOUT_UNAVAILABLE: String = "unsupported.text.binding_layout_unavailable"
    const val DESTINATION_READ_UNACCEPTED: String = "unsupported.text.destination_read_unaccepted"
    const val CLIP_ROUTE_UNACCEPTED: String = "unsupported.text.clip_route_unaccepted"
    const val CPU_RENDERED_TEXTURE_FORBIDDEN: String = "unsupported.text.cpu_rendered_texture_forbidden"

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
        BITMAP_ROUTE_UNSUPPORTED,
        SVG_PLAN_UNSUPPORTED,
        EMOJI_COLOR_GLYPH_UNAVAILABLE,
        LCD_FUTURE_RESEARCH,
        INSTANCE_BUFFER_BUDGET_EXCEEDED,
        BINDING_LAYOUT_UNAVAILABLE,
        DESTINATION_READ_UNACCEPTED,
        CLIP_ROUTE_UNACCEPTED,
        CPU_RENDERED_TEXTURE_FORBIDDEN,
    )
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
