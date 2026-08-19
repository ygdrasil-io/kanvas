package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUTextureView
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID

class GPUWgpu4kRenderCommandOffsetsTest {
    @Test
    fun `encoder forwards real buffer slices and complete indexed draw parameters`() {
        val generation = GPUDeviceGenerationID(17)
        val vertex = fakeNative<GPUBuffer>("vertex.shared")
        val index = fakeNative<GPUBuffer>("index.shared")
        val events = mutableListOf<String>()
        val commands = listOf(
            GPUPreparedNativeRenderCommand.SetPipeline(
                GPUPreparedNativeRenderPipelineOperand(fakeNative<GPURenderPipeline>("pipeline"), generation),
            ),
            GPUPreparedNativeRenderCommand.SetBindGroup(
                0,
                GPUPreparedNativeBindGroupOperand(fakeNative<GPUBindGroup>("bind"), generation),
                dynamicOffsets = listOf(256L),
            ),
            GPUPreparedNativeRenderCommand.SetVertexBuffer(
                0,
                GPUPreparedNativeBufferOperand(vertex, generation),
                offset = 0,
                size = 56,
            ),
            GPUPreparedNativeRenderCommand.SetIndexBuffer(
                GPUPreparedNativeBufferOperand(index, generation),
                GPUPreparedNativeIndexFormat.Uint32,
                offset = 0,
                size = 36,
            ),
            GPUPreparedNativeRenderCommand.SetScissor(3, 5, 7, 9),
            GPUPreparedNativeRenderCommand.DrawIndexed(
                GPUPreparedNativeDrawCall.DrawIndexed(
                    indexCount = 3,
                    firstIndex = 6,
                    baseVertex = 4,
                ),
            ),
        )

        val draws = encodeWgpu4kRenderCommands(
            commands,
            GPUWgpu4kRenderCommandActions(
                setPipeline = { events += "pipeline" },
                setStencilReference = { events += "stencil:$it" },
                setBindGroup = { slot, _, offsets -> events += "bind:$slot:${offsets.joinToString(",")}" },
                setVertexBuffer = { slot, _, offset, size -> events += "vertex:$slot:$offset:$size" },
                setIndexBuffer = { _, format, offset, size -> events += "index:$format:$offset:$size" },
                setScissor = { x, y, width, height -> events += "scissor:$x:$y:$width:$height" },
                draw = { _, _, _, _ -> events += "draw" },
                drawIndexed = { count, instances, firstIndex, baseVertex, firstInstance ->
                    events += "indexed:$count:$instances:$firstIndex:$baseVertex:$firstInstance"
                },
            ),
        )

        assertEquals(1, draws)
        assertEquals(
            listOf(
                "pipeline",
                "bind:0:256",
                "vertex:0:0:56",
                "index:Uint32:0:36",
                "scissor:3:5:7:9",
                "indexed:3:1:6:4:0",
            ),
            events,
        )
    }

    @Test
    fun `buffer bindings reject slices beyond their declared capacity`() {
        val generation = GPUDeviceGenerationID(18)
        val vertex = GPUPreparedNativeBufferOperand(
            fakeNative<GPUBuffer>("vertex"),
            generation,
            byteCapacity = 32L,
        )
        val index = GPUPreparedNativeBufferOperand(
            fakeNative<GPUBuffer>("index"),
            generation,
            byteCapacity = 24L,
        )

        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeRenderCommand.SetVertexBuffer(0, vertex, 16L, 24L, 8L)
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeRenderCommand.SetIndexBuffer(
                index,
                GPUPreparedNativeIndexFormat.Uint32,
                16L,
                12L,
            )
        }
    }

    @Test
    fun `indexed draw validates cumulative offsets with exact local maximum authority`() {
        val generation = GPUDeviceGenerationID(19)
        val vertex = GPUPreparedNativeBufferOperand(
            fakeNative<GPUBuffer>("vertex.shared"),
            generation,
            byteCapacity = 56L,
        )
        val index = GPUPreparedNativeBufferOperand(
            fakeNative<GPUBuffer>("index.shared"),
            generation,
            byteCapacity = 36L,
        )
        val prefix = listOf(
            GPUPreparedNativeRenderCommand.SetPipeline(
                GPUPreparedNativeRenderPipelineOperand(fakeNative<GPURenderPipeline>("pipeline"), generation),
            ),
            GPUPreparedNativeRenderCommand.SetBindGroup(
                0,
                GPUPreparedNativeBindGroupOperand(fakeNative<GPUBindGroup>("bind"), generation),
            ),
            GPUPreparedNativeRenderCommand.SetVertexBuffer(0, vertex, 0L, 56L, 8L),
            GPUPreparedNativeRenderCommand.SetIndexBuffer(
                index,
                GPUPreparedNativeIndexFormat.Uint32,
                0L,
                36L,
            ),
            GPUPreparedNativeRenderCommand.SetScissor(0, 0, 4, 4),
        )
        fun render(draw: GPUPreparedNativeDrawCall.DrawIndexed) = GPUPreparedNativeScopeOperand.Render(
            sourceStepIndex = 1,
            pass = GPUPreparedNativeRenderPassConfig(
                GPUPreparedNativeTextureViewOperand(fakeNative<GPUTextureView>("target"), generation),
            ),
            commands = prefix + GPUPreparedNativeRenderCommand.DrawIndexed(draw),
        )

        render(
            GPUPreparedNativeDrawCall.DrawIndexed(
                indexCount = 3,
                firstIndex = 6,
                baseVertex = 4,
                vertexCount = 3,
                maxLocalIndex = 2,
            ),
        )
        assertFailsWith<IllegalArgumentException> {
            render(
                GPUPreparedNativeDrawCall.DrawIndexed(
                    indexCount = 3,
                    firstIndex = 7,
                    baseVertex = 4,
                    vertexCount = 3,
                    maxLocalIndex = 2,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            render(GPUPreparedNativeDrawCall.DrawIndexed(indexCount = 3, firstIndex = Int.MAX_VALUE))
        }
        assertFailsWith<IllegalArgumentException> {
            render(GPUPreparedNativeDrawCall.DrawIndexed(indexCount = 1, baseVertex = 3))
        }
        assertFailsWith<IllegalArgumentException> {
            render(
                GPUPreparedNativeDrawCall.DrawIndexed(
                    indexCount = 3,
                    firstIndex = 6,
                    baseVertex = 5,
                    vertexCount = 3,
                    maxLocalIndex = 2,
                ),
            )
        }
        assertFailsWith<IllegalArgumentException> {
            GPUPreparedNativeDrawCall.DrawIndexed(
                indexCount = 3,
                firstIndex = 6,
                baseVertex = 4,
                vertexCount = 3,
                maxLocalIndex = 3,
            )
        }
    }

    @Suppress("UNCHECKED_CAST")
    private inline fun <reified T> fakeNative(label: String): T = Proxy.newProxyInstance(
        T::class.java.classLoader,
        arrayOf(T::class.java),
    ) { proxy, method, args ->
        when (method.name) {
            "toString" -> label
            "hashCode" -> System.identityHashCode(proxy)
            "equals" -> proxy === args?.firstOrNull()
            "close" -> Unit
            else -> null
        }
    } as T
}
