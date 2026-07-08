package org.graphiks.kanvas.gpu.renderer.intermediates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaAdapterCapability
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendMode

class GPUIntermediateMsaaPlanTest {
    @Test
    fun `sample count four emits msaa target and resolve steps`() {
        val plan = GPUIntermediatePlanner().plan(
            GPUIntermediatePlannerRequest(
                planId = "plan:msaa",
                targetId = "target:main",
                targetFormatClass = "rgba8unorm",
                targetUsageLabels = setOf("render_attachment", "texture_binding", "copy_src", "copy_dst"),
                deviceGeneration = 1,
                requestedSampleCount = 4,
                msaaAdapter = GPUMsaaAdapterCapability(
                    adapterLabel = "adapter:test",
                    maxSampleCount = 4,
                    supportsAlphaToCoverage = false,
                ),
                drawRequests = listOf(
                    GPUIntermediateDrawRequest(
                        commandId = "cmd-aa",
                        targetLabel = "surface:main",
                        targetGeneration = 1,
                        bounds = GPUDestinationReadBounds(
                            boundsLabel = "bounds:aa",
                            conservative = true,
                            pixelAligned = true,
                            requestedBoundsLabel = "requested:aa",
                            unclippedBoundsLabel = "unclipped:aa",
                            clippedBoundsLabel = "clipped:aa",
                            copyBoundsLabel = "copy:aa",
                            width = 64,
                            height = 64,
                            targetWidth = 320,
                            targetHeight = 200,
                        ),
                        blendMode = GPUBlendMode.SrcOver,
                        materialKeyHash = "material:solid",
                        renderStepIdentity = "rect-fill",
                    ),
                ),
            ),
        )

        assertTrue(plan.steps.any { it is GPUIntermediatePlanStep.ResolveMSAA })
        assertEquals(1, plan.telemetry.msaaTargets)
        assertEquals(1, plan.telemetry.msaaResolves)
    }
}
