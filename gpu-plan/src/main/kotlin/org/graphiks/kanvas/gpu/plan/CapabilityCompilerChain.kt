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
                is GpuPlanSelection.InvalidScene -> return selection
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
            return CapabilityCompilerChain(compilers.toList())
        }
    }
}
