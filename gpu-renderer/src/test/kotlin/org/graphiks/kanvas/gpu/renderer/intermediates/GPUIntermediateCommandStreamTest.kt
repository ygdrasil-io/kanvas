package org.graphiks.kanvas.gpu.renderer.intermediates

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommandStream

class GPUIntermediateCommandStreamTest {
    @Test
    fun `destination copy lowers before draw that samples it`() {
        val descriptor = descriptor("intermediate:dst-copy:cmd-1", GPUIntermediatePurpose.DestinationCopy)
        val stream = GPUPassCommandStream.fromIntermediatePlan(
            streamId = "stream:intermediate",
            packetStreamId = "packets:intermediate",
            passId = "pass:main",
            targetStateHash = "target:rgba8:sample1",
            loadStoreLabel = "load-store:load-store",
            plan = GPUIntermediatePlan(
                planId = "plan:screen",
                targetId = "target:main",
                steps = listOf(
                    GPUIntermediatePlanStep.CreateIntermediate(descriptor),
                    GPUIntermediatePlanStep.CopyDestination(
                        sourceLabel = "surface:main",
                        destination = descriptor,
                        boundsLabel = "bounds:cmd-1",
                        tokenLabel = "dst-token:cmd-1:1",
                        passSplitRequired = true,
                        copyBeforeSample = true,
                    ),
                    GPUIntermediatePlanStep.RenderToTarget(
                        commandId = "cmd-1",
                        targetLabel = "surface:main",
                        routeLabel = "shader-blend:Screen",
                        orderingToken = "order:cmd-1",
                    ),
                ),
            ),
        )

        assertEquals(
            listOf("beginRenderPass", "prepareIntermediateTexture", "copyTexture", "draw", "endRenderPass"),
            stream.commandLabels,
        )
        val prepare = assertIs<GPUPassCommand.PrepareIntermediateTexture>(stream.commands[1])
        assertEquals("intermediate:dst-copy:cmd-1", prepare.textureLabel)
        val copy = assertIs<GPUPassCommand.CopyTexture>(stream.commands[2])
        assertEquals("surface:main", copy.sourceLabel)
        assertEquals("intermediate:dst-copy:cmd-1", copy.destinationLabel)
    }

    @Test
    fun `msaa resolve lowers to explicit resolve command`() {
        val msaa = descriptor("intermediate:msaa:layer-a", GPUIntermediatePurpose.LayerTarget, sampleCount = 4)
        val resolved = descriptor("intermediate:resolved:layer-a", GPUIntermediatePurpose.MsaaResolve, sampleCount = 1)
        val stream = GPUPassCommandStream.fromIntermediatePlan(
            streamId = "stream:msaa",
            packetStreamId = "packets:msaa",
            passId = "pass:msaa",
            targetStateHash = "target:rgba8:sample4",
            loadStoreLabel = "load-store:clear-resolve",
            plan = GPUIntermediatePlan(
                planId = "plan:msaa",
                targetId = "target:main",
                steps = listOf(
                    GPUIntermediatePlanStep.CreateIntermediate(msaa),
                    GPUIntermediatePlanStep.ResolveMSAA(
                        source = msaa,
                        destination = resolved,
                        strategyLabel = "WGPU_BUILTIN",
                        tokenLabel = "msaa-token:layer-a",
                    ),
                ),
            ),
        )

        assertEquals(listOf("beginRenderPass", "prepareIntermediateTexture", "resolveMSAA", "endRenderPass"), stream.commandLabels)
        assertIs<GPUPassCommand.ResolveMSAA>(stream.commands[2])
    }

    private fun descriptor(
        label: String,
        purpose: GPUIntermediatePurpose,
        sampleCount: Int = 1,
    ): GPUIntermediateTextureDescriptor =
        GPUIntermediateTextureDescriptor(
            label = label,
            purpose = purpose,
            descriptorHash = "sha256:$label",
            sourceTargetLabel = "surface:main",
            boundsLabel = "bounds:all",
            width = 64,
            height = 64,
            formatClass = "rgba8unorm",
            usageLabels = listOf("render_attachment", "texture_binding", "copy_dst"),
            sampleCount = sampleCount,
            generation = 1,
            lifetimeClass = "pass-local",
            ownerScope = "target:main",
            byteEstimate = 16384,
        )
}
