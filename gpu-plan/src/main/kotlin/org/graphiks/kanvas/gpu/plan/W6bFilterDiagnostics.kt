package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity

/** Stable W6b admission vocabulary; an owned filter frame has no legacy continuation. */
public object W6bFilterDiagnostics {
    public const val UnsupportedFamily: String = "w6b.filter.unsupported_family"
    public const val UnsupportedBackdrop: String = "w6b.filter.unsupported_backdrop"
    public const val FilteredPrevious: String = "w6b.filter.filtered_previous"
    public const val UnsupportedTargetFormat: String = "w6b.filter.unsupported_target_format"
    public const val NativeExecutionUnimplemented: String = "w6b.filter.native_execution_unimplemented"
    public const val InvalidBounds: String = "w6b.filter.invalid_bounds"
    public const val FrameBudgetExceeded: String = "w6b.filter.frame_budget_exceeded"
    public const val NativeCapability: String = "w6b.filter.native_capability"

    public fun refusal(code: String, message: String): RenderDiagnostic = RenderDiagnostic(
        RenderDiagnosticCode(code), RenderDiagnosticDomain.SCENE, RenderDiagnosticSeverity.ERROR, message,
    )
}
