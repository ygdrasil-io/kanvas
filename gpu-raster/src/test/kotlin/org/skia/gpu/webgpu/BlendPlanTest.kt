package org.skia.gpu.webgpu

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.skia.foundation.SkBlendMode

class BlendPlanTest {
    @Test
    fun `fixed-function allowlist accepts SrcOver for generated solid rect pilot`() {
        val plan = selectWebGpuBlendPlan(SkBlendMode.kSrcOver)

        assertEquals(BlendPlan.Kind.FixedFunction, plan.kind)
        assertTrue(plan.reason.contains("fixed-function WebGPU blend allowlist accepts kSrcOver"))
    }

    @Test
    fun `shader composite modes refuse fixed-function generated path with stable reason`() {
        val plan = selectWebGpuBlendPlan(SkBlendMode.kModulate)

        assertEquals(BlendPlan.Kind.ShaderLayerComposite, plan.kind)
        assertEquals(
            "blend mode kModulate requires shader/layer composite BlendPlan; " +
                "fixed-function WebGPU path refuses it",
            plan.reason,
        )
    }

    @Test
    fun `unsupported blend modes report fixed-function and shader allowlists`() {
        val plan = selectWebGpuBlendPlan(SkBlendMode.kOverlay)

        assertEquals(BlendPlan.Kind.RefuseDiagnostic, plan.kind)
        assertTrue(plan.reason.contains("blend mode kOverlay is not in the M7 WebGPU BlendPlan allowlist"))
        assertTrue(plan.reason.contains("Fixed-function modes: kClear / kSrc / kSrcOver / kDstOver / kPlus"))
        assertTrue(
            plan.reason.contains(
                "Shader layer-composite modes: kModulate / kScreen / kDarken / kLighten / " +
                    "kDifference / kExclusion / kMultiply",
            ),
        )
    }

    @Test
    fun `layer composite keeps kPlus on shader composite plan`() {
        val directPlan = selectWebGpuBlendPlan(SkBlendMode.kPlus)
        val layerPlan = selectLayerCompositeBlendPlan(SkBlendMode.kPlus)

        assertEquals(BlendPlan.Kind.FixedFunction, directPlan.kind)
        assertEquals(BlendPlan.Kind.ShaderLayerComposite, layerPlan.kind)
        assertTrue(layerPlan.reason.contains("saveLayer composition"))
    }
}
