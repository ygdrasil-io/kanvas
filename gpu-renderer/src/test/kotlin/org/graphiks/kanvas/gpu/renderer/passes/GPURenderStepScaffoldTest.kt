package org.graphiks.kanvas.gpu.renderer.passes

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals
import org.graphiks.kanvas.gpu.renderer.execution.GPUCommandEncoderPlan
import org.graphiks.kanvas.gpu.renderer.execution.GPUCommandScope
import org.graphiks.kanvas.gpu.renderer.execution.GPUDeviceGeneration
import org.graphiks.kanvas.gpu.renderer.execution.dumpLines
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadFingerprint
import org.graphiks.kanvas.gpu.renderer.payloads.GPUPayloadSlotID
import org.graphiks.kanvas.gpu.renderer.payloads.GPUResourceBindingSlot
import org.graphiks.kanvas.gpu.renderer.payloads.GPUUniformPayloadSlot
import org.graphiks.kanvas.gpu.renderer.pipelines.GPURenderPipelineKey

/** Verifies render-step metadata and dump scaffolding for the first executable route. */
class GPURenderStepScaffoldTest {
    @Test
    fun `render step descriptor snapshots layouts and projects pipeline axes`() {
        val staticAttributes = mutableListOf(
            GPURenderStepAttribute(
                name = "position",
                valueClass = "vec2<f32>",
                sourceClass = "static",
                byteOffset = 0L,
                byteSize = 8L,
            ),
        )
        val appendAttributes = mutableListOf(
            GPURenderStepAttribute(
                name = "rectBounds",
                valueClass = "vec4<f32>",
                sourceClass = "append",
                byteOffset = 0L,
                byteSize = 16L,
            ),
        )
        val descriptor = GPURenderStepDescriptor(
            stepId = GPURenderStepID("fill-rect"),
            version = 1,
            geometryClass = "axis-aligned-rect",
            coverageClass = "analytic-fill",
            vertexLayout = GPURenderStepVertexLayout(
                layoutHash = "vertex-layout:solid-rect-v1",
                primitiveTopology = "triangle-list",
                staticAttributes = staticAttributes,
                appendAttributes = appendAttributes,
                instanceStepRate = "per-draw",
            ),
            payloadLayout = GPURenderStepPayloadLayout(
                bindingLayoutHash = "binding-layout:solid-v1",
                uniformLayoutHash = "uniform-layout:solid-rect-v1",
                resourceLayoutHash = "resource-layout:none",
                dynamicOffsetPolicy = "none",
            ),
            fixedState = GPURenderStepFixedState(
                stateHash = "fixed-state:src-over-msaa1",
                blendStateHash = "blend:src-over",
                sampleStateHash = "sample:msaa1",
                depthStencilStateHash = "depth-stencil:none",
                targetStateClass = "rgba8unorm-premul",
                coverageFunctionIdentity = "solid_rect_coverage_fn",
            ),
            wgslVertexEntryPoint = "vs_solid_rect",
            wgslFragmentEntryPoint = "fs_solid_rect",
            wgslModuleHash = "wgsl:solid-rect-module",
        )

        staticAttributes += GPURenderStepAttribute(
            name = "callerMutation",
            valueClass = "vec4<f32>",
            sourceClass = "static",
            byteOffset = 8L,
            byteSize = 16L,
        )
        appendAttributes.clear()

        assertEquals(listOf("position"), descriptor.vertexLayout.staticAttributeNames)
        assertEquals(listOf("rectBounds"), descriptor.vertexLayout.appendAttributeNames)

        val plan = descriptor.toPlan()

        assertEquals(GPURenderStepID("fill-rect"), plan.stepId)
        assertEquals("axis-aligned-rect", plan.geometryClass)
        assertEquals("analytic-fill", plan.coverageClass)
        assertEquals("vertex-layout:solid-rect-v1", plan.vertexLayoutHash)
        assertEquals("fixed-state:src-over-msaa1", plan.fixedStateHash)
        assertEquals("wgsl:solid-rect-module#fs_solid_rect", plan.wgslFragmentHash)
        assertEquals("triangle-list", plan.pipelineAxes["primitiveTopology"])
        assertEquals("binding-layout:solid-v1", plan.pipelineAxes["bindingLayoutHash"])
        assertEquals("solid_rect_coverage_fn", plan.pipelineAxes["coverageFunctionIdentity"])
    }

