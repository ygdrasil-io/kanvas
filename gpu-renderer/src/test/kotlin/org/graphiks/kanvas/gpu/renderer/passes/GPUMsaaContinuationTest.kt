package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
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
                        end = GPUSampleEndTransition.StoreForContinuation,
                    ),
                    transition(
                        load = GPUSampleLoadTransition.RetainedLoad,
                        end = GPUSampleEndTransition.StoreForContinuation,
                    ),
                    transition(
                        load = GPUSampleLoadTransition.RetainedLoad,
                        end = GPUSampleEndTransition.Resolve,
                    ),
                ),
            ),
        )

        assertEquals(3, plan.transitions.size)
        assertTrue(plan.transitions[0].storedForNextTransition)
        assertTrue(plan.transitions[1].storedForNextTransition)
        assertEquals(GPUSampleLoadTransition.RetainedLoad, plan.transitions[1].loadTransition)
        assertEquals(GPUSampleEndTransition.Resolve, plan.transitions[2].endTransition)
        assertNull(plan.finalStoredKey)
    }

    @Test
    fun `fresh transient attachment cannot claim preserve load`() {
        val result = planner.plan(
            sequence(
                transition(
                    load = GPUSampleLoadTransition.FreshClear,
                    end = GPUSampleEndTransition.StoreForContinuation,
                ),
                transition(
                    key = continuationKey(
                        colorAttachment = GPUTargetIdentity("msaa-color:fresh"),
                    ),
                    load = GPUSampleLoadTransition.RetainedLoad,
                    end = GPUSampleEndTransition.StoreForContinuation,
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
    fun `resolve and discard make every earlier stored continuation proof stale`() {
        listOf(
            GPUSampleEndTransition.Resolve,
            GPUSampleEndTransition.Discard,
        ).forEach { terminalTransition ->
            val result = planner.plan(
                sequence(
                    transition(
                        load = GPUSampleLoadTransition.FreshClear,
                        end = GPUSampleEndTransition.StoreForContinuation,
                    ),
                    transition(
                        load = GPUSampleLoadTransition.RetainedLoad,
                        end = terminalTransition,
                    ),
                    transition(
                        load = GPUSampleLoadTransition.RetainedLoad,
                        end = GPUSampleEndTransition.StoreForContinuation,
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
    }

    private fun refusedAfterStored(
        nextKey: GPUSampleContinuationKey,
    ): GPUSampleContinuationResult.Refused = assertIs(
        planner.plan(
            sequence(
                transition(
                    load = GPUSampleLoadTransition.FreshClear,
                    end = GPUSampleEndTransition.StoreForContinuation,
                ),
                transition(
                    key = nextKey,
                    load = GPUSampleLoadTransition.RetainedLoad,
                    end = GPUSampleEndTransition.StoreForContinuation,
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
        end: GPUSampleEndTransition,
    ): GPUSampleContinuationRequest = GPUSampleContinuationRequest(
        key = key,
        loadTransition = load,
        endTransition = end,
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
