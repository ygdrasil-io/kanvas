package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUCommandEncoder
import io.ygdrasil.webgpu.GPURenderPassDescriptor
import io.ygdrasil.webgpu.GPURenderPassEncoder
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTextureView
import java.lang.reflect.Proxy
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import org.graphiks.kanvas.gpu.renderer.capabilities.GPUDeviceGenerationID

class GPUWgpu4kPreparedDepthStencilEncodingTest {
    @Test
    fun `prepared depth stencil target maps to exact wgpu4k attachment`() {
        val generation = GPUDeviceGenerationID(91)
        val colorView = proxiedTextureView("color-view")
        val depthStencilView = proxiedTextureView("depth-stencil-view")
        val pipeline = proxiedNative(GPURenderPipeline::class.java, "pipeline")
        val events = mutableListOf<String>()
        var capturedDescriptor: GPURenderPassDescriptor? = null
        val renderPass = proxiedNative(GPURenderPassEncoder::class.java, "render-pass") { method, arguments ->
            when {
                method == "setPipeline" -> events += "pipeline"
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
                GPUPreparedNativeRenderCommand.SetStencilReference(0x7fu),
                GPUPreparedNativeRenderCommand.Draw(GPUPreparedNativeDrawCall.Draw(3)),
            ),
        )

        val draws = encodeWgpu4kRenderPass(commandEncoder, render)
        val descriptor = requireNotNull(capturedDescriptor)
        val attachment = requireNotNull(descriptor.depthStencilAttachment)

        assertEquals(1, draws)
        assertEquals(listOf("begin", "pipeline", "stencil-reference", "draw", "end"), events)
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