    @Test
    fun `render step descriptor dumps deterministic backend neutral evidence`() {
        val lines = renderStepDescriptor().dumpLines()

        assertEquals(
            "passes.render-step id=fill-rect version=1 geometry=axis-aligned-rect " +
                "coverage=analytic-fill topology=triangle-list vertexLayout=vertex-layout:solid-rect-v1 " +
                "payloadLayout=binding-layout:solid-v1 fixedState=fixed-state:src-over-msaa1 " +
                "wgsl=wgsl:solid-rect-module vertexEntry=vs_solid_rect fragmentEntry=fs_solid_rect " +
                "diagnostics=none",
            lines.first(),
        )
        assertContains(lines, "passes.render-step.attributes static=position:vec2<f32>@0+8 append=rectBounds:vec4<f32>@0+16")
        assertContains(
            lines,
            "passes.render-step.state blend=blend:src-over sample=sample:msaa1 " +
                "depthStencil=depth-stencil:none target=rgba8unorm-premul coverageFn=solid_rect_coverage_fn",
        )
    }

    @Test
    fun `packet command and encoder dumps expose stable command stream evidence`() {
        val packetStream = packetStream()
        val commandStream = GPUPassCommandStream.fromDrawPacketStream(
            streamId = "pass-command-stream-main",
            packetStream = packetStream,
            targetStateHash = "rgba8-premul-msaa1",
            loadStoreLabel = "clear-store",
        )
        val encoderPlan = GPUCommandEncoderPlan.fromPassCommandStream(
            planId = "encoder-plan-frame-1",
            contextIdentity = "wgpu-context-main",
            deviceGeneration = GPUDeviceGeneration(4L),
            targetGeneration = 11L,
            scope = GPUCommandScope.Render(
                label = "main-pass",
                useTokenLabels = listOf("payload-generation:7", "target-generation:11"),
            ),
            packetStream = packetStream,
            passCommandStream = commandStream,
            resourceGenerationLabels = listOf("payload-generation:7", "target-generation:11"),
        )

        assertEquals(
            "passes.packet-stream id=packet-stream-main pass=main-pass packets=packet-1,packet-2 " +
                "commands=1,2 sortKeys=100,200 pipelines=render:solid-fill diagnostics=none",
            packetStream.dumpLines().first(),
        )
        assertContains(
            packetStream.dumpLines(),
            "passes.packet id=packet-1 command=1 analysis=analysis-1 pass=main-pass layer=root-layer " +
                "bindingList=bindings-1 role=Shading step=fill-rect@1 renderPipeline=render:solid-fill " +
                "computePipeline=none bindingLayout=layout-solid-v1 uniformSlot=uniform-1 " +
                "resourceSlot=resource-1 vertex=solid-quad scissor=scissor-0-0-64-64 " +
                "target=rgba8-premul-msaa1 order=1 resourceGeneration=7 diagnostics=none",
        )
        assertEquals(
            "passes.command-stream id=pass-command-stream-main packetStream=packet-stream-main pass=main-pass " +
                "commands=beginRenderPass,setRenderPipeline,setBindGroup,setScissor,draw,setRenderPipeline," +
                "setBindGroup,setScissor,draw,endRenderPass packets=packet-1,packet-1,packet-1,packet-1," +
                "packet-2,packet-2,packet-2,packet-2 diagnostics=none",
            commandStream.dumpLines().first(),
        )
        assertContains(commandStream.dumpLines(), "passes.command draw packet=packet-1 vertex=solid-quad")
        assertEquals(
            "execution.encoder-plan id=encoder-plan-frame-1 context=wgpu-context-main class=Render " +
                "scope=main-pass deviceGeneration=4 targetGeneration=11 packetStream=packet-stream-main " +
                "passCommandStream=pass-command-stream-main packets=2 commands=10 operations=beginRenderPass," +
                "setRenderPipeline,setBindGroup,setScissor,draw,setRenderPipeline,setBindGroup,setScissor,draw," +
                "endRenderPass resources=payload-generation:7,target-generation:11 diagnostics=none",
            encoderPlan.dumpLines().first(),
        )
    }

