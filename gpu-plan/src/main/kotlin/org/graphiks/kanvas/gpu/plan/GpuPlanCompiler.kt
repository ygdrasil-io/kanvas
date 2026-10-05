package org.graphiks.kanvas.gpu.plan

import java.util.Collections
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneSnapshot
import org.graphiks.kanvas.render.ir.CanonicalId
import org.graphiks.kanvas.render.ir.RenderDiagnostic

/** Opaque semantic selection that a capability compiler may later plan. */
public interface GpuPlanCandidate {
    public val capabilityId: String
    public val sceneCanonicalId: CanonicalId
    public val target: RenderTargetDescriptor
}

/** Result of selecting a device-independent GPU planning capability. */
public sealed interface GpuPlanSelection {
    public data class Candidate(public val candidate: GpuPlanCandidate) : GpuPlanSelection

    /** Issued only after every non-material scene admission check succeeded in a native compiler. */
    public class MaterialOnlyRefusal internal constructor(
        public val capabilityId: String,
        public val sceneCanonicalId: CanonicalId,
        public val target: RenderTargetDescriptor,
        refusals: List<EffectiveMaterialPlanner.Result.Refused>,
    ) : GpuPlanSelection {
        private val values = immutableDiagnostics(refusals.map { refusal ->
            org.graphiks.kanvas.render.ir.RenderDiagnostic(
                org.graphiks.kanvas.render.ir.RenderDiagnosticCode(refusal.diagnosticCode),
                org.graphiks.kanvas.render.ir.RenderDiagnosticDomain.SCENE,
                org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity.ERROR,
                "Native capability $capabilityId admits the scene except for its W5a material.",
            )
        })
        internal val materialRefusals = refusals.toList()
        public fun diagnostics(): List<RenderDiagnostic> = values
    }

    public class NotCandidate(diagnostics: List<RenderDiagnostic>) : GpuPlanSelection {
        private val values: List<RenderDiagnostic> = immutableDiagnostics(diagnostics)

        public fun diagnostics(): List<RenderDiagnostic> = values
    }

    public class InvalidScene(diagnostics: List<RenderDiagnostic>) : GpuPlanSelection {
        private val values: List<RenderDiagnostic> = immutableDiagnostics(diagnostics)

        public fun diagnostics(): List<RenderDiagnostic> = values
    }

    /** Semantic resource limits are terminal before a physical device is acquired. */
    public class ResourceLimitExceeded(diagnostics: List<RenderDiagnostic>) : GpuPlanSelection {
        private val values: List<RenderDiagnostic> = immutableDiagnostics(diagnostics)

        public fun diagnostics(): List<RenderDiagnostic> = values
    }
}

private fun immutableDiagnostics(diagnostics: List<RenderDiagnostic>): List<RenderDiagnostic> {
    require(diagnostics.isNotEmpty()) { "A selection refusal must include at least one diagnostic" }
    return Collections.unmodifiableList(ArrayList(diagnostics))
}

/** Compiles an immutable Scene IR snapshot into a handle-free render graph. */
public interface GpuPlanCompiler {
    /** Selects an opaque candidate without acquiring a physical device. */
    public fun select(
        scene: SceneSnapshot,
        target: RenderTargetDescriptor,
    ): GpuPlanSelection

    public fun plan(
        candidate: GpuPlanCandidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): RenderPlanResult<RenderGraph>
}
