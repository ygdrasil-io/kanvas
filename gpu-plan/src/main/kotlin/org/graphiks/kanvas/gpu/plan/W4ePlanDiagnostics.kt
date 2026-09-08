package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity

/** Stable public diagnostics for bounded W4e clip-stack planning. */
public object W4ePlanDiagnostics {
    public val CommandNotMigrated: RenderDiagnosticCode = RenderDiagnosticCode("w4e.clip.command-not-migrated")
    public val SceneInvalid: RenderDiagnosticCode = RenderDiagnosticCode("w4e.clip.scene-invalid")
    public val LegacyUnavailable: RenderDiagnosticCode = RenderDiagnosticCode("w4e.clip.legacy-unavailable")
    public val GeometryLimit: RenderDiagnosticCode = RenderDiagnosticCode("w4e.clip.geometry-limit")
    public val CapabilityUnavailable: RenderDiagnosticCode = RenderDiagnosticCode("w4e.clip.capability-unavailable")
    public val MaskFormatUnavailable: RenderDiagnosticCode = RenderDiagnosticCode("w4e.clip.mask-format-unavailable")
    public val SampleCountUnavailable: RenderDiagnosticCode = RenderDiagnosticCode("w4e.clip.sample-count-unavailable")
    public val BudgetFrameLocalExceeded: RenderDiagnosticCode = RenderDiagnosticCode("w4e.clip.budget.frame-local-exceeded")
    public val SizeOverflow: RenderDiagnosticCode = RenderDiagnosticCode("w4e.clip.size-overflow")
    public val PlanIdentityInvalid: RenderDiagnosticCode = RenderDiagnosticCode("w4e.clip.plan-identity-invalid")

    public fun diagnostic(code: RenderDiagnosticCode, domain: RenderDiagnosticDomain, message: String): RenderDiagnostic =
        RenderDiagnostic(code, domain, RenderDiagnosticSeverity.ERROR, message)
}
