package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity

/** Stable terminal refusals for W6d's descriptor-selected runtime image-filter route. */
public object W6dPlanDiagnostics {
    /** Every W6d resource/slot is charged before its native target can be reserved. */
    public const val FrameBudgetExceeded: String = "w6d.layer.frame_budget_exceeded"
    /** W6d never substitutes an RGBA8 target for an F16/HDR request. */
    public const val UnsupportedTargetFormat: String = "w6d.layer.unsupported_target_format"
    public const val RuntimeEffectNotRegistered: String = "w6d.runtime_effect.not_registered"
    public const val RuntimeEffectAbiUnsupported: String = "w6d.runtime_effect.abi_unsupported"
    public const val RuntimeEffectInvalidBinding: String = "w6d.runtime_effect.invalid_binding"

    public fun refusal(code: String, message: String): RenderDiagnostic = RenderDiagnostic(
        RenderDiagnosticCode(code), RenderDiagnosticDomain.SCENE, RenderDiagnosticSeverity.ERROR, message,
    )

    public fun budgetRefusal(message: String): RenderDiagnostic = RenderDiagnostic(
        RenderDiagnosticCode(FrameBudgetExceeded), RenderDiagnosticDomain.RESOURCE, RenderDiagnosticSeverity.ERROR, message,
    )
}
