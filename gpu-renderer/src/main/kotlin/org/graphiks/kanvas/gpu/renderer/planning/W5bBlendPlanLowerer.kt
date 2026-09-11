package org.graphiks.kanvas.gpu.renderer.planning

import org.graphiks.kanvas.gpu.plan.BlendFactorV1
import org.graphiks.kanvas.gpu.plan.BlendPlan
import org.graphiks.kanvas.gpu.plan.BlendCoverageEncodingV1
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendPlan
import org.graphiks.kanvas.gpu.renderer.passes.GPUSourceCoverageEncoding
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendComponent
import org.graphiks.kanvas.gpu.renderer.state.GPUFixedFunctionBlendState

/** Renderer adapter: lowers a sealed W5b plan and never classifies a draw. */
public object W5bBlendPlanLowerer {
    public fun lower(plan: BlendPlan): GPUBlendPlan = when (plan) {
        BlendPlan.LegacySrcOverV1 -> legacySrcOver()
        BlendPlan.NoOpV1 -> GPUBlendPlan.NoOp(GPUBlendMode.DST, "sealed-w5b-dst-noop")
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
        is BlendPlan.DestinationReadV1 -> {
            require(plan.compositionAbiI32 in 3..4 && plan.snapshotResource != null &&
                plan.requiredDestinationVersion.valueI64 >= 0L &&
                org.graphiks.kanvas.gpu.renderer.pipelines.GPUBlendFormulaProgramLibrary
                    .selectedFullCoverageFunctionWgsl(plan.mode.name.lowercase(), plan.formulaIdentity) != null) {
                "Invalid sealed W5b destination formula/version/ABI"
            }
            GPUBlendPlan.ShaderBlendWithDstRead(
                GPUBlendMode.valueOf(plan.mode.name), plan.formulaIdentity, coverage(plan.coverage), plan,
            )
        }
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
