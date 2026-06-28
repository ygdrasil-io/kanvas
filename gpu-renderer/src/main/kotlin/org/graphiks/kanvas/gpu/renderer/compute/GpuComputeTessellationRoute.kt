package org.graphiks.kanvas.gpu.renderer.compute

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

data class DispatchGrid(val x: Int, val y: Int = 1, val z: Int = 1)

sealed interface GpuComputeTessellationRoute {
    data class Accepted(val artifact: GpuComputeTessellationArtifact) : GpuComputeTessellationRoute
    data class CapabilityUnavailable(val reason: String) : GpuComputeTessellationRoute
    data class Refused(val diagnostic: RefuseDiagnostic) : GpuComputeTessellationRoute
}
