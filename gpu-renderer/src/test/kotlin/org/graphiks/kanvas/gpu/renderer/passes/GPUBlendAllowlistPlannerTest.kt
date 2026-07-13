package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotEquals

class GPUBlendAllowlistPlannerTest {
    private val planner = GPUBlendAllowlistPlanner()

    @Test
    fun `adapter reports the canonical screen and multiply routes`() {
        val screen = planner.plan(request(GPUBlendMode.SCREEN))
        val multiply = planner.plan(request(GPUBlendMode.MULTIPLY))

        assertIs<GPUBlendPlan.FixedFunctionBlend>(screen.plan)
        assertEquals(GPUBlendPlanKind.FixedFunctionBlend, screen.planKind)
        assertEquals(GPUBlendDestinationReadRequirement.None, screen.destinationReadRequirement)
        assertIs<GPUBlendPlan.ShaderBlendWithDstRead>(multiply.plan)
        assertEquals(GPUBlendPlanKind.ShaderBlendWithDstRead, multiply.planKind)
        assertEquals(
            GPUBlendDestinationReadRequirement.DestinationTextureRequired,
            multiply.destinationReadRequirement,
        )
    }

    @Test
    fun `pipeline evidence includes coverage topology`() {
        val full = planner.plan(request(GPUBlendMode.SRC_OVER))
        val scalar = planner.plan(
            request(GPUBlendMode.SRC_OVER).copy(coverage = GPUCoverageConsumption.ScalarCoverage),
        )

        assertNotEquals(full.blendStateHash, scalar.blendStateHash)
        assertNotEquals(full.pipelineKeyHash, scalar.pipelineKeyHash)
    }

    private fun request(mode: GPUBlendMode) = GPUBlendAllowlistRequest(
        commandId = "draw:1",
        mode = mode,
        targetFormatClass = "rgba8unorm",
        materialKeyHash = "material:1",
        renderStepIdentity = "render-step:solid",
    )
}
