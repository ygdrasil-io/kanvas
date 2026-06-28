package org.graphiks.kanvas.gpu.renderer.compute

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic
import kotlin.math.ceil

data class GpuComputeTessellationPlan(
    val dispatchGrid: DispatchGrid,
    val artifactKey: String,
    val vertexCount: Int,
) {
    companion object {
        const val MAX_VERTEX_BUDGET = 1_000_000

        fun forPathFill(vertexCount: Int, workgroupSize: Int): GpuComputeTessellationPlan {
            require(vertexCount > 0) { "vertexCount must be positive, got $vertexCount" }
            require(workgroupSize > 0) { "workgroupSize must be positive, got $workgroupSize" }
            val workgroups = ceil(vertexCount.toDouble() / workgroupSize.toDouble()).toInt()
            return GpuComputeTessellationPlan(
                dispatchGrid = DispatchGrid(x = workgroups),
                artifactKey = "compute-tessellation-${vertexCount}-${workgroupSize}",
                vertexCount = vertexCount,
            )
        }
    }

    fun analyze(capabilities: GpuCapabilities): GpuComputeTessellationRoute {
        if (!capabilities.computeSupported) {
            return GpuComputeTessellationRoute.CapabilityUnavailable("compute_not_supported")
        }
        if (vertexCount > MAX_VERTEX_BUDGET) {
            return GpuComputeTessellationRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.tessellation.vertex_budget_exceeded",
                    message = "vertex count $vertexCount exceeds max budget $MAX_VERTEX_BUDGET",
                    stage = "tessellation.analysis",
                    terminal = true,
                )
            )
        }
        return GpuComputeTessellationRoute.Accepted(
            GpuComputeTessellationArtifact(
                planKey = artifactKey,
                vertexCount = vertexCount,
            )
        )
    }
}
