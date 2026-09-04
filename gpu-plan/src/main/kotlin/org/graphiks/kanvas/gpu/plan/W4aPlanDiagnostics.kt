package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity

/** Stable public planning diagnostics for the W4a analytic-rectangle capability. */
public object W4aPlanDiagnostics {
    public val CommandNotMigrated: RenderDiagnosticCode = RenderDiagnosticCode("w4a.command.not_migrated")
    public val SceneInvalid: RenderDiagnosticCode = RenderDiagnosticCode("w4a.scene.invalid")
    public val SizeOverflow: RenderDiagnosticCode = RenderDiagnosticCode("w4a.size.overflow")
    public val CapabilityTextureDimension: RenderDiagnosticCode = RenderDiagnosticCode("w4a.capability.texture_dimension")
    public val CapabilityBufferSize: RenderDiagnosticCode = RenderDiagnosticCode("w4a.capability.buffer_size")
    public val CapabilityDynamicUniform: RenderDiagnosticCode = RenderDiagnosticCode("w4a.capability.dynamic_uniform")
    public val CapabilityOperation: RenderDiagnosticCode = RenderDiagnosticCode("w4a.capability.operation")
    public val CapabilityFormat: RenderDiagnosticCode = RenderDiagnosticCode("w4a.capability.format")
    public val CapabilityAllocationPolicy: RenderDiagnosticCode = RenderDiagnosticCode("w4a.capability.allocation_policy")
    public val BudgetFrameLocalExceeded: RenderDiagnosticCode = RenderDiagnosticCode("w4a.budget.frame_local_exceeded")
    public val PlanIdentityInvalid: RenderDiagnosticCode = RenderDiagnosticCode("w4a.plan.identity_invalid")

    internal fun diagnostic(
        code: RenderDiagnosticCode,
        domain: RenderDiagnosticDomain,
        message: String,
    ): RenderDiagnostic = RenderDiagnostic(code, domain, RenderDiagnosticSeverity.ERROR, message)
}
