package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity

/** Stable public diagnostics for the bounded W4d path-stroke capability. */
public object W4dPlanDiagnostics {
    public val CommandNotMigrated: RenderDiagnosticCode = RenderDiagnosticCode("w4d.command-not-migrated")
    public val SceneInvalid: RenderDiagnosticCode = RenderDiagnosticCode("w4d.scene-invalid")
    public val PathResourceLimit: RenderDiagnosticCode = RenderDiagnosticCode("w4d.path-resource-limit")
    public val BudgetFrameLocalExceeded: RenderDiagnosticCode = RenderDiagnosticCode("w4d.budget.frame-local-exceeded")
    public val SizeOverflow: RenderDiagnosticCode = RenderDiagnosticCode("w4d.size-overflow")
    public val CapabilityUnavailable: RenderDiagnosticCode = RenderDiagnosticCode("w4d.capability-unavailable")
    public val PlanIdentityInvalid: RenderDiagnosticCode = RenderDiagnosticCode("w4d.plan-identity-invalid")

    public fun diagnostic(
        code: RenderDiagnosticCode,
        domain: RenderDiagnosticDomain,
        message: String,
    ): RenderDiagnostic = RenderDiagnostic(code, domain, RenderDiagnosticSeverity.ERROR, message)
}
