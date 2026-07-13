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

    @Test
    fun `adapter is equivalent to canonical planner for fixed and destination-reading facts`() {
        val canonicalPlanner = GPUBlendPlanner()
        listOf(GPUBlendMode.SRC_OVER, GPUBlendMode.MULTIPLY).forEach { mode ->
            listOf(false, true).forEach { activeAttachmentSampled ->
                val adapterRequest = request(mode).copy(
                    activeAttachmentSampled = activeAttachmentSampled,
                )
                val canonicalRequest = GPUBlendSpecializationRequest(
                    mode = mode,
                    coverage = adapterRequest.coverage,
                    sourceAlpha = adapterRequest.sourceAlpha,
                    target = GPUTargetBlendFacts(
                        formatClass = adapterRequest.targetFormatClass,
                        clampsNormalizedColorWrites = true,
                        premultipliedAlpha = true,
                    ),
                    samplePlan = adapterRequest.samplePlan,
                    activeAttachmentSampled = activeAttachmentSampled,
                )

                val direct = canonicalPlanner.plan(canonicalRequest)
                val adapted = planner.plan(adapterRequest)

                assertEquals(direct, adapted.plan, "$mode activeAttachmentSampled=$activeAttachmentSampled")
                assertEquals(direct.destinationReadRequirement, adapted.destinationReadRequirement)
            }
        }
    }

    private fun request(mode: GPUBlendMode) = GPUBlendAllowlistRequest(
        commandId = "draw:1",
        mode = mode,
        targetFormatClass = "rgba8unorm",
        materialKeyHash = "material:1",
        renderStepIdentity = "render-step:solid",
    )
}
