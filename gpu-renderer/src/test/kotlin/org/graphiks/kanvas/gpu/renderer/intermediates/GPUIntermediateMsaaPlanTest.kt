package org.graphiks.kanvas.gpu.renderer.intermediates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.destination.GPUDestinationReadBounds
import org.graphiks.kanvas.gpu.renderer.passes.GPUMsaaAdapterCapability
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.passes.GPUSamplePlan

class GPUIntermediateMsaaPlanTest {
    @Test
    fun `sample count four is refused without native resolve evidence`() {
        val plan = GPUIntermediatePlanner().plan(
            msaaRequest(
                samplePlan = GPUSamplePlan.MultisampleFrame(4),
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
                samplePlan = GPUSamplePlan.MultisampleFrame(2),
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
    fun `native resolve remains refused until runtime resolve evidence is wired`() {
        val plan = GPUIntermediatePlanner().plan(
            msaaRequest(
                samplePlan = GPUSamplePlan.MultisampleFrame(4),
                msaaAdapter = GPUMsaaAdapterCapability(
                    adapterLabel = "adapter:test",
                    maxSampleCount = 4,
                    supportsAlphaToCoverage = false,
                    supportsNativeResolve = true,
                ),
            ),
        )

        val refusal = assertIs<GPUIntermediatePlanStep.Refuse>(plan.steps.single())
        assertEquals("unsupported.msaa.runtime_resolve_unwired", refusal.reasonCode)
        assertEquals("target:main", refusal.scopeLabel)
        assertEquals(1, plan.telemetry.intermediatesRefused)
        assertEquals(0, plan.telemetry.msaaTargets)
        assertEquals(0, plan.telemetry.msaaResolves)
        assertFalse(plan.steps.any { it is GPUIntermediatePlanStep.ResolveMSAA })
    }

    @Test
    fun `local resolve approximation cannot prove advanced blend exactness`() {
        val plan = GPUIntermediatePlanner().plan(
            msaaRequest(
                samplePlan = GPUSamplePlan.LocalResolveApproximation(sourceSampleCount = 4),
                msaaAdapter = null,
                blendMode = GPUBlendMode.MULTIPLY,
            ),
        )

        val refusal = assertIs<GPUIntermediatePlanStep.Refuse>(plan.steps.single())
        assertEquals("unsupported.blend.msaa_destination_read_exactness", refusal.reasonCode)
    }

    @Test
    fun `multisample advanced blend exactness refusal precedes runtime resolve wiring`() {
        val plan = GPUIntermediatePlanner().plan(
            msaaRequest(
                samplePlan = GPUSamplePlan.MultisampleFrame(4),
                msaaAdapter = GPUMsaaAdapterCapability(
                    adapterLabel = "adapter:test",
                    maxSampleCount = 4,
                    supportsAlphaToCoverage = false,
                    supportsNativeResolve = true,
                ),
                blendMode = GPUBlendMode.MULTIPLY,
            ),
        )

        val refusal = assertIs<GPUIntermediatePlanStep.Refuse>(plan.steps.single())
        assertEquals("unsupported.blend.msaa_destination_read_exactness", refusal.reasonCode)
        assertEquals("cmd-aa", refusal.scopeLabel)
    }

    @Test
    fun `canonical msaa capability refusal still precedes advanced blend exactness`() {
        val plan = GPUIntermediatePlanner().plan(
            msaaRequest(
                samplePlan = GPUSamplePlan.MultisampleFrame(2),
                msaaAdapter = GPUMsaaAdapterCapability(
                    adapterLabel = "adapter:test",
                    maxSampleCount = 8,
                    supportsAlphaToCoverage = false,
                    supportsNativeResolve = true,
                ),
                blendMode = GPUBlendMode.MULTIPLY,
            ),
        )

        val refusal = assertIs<GPUIntermediatePlanStep.Refuse>(plan.steps.single())
        assertEquals("unsupported.msaa.sample_count", refusal.reasonCode)
        assertEquals("target:main", refusal.scopeLabel)
    }

    private fun msaaRequest(
        samplePlan: GPUSamplePlan,
        msaaAdapter: GPUMsaaAdapterCapability?,
        blendMode: GPUBlendMode = GPUBlendMode.SRC_OVER,
    ) = GPUIntermediatePlannerRequest(
        planId = "plan:msaa",
        targetId = "target:main",
        targetFormatClass = "rgba8unorm",
        targetUsageLabels = setOf("render_attachment", "texture_binding", "copy_src", "copy_dst"),
        deviceGeneration = 1,
        samplePlan = samplePlan,
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
                blendMode = blendMode,
                materialKeyHash = "material:solid",
                renderStepIdentity = "rect-fill",
            ),
        ),
    )
}
