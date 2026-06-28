package org.graphiks.kanvas.gpu.renderer.stroke

import org.graphiks.kanvas.gpu.renderer.routing.RefuseDiagnostic

data class StrokeDescriptor(val strokeWidth: Float)

sealed interface AdvancedStrokeRoute {
    data class Accepted(val descriptor: StrokeDescriptor) : AdvancedStrokeRoute
    data class Refused(val diagnostic: RefuseDiagnostic) : AdvancedStrokeRoute
}

data class AdvancedStrokePlan(
    val strokeWidth: Float,
    val dashEffect: PathEffect? = null,
    val cornerEffect: PathEffect? = null,
) {
    fun toDescriptor(): StrokeDescriptor = StrokeDescriptor(strokeWidth)

    fun analyze(): AdvancedStrokeRoute {
        val effects = listOfNotNull(dashEffect, cornerEffect)
        if (effects.isEmpty()) return AdvancedStrokeRoute.Accepted(toDescriptor())
        val chain = PathEffectChain(effects)
        val result = chain.apply(100f)
        return if (result.isValid) {
            AdvancedStrokeRoute.Accepted(toDescriptor())
        } else {
            AdvancedStrokeRoute.Refused(
                RefuseDiagnostic(
                    code = "unsupported.stroke.path_effect",
                    message = "unsupported path effect: ${result.report}",
                    stage = "stroke.analysis",
                    terminal = true,
                )
            )
        }
    }
}
