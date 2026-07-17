package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.color.GPUColorFormat
import org.graphiks.kanvas.gpu.renderer.color.GPUColorInterpretation
import org.graphiks.kanvas.gpu.renderer.state.GPUTargetIdentity

class GPUMsaaContinuationTest {
    private val planner = GPUSampleContinuationPlanner()

    @Test
    fun `preserve load survives several ordered pass breaks only with the same stored attachments`() {
        val plan = accepted(
            planner.plan(
                sequence(
                    transition(
                        load = GPUSampleLoadTransition.FreshClear,
                        store = GPUSampleStoreAction.Store,
                    ),
                    transition(
                        load = GPUSampleLoadTransition.RetainedLoad,
                        store = GPUSampleStoreAction.Store,
                    ),
                    transition(
                        load = GPUSampleLoadTransition.RetainedLoad,
                        store = GPUSampleStoreAction.Store,
                    ),
                ),
            ),
        )

        assertEquals(3, plan.transitions.size)
        assertTrue(plan.transitions[0].storedForNextTransition)
        assertTrue(plan.transitions[1].storedForNextTransition)
        assertEquals(GPUSampleLoadTransition.RetainedLoad, plan.transitions[1].loadTransition)
        assertEquals(GPUSampleResolveAction.ResolveCanonical, plan.transitions[2].resolveAction)
        assertEquals(continuationKey(), plan.finalStoredKey)
    }

    @Test
    fun `resolving the canonical target at every producing pass preserves stored attachment authority`() {
        val result = planner.plan(
            sequence(
                transition(
                    load = GPUSampleLoadTransition.FreshClear,
                    store = GPUSampleStoreAction.Store,
                ),
                transition(
                    load = GPUSampleLoadTransition.RetainedLoad,
                    store = GPUSampleStoreAction.Store,
                ),
                transition(
                    load = GPUSampleLoadTransition.RetainedLoad,
                    store = GPUSampleStoreAction.Store,
                ),
            ),
        )

        val plan = assertIs<GPUSampleContinuationResult.Accepted>(result).plan
        assertEquals(3, plan.transitions.size)
        assertTrue(plan.transitions.all { it.storedForNextTransition })
        assertEquals(continuationKey(), plan.finalStoredKey)
    }

    @Test
    fun `preserve load without a retained proof refuses deterministically`() {
        val result = planner.plan(
            sequence(
                transition(
                    load = GPUSampleLoadTransition.RetainedLoad,
                    store = GPUSampleStoreAction.Store,
                ),
            ),
        )

        val refusal = assertIs<GPUSampleContinuationResult.Refused>(result)
        assertEquals(0, refusal.transitionIndex)
        assertEquals(
            "unsupported.msaa.continuation_attachment_not_stored",
            refusal.diagnostic.code.value,
        )
    }

    @Test
    fun `device format interpretation and attachment mismatches all refuse retained load`() {
        val cases = listOf(
            continuationKey().copy(deviceGeneration = GPUDeviceGenerationID(4)) to
                "unsupported.msaa.continuation_device_generation",
            continuationKey().copy(colorFormat = GPUColorFormat("bgra8unorm")) to
                "unsupported.msaa.continuation_color_contract",
            continuationKey().copy(colorInterpretation = GPUColorInterpretation("linear-premul")) to
                "unsupported.msaa.continuation_color_contract",
            continuationKey().copy(colorAttachment = GPUTargetIdentity("msaa-color:other")) to
                "unsupported.msaa.continuation_attachment_mismatch",
        )

        cases.forEach { (nextKey, expectedCode) ->
            assertEquals(expectedCode, refusedAfterStored(nextKey).diagnostic.code.value)
        }
    }

