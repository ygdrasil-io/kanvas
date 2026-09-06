package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity

/** Stable public planning diagnostics for bounded W4c path fills. */
public object W4cPlanDiagnostics {
    public val CommandNotMigrated: RenderDiagnosticCode = RenderDiagnosticCode("w4c.command.not_migrated")
    public val SceneInvalid: RenderDiagnosticCode = RenderDiagnosticCode("w4c.scene.invalid")
    public val PathResourceLimit: RenderDiagnosticCode = RenderDiagnosticCode("w4c.path.resource_limit")
    public val SizeOverflow: RenderDiagnosticCode = RenderDiagnosticCode("w4c.size.overflow")
    public val CapabilityTextureDimension: RenderDiagnosticCode = RenderDiagnosticCode("w4c.capability.texture_dimension")
    public val CapabilityBufferSize: RenderDiagnosticCode = RenderDiagnosticCode("w4c.capability.buffer_size")
    public val CapabilityDynamicUniform: RenderDiagnosticCode = RenderDiagnosticCode("w4c.capability.dynamic_uniform")
    public val CapabilityOperation: RenderDiagnosticCode = RenderDiagnosticCode("w4c.capability.operation")
    public val CapabilityFormat: RenderDiagnosticCode = RenderDiagnosticCode("w4c.capability.format")
    public val CapabilityDepthStencilFormat: RenderDiagnosticCode =
        RenderDiagnosticCode("w4c.capability.depth_stencil_format")
    public val CapabilityAllocationPolicy: RenderDiagnosticCode =
        RenderDiagnosticCode("w4c.capability.allocation_policy")
    public val BudgetFrameLocalExceeded: RenderDiagnosticCode =
        RenderDiagnosticCode("w4c.budget.frame_local_exceeded")
    public val PlanIdentityInvalid: RenderDiagnosticCode = RenderDiagnosticCode("w4c.plan.identity_invalid")

    internal fun diagnostic(
        code: RenderDiagnosticCode,
        domain: RenderDiagnosticDomain,
        message: String,
    ): RenderDiagnostic = RenderDiagnostic(code, domain, RenderDiagnosticSeverity.ERROR, message)
}
