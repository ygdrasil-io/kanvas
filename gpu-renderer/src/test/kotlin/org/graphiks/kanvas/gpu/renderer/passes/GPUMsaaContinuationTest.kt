package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity

class GPUMsaaContinuationTest {
    private val planner = GPUSampleContinuationPlanner()

    @Test
    fun `preserve load survives several pass breaks only with the same stored attachments`() {
        val first = accepted(
            planner.plan(
                GPUSampleContinuationRequest(
                    key = continuationKey(),
                    loadTransition = GPUSampleLoadTransition.FreshClear,
                    endTransition = GPUSampleEndTransition.StoreForContinuation,
                ),
            ),
        )

        val second = accepted(
            planner.plan(
                GPUSampleContinuationRequest(
                    key = continuationKey(),
                    loadTransition = GPUSampleLoadTransition.RetainedLoad,
                    endTransition = GPUSampleEndTransition.StoreForContinuation,
                    storedState = assertNotNull(first.storedState),
                ),
            ),
        )
        val third = accepted(
            planner.plan(
                GPUSampleContinuationRequest(
                    key = continuationKey(),
                    loadTransition = GPUSampleLoadTransition.RetainedLoad,
                    endTransition = GPUSampleEndTransition.Resolve,
                    storedState = assertNotNull(second.storedState),
                ),
            ),
        )

        assertEquals(GPUSampleLoadTransition.RetainedLoad, second.loadTransition)
        assertEquals(GPUSampleEndTransition.Resolve, third.endTransition)
        assertNull(third.storedState)
    }

    @Test
    fun `fresh transient attachment cannot claim preserve load`() {
        val stored = assertNotNull(
            accepted(
                planner.plan(
                    GPUSampleContinuationRequest(
                        key = continuationKey(),
                        loadTransition = GPUSampleLoadTransition.FreshClear,
                        endTransition = GPUSampleEndTransition.StoreForContinuation,
                    ),
                ),
            ).storedState,
        )

        val result = planner.plan(
            GPUSampleContinuationRequest(
                key = continuationKey(
                    colorAttachment = GPUTargetIdentity("msaa-color:fresh"),
                ),
                loadTransition = GPUSampleLoadTransition.RetainedLoad,
                endTransition = GPUSampleEndTransition.StoreForContinuation,
                storedState = stored,
            ),
        )

        assertEquals(
            "unsupported.msaa.continuation_attachment_mismatch",
            assertIs<GPUSampleContinuationResult.Refused>(result).diagnostic.code.value,
        )
    }

    @Test
    fun `target generation layer identity and sample plan stay isolated`() {
        val stored = assertNotNull(
            accepted(
                planner.plan(
                    GPUSampleContinuationRequest(
                        key = continuationKey(),
                        loadTransition = GPUSampleLoadTransition.FreshClear,
                        endTransition = GPUSampleEndTransition.StoreForContinuation,
                    ),
                ),
            ).storedState,
        )

        val staleTarget = planner.plan(
            GPUSampleContinuationRequest(
                key = continuationKey(targetGeneration = 8),
                loadTransition = GPUSampleLoadTransition.RetainedLoad,
                endTransition = GPUSampleEndTransition.StoreForContinuation,
                storedState = stored,
            ),
        )
        val layer = planner.plan(
            GPUSampleContinuationRequest(
                key = continuationKey(target = GPUTargetIdentity("layer:7")),
                loadTransition = GPUSampleLoadTransition.RetainedLoad,
                endTransition = GPUSampleEndTransition.StoreForContinuation,
                storedState = stored,
            ),
        )
        val differentSamplePlan = planner.plan(
            GPUSampleContinuationRequest(
                key = continuationKey(samplePlan = GPUSamplePlan.MultisampleFrame(2)),
                loadTransition = GPUSampleLoadTransition.RetainedLoad,
                endTransition = GPUSampleEndTransition.StoreForContinuation,
                storedState = stored,
            ),
        )

        assertEquals(
            "unsupported.msaa.continuation_target_generation",
            assertIs<GPUSampleContinuationResult.Refused>(staleTarget).diagnostic.code.value,
        )
        assertEquals(
            "unsupported.msaa.continuation_target_identity",
            assertIs<GPUSampleContinuationResult.Refused>(layer).diagnostic.code.value,
        )
        assertEquals(
            "unsupported.msaa.continuation_sample_plan",
            assertIs<GPUSampleContinuationResult.Refused>(differentSamplePlan).diagnostic.code.value,
        )
    }

    @Test
    fun `discard invalidates stored continuation state`() {
        val stored = assertNotNull(
            accepted(
                planner.plan(
                    GPUSampleContinuationRequest(
                        key = continuationKey(),
                        loadTransition = GPUSampleLoadTransition.FreshClear,
                        endTransition = GPUSampleEndTransition.StoreForContinuation,
                    ),
                ),
            ).storedState,
        )

        val discarded = accepted(
            planner.plan(
                GPUSampleContinuationRequest(
                    key = continuationKey(),
                    loadTransition = GPUSampleLoadTransition.RetainedLoad,
                    endTransition = GPUSampleEndTransition.Discard,
                    storedState = stored,
                ),
            ),
        )

        assertEquals(GPUSampleEndTransition.Discard, discarded.endTransition)
        assertNull(discarded.storedState)
    }

    private fun continuationKey(
        target: GPUTargetIdentity = GPUTargetIdentity("scene"),
        targetGeneration: Long = 7,
        colorAttachment: GPUTargetIdentity = GPUTargetIdentity("msaa-color:scene:7"),
        samplePlan: GPUSamplePlan.MultisampleFrame = GPUSamplePlan.MultisampleFrame(4),
    ): GPUSampleContinuationKey = GPUSampleContinuationKey(
        target = target,
        targetGeneration = targetGeneration,
        deviceGeneration = GPUDeviceGenerationID(3),
        colorFormat = GPUColorFormat("rgba8unorm"),
        colorInterpretation = GPUColorInterpretation("encoded-premul-srgb"),
        samplePlan = samplePlan,
        colorAttachment = colorAttachment,
        depthStencilAttachment = GPUTargetIdentity("msaa-depth-stencil:scene:7"),
    )

    private fun accepted(result: GPUSampleContinuationResult): GPUSampleContinuationPlan =
        assertIs<GPUSampleContinuationResult.Accepted>(result).plan
}
