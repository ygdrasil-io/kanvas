package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity

/** Stable public planning diagnostics for the W3 solid-rectangle capability. */
public object W3PlanDiagnostics {
    public val CommandNotMigrated: RenderDiagnosticCode = RenderDiagnosticCode("w3.command.not_migrated")
    public val GeometryNotPixelAligned: RenderDiagnosticCode = RenderDiagnosticCode("w3.geometry.not_pixel_aligned")
    public val ClipNotPixelAligned: RenderDiagnosticCode = RenderDiagnosticCode("w3.clip.not_pixel_aligned")
    public val SceneInvalid: RenderDiagnosticCode = RenderDiagnosticCode("w3.scene.invalid")
    public val SizeOverflow: RenderDiagnosticCode = RenderDiagnosticCode("w3.size.overflow")
    public val CapabilityTextureDimension: RenderDiagnosticCode = RenderDiagnosticCode("w3.capability.texture_dimension")
    public val CapabilityBufferSize: RenderDiagnosticCode = RenderDiagnosticCode("w3.capability.buffer_size")
    public val CapabilityDynamicUniform: RenderDiagnosticCode = RenderDiagnosticCode("w3.capability.dynamic_uniform")
    public val CapabilityFormat: RenderDiagnosticCode = RenderDiagnosticCode("w3.capability.format")
    public val BudgetFrameLocalExceeded: RenderDiagnosticCode = RenderDiagnosticCode("w3.budget.frame_local_exceeded")
    public val PlanIdentityInvalid: RenderDiagnosticCode = RenderDiagnosticCode("w3.plan.identity_invalid")

    internal fun diagnostic(
        code: RenderDiagnosticCode,
        domain: RenderDiagnosticDomain,
        message: String,
    ): RenderDiagnostic = RenderDiagnostic(code, domain, RenderDiagnosticSeverity.ERROR, message)
}
