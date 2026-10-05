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
    /** Historical Picture payloads may decode a table that no longer has the W6b ABI length. */
    public const val InvalidMaskTableLength: String = "invalid.mask_filter.table_length"
    public const val InvalidBounds: String = "w6b.filter.invalid_bounds"
    public const val FrameBudgetExceeded: String = "w6b.filter.frame_budget_exceeded"
    public const val NativeCapability: String = "w6b.filter.native_capability"
    /** Direct filtered draws cannot approximate an opaque terminal clip with an AABB. */
    public const val DirectTerminalClip: String = "w6b.filter.direct_terminal_clip"
    /** A malformed frozen drawPicture aggregate; capture/bounds/budget retain their own codes. */
    public const val PictureStreamInvalid: String = "w6b.picture_stream.invalid"

    public fun refusal(code: String, message: String): RenderDiagnostic = RenderDiagnostic(
        RenderDiagnosticCode(code), RenderDiagnosticDomain.SCENE, RenderDiagnosticSeverity.ERROR, message,
    )

    /** A W6b-owned physical peak must retain the filter owner instead of leaking W6a's code. */
    public fun budgetRefusal(message: String): RenderDiagnostic = RenderDiagnostic(
        RenderDiagnosticCode(FrameBudgetExceeded), RenderDiagnosticDomain.RESOURCE, RenderDiagnosticSeverity.ERROR, message,
    )
}
