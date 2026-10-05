package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity

/** Stable public diagnostics for W4d.2 transformed and AA path planning. */
public object W4dGeneralPlanDiagnostics {
    public val CommandNotMigrated: RenderDiagnosticCode = RenderDiagnosticCode("w4d.general.command-not-migrated")
    public val SceneInvalid: RenderDiagnosticCode = RenderDiagnosticCode("w4d.general.scene-invalid")
    public val ProjectionHorizonCrossing: RenderDiagnosticCode =
        RenderDiagnosticCode("w4d.general.projection-horizon-crossing")
    public val PathResourceLimit: RenderDiagnosticCode = RenderDiagnosticCode("w4d.general.path-resource-limit")
    public val BudgetFrameLocalExceeded: RenderDiagnosticCode =
        RenderDiagnosticCode("w4d.general.budget.frame-local-exceeded")
    public val SizeOverflow: RenderDiagnosticCode = RenderDiagnosticCode("w4d.general.size-overflow")
    public val CapabilityUnavailable: RenderDiagnosticCode = RenderDiagnosticCode("w4d.general.capability-unavailable")
    public val TextureSampleSupportUnavailable: RenderDiagnosticCode =
        RenderDiagnosticCode("w4d.general.texture-sample-support-unavailable")
    public val ResolveUnsupported: RenderDiagnosticCode = RenderDiagnosticCode("w4d.general.resolve-unsupported")
    public val PlanIdentityInvalid: RenderDiagnosticCode = RenderDiagnosticCode("w4d.general.plan-identity-invalid")

    public fun diagnostic(
        code: RenderDiagnosticCode,
        domain: RenderDiagnosticDomain,
        message: String,
    ): RenderDiagnostic = RenderDiagnostic(code, domain, RenderDiagnosticSeverity.ERROR, message)
}
