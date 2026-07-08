package org.graphiks.kanvas.gpu.renderer.intermediates

import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassCommand
import org.graphiks.kanvas.gpu.renderer.passes.GPUPassDiagnostic
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
                    GPUIntermediatePlanStep.BindIntermediate(
                        descriptor = descriptor,
                        bindingLabel = "dst-read:cmd-1",
                        layoutHash = "layout:dst-read",
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
            listOf("prepareIntermediateTexture", "copyTexture", "bindIntermediate", "beginRenderPass", "draw", "endRenderPass"),
            stream.commandLabels,
        )
        val prepare = assertIs<GPUPassCommand.PrepareIntermediateTexture>(stream.commands[0])
        assertEquals("intermediate:dst-copy:cmd-1", prepare.textureLabel)
        val copy = assertIs<GPUPassCommand.CopyTexture>(stream.commands[1])
        assertEquals("surface:main", copy.sourceLabel)
        assertEquals("intermediate:dst-copy:cmd-1", copy.destinationLabel)
        val bind = assertIs<GPUPassCommand.BindIntermediate>(stream.commands[2])
        assertEquals("intermediate:dst-copy:cmd-1", bind.textureLabel)
        assertEquals("dst-read:cmd-1", bind.bindingLabel)
        assertEquals("layout:dst-read", bind.layoutHash)
    }

    @Test
    fun `copy-before-sample closes active render pass before copy and reopens later`() {
        val descriptor = descriptor("intermediate:dst-copy:cmd-2", GPUIntermediatePurpose.DestinationCopy)
        val stream = GPUPassCommandStream.fromIntermediatePlan(
            streamId = "stream:split",
            packetStreamId = "packets:split",
            passId = "pass:split",
            targetStateHash = "target:rgba8:sample1",
            loadStoreLabel = "load-store:load-store",
            plan = GPUIntermediatePlan(
                planId = "plan:split",
                targetId = "target:main",
                steps = listOf(
                    GPUIntermediatePlanStep.RenderToTarget(
                        commandId = "cmd-1",
                        targetLabel = "surface:main",
                        routeLabel = "shader:solid",
                        orderingToken = "order:cmd-1",
                    ),
                    GPUIntermediatePlanStep.CreateIntermediate(descriptor),
                    GPUIntermediatePlanStep.CopyDestination(
                        sourceLabel = "surface:main",
                        destination = descriptor,
                        boundsLabel = "bounds:cmd-2",
                        tokenLabel = "dst-token:cmd-2:1",
                        passSplitRequired = true,
                        copyBeforeSample = true,
                    ),
                    GPUIntermediatePlanStep.RenderToTarget(
                        commandId = "cmd-2",
                        targetLabel = "surface:main",
                        routeLabel = "shader-blend:Screen",
                        orderingToken = "order:cmd-2",
                    ),
                ),
            ),
        )

        assertEquals(
            listOf(
                "beginRenderPass",
                "draw",
                "endRenderPass",
                "prepareIntermediateTexture",
                "copyTexture",
                "beginRenderPass",
                "draw",
                "endRenderPass",
            ),
            stream.commandLabels,
        )
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

        assertEquals(listOf("prepareIntermediateTexture", "resolveMSAA"), stream.commandLabels)
        assertIs<GPUPassCommand.ResolveMSAA>(stream.commands[1])
    }

    @Test
    fun `refusal-only plan lowers to refusal evidence without render pass commands`() {
        val stream = GPUPassCommandStream.fromIntermediatePlan(
            streamId = "stream:refusal",
            packetStreamId = "packets:refusal",
            passId = "pass:refusal",
            targetStateHash = "target:rgba8:sample1",
            loadStoreLabel = "load-store:refused",
            plan = GPUIntermediatePlan(
                planId = "plan:refusal",
                targetId = "target:main",
                steps = listOf(
                    GPUIntermediatePlanStep.Refuse(
                        scopeLabel = "scope:filter",
                        reasonCode = "FILTER_UNSUPPORTED",
                    ),
                ),
            ),
        )

        assertEquals(listOf("refuseIntermediate"), stream.commandLabels)
        val refusal = assertIs<GPUPassCommand.RefuseIntermediate>(stream.commands.single())
        assertEquals("scope:filter", refusal.scopeLabel)
        assertEquals("FILTER_UNSUPPORTED", refusal.reasonCode)
    }

    @Test
    fun `plan diagnostics propagate into pass diagnostics`() {
        val stream = GPUPassCommandStream.fromIntermediatePlan(
            streamId = "stream:diagnostics",
            packetStreamId = "packets:diagnostics",
            passId = "pass:diagnostics",
            targetStateHash = "target:rgba8:sample1",
            loadStoreLabel = "load-store:load-store",
            plan = GPUIntermediatePlan(
                planId = "plan:diagnostics",
                targetId = "target:main",
                steps = listOf(
                    GPUIntermediatePlanStep.RenderToTarget(
                        commandId = "cmd-1",
                        targetLabel = "surface:main",
                        routeLabel = "shader:solid",
                        orderingToken = "order:cmd-1",
                    ),
                ),
                diagnostics = listOf(
                    GPUIntermediateDiagnostic(
                        code = "INTERMEDIATE_NOTE",
                        scopeLabel = "scope:diagnostics",
                        message = "note",
                        terminal = false,
                    ),
                    GPUIntermediateDiagnostic(
                        code = "INTERMEDIATE_REFUSED",
                        scopeLabel = "scope:diagnostics",
                        message = "terminal",
                        terminal = true,
                    ),
                ),
            ),
        )

        assertContentEquals(
            listOf(
                GPUPassDiagnostic(
                    code = "INTERMEDIATE_NOTE",
                    passId = "pass:diagnostics",
                    invocationId = "scope:diagnostics",
                    terminal = false,
                ),
                GPUPassDiagnostic(
                    code = "INTERMEDIATE_REFUSED",
                    passId = "pass:diagnostics",
                    invocationId = "scope:diagnostics",
                    terminal = true,
                ),
            ),
            stream.diagnostics,
        )
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
