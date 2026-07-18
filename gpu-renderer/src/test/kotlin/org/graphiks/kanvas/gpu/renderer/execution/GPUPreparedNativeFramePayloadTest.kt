package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUTextureView
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertSame
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID
import org.graphiks.kanvas.gpu.renderer.recording.GPUFrameID

class GPUPreparedNativeFramePayloadTest {
    @Test
    fun `command order remains the default direct render operand layout`() {
        val generation = GPUDeviceGenerationID(7)
        val target = textureViewOperand("target", generation)
        val pipeline = pipelineOperand("direct", generation)
        val bindGroup = bindGroupOperand("direct", generation)
        val vertex = bufferOperand("vertices", generation)
        val index = bufferOperand("indices", generation)
        val commands = listOf(
            GPUPreparedNativeRenderCommand.SetPipeline(pipeline),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroup),
            vertexCommand(vertex),
            indexCommand(index),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
            drawCommand(),
        )

        val render = GPUPreparedNativeScopeOperand.Render(
            sourceStepIndex = 2,
            pass = GPUPreparedNativeRenderPassConfig(target),
            commands = commands,
        )

        assertEquals(commands, render.commands)
        assertOperandIdentities(
            listOf(target, pipeline, bindGroup, vertex, index),
            render.operands,
        )
    }

    @Test
    fun `indexed core path layout matches path only C3 native operand keys`() {
        val generation = GPUDeviceGenerationID(7)
        val target = textureViewOperand("target", generation)
        val depthStencil = textureViewOperand("depth-stencil", generation)
        val producerPipeline = pipelineOperand("producer", generation)
        val coverPipeline = pipelineOperand("cover", generation)
        val vertex = bufferOperand("vertices", generation)
        val index = bufferOperand("indices", generation)
        val producerBindGroup = bindGroupOperand("producer", generation)
        val coverBindGroup = bindGroupOperand("cover", generation)
        val commands = listOf(
            GPUPreparedNativeRenderCommand.SetPipeline(producerPipeline),
            vertexCommand(vertex),
            indexCommand(index),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, producerBindGroup, listOf(0L)),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
            GPUPreparedNativeRenderCommand.SetStencilReference(0u),
            drawCommand(),
            GPUPreparedNativeRenderCommand.SetPipeline(coverPipeline),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, coverBindGroup, listOf(256L)),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
            GPUPreparedNativeRenderCommand.SetStencilReference(0u),
            drawCommand(),
        )
        val render = GPUPreparedNativeScopeOperand.Render(
            sourceStepIndex = 2,
            pass = pathPass(target, depthStencil),
            commands = commands,
            operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
        )
        val keys = listOf(
            key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, "target"),
            key(
                GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                GPUPreparedNativeOperandKind.TextureView,
                "depth-stencil",
            ),
            key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "producer"),
            key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "cover"),
            key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer, "vertices"),
            key(GPUPreparedNativeOperandRole.RenderIndexBuffer, GPUPreparedNativeOperandKind.Buffer, "indices"),
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "producer"),
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "cover"),
        )

        val payload = payload(render, keys, generation)

        assertEquals(commands, render.commands)
        assertOperandIdentities(
            listOf(
                target,
                depthStencil,
                producerPipeline,
                coverPipeline,
                vertex,
                index,
                producerBindGroup,
                coverBindGroup,
            ),
            render.operands,
        )
        assertSame(render, payload.scopeOperands.single())
        assertEquals(keys, payload.scopeOperandKeys.single())
    }

    @Test
    fun `indexed core mixed layout groups pipelines by native identity and keeps bind groups in draw order`() {
        val generation = GPUDeviceGenerationID(7)
        val target = textureViewOperand("target", generation)
        val depthStencil = textureViewOperand("depth-stencil", generation)
        val directPipelineHandle = fakeNative<GPURenderPipeline>("direct")
        val firstDirectPipeline = GPUPreparedNativeRenderPipelineOperand(directPipelineHandle, generation)
        val repeatedDirectPipeline = GPUPreparedNativeRenderPipelineOperand(directPipelineHandle, generation)
        val producerPipeline = pipelineOperand("producer", generation)
        val coverPipeline = pipelineOperand("cover", generation)
        val vertex = bufferOperand("vertices", generation)
        val index = bufferOperand("indices", generation)
        val directFirstBindGroup = bindGroupOperand("direct-first", generation)
        val producerBindGroup = bindGroupOperand("producer", generation)
        val coverBindGroup = bindGroupOperand("cover", generation)
        val directLastBindGroup = bindGroupOperand("direct-last", generation)
        val commands = listOf(
            GPUPreparedNativeRenderCommand.SetPipeline(firstDirectPipeline),
            vertexCommand(vertex),
            indexCommand(index),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, directFirstBindGroup, listOf(0L)),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
            drawCommand(),
            GPUPreparedNativeRenderCommand.SetPipeline(producerPipeline),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, producerBindGroup, listOf(256L)),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
            GPUPreparedNativeRenderCommand.SetStencilReference(0u),
            drawCommand(),
            GPUPreparedNativeRenderCommand.SetPipeline(coverPipeline),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, coverBindGroup, listOf(512L)),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
            GPUPreparedNativeRenderCommand.SetStencilReference(0u),
            drawCommand(),
            GPUPreparedNativeRenderCommand.SetPipeline(repeatedDirectPipeline),
            GPUPreparedNativeRenderCommand.SetBindGroup(0, directLastBindGroup, listOf(768L)),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
            drawCommand(),
        )
        val render = GPUPreparedNativeScopeOperand.Render(
            sourceStepIndex = 2,
            pass = pathPass(target, depthStencil),
            commands = commands,
            operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
        )
        val keys = listOf(
            key(GPUPreparedNativeOperandRole.RenderColorTarget, GPUPreparedNativeOperandKind.TextureView, "target"),
            key(
                GPUPreparedNativeOperandRole.RenderDepthStencilTarget,
                GPUPreparedNativeOperandKind.TextureView,
                "depth-stencil",
            ),
            key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "direct"),
            key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "producer"),
            key(GPUPreparedNativeOperandRole.RenderPipeline, GPUPreparedNativeOperandKind.RenderPipeline, "cover"),
            key(GPUPreparedNativeOperandRole.RenderVertexBuffer, GPUPreparedNativeOperandKind.Buffer, "vertices"),
            key(GPUPreparedNativeOperandRole.RenderIndexBuffer, GPUPreparedNativeOperandKind.Buffer, "indices"),
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "direct-first"),
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "producer"),
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "cover"),
            key(GPUPreparedNativeOperandRole.RenderBindGroup, GPUPreparedNativeOperandKind.BindGroup, "direct-last"),
        )

        val payload = payload(render, keys, generation)

        assertEquals(commands, render.commands)
        assertOperandIdentities(
            listOf(
                target,
                depthStencil,
                firstDirectPipeline,
                producerPipeline,
                coverPipeline,
                vertex,
                index,
                directFirstBindGroup,
                producerBindGroup,
                coverBindGroup,
                directLastBindGroup,
            ),
            render.operands,
        )
        assertSame(render, payload.scopeOperands.single())
        assertEquals(keys, payload.scopeOperandKeys.single())
    }

    @Test
    fun `indexed core layout refuses ambiguous shared geometry commands`() {
        val generation = GPUDeviceGenerationID(7)
        val target = textureViewOperand("target", generation)
        val pipeline = pipelineOperand("pipeline", generation)
        val bindGroup = bindGroupOperand("bind-group", generation)
        val vertex = bufferOperand("vertices", generation)
        val otherVertex = bufferOperand("other-vertices", generation)
        val index = bufferOperand("indices", generation)
        val otherIndex = bufferOperand("other-indices", generation)

        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = 2,
                pass = GPUPreparedNativeRenderPassConfig(target),
                commands = listOf(
                    GPUPreparedNativeRenderCommand.SetPipeline(pipeline),
                    vertexCommand(vertex),
                    vertexCommand(otherVertex),
                    indexCommand(index),
                    GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroup),
                    GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
                    drawCommand(),
                ),
                operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = 2,
                pass = GPUPreparedNativeRenderPassConfig(target),
                commands = listOf(
                    GPUPreparedNativeRenderCommand.SetPipeline(pipeline),
                    vertexCommand(vertex),
                    indexCommand(index),
                    indexCommand(otherIndex),
                    GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroup),
                    GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
                    drawCommand(),
                ),
                operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
            )
        }
    }

    @Test
    fun `indexed core layout refuses ambiguous metadata for one pipeline native identity`() {
        val generation = GPUDeviceGenerationID(7)
        val target = textureViewOperand("target", generation)
        val pipelineHandle = fakeNative<GPURenderPipeline>("pipeline")
        val borrowedPipeline = GPUPreparedNativeRenderPipelineOperand(pipelineHandle, generation)
        val completionOwnedPipeline = GPUPreparedNativeRenderPipelineOperand(
            pipelineHandle,
            generation,
            GPUPreparedNativeOperandOwnership.PayloadOwnedCompletion,
        )
        val vertex = bufferOperand("vertices", generation)
        val index = bufferOperand("indices", generation)
        val bindGroup = bindGroupOperand("bind-group", generation)

        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeScopeOperand.Render(
                sourceStepIndex = 2,
                pass = GPUPreparedNativeRenderPassConfig(target),
                commands = listOf(
                    GPUPreparedNativeRenderCommand.SetPipeline(borrowedPipeline),
                    vertexCommand(vertex),
                    indexCommand(index),
                    GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroup),
                    GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
                    drawCommand(),
                    GPUPreparedNativeRenderCommand.SetPipeline(completionOwnedPipeline),
                    GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroup),
                    GPUPreparedNativeRenderCommand.SetScissor(0, 0, 8, 8),
                    drawCommand(),
                ),
                operandLayout = GPUPreparedNativeRenderOperandLayout.IndexedCorePrimitive,
            )
        }
    }

    private fun pathPass(
        target: GPUPreparedNativeTextureViewOperand,
        depthStencil: GPUPreparedNativeTextureViewOperand,
    ) = GPUPreparedNativeRenderPassConfig(
        colorTarget = target,
        depthStencilTarget = depthStencil,
        depthReadOnly = true,
        stencilLoadOperation = GPUPreparedNativeLoadOperation.Clear,
        stencilStoreOperation = GPUPreparedNativeStoreOperation.Discard,
        stencilClearValue = 0u,
        stencilReadOnly = false,
    )

    private fun payload(
        render: GPUPreparedNativeScopeOperand.Render,
        keys: List<GPUPreparedNativeOperandKey>,
        generation: GPUDeviceGenerationID,
    ): GPUPreparedNativeFramePayload {
        val scopeKey = GPUPreparedNativeScopeKey(
            sourceStepIndex = render.sourceStepIndex,
            operationKind = GPUEncoderOperationKind.Render,
            resourceGenerationLabels = listOf("GPUFrameTargetRef:target.scene@1"),
            operandKeys = keys,
        )
        return GPUPreparedNativeFramePayload(
            identity = GPUPreparedNativeFrameIdentity(
                frameId = GPUFrameID(32),
                contextIdentity = "target.scene",
                encoderPlanId = "frame.32",
                deviceGeneration = generation,
                targetGeneration = 1,
                scopes = listOf(scopeKey),
            ),
            scopeOperands = listOf(render),
            scopeOperandKeys = listOf(keys),
        )
    }

    private fun key(
        role: GPUPreparedNativeOperandRole,
        kind: GPUPreparedNativeOperandKind,
        binding: String,
    ) = GPUPreparedNativeOperandKey(role, kind, gpuPreparedNativeBindingKey(binding))

    private fun textureViewOperand(label: String, generation: GPUDeviceGenerationID) =
        GPUPreparedNativeTextureViewOperand(fakeNative<GPUTextureView>(label), generation)

    private fun pipelineOperand(label: String, generation: GPUDeviceGenerationID) =
        GPUPreparedNativeRenderPipelineOperand(fakeNative<GPURenderPipeline>(label), generation)

    private fun bindGroupOperand(label: String, generation: GPUDeviceGenerationID) =
        GPUPreparedNativeBindGroupOperand(fakeNative<GPUBindGroup>(label), generation)

    private fun bufferOperand(label: String, generation: GPUDeviceGenerationID) =
        GPUPreparedNativeBufferOperand(fakeNative<GPUBuffer>(label), generation, byteCapacity = 256L)

    private fun vertexCommand(buffer: GPUPreparedNativeBufferOperand) =
        GPUPreparedNativeRenderCommand.SetVertexBuffer(0, buffer, 0L, 256L, 8L)

    private fun indexCommand(buffer: GPUPreparedNativeBufferOperand) =
        GPUPreparedNativeRenderCommand.SetIndexBuffer(
            buffer,
            GPUPreparedNativeIndexFormat.Uint32,
            0L,
            256L,
        )

    private fun drawCommand() = GPUPreparedNativeRenderCommand.DrawIndexed(
        GPUPreparedNativeDrawCall.DrawIndexed(
            indexCount = 3,
            vertexCount = 4,
            maxLocalIndex = 2,
        ),
    )

    private fun assertOperandIdentities(
        expected: List<GPUPreparedNativeOperand>,
        actual: List<GPUPreparedNativeOperand>,
    ) {
        assertEquals(expected.size, actual.size)
        expected.zip(actual).forEachIndexed { index, (expectedOperand, actualOperand) ->
            assertSame(expectedOperand, actualOperand, "operand[$index]")
        }
    }
}

private inline fun <reified T> fakeNative(label: String): T = Proxy.newProxyInstance(
    T::class.java.classLoader,
    arrayOf(T::class.java),
) { _, method, _ ->
    when (method.name) {
        "getLabel" -> label
        "setLabel", "close" -> Unit
        "toString" -> "FakeNative($label)"
        else -> error("Unexpected fake native call: ${method.name}")
    }
} as T
