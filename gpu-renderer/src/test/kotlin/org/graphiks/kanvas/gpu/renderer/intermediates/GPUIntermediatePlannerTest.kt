package org.graphiks.kanvas.gpu.renderer.intermediates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode

class GPUIntermediatePlannerTest {
    @Test
    fun `srcOver draw renders directly without destination copy`() {
        val plan = GPUIntermediatePlanner().plan(
            request(
                GPUIntermediateDrawRequest(
                    commandId = "cmd-1",
                    targetLabel = "surface:main",
                    targetGeneration = 1,
                    bounds = bounds("cmd-1"),
                    blendMode = GPUBlendMode.SRC_OVER,
                    materialKeyHash = "material:solid",
                    renderStepIdentity = "rect-fill",
                ),
            ),
        )

        assertEquals(
            listOf("RenderToTarget"),
            plan.steps.map { it::class.simpleName },
        )
        assertEquals(0L, plan.telemetry.destinationReadCopies)
        assertTrue(plan.dumpLines().any { it.contains("route=fixed-function:src_over:one_isa") })
    }

    @Test
    fun `screen blend uses the canonical fixed function route without a destination copy`() {
        val plan = GPUIntermediatePlanner().plan(
            request(
                GPUIntermediateDrawRequest(
                    commandId = "cmd-screen",
                    targetLabel = "surface:main",
                    targetGeneration = 9,
                    bounds = bounds("cmd-screen"),
                    blendMode = GPUBlendMode.SCREEN,
                    materialKeyHash = "material:screen",
                    renderStepIdentity = "rect-fill",
                ),
            ),
        )

        assertEquals(
            listOf("RenderToTarget"),
            plan.steps.map { it::class.simpleName },
        )
        assertEquals(0L, plan.telemetry.destinationReadCopies)
        assertEquals(0L, plan.telemetry.passSplits)
        assertTrue(plan.dumpLines().any { it.contains("fixed-function:screen:one_isc") })
        assertTrue(plan.dumpLines().none { it.contains("unsupported.blend.shader_route_unvalidated") })
    }

    @Test
    fun `active attachment sampling refuses before encoding`() {
        val plan = GPUIntermediatePlanner().plan(
            request(
                GPUIntermediateDrawRequest(
                    commandId = "cmd-bad",
                    targetLabel = "surface:main",
                    targetGeneration = 2,
                    bounds = bounds("cmd-bad"),
                    blendMode = GPUBlendMode.MULTIPLY,
                    materialKeyHash = "material:multiply",
                    renderStepIdentity = "rect-fill",
                    activeAttachmentSampled = true,
                ),
            ),
        )

        assertEquals(listOf("Refuse"), plan.steps.map { it::class.simpleName })
        assertEquals(
            "intermediate.refused scope=cmd-bad reason=unsupported.destination_read.active_attachment_sampled",
            plan.dumpLines()[1],
        )
    }

    private fun request(draw: GPUIntermediateDrawRequest): GPUIntermediatePlannerRequest =
        GPUIntermediatePlannerRequest(
            planId = "plan:test",
            targetId = "target:main",
            targetFormatClass = "rgba8unorm",
            targetUsageLabels = setOf("render_attachment", "copy_src", "copy_dst", "texture_binding"),
            deviceGeneration = 1,
            drawRequests = listOf(draw),
        )

    private fun bounds(commandId: String): GPUDestinationReadBounds =
        GPUDestinationReadBounds(
            boundsLabel = "bounds:$commandId",
            conservative = true,
            pixelAligned = true,
            requestedBoundsLabel = "requested:$commandId",
            unclippedBoundsLabel = "unclipped:$commandId",
            clippedBoundsLabel = "clipped:$commandId",
            copyBoundsLabel = "copy:$commandId",
            width = 32,
            height = 16,
            targetWidth = 320,
            targetHeight = 200,
        )
}
