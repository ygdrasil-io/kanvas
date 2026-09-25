package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity

/** Stable terminal refusals for W6d's descriptor-selected runtime image-filter route. */
public object W6dPlanDiagnostics {
    public const val RuntimeEffectNotRegistered: String = "w6d.runtime_effect.not_registered"
    public const val RuntimeEffectAbiUnsupported: String = "w6d.runtime_effect.abi_unsupported"
    public const val RuntimeEffectInvalidBinding: String = "w6d.runtime_effect.invalid_binding"

    public fun refusal(code: String, message: String): RenderDiagnostic = RenderDiagnostic(
        RenderDiagnosticCode(code), RenderDiagnosticDomain.SCENE, RenderDiagnosticSeverity.ERROR, message,
    )
}
