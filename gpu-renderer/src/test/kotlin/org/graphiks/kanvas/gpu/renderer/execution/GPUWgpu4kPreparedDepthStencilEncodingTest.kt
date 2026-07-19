package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPURenderPassDescriptor
import io.ygdrasil.webgpu.GPURenderPassEncoder
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTextureView
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID

class GPUWgpu4kPreparedDepthStencilEncodingTest {
    @Test
    fun `one encoder retains one writable stencil producer then read only consumers on the same view`() {
        val generation = GPUDeviceGenerationID(90)
        val colorView = proxiedTextureView("clip-color-view")
        val depthStencilView = proxiedTextureView("shared-clip-depth-stencil-view")
        val producerPipeline = proxiedNative(GPURenderPipeline::class.java, "clip-producer-pipeline")
        val consumerPipeline = proxiedNative(GPURenderPipeline::class.java, "clip-consumer-pipeline")
        val consumerBindGroup = proxiedNative(GPUBindGroup::class.java, "clip-consumer-bind-group")
        val vertexBuffer = proxiedNative(GPUBuffer::class.java, "shared-clip-vertices")
        val indexBuffer = proxiedNative(GPUBuffer::class.java, "shared-clip-indices")
        val descriptors = mutableListOf<GPURenderPassDescriptor>()
        val events = mutableListOf<String>()
        val passLabels = listOf("producer", "consumer-0", "consumer-1")
        val expectedPipelines = listOf(producerPipeline, consumerPipeline, consumerPipeline)
        val expectedDynamicOffsets = listOf<List<UInt>?>(null, listOf(256u), listOf(512u))
        val renderPasses = passLabels.indices.map { passIndex ->
            proxiedNative(GPURenderPassEncoder::class.java, passLabels[passIndex]) { method, arguments ->
                when {
                    method == "setPipeline" -> {
                        assertSame(expectedPipelines[passIndex], arguments.single())
                        events += "${passLabels[passIndex]}.pipeline"
                    }
                    method.startsWith("setStencilReference") -> {
                        assertEquals(0, arguments.single())
                        events += "${passLabels[passIndex]}.stencil-reference"
                    }
                    method.startsWith("setBindGroup") -> {
                        val expectedOffsets = requireNotNull(expectedDynamicOffsets[passIndex]) {
                            "The stencil producer must not encode a bind group"
                        }
                        assertEquals(0, arguments[0])
                        assertSame(consumerBindGroup, arguments[1])
                        assertEquals(expectedOffsets, arguments[2])
                        events += "${passLabels[passIndex]}.bind-group"
                    }
                    method.startsWith("setVertexBuffer") -> {
                        assertEquals(0, arguments[0])
                        assertSame(vertexBuffer, arguments[1])
                        assertEquals(0L, arguments[2])
                        assertEquals(32uL, arguments[3])
                        events += "${passLabels[passIndex]}.vertex-buffer"
                    }
                    method.startsWith("setIndexBuffer") -> {
                        assertSame(indexBuffer, arguments[0])
                        assertEquals(0L, arguments[2])
                        assertEquals(12uL, arguments[3])
                        events += "${passLabels[passIndex]}.index-buffer"
                    }
                    method.startsWith("setScissorRect") -> {
                        assertEquals(listOf(1, 2, 13, 14), arguments)
                        events += "${passLabels[passIndex]}.scissor"
                    }
                    method.startsWith("drawIndexed") -> {
                        assertEquals(listOf(3, 1, 0, 0, 0), arguments)
                        events += "${passLabels[passIndex]}.draw-indexed"
                    }
                    method == "end" -> events += "${passLabels[passIndex]}.end"
                    else -> error("Unexpected ${passLabels[passIndex]} render-pass call: $method")
                }
                Unit
            }
        }
        var nextPass = 0
        val commandEncoder = proxiedNative(
            GPUCommandEncoder::class.java,
            "one-clip-command-encoder",
        ) { method, arguments ->
            when (method) {
                "beginRenderPass" -> {
                    descriptors += arguments.single() as GPURenderPassDescriptor
                    val passIndex = nextPass++
                    events += "${passLabels[passIndex]}.begin"
                    renderPasses[passIndex]
                }
                else -> error("Unexpected command-encoder call: $method")
            }
        }
        val colorTarget = GPUPreparedNativeTextureViewOperand(colorView, generation)
        val depthStencilTarget = GPUPreparedNativeTextureViewOperand(depthStencilView, generation)
        val sharedVertexBuffer = GPUPreparedNativeBufferOperand(vertexBuffer, generation, byteCapacity = 32L)
        val sharedIndexBuffer = GPUPreparedNativeBufferOperand(indexBuffer, generation, byteCapacity = 12L)
        noBindingsPipelineFixture(producerPipeline, generation).use { producer ->
            val renders = listOf(
                indexedStencilRender(
                    sourceStepIndex = 0,
                    colorTarget = colorTarget,
                    depthStencilTarget = depthStencilTarget,
                    pipeline = producer.operand,
                    bindGroup = null,
                    dynamicOffset = null,
                    vertexBuffer = sharedVertexBuffer,
                    indexBuffer = sharedIndexBuffer,
                    writableStencilProducer = true,
                ),
                indexedStencilRender(
                    sourceStepIndex = 1,
                    colorTarget = colorTarget,
                    depthStencilTarget = depthStencilTarget,
                    pipeline = GPUPreparedNativeRenderPipelineOperand(consumerPipeline, generation),
                    bindGroup = GPUPreparedNativeBindGroupOperand(consumerBindGroup, generation),
                    dynamicOffset = 256L,
                    vertexBuffer = sharedVertexBuffer,
                    indexBuffer = sharedIndexBuffer,
                    writableStencilProducer = false,
                ),
                indexedStencilRender(
                    sourceStepIndex = 2,
                    colorTarget = colorTarget,
                    depthStencilTarget = depthStencilTarget,
                    pipeline = GPUPreparedNativeRenderPipelineOperand(consumerPipeline, generation),
                    bindGroup = GPUPreparedNativeBindGroupOperand(consumerBindGroup, generation),
                    dynamicOffset = 512L,
                    vertexBuffer = sharedVertexBuffer,
                    indexBuffer = sharedIndexBuffer,
                    writableStencilProducer = false,
                ),
            )

            assertEquals(listOf(1, 1, 1), renders.map { render ->
                encodeWgpu4kRenderPass(commandEncoder, render)
            })
        }

        assertEquals(3, nextPass)
        assertEquals(3, descriptors.size)
        descriptors.forEach { descriptor ->
            val attachment = requireNotNull(descriptor.depthStencilAttachment)
            assertSame(depthStencilView, attachment.view)
            assertNull(attachment.depthClearValue)
            assertNull(attachment.depthLoadOp)
            assertNull(attachment.depthStoreOp)
            assertTrue(attachment.depthReadOnly)
        }
        with(requireNotNull(descriptors[0].depthStencilAttachment)) {
            assertEquals(0u, stencilClearValue)
            assertEquals(GPULoadOp.Clear, stencilLoadOp)
            assertEquals(GPUStoreOp.Store, stencilStoreOp)
            assertEquals(false, stencilReadOnly)
        }
        descriptors.drop(1).forEach { descriptor ->
            with(requireNotNull(descriptor.depthStencilAttachment)) {
                assertEquals(0u, stencilClearValue)
                assertNull(stencilLoadOp)
                assertNull(stencilStoreOp)
                assertTrue(stencilReadOnly)
            }
        }
        assertEquals(
            listOf(
                "producer.begin",
                "producer.pipeline",
                "producer.stencil-reference",
                "producer.vertex-buffer",
                "producer.index-buffer",
                "producer.scissor",
                "producer.draw-indexed",
                "producer.end",
                "consumer-0.begin",
                "consumer-0.pipeline",
                "consumer-0.stencil-reference",
                "consumer-0.bind-group",
                "consumer-0.vertex-buffer",
                "consumer-0.index-buffer",
                "consumer-0.scissor",
                "consumer-0.draw-indexed",
                "consumer-0.end",
                "consumer-1.begin",
                "consumer-1.pipeline",
                "consumer-1.stencil-reference",
                "consumer-1.bind-group",
                "consumer-1.vertex-buffer",
                "consumer-1.index-buffer",
                "consumer-1.scissor",
                "consumer-1.draw-indexed",
                "consumer-1.end",
            ),
            events,
        )
    }

