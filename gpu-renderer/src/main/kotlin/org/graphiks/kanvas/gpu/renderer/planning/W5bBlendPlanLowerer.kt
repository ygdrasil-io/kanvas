package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.BlendFactorV1
import org.graphiks.kanvas.gpu.plan.BlendPlan
import org.graphiks.kanvas.gpu.plan.BlendCoverageEncodingV1
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendDiagnostic
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPURefusalScope
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState

/** Renderer adapter: lowers a sealed W5b plan and never classifies a draw. */
internal object W5bBlendPlanLowerer {
    fun lower(plan: BlendPlan): GPUBlendPlan = when (plan) {
        BlendPlan.LegacySrcOverV1 -> legacySrcOver()
        BlendPlan.NoOpV1 -> GPUBlendPlan.FixedFunctionBlend(
            mode = GPUBlendMode.DST,
            state = GPUFixedFunctionBlendState(
                stateId = "w5b.dst-noop@v1",
                color = GPUFixedFunctionBlendComponent("zero", "one", "add"),
                alpha = GPUFixedFunctionBlendComponent("zero", "one", "add"),
                writeMask = "rgba",
            ),
            sourceCoverageEncoding = GPUSourceCoverageEncoding.None,
        )
        is BlendPlan.FixedFunctionV1 -> GPUBlendPlan.FixedFunctionBlend(
            mode = GPUBlendMode.valueOf(plan.mode.name),
            state = GPUFixedFunctionBlendState(
                stateId = "w5b.${plan.mode.name.lowercase()}@v1",
                color = GPUFixedFunctionBlendComponent(factor(plan.colorSource), factor(plan.colorDestination), "add"),
                alpha = GPUFixedFunctionBlendComponent(factor(plan.alphaSource), factor(plan.alphaDestination), "add"),
                writeMask = "rgba",
            ),
            sourceCoverageEncoding = coverage(plan.coverage),
        )
        is BlendPlan.DestinationReadV1 -> GPUBlendPlan.UnsupportedBlend(
            mode = GPUBlendMode.valueOf(plan.mode.name),
            diagnostic = GPUBlendDiagnostic(
                code = "unsupported.w5b.destination-read.task-2",
                mode = GPUBlendMode.valueOf(plan.mode.name),
                message = "W5b destination-read ${plan.formulaIdentity} is deferred to Task 2.",
            ),
            refusalScope = GPURefusalScope.AtomicFrameFailure,
        )
    }

    private fun legacySrcOver() = GPUBlendPlan.FixedFunctionBlend(
        GPUBlendMode.SRC_OVER,
        GPUFixedFunctionBlendState(
            "one_isa",
            GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            GPUFixedFunctionBlendComponent("one", "one-minus-src-alpha", "add"),
            "rgba",
        ),
        GPUSourceCoverageEncoding.None,
    )

    private fun coverage(value: BlendCoverageEncodingV1): GPUSourceCoverageEncoding = when (value) {
        BlendCoverageEncodingV1.FullOrScissor -> GPUSourceCoverageEncoding.None
        BlendCoverageEncodingV1.ScalarCoverageInShader -> GPUSourceCoverageEncoding.ScalarCoverageInShader
    }

    private fun factor(value: BlendFactorV1): String = when (value) {
        BlendFactorV1.Zero -> "zero"
        BlendFactorV1.One -> "one"
        BlendFactorV1.SrcAlpha -> "src-alpha"
        BlendFactorV1.OneMinusSrcAlpha -> "one-minus-src-alpha"
        BlendFactorV1.DstAlpha -> "dst-alpha"
        BlendFactorV1.OneMinusDstAlpha -> "one-minus-dst-alpha"
        BlendFactorV1.SrcColor -> "src"
        BlendFactorV1.OneMinusSrcColor -> "one-minus-src"
    }
}