    private fun renderStepDescriptor(): GPURenderStepDescriptor =
        GPURenderStepDescriptor(
            stepId = GPURenderStepID("fill-rect"),
            version = 1,
            geometryClass = "axis-aligned-rect",
            coverageClass = "analytic-fill",
            vertexLayout = GPURenderStepVertexLayout(
                layoutHash = "vertex-layout:solid-rect-v1",
                primitiveTopology = "triangle-list",
                staticAttributes = listOf(
                    GPURenderStepAttribute(
                        name = "position",
                        valueClass = "vec2<f32>",
                        sourceClass = "static",
                        byteOffset = 0L,
                        byteSize = 8L,
                    ),
                ),
                appendAttributes = listOf(
                    GPURenderStepAttribute(
                        name = "rectBounds",
                        valueClass = "vec4<f32>",
                        sourceClass = "append",
                        byteOffset = 0L,
                        byteSize = 16L,
                    ),
                ),
                instanceStepRate = "per-draw",
            ),
            payloadLayout = GPURenderStepPayloadLayout(
                bindingLayoutHash = "binding-layout:solid-v1",
                uniformLayoutHash = "uniform-layout:solid-rect-v1",
                resourceLayoutHash = "resource-layout:none",
                dynamicOffsetPolicy = "none",
            ),
            fixedState = GPURenderStepFixedState(
                stateHash = "fixed-state:src-over-msaa1",
                blendStateHash = "blend:src-over",
                sampleStateHash = "sample:msaa1",
                depthStencilStateHash = "depth-stencil:none",
                targetStateClass = "rgba8unorm-premul",
                coverageFunctionIdentity = "solid_rect_coverage_fn",
            ),
            wgslVertexEntryPoint = "vs_solid_rect",
            wgslFragmentEntryPoint = "fs_solid_rect",
            wgslModuleHash = "wgsl:solid-rect-module",
        )

    private fun packetStream(): GPUDrawPacketStream =
        GPUDrawPacketStream(
            streamId = "packet-stream-main",
            passId = "main-pass",
            packets = listOf(
                packet(packetId = GPUDrawPacketID("packet-1"), commandId = 1, sortKey = 100L),
                packet(packetId = GPUDrawPacketID("packet-2"), commandId = 2, sortKey = 200L),
            ),
        )

    private fun packet(packetId: GPUDrawPacketID, commandId: Int, sortKey: Long): GPUDrawPacket =
        GPUDrawPacket(
            packetId = packetId,
            commandIdValue = commandId,
            analysisRecordId = "analysis-$commandId",
            passId = "main-pass",
            layerId = "root-layer",
            bindingListId = "bindings-$commandId",
            insertionReasonCode = "native-fill-rect",
            sortKey = sortKey,
            sortKeyPreimage = "paint|clip|transform|$commandId",
            renderStepId = GPURenderStepID("fill-rect"),
            renderStepVersion = 1,
            role = GPUDrawPacketRole.Shading,
            renderPipelineKey = GPURenderPipelineKey("render:solid-fill"),
            bindingLayoutHash = "layout-solid-v1",
            uniformSlot = uniformSlot(commandId),
            resourceSlot = resourceSlot(commandId),
            vertexSourceLabel = "solid-quad",
            scissorBoundsHash = "scissor-0-0-64-64",
            targetStateHash = "rgba8-premul-msaa1",
            originalPaintOrder = commandId,
            resourceGeneration = 7L,
        )

    private fun uniformSlot(commandId: Int): GPUUniformPayloadSlot =
        GPUUniformPayloadSlot(
            slotId = GPUPayloadSlotID("uniform-$commandId"),
            fingerprint = GPUPayloadFingerprint("uniform-fingerprint-$commandId"),
            byteOffset = ((commandId - 1) * 64).toLong(),
        )

    private fun resourceSlot(commandId: Int): GPUResourceBindingSlot =
        GPUResourceBindingSlot(
            slotId = GPUPayloadSlotID("resource-$commandId"),
            fingerprint = GPUPayloadFingerprint("resource-fingerprint-$commandId"),
            bindingIndex = 0,
        )
}