    @Test
    fun `prepared depth stencil target maps to exact wgpu4k attachment`() {
        val generation = GPUDeviceGenerationID(91)
        val colorView = proxiedTextureView("color-view")
        val depthStencilView = proxiedTextureView("depth-stencil-view")
        val pipeline = proxiedNative(GPURenderPipeline::class.java, "pipeline")
        val bindGroup = proxiedNative(GPUBindGroup::class.java, "bind-group")
        val events = mutableListOf<String>()
        var capturedDescriptor: GPURenderPassDescriptor? = null
        val renderPass = proxiedNative(GPURenderPassEncoder::class.java, "render-pass") { method, arguments ->
            when {
                method == "setPipeline" -> events += "pipeline"
                method.startsWith("setBindGroup") -> events += "bind-group"
                method.startsWith("setStencilReference") -> {
                    assertEquals(0x7f, arguments.single())
                    events += "stencil-reference"
                }
                method.startsWith("draw") -> events += "draw"
                method == "end" -> events += "end"
                else -> error("Unexpected render-pass call: $method")
            }
            Unit
        }
        val commandEncoder = proxiedNative(GPUCommandEncoder::class.java, "command-encoder") { method, arguments ->
            when (method) {
                "beginRenderPass" -> {
                    capturedDescriptor = arguments.single() as GPURenderPassDescriptor
                    events += "begin"
                    renderPass
                }
                else -> error("Unexpected command-encoder call: $method")
            }
        }
        val pass = GPUPreparedNativeRenderPassConfig(
            colorTarget = GPUPreparedNativeTextureViewOperand(colorView, generation),
            depthStencilTarget = GPUPreparedNativeTextureViewOperand(depthStencilView, generation),
            depthReadOnly = true,
            stencilLoadOperation = GPUPreparedNativeLoadOperation.Clear,
            stencilStoreOperation = GPUPreparedNativeStoreOperation.Store,
            stencilClearValue = 0x7fu,
            stencilReadOnly = false,
        )
        val render = GPUPreparedNativeScopeOperand.Render(
            sourceStepIndex = 0,
            pass = pass,
            commands = listOf(
                GPUPreparedNativeRenderCommand.SetPipeline(
                    GPUPreparedNativeRenderPipelineOperand(pipeline, generation),
                ),
                GPUPreparedNativeRenderCommand.SetBindGroup(
                    0,
                    GPUPreparedNativeBindGroupOperand(bindGroup, generation),
                ),
                GPUPreparedNativeRenderCommand.SetStencilReference(0x7fu),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3)),
            ),
        )

        val draws = encodeWgpu4kRenderPass(commandEncoder, render)
        val descriptor = requireNotNull(capturedDescriptor)
        val attachment = requireNotNull(descriptor.depthStencilAttachment)

        assertEquals(1, draws)
        assertEquals(listOf("begin", "pipeline", "bind-group", "stencil-reference", "draw", "end"), events)
        assertSame(depthStencilView, attachment.view)
        assertNull(attachment.depthClearValue)
        assertNull(attachment.depthLoadOp)
        assertNull(attachment.depthStoreOp)
        assertTrue(attachment.depthReadOnly)
        assertEquals(0x7fu, attachment.stencilClearValue)
        assertEquals(GPULoadOp.Clear, attachment.stencilLoadOp)
        assertEquals(GPUStoreOp.Store, attachment.stencilStoreOp)
        assertEquals(false, attachment.stencilReadOnly)
    }

    @Test
    fun `prepared color only pass omits wgpu4k depth stencil attachment`() {
        val generation = GPUDeviceGenerationID(92)
        val pass = GPUPreparedNativeRenderPassConfig(
            colorTarget = GPUPreparedNativeTextureViewOperand(proxiedTextureView("color-only"), generation),
        )

        assertNull(buildWgpu4kRenderPassDescriptor(pass).depthStencilAttachment)
    }

    @Test
    fun `render callbacks retain successful native calls when a later draw or end fails`() {
        PartialFailure.entries.forEach { failurePoint ->
            var renderPasses = 0
            var draws = 0
            var drawIndexed = 0
            var nativeDrawIndexed = 0
            val renderPass = proxiedNative(GPURenderPassEncoder::class.java, "partial-${failurePoint.name}") {
                    method, _ ->
                when {
                    method == "setPipeline" || method.startsWith("setBindGroup") ||
                        method.startsWith("setVertexBuffer") || method.startsWith("setIndexBuffer") ||
                        method.startsWith("setScissorRect") -> Unit
                    method.startsWith("drawIndexed") -> {
                        nativeDrawIndexed += 1
                        if (failurePoint == PartialFailure.SecondDraw && nativeDrawIndexed == 2) {
                            error("second drawIndexed failed")
                        }
                    }
                    method == "end" -> {
                        if (failurePoint == PartialFailure.End) error("render pass end failed")
                    }
                    else -> error("Unexpected render-pass call: $method")
                }
            }
            val commandEncoder = proxiedNative(GPUCommandEncoder::class.java, "partial-encoder") { method, _ ->
                when (method) {
                    "beginRenderPass" -> renderPass
                    else -> error("Unexpected command-encoder call: $method")
                }
            }

            assertFailsWith<IllegalStateException> {
                encodeWgpu4kRenderPass(
                    commandEncoder,
                    indexedRender(if (failurePoint == PartialFailure.SecondDraw) 2 else 1),
                    onRenderPassBegan = { renderPasses += 1 },
                    onDrawEncoded = { draws += 1 },
                    onDrawIndexedEncoded = { drawIndexed += 1 },
                )
            }

            assertEquals(1, renderPasses, failurePoint.name)
            assertEquals(1, draws, failurePoint.name)
            assertEquals(1, drawIndexed, failurePoint.name)
        }
    }

    @Test
    fun `prepared depth stencil validation rejects impossible native states`() {
        val generation = GPUDeviceGenerationID(93)
        val color = GPUPreparedNativeTextureViewOperand(proxiedTextureView("validation-color"), generation)
        val depthStencil = GPUPreparedNativeTextureViewOperand(
            proxiedTextureView("validation-depth-stencil"),
            generation,
        )
        val invalidConfigurations = listOf<() -> Unit>(
            {
                GPUPreparedNativeRenderPassConfig(
                    colorTarget = color,
                    depthStencilTarget = depthStencil,
                    depthReadOnly = false,
                    depthLoadOperation = GPUPreparedNativeLoadOperation.Clear,
                    depthStoreOperation = GPUPreparedNativeStoreOperation.Store,
                )
            },
            {
                GPUPreparedNativeRenderPassConfig(
                    colorTarget = color,
                    depthStencilTarget = depthStencil,
                    depthReadOnly = false,
                    depthLoadOperation = GPUPreparedNativeLoadOperation.Load,
                    depthStoreOperation = GPUPreparedNativeStoreOperation.Store,
                    depthClearValue = 0.5f,
                )
            },
            {
                GPUPreparedNativeRenderPassConfig(
                    colorTarget = color,
                    stencilLoadOperation = GPUPreparedNativeLoadOperation.Load,
                    stencilStoreOperation = GPUPreparedNativeStoreOperation.Store,
                    stencilReadOnly = false,
                )
            },
            {
                GPUPreparedNativeRenderPassConfig(
                    colorTarget = color,
                    depthStencilTarget = depthStencil,
                    depthReadOnly = true,
                    depthLoadOperation = GPUPreparedNativeLoadOperation.Load,
                    depthStoreOperation = GPUPreparedNativeStoreOperation.Store,
                )
            },
            {
                GPUPreparedNativeRenderPassConfig(
                    colorTarget = color,
                    depthStencilTarget = depthStencil,
                    depthReadOnly = false,
                    depthLoadOperation = GPUPreparedNativeLoadOperation.Clear,
                    depthStoreOperation = GPUPreparedNativeStoreOperation.Store,
                    depthClearValue = 1.1f,
                )
            },
            {
                GPUPreparedNativeRenderPassConfig(
                    colorTarget = color,
                    depthStencilTarget = depthStencil,
                    stencilLoadOperation = GPUPreparedNativeLoadOperation.Clear,
                    stencilStoreOperation = GPUPreparedNativeStoreOperation.Store,
                    stencilClearValue = 0x100u,
                    stencilReadOnly = false,
                )
            },
            { GPUPreparedNativeRenderCommand.SetStencilReference(0x100u) },
        )

        invalidConfigurations.forEach { invalid ->
            assertFailsWith<IllegalArgumentException> { invalid() }
        }
    }

    private enum class PartialFailure {
        SecondDraw,
        End,
    }

    private fun indexedStencilRender(
        sourceStepIndex: Int,
        colorTarget: GPUPreparedNativeTextureViewOperand,
        depthStencilTarget: GPUPreparedNativeTextureViewOperand,
        pipeline: GPUPreparedNativeRenderPipelineOperand,
        bindGroup: GPUPreparedNativeBindGroupOperand?,
        dynamicOffset: Long?,
        vertexBuffer: GPUPreparedNativeBufferOperand,
        indexBuffer: GPUPreparedNativeBufferOperand,
        writableStencilProducer: Boolean,
    ) = GPUPreparedNativeScopeOperand.Render(
        sourceStepIndex = sourceStepIndex,
        pass = GPUPreparedNativeRenderPassConfig(
            colorTarget = colorTarget,
            depthStencilTarget = depthStencilTarget,
            stencilLoadOperation = if (writableStencilProducer) GPUPreparedNativeLoadOperation.Clear else null,
            stencilStoreOperation = if (writableStencilProducer) GPUPreparedNativeStoreOperation.Store else null,
            stencilClearValue = if (writableStencilProducer) 0u else null,
            stencilReadOnly = !writableStencilProducer,
        ),
        commands = buildList {
            add(GPUPreparedNativeRenderCommand.SetPipeline(pipeline))
            add(GPUPreparedNativeRenderCommand.SetStencilReference(0u))
            if (bindGroup != null) {
                add(
                    GPUPreparedNativeRenderCommand.SetBindGroup(
                        0,
                        bindGroup,
                        listOf(requireNotNull(dynamicOffset)),
                    ),
                )
            } else {
                require(dynamicOffset == null)
            }
            add(GPUPreparedNativeRenderCommand.SetVertexBuffer(0, vertexBuffer, 0L, 32L, 8L))
            add(
                GPUPreparedNativeRenderCommand.SetIndexBuffer(
                    indexBuffer,
                    GPUPreparedNativeIndexFormat.Uint32,
                    0L,
                    12L,
                ),
            )
            add(GPUPreparedNativeRenderCommand.SetScissor(1, 2, 13, 14))
            add(
                GPUPreparedNativeRenderCommand.DrawIndexed(
                    GPUPreparedNativeDrawCall.DrawIndexed(
                        indexCount = 3,
                        vertexCount = 4,
                        maxLocalIndex = 2,
                    ),
                ),
            )
        },
    )

    private fun noBindingsPipelineFixture(
        pipeline: GPURenderPipeline,
        generation: GPUDeviceGenerationID,
    ): NoBindingsPipelineFixture {
        val nativeFactory = object : GPUWgpu4kCorePrimitiveSessionNativeFactory {
            override fun acceptsPipelineIdentity(identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity) = true

            override fun createBindGroupLayout(
                componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
            ) = proxiedNative(GPUBindGroupLayout::class.java, "clip-producer-bind-group-layout")

            override fun createShaderModule(
                componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
                plan: GPUCorePrimitiveNativeShaderPlan,
            ) = proxiedNative(GPUShaderModule::class.java, "clip-producer-shader")

            override fun createPipelineLayout(
                componentIdentity: GPUWgpu4kCorePrimitiveComponentIdentity,
                bindGroupLayout: GPUBindGroupLayout,
            ) = proxiedNative(GPUPipelineLayout::class.java, "clip-producer-pipeline-layout")

            override fun createRenderPipeline(
                identity: GPUWgpu4kCorePrimitiveRenderPipelineIdentity,
                shader: GPUShaderModule,
                pipelineLayout: GPUPipelineLayout,
            ) = pipeline
        }
        val cache = GPUWgpu4kCorePrimitiveSessionCache(
            proxiedNative(GPUDevice::class.java, "clip-producer-device"),
            generation,
            nativeFactory,
        )
        val acquired = assertIs<GPUWgpu4kCorePrimitiveSessionCacheAcquire.Acquired>(
            cache.acquire(
                GPUWgpu4kCorePrimitivePipelineCacheKey(
                    componentIdentity = PRODUCTION_CORE_PRIMITIVE_CLIP_STENCIL_PRODUCER_COMPONENT_IDENTITY,
                    pipelineIdentity = GPUWgpu4kCorePrimitiveRenderPipelineIdentity(
                        targetFormat = "rgba8unorm",
                        sampleCount = 1,
                        topology = "triangle-list",
                        frontFace = "ccw",
                        cullMode = "none",
                        program = GPUWgpu4kCorePrimitivePipelineProgram.ClipStencilProducerWinding,
                    ),
                ),
            ),
        )
        return NoBindingsPipelineFixture(
            GPUPreparedNativeRenderPipelineOperand.fromCorePrimitiveAcquisition(acquired, generation),
            cache,
        )
    }

    private class NoBindingsPipelineFixture(
        val operand: GPUPreparedNativeRenderPipelineOperand,
        private val cache: GPUWgpu4kCorePrimitiveSessionCache,
    ) : AutoCloseable {
        override fun close() = cache.close()
    }

    private fun indexedRender(drawCount: Int): GPUPreparedNativeScopeOperand.Render {
        val generation = GPUDeviceGenerationID(94)
        val pipeline = GPUPreparedNativeRenderPipelineOperand(
            proxiedNative(GPURenderPipeline::class.java, "indexed-pipeline"),
            generation,
        )
        val bindGroup = GPUPreparedNativeBindGroupOperand(
            proxiedNative(GPUBindGroup::class.java, "indexed-bind-group"),
            generation,
        )
        val vertex = GPUPreparedNativeBufferOperand(
            proxiedNative(GPUBuffer::class.java, "indexed-vertex"),
            generation,
            byteCapacity = 8L,
        )
        val index = GPUPreparedNativeBufferOperand(
            proxiedNative(GPUBuffer::class.java, "indexed-index"),
            generation,
            byteCapacity = 4L,
        )
        return GPUPreparedNativeScopeOperand.Render(
            sourceStepIndex = 0,
            pass = GPUPreparedNativeRenderPassConfig(
                colorTarget = GPUPreparedNativeTextureViewOperand(
                    proxiedTextureView("indexed-color"),
                    generation,
                ),
            ),
            commands = buildList {
                add(GPUPreparedNativeRenderCommand.SetPipeline(pipeline))
                add(GPUPreparedNativeRenderCommand.SetBindGroup(0, bindGroup, emptyList()))
                add(GPUPreparedNativeRenderCommand.SetVertexBuffer(0, vertex, 0L, 8L, 8L))
                add(
                    GPUPreparedNativeRenderCommand.SetIndexBuffer(
                        index,
                        GPUPreparedNativeIndexFormat.Uint32,
                        0L,
                        4L,
                    ),
                )
                add(GPUPreparedNativeRenderCommand.SetScissor(0, 0, 1, 1))
                repeat(drawCount) {
                    add(
                        GPUPreparedNativeRenderCommand.DrawIndexed(
                            GPUPreparedNativeDrawCall.DrawIndexed(
                                indexCount = 1,
                                vertexCount = 1,
                                maxLocalIndex = 0,
                            ),
                        ),
                    )
                }
            },
        )
    }

    private fun proxiedTextureView(label: String): GPUTextureView = GPUTextureView::class.java.cast(
        Proxy.newProxyInstance(
            GPUTextureView::class.java.classLoader,
            arrayOf(GPUTextureView::class.java),
        ) { proxy, method, arguments ->
            when (method.name) {
                "equals" -> proxy === arguments?.singleOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "getLabel" -> label
                "setLabel", "close" -> Unit
                "toString" -> "ProxiedTextureView($label)"
                else -> error("Unexpected proxied texture-view call: ${method.name}")
            }
        },
    )

    private fun <T : Any> proxiedNative(
        type: Class<T>,
        label: String,
        call: (String, List<Any?>) -> Any? = { method, _ -> error("Unexpected $label call: $method") },
    ): T = type.cast(
        Proxy.newProxyInstance(type.classLoader, arrayOf(type)) { proxy, method, arguments ->
            when (method.name) {
                "equals" -> proxy === arguments?.singleOrNull()
                "hashCode" -> System.identityHashCode(proxy)
                "getLabel" -> label
                "setLabel", "close" -> Unit
                "toString" -> "ProxiedNative($label)"
                else -> call(method.name, arguments?.toList().orEmpty())
            }
        },
    )
}