    @Test
    fun `fresh transient attachment cannot claim preserve load`() {
        val result = planner.plan(
            sequence(
                transition(
                    load = GPUSampleLoadTransition.FreshClear,
                    store = GPUSampleStoreAction.Store,
                ),
                transition(
                    key = continuationKey(
                        colorAttachment = GPUTargetIdentity("msaa-color:fresh"),
                    ),
                    load = GPUSampleLoadTransition.RetainedLoad,
                    store = GPUSampleStoreAction.Store,
                ),
            ),
        )

        val refusal = assertIs<GPUSampleContinuationResult.Refused>(result)
        assertEquals(1, refusal.transitionIndex)
        assertEquals(
            "unsupported.msaa.continuation_attachment_mismatch",
            refusal.diagnostic.code.value,
        )
    }

    @Test
    fun `target generation layer identity and sample plan stay isolated`() {
        val staleTarget = refusedAfterStored(continuationKey(targetGeneration = 8))
        val layer = refusedAfterStored(
            continuationKey(target = GPUTargetIdentity("layer:7")),
        )
        val differentSamplePlan = refusedAfterStored(
            continuationKey(samplePlan = GPUSamplePlan.MultisampleFrame(2)),
        )

        assertEquals(
            "unsupported.msaa.continuation_target_generation",
            staleTarget.diagnostic.code.value,
        )
        assertEquals(
            "unsupported.msaa.continuation_target_identity",
            layer.diagnostic.code.value,
        )
        assertEquals(
            "unsupported.msaa.continuation_sample_plan",
            differentSamplePlan.diagnostic.code.value,
        )
    }

    @Test
    fun `discard makes earlier stored continuation proof stale`() {
        val result = planner.plan(
            sequence(
                transition(
                    load = GPUSampleLoadTransition.FreshClear,
                    store = GPUSampleStoreAction.Store,
                ),
                transition(
                    load = GPUSampleLoadTransition.RetainedLoad,
                    store = GPUSampleStoreAction.Discard,
                ),
                transition(
                    load = GPUSampleLoadTransition.RetainedLoad,
                    store = GPUSampleStoreAction.Store,
                ),
            ),
        )

        val refusal = assertIs<GPUSampleContinuationResult.Refused>(result)
        assertEquals(2, refusal.transitionIndex)
        assertEquals(
            "unsupported.msaa.continuation_attachment_not_stored",
            refusal.diagnostic.code.value,
        )
    }

    @Test
    fun `store without resolving canonical refuses an impossible continuation state`() {
        val result = planner.plan(
            sequence(
                transition(
                    load = GPUSampleLoadTransition.FreshClear,
                    store = GPUSampleStoreAction.Store,
                    resolve = GPUSampleResolveAction.Skip,
                ),
            ),
        )

        assertEquals(
            "unsupported.msaa.continuation_resolve_missing",
            assertIs<GPUSampleContinuationResult.Refused>(result).diagnostic.code.value,
        )
    }

    private fun refusedAfterStored(
        nextKey: GPUSampleContinuationKey,
    ): GPUSampleContinuationResult.Refused = assertIs(
        planner.plan(
            sequence(
                transition(
                    load = GPUSampleLoadTransition.FreshClear,
                    store = GPUSampleStoreAction.Store,
                ),
                transition(
                    key = nextKey,
                    load = GPUSampleLoadTransition.RetainedLoad,
                    store = GPUSampleStoreAction.Store,
                ),
            ),
        ),
    )

    private fun sequence(
        vararg transitions: GPUSampleContinuationRequest,
    ): GPUSampleContinuationSequenceRequest = GPUSampleContinuationSequenceRequest(
        transitions = transitions.toList(),
    )

    private fun transition(
        key: GPUSampleContinuationKey = continuationKey(),
        load: GPUSampleLoadTransition,
        store: GPUSampleStoreAction,
        resolve: GPUSampleResolveAction = GPUSampleResolveAction.ResolveCanonical,
    ): GPUSampleContinuationRequest = GPUSampleContinuationRequest(
        key = key,
        loadTransition = load,
        storeAction = store,
        resolveAction = resolve,
    )

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
