package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity

/** Stable public planning diagnostics for the W4b analytic-rounded-rectangle capability. */
public object W4bPlanDiagnostics {
    public val CommandNotMigrated: RenderDiagnosticCode = RenderDiagnosticCode("w4b.command.not_migrated")
    public val SceneInvalid: RenderDiagnosticCode = RenderDiagnosticCode("w4b.scene.invalid")
    public val SizeOverflow: RenderDiagnosticCode = RenderDiagnosticCode("w4b.size.overflow")
    public val CapabilityTextureDimension: RenderDiagnosticCode = RenderDiagnosticCode("w4b.capability.texture_dimension")
    public val CapabilityBufferSize: RenderDiagnosticCode = RenderDiagnosticCode("w4b.capability.buffer_size")
    public val CapabilityDynamicUniform: RenderDiagnosticCode = RenderDiagnosticCode("w4b.capability.dynamic_uniform")
    public val CapabilityOperation: RenderDiagnosticCode = RenderDiagnosticCode("w4b.capability.operation")
    public val CapabilityFormat: RenderDiagnosticCode = RenderDiagnosticCode("w4b.capability.format")
    public val CapabilityAllocationPolicy: RenderDiagnosticCode = RenderDiagnosticCode("w4b.capability.allocation_policy")
    public val BudgetFrameLocalExceeded: RenderDiagnosticCode = RenderDiagnosticCode("w4b.budget.frame_local_exceeded")
    public val PlanIdentityInvalid: RenderDiagnosticCode = RenderDiagnosticCode("w4b.plan.identity_invalid")

    internal fun diagnostic(
        code: RenderDiagnosticCode,
        domain: RenderDiagnosticDomain,
        message: String,
    ): RenderDiagnostic = RenderDiagnostic(code, domain, RenderDiagnosticSeverity.ERROR, message)
}
