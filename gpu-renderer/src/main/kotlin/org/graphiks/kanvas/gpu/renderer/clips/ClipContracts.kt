package org.graphiks.kanvas.gpu.renderer.clips

/** Clip ordering token. */
@JvmInline
value class GPUClipOrderingToken(val value: String) {
    init {
        require(value.isNotBlank()) { "GPUClipOrderingToken.value must not be blank" }
    }
}

/** Captured clip stack descriptor. */
data class GPUClipStackDescriptor(
    val stackId: String,
    val stateLabel: String,
    val boundsLabel: String,
    val activeElementCount: Int,
    val generation: Long,
    val provenance: String,
)

/** Clip element plan. */
sealed interface GPUClipElementPlan {
    /** Clip element can be ignored. */
    data class Ignore(val reasonCode: String) : GPUClipElementPlan

    /** Clip element culls the draw. */
    data class Cull(val reasonCode: String) : GPUClipElementPlan

    /** Clip element is represented as geometry. */
    data class Geometric(val geometryLabel: String) : GPUClipElementPlan

    /** Clip element is represented as a scissor. */
    data class Scissor(val plan: GPUClipScissorPlan) : GPUClipElementPlan

    /** Clip element is represented analytically. */
    data class Analytic(val plan: GPUClipAnalyticPlan) : GPUClipElementPlan

    /** Clip element is represented with stencil. */
    data class Stencil(val plan: GPUClipStencilPlan) : GPUClipElementPlan

    /** Clip element is represented with a mask artifact. */
    data class Mask(val plan: GPUClipMaskPlan) : GPUClipElementPlan

    /** Clip element is represented in shader code. */
    data class Shader(val plan: GPUClipShaderPlan) : GPUClipElementPlan

    /** Clip element is refused. */
    data class Refused(val diagnostic: GPUClipDiagnostic) : GPUClipElementPlan
}

/** Clip plan for one draw. */
data class GPUClipPlan(
    val stack: GPUClipStackDescriptor,
    val elements: List<GPUClipElementPlan>,
    val orderingToken: GPUClipOrderingToken,
    val diagnostics: List<GPUClipDiagnostic> = emptyList(),
)

/** Clip bounds plan. */
data class GPUClipBoundsPlan(
    val inputBoundsLabel: String,
    val reducedBoundsLabel: String,
    val conservative: Boolean,
)

/** Scissor clip plan. */
data class GPUClipScissorPlan(
    val x: Int,
    val y: Int,
    val width: Int,
    val height: Int,
)

/** Analytic clip plan. */
data class GPUClipAnalyticPlan(
    val shapeLabel: String,
    val coverageMode: String,
)

/** Stencil clip plan. */
data class GPUClipStencilPlan(
    val stencilLabel: String,
    val fillRule: String,
    val preserveStencil: Boolean,
)

/** Clip mask plan. */
data class GPUClipMaskPlan(
    val maskArtifactKey: String,
    val boundsLabel: String,
    val samplingPolicy: String,
)

/** Shader clip plan. */
data class GPUClipShaderPlan(
    val snippetHash: String,
    val payloadHash: String,
)

/** Clip diagnostic. */
data class GPUClipDiagnostic(
    val code: String,
    val stackId: String? = null,
    val message: String,
    val terminal: Boolean,
)
