package org.graphiks.kanvas.gpu.renderer.passes

/** Concrete render step that maps first-route draw invocations to geometry and coverage plans. */
class FirstRouteRenderStep(
    override val stepId: GPURenderStepID,
    override val version: Int = 1,
) : GPURenderStep {
    override fun planFor(invocation: GPUDrawInvocation): GPURenderStepPlan {
        val geometryClass = when (invocation.role) {
            "fill" -> "axis-aligned-rect"
            "text" -> "glyph-run"
            else -> "none"
        }
        val coverageClass = when (invocation.role) {
            "fill" -> "analytic-fill"
            "text" -> "analytic-fill"
            else -> "none"
        }
        val scissorAxes = invocation.scissorBoundsHash?.let {
            mapOf("scissorBoundsHash" to it)
        } ?: emptyMap()
        return GPURenderStepPlan(
            stepId = invocation.renderStepId,
            geometryClass = geometryClass,
            coverageClass = coverageClass,
            vertexLayoutHash = "vertex-layout:${invocation.renderStepId.value}",
            fixedStateHash = "fixed-state:${invocation.renderStepId.value}",
            wgslFragmentHash = invocation.pipelineKeyHash,
            pipelineAxes = mapOf(
                "role" to invocation.role,
                "layerScope" to invocation.layerScopeId,
            ) + scissorAxes,
        )
    }
}
