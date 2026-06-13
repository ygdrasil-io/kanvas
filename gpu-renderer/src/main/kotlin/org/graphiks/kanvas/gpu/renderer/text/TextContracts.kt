package org.graphiks.kanvas.gpu.renderer.text

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
