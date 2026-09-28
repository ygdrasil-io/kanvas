package org.graphiks.kanvas.gpu.renderer.execution

import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Color
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUErrorFilter
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.MultisampleState
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDepthStencilAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.StencilFaceState
import io.ygdrasil.webgpu.TexelCopyBufferInfo
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.VertexState
import io.ygdrasil.webgpu.beginRenderPass
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout

private const val SRGB_PROBE_ROW_BYTES = 256
private const val SRGB_PROBE_TIMEOUT_MS = 20_000L

/** A session-local, fail-closed native observation, never an adapter-name inference. */
internal fun probeSrgb4xResolveSupport(device: GPUDevice): Boolean = runBlocking {
    probeSrgb4xResolveCase(device, withDepthStencil = false) &&
        probeSrgb4xResolveCase(device, withDepthStencil = true)
}

private suspend fun probeSrgb4xResolveCase(device: GPUDevice, withDepthStencil: Boolean): Boolean {
    val label = if (withDepthStencil) "kanvas-srgb4x-depth-probe" else "kanvas-srgb4x-color-probe"
    val owned = mutableListOf<AutoCloseable>()
    var scopeOpen = false
    try {
        device.pushErrorScope(GPUErrorFilter.Validation)
        scopeOpen = true

        val multisample = device.createTexture(TextureDescriptor(
            size = Extent3D(1u, 1u),
            format = GPUTextureFormat.RGBA8UnormSrgb,
            usage = GPUTextureUsage.RenderAttachment,
            sampleCount = 4u,
            label = "$label.msaa",
        )).also(owned::add)
        val resolved = device.createTexture(TextureDescriptor(
            size = Extent3D(1u, 1u),
            format = GPUTextureFormat.RGBA8UnormSrgb,
            usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding or GPUTextureUsage.CopySrc,
            label = "$label.resolved",
        )).also(owned::add)
        val depth = if (withDepthStencil) device.createTexture(TextureDescriptor(
            size = Extent3D(1u, 1u),
            format = GPUTextureFormat.Depth24PlusStencil8,
            usage = GPUTextureUsage.RenderAttachment,
            sampleCount = 4u,
            label = "$label.depth",
        )).also(owned::add) else null
        val colorView = multisample.createView().also(owned::add)
        val resolveView = resolved.createView().also(owned::add)
        val depthView = depth?.createView()?.also(owned::add)
        val shader = device.createShaderModule(ShaderModuleDescriptor(
            label = "$label.shader",
            code = """
                @vertex fn vs_main(@builtin(vertex_index) vertex: u32) -> @builtin(position) vec4f {
                    let x = f32((vertex << 1u) & 2u) * 2.0 - 1.0;
                    let y = f32(vertex & 2u) * 2.0 - 1.0;
                    return vec4f(x, y, 0.0, 1.0);
                }
                @fragment fn fs_main() -> @location(0) vec4f {
                    return vec4f(1.0, 0.0, 0.0, 1.0);
                }
            """.trimIndent(),
        )).also(owned::add)
        val layout = device.createPipelineLayout(PipelineLayoutDescriptor(
            label = "$label.layout",
            bindGroupLayouts = emptyList(),
        )).also(owned::add)
        val pipeline = device.createRenderPipeline(RenderPipelineDescriptor(
            label = "$label.pipeline",
            layout = layout,
            vertex = VertexState(module = shader, entryPoint = "vs_main"),
            primitive = PrimitiveState(),
            depthStencil = if (withDepthStencil) DepthStencilState(
                format = GPUTextureFormat.Depth24PlusStencil8,
                depthWriteEnabled = false,
                depthCompare = GPUCompareFunction.Always,
                stencilFront = StencilFaceState(compare = GPUCompareFunction.Always),
                stencilBack = StencilFaceState(compare = GPUCompareFunction.Always),
            ) else null,
            multisample = MultisampleState(count = 4u),
            fragment = FragmentState(
                module = shader,
                entryPoint = "fs_main",
                targets = listOf(ColorTargetState(format = GPUTextureFormat.RGBA8UnormSrgb)),
            ),
        )).also(owned::add)
        val readback = device.createBuffer(BufferDescriptor(
            size = SRGB_PROBE_ROW_BYTES.toULong(),
            usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
            mappedAtCreation = false,
            label = "$label.readback",
        )).also(owned::add)
        val encoder = device.createCommandEncoder().also(owned::add)
        encoder.beginRenderPass(RenderPassDescriptor(
            colorAttachments = listOf(RenderPassColorAttachment(
                view = colorView,
                resolveTarget = resolveView,
                loadOp = GPULoadOp.Clear,
                clearValue = Color(0.0, 0.0, 0.0, 0.0),
                storeOp = GPUStoreOp.Store,
            )),
            depthStencilAttachment = depthView?.let { RenderPassDepthStencilAttachment(
                view = it,
                stencilClearValue = 0u,
                stencilLoadOp = GPULoadOp.Clear,
                stencilStoreOp = GPUStoreOp.Store,
                stencilReadOnly = false,
                depthReadOnly = true,
            ) },
        )) {
            setPipeline(pipeline)
            draw(3u, 1u, 0u, 0u)
            end()
        }
        encoder.copyTextureToBuffer(
            source = TexelCopyTextureInfo(texture = resolved),
            destination = TexelCopyBufferInfo(
                buffer = readback,
                offset = 0uL,
                bytesPerRow = SRGB_PROBE_ROW_BYTES.toUInt(),
                rowsPerImage = 1u,
            ),
            copySize = Extent3D(1u, 1u),
        )
        val commandBuffer = encoder.finish().also(owned::add)
        device.queue.submit(listOf(commandBuffer))
        withTimeout(SRGB_PROBE_TIMEOUT_MS) { device.queue.onSubmittedWorkDone().getOrThrow() }
        // popErrorScope starts an asynchronous native pop. It must never be retried if its
        // callback fails or times out after the pop has already been issued.
        scopeOpen = false
        val validationError = withTimeout(SRGB_PROBE_TIMEOUT_MS) { device.popErrorScope().getOrThrow() }
        if (validationError != null) return false

        withTimeout(SRGB_PROBE_TIMEOUT_MS) {
            readback.mapAsync(GPUMapMode.Read, 0uL, SRGB_PROBE_ROW_BYTES.toULong()).getOrThrow()
        }
        val bytes = readback.getMappedRange(0uL, SRGB_PROBE_ROW_BYTES.toULong()).toByteArray()
        readback.unmap()
        return bytes[0] == 0xff.toByte() && bytes[1] == 0.toByte() &&
            bytes[2] == 0.toByte() && bytes[3] == 0xff.toByte()
    } catch (_: Throwable) {
        return false
    } finally {
        if (scopeOpen) {
            try { withTimeout(SRGB_PROBE_TIMEOUT_MS) { device.popErrorScope().getOrThrow() } }
            catch (_: Throwable) { /* A failed probe never advertises the capability. */ }
        }
        owned.asReversed().forEach { resource -> runCatching { resource.close() } }
    }
}
