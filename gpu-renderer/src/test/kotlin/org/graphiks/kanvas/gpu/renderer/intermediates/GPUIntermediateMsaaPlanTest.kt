package org.graphiks.kanvas.gpu.renderer.intermediates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaAdapterCapability
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendMode

class GPUIntermediateMsaaPlanTest {
    @Test
    fun `sample count four is refused without native resolve evidence`() {
        val plan = GPUIntermediatePlanner().plan(
            msaaRequest(
                requestedSampleCount = 4,
                msaaAdapter = GPUMsaaAdapterCapability(
                    adapterLabel = "adapter:test",
                    maxSampleCount = 4,
                    supportsAlphaToCoverage = false,
                ),
            ),
        )

        val refusal = assertIs<GPUIntermediatePlanStep.Refuse>(plan.steps.single())
        assertEquals("unsupported.msaa.native_resolve_unavailable", refusal.reasonCode)
        assertEquals("target:main", refusal.scopeLabel)
        assertEquals(1, plan.telemetry.intermediatesRefused)
        assertEquals(0, plan.telemetry.msaaTargets)
        assertEquals(0, plan.telemetry.msaaResolves)
        assertFalse(plan.steps.any { it is GPUIntermediatePlanStep.ResolveMSAA })
    }

    @Test
    fun `unsupported sample counts are refused with stable diagnostic`() {
        val plan = GPUIntermediatePlanner().plan(
            msaaRequest(
                requestedSampleCount = 2,
                msaaAdapter = GPUMsaaAdapterCapability(
                    adapterLabel = "adapter:test",
                    maxSampleCount = 8,
                    supportsAlphaToCoverage = false,
                    supportsNativeResolve = true,
                ),
            ),
        )

        val refusal = assertIs<GPUIntermediatePlanStep.Refuse>(plan.steps.single())
        assertEquals("unsupported.msaa.sample_count", refusal.reasonCode)
        assertEquals(1, plan.telemetry.intermediatesRefused)
        assertEquals(0, plan.telemetry.msaaTargets)
        assertEquals(0, plan.telemetry.msaaResolves)
    }

    @Test
    fun `native resolve evidence prepares both targets before resolve step`() {
        val plan = GPUIntermediatePlanner().plan(
            msaaRequest(
                requestedSampleCount = 4,
                msaaAdapter = GPUMsaaAdapterCapability(
                    adapterLabel = "adapter:test",
                    maxSampleCount = 4,
                    supportsAlphaToCoverage = false,
                    supportsNativeResolve = true,
                ),
            ),
        )

        val createSteps = plan.steps.filterIsInstance<GPUIntermediatePlanStep.CreateIntermediate>()
        assertEquals(2, createSteps.size)
        val resolve = assertIs<GPUIntermediatePlanStep.ResolveMSAA>(plan.steps.last())
        assertTrue(createSteps.any { it.descriptor.label == resolve.source.label })
        assertTrue(createSteps.any { it.descriptor.label == resolve.destination.label })
        assertEquals(1, plan.telemetry.msaaTargets)
        assertEquals(1, plan.telemetry.msaaResolves)
    }

    private fun msaaRequest(
        requestedSampleCount: Int,
        msaaAdapter: GPUMsaaAdapterCapability?,
    ) = GPUIntermediatePlannerRequest(
        planId = "plan:msaa",
        targetId = "target:main",
        targetFormatClass = "rgba8unorm",
        targetUsageLabels = setOf("render_attachment", "texture_binding", "copy_src", "copy_dst"),
        deviceGeneration = 1,
        requestedSampleCount = requestedSampleCount,
        msaaAdapter = msaaAdapter,
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
    )
}
