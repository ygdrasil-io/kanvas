package org.graphiks.kanvas.gpu.plan

import org.graphiks.kanvas.render.ir.RenderDiagnostic
import org.graphiks.kanvas.render.ir.RenderDiagnosticCode
import org.graphiks.kanvas.render.ir.RenderDiagnosticDomain
import org.graphiks.kanvas.render.ir.RenderDiagnosticSeverity
import org.graphiks.kanvas.render.ir.RenderPlanResult
import org.graphiks.kanvas.render.ir.RenderTargetDescriptor
import org.graphiks.kanvas.render.ir.SceneSnapshot

/** Selects the first semantic capability candidate, preserving ordered gaps. */
public class CapabilityCompilerChain private constructor(
    private val compilers: List<GpuPlanCompiler>,
) : GpuPlanCompiler {
    override fun select(scene: SceneSnapshot, target: RenderTargetDescriptor): GpuPlanSelection {
        if (scene.extent != target.extent || scene.colorSpace != target.colorSpace) {
            return GpuPlanSelection.InvalidScene(listOf(
                diagnostic("gpu-plan.selection.scene-target-mismatch", "Scene and target descriptors disagree."),
            ))
        }

        val gaps = mutableListOf<RenderDiagnostic>()
        compilers.forEachIndexed { index, compiler ->
            when (val selection = compiler.select(scene, target)) {
                is GpuPlanSelection.Candidate -> return GpuPlanSelection.Candidate(
                    ChainCandidate(this, index, compiler, selection.candidate),
                )
                is GpuPlanSelection.NotCandidate -> gaps += selection.diagnostics()
                is GpuPlanSelection.MaterialOnlyRefusal -> return selection
                is GpuPlanSelection.InvalidScene -> return selection
                is GpuPlanSelection.ResourceLimitExceeded -> return selection
            }
        }
        return GpuPlanSelection.NotCandidate(gaps)
    }

    override fun plan(
        candidate: GpuPlanCandidate,
        capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
    ): RenderPlanResult<RenderGraph> {
        val chained = candidate as? ChainCandidate
            ?: return invalidCandidate()
        if (chained.owner !== this || compilers.getOrNull(chained.index) !== chained.compiler) {
            return invalidCandidate()
        }
        return chained.compiler.plan(chained.candidate, capabilities, budget)
    }

    /** Private candidate ownership is checked before the selected compiler exposes metadata. */
    internal fun constructSourceLayout(candidate: GpuPlanCandidate,capabilities: PlanCapabilitySnapshot,
        budget: PlanBudget,
        overlay: (SourceDeferredRenderConstructionV4)->SourceConstructionResultV4<SourceDeferredRenderConstructionV4>,
    ): RenderPlanResult<FrameSourceLayoutV4> {
        val chained = candidate as? ChainCandidate ?: return invalidCandidate()
        if (chained.owner !== this || compilers.getOrNull(chained.index) !== chained.compiler) return invalidCandidate()
        if (chained.compiler is W5aCompositePlanCompiler)
            return chained.compiler.constructSourceLayout(chained.candidate,capabilities,budget,overlay)
        return when (val result = chained.compiler.constructSourceLaneV4(chained.candidate,capabilities,budget)) {
            is RenderPlanResult.Ready -> when (val captured = overlay(result.plan)) {
                is SourceConstructionResultV4.Refused -> captured.failure
                is SourceConstructionResultV4.Built -> when (val layout = FrameSourceLayoutV4.standalone(captured.value)) {
                    is SourceConstructionResultV4.Built -> RenderPlanResult.Ready(layout.value)
                    is SourceConstructionResultV4.Refused -> layout.failure
                }
            }
            is RenderPlanResult.GapNotMigrated -> result
            is RenderPlanResult.GapOnPromotedScope -> result
            is RenderPlanResult.InvalidScene -> result
            is RenderPlanResult.ResourceLimitExceeded -> result
        }
    }

    private fun invalidCandidate(): RenderPlanResult.InvalidScene = RenderPlanResult.InvalidScene(listOf(
        diagnostic("gpu-plan.selection.invalid-candidate", "Candidate does not belong to this compiler chain."),
    ))

    private fun diagnostic(code: String, message: String): RenderDiagnostic = RenderDiagnostic(
        RenderDiagnosticCode(code),
        RenderDiagnosticDomain.SCENE,
        RenderDiagnosticSeverity.ERROR,
        message,
    )

    private class ChainCandidate(
        val owner: CapabilityCompilerChain,
        val index: Int,
        val compiler: GpuPlanCompiler,
        val candidate: GpuPlanCandidate,
    ) : GpuPlanCandidate {
        override val capabilityId: String = candidate.capabilityId
        override val sceneCanonicalId = candidate.sceneCanonicalId
        override val target: RenderTargetDescriptor = candidate.target
    }

    public companion object {
        public fun of(compilers: List<GpuPlanCompiler>): CapabilityCompilerChain {
            require(compilers.isNotEmpty()) { "CapabilityCompilerChain requires at least one compiler" }
            val ordered = compilers.toMutableList()
            val lastNarrowPathIndex = ordered.indexOfLast { compiler ->
                compiler is W4cPathFillPlanCompiler || compiler is W4dPathStrokePlanCompiler
            }
            if (lastNarrowPathIndex >= 0 && ordered.none { it is W4dGeneralPathPlanCompiler }) {
                ordered.add(lastNarrowPathIndex + 1, W4dGeneralPathPlanCompiler())
            }
            val w4dGeneralIndex = ordered.indexOfLast { it is W4dGeneralPathPlanCompiler }
            if (w4dGeneralIndex >= 0 && ordered.none { it is W4eClipPlanCompiler }) {
                ordered.add(w4dGeneralIndex + 1, W4eClipPlanCompiler())
            }
            return CapabilityCompilerChain(ordered)
        }
    }
}
