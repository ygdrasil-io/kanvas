package org.graphiks.kanvas.gpu.renderer.execution

import ffi.JvmNativeAddress
import ffi.LibraryLoader
import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BindGroupLayoutDescriptor
import io.ygdrasil.webgpu.BindGroupLayoutEntry
import io.ygdrasil.webgpu.BlendComponent
import io.ygdrasil.webgpu.BlendState
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferBindingLayout
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Color
import io.ygdrasil.webgpu.ColorTargetState
import io.ygdrasil.webgpu.CompositeAlphaMode
import io.ygdrasil.webgpu.DepthStencilState
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GLFWContext
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUColorWrite
import io.ygdrasil.webgpu.GPUCompareFunction
import io.ygdrasil.webgpu.GPUAdapterInfo
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUErrorFilter
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUIndexFormat
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUSamplerBindingType
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUStencilOperation
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureSampleType
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.GPUTextureViewDimension
import io.ygdrasil.webgpu.GPUVertexFormat
import io.ygdrasil.webgpu.NativeSurface
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDepthStencilAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.SamplerBindingLayout
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.StencilFaceState
import io.ygdrasil.webgpu.SurfaceConfiguration
import io.ygdrasil.webgpu.SurfaceTextureStatus
import io.ygdrasil.webgpu.TexelCopyBufferInfo
import io.ygdrasil.webgpu.TexelCopyBufferLayout
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.TextureBindingLayout
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.VertexAttribute
import io.ygdrasil.webgpu.VertexBufferLayout
import io.ygdrasil.webgpu.VertexState
import io.ygdrasil.webgpu.WGPU
import io.ygdrasil.webgpu.WGPUInstanceBackend
import io.ygdrasil.webgpu.beginRenderPass
import io.ygdrasil.webgpu.glfwContextRenderer
import java.lang.foreign.MemorySegment
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeyPreimage
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUPipelineKeys
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetry
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUTelemetryLedger

import org.graphiks.kanvas.gpu.renderer.text.colorGlyphCompositeWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.TexturedVerticesWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.TexturedVerticesDualBlendWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.TexturedVerticesColorFilterWgsl
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendFactor
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode

private const val COPY_BYTES_PER_ROW_ALIGNMENT: Int = 256
private const val FULL_SCREEN_TRIANGLE_VERTEX_COUNT: UInt = 3u
private const val RGBA_BYTES_PER_PIXEL: Int = 4
private const val RECT_COLOR_UNIFORM_SIZE_BYTES: ULong = 16uL
private const val VERTEX_COLOR_STRIDE_BYTES: Int = 32
private const val TEXT_ATLAS_VERTEX_STRIDE_BYTES: Int = 16
private val sessionOrdinalCounter = AtomicLong(0L)
private val windowRuntimeOrdinalCounter = AtomicLong(0L)

internal fun alignCopyBytesPerRow(unpaddedBytesPerRow: Int): Int {
    require(unpaddedBytesPerRow > 0) { "unpaddedBytesPerRow must be positive" }
    val alignment = COPY_BYTES_PER_ROW_ALIGNMENT
    return ((unpaddedBytesPerRow + alignment - 1) / alignment) * alignment
}

internal fun stripRowPadding(
    bytes: ByteArray,
    width: Int,
    height: Int,
    bytesPerPixel: Int,
    paddedBytesPerRow: Int,
): ByteArray {
    require(width > 0) { "width must be positive" }
    require(height > 0) { "height must be positive" }
    require(bytesPerPixel > 0) { "bytesPerPixel must be positive" }
    require(paddedBytesPerRow > 0) { "paddedBytesPerRow must be positive" }

    val tightBytesPerRow = width * bytesPerPixel
    require(paddedBytesPerRow >= tightBytesPerRow) {
        "paddedBytesPerRow $paddedBytesPerRow must be >= tight row size $tightBytesPerRow"
    }
    require(bytes.size >= paddedBytesPerRow * height) {
        "Byte array is too small for $height padded rows of $paddedBytesPerRow bytes"
    }

    if (paddedBytesPerRow == tightBytesPerRow) {
        return bytes.copyOf(tightBytesPerRow * height)
    }

    val stripped = ByteArray(tightBytesPerRow * height)
    for (row in 0 until height) {
        System.arraycopy(
            bytes,
            row * paddedBytesPerRow,
            stripped,
            row * tightBytesPerRow,
            tightBytesPerRow,
        )
    }
    return stripped
}

internal fun swizzleBgraToRgba(bytes: ByteArray): ByteArray {
    require(bytes.size % RGBA_BYTES_PER_PIXEL == 0) {
        "BGRA byte array size ${bytes.size} must be a multiple of $RGBA_BYTES_PER_PIXEL"
    }
    val rgba = ByteArray(bytes.size)
    for (offset in bytes.indices step RGBA_BYTES_PER_PIXEL) {
        rgba[offset] = bytes[offset + 2]
        rgba[offset + 1] = bytes[offset + 1]
        rgba[offset + 2] = bytes[offset]
        rgba[offset + 3] = bytes[offset + 3]
    }
    return rgba
}

internal fun windowSurfaceDeviceGeneration(windowRuntimeOrdinal: Long): GPUDeviceGeneration {
    require(windowRuntimeOrdinal > 0L) { "windowRuntimeOrdinal must be positive" }
    return GPUDeviceGeneration(windowRuntimeOrdinal)
}

internal fun windowSurfaceTargetId(
    windowRuntimeOrdinal: Long,
    binding: GPUNativeSurfaceBinding,
): String {
    require(windowRuntimeOrdinal > 0L) { "windowRuntimeOrdinal must be positive" }
    return "wgpu-window-surface-$windowRuntimeOrdinal-${binding.platform.name.lowercase()}-${binding.width}x${binding.height}"
}

internal fun sessionDeviceGeneration(sessionOrdinal: Long): GPUDeviceGeneration {
    require(sessionOrdinal > 0L) { "sessionOrdinal must be positive" }
    return GPUDeviceGeneration(sessionOrdinal)
}

internal fun offscreenTargetId(
    sessionOrdinal: Long,
    offscreenTargetOrdinal: Long,
    request: GPUOffscreenTargetRequest,
): String {
    require(sessionOrdinal > 0L) { "sessionOrdinal must be positive" }
    require(offscreenTargetOrdinal > 0L) { "offscreenTargetOrdinal must be positive" }
    return "wgpu-offscreen-$sessionOrdinal-$offscreenTargetOrdinal-${request.width}x${request.height}-${request.colorFormat.normalizedColorFormat()}"
}

private fun nextSessionOrdinal(): Long = sessionOrdinalCounter.incrementAndGet()
private fun nextWindowRuntimeOrdinal(): Long = windowRuntimeOrdinalCounter.incrementAndGet()

object WgpuBackendRuntimeFactory {
    /** Creates a WebGPU-backed runtime session when the host can initialize the backend. */
    fun createOrNull(): GPUBackendSession? = try {
        LibraryLoader.load()
        val glfw = runBlocking {
            glfwContextRenderer(
                width = 1,
                height = 1,
                title = "kanvas-gpu-renderer-wgpu-runtime",
                deferredRendering = true,
            )
        }
        WgpuBackendSession(glfw)
    } catch (_: Throwable) {
        null
    }
}

private class WgpuBackendSession(
    private val glfw: GLFWContext,
) : GPUBackendSession {
    private val sessionOrdinal = nextSessionOrdinal()
    private val deviceGeneration = sessionDeviceGeneration(sessionOrdinal)
    private val executionCaches = WgpuExecutionCaches(deviceGeneration)
    private var offscreenTargetOrdinalCounter = 0L

    override val adapterInfo: GPUBackendAdapterSummary? =
        GPUBackendAdapterSummary(adapterSummary(glfw))

    override val executionCacheTelemetry: List<GPUCacheTelemetry>
        get() = executionCaches.cacheTelemetry

    override val executionCacheDumpLines: List<String>
        get() = executionCaches.dumpLines

    override fun createOffscreenTarget(request: GPUOffscreenTargetRequest): GPUBackendOffscreenTarget =
        WgpuOffscreenTarget(
            sessionOrdinal = sessionOrdinal,
            offscreenTargetOrdinal = nextOffscreenTargetOrdinal(),
            deviceGeneration = deviceGeneration,
            device = glfw.wgpuContext.device,
            queue = glfw.wgpuContext.device.queue,
            request = request,
            executionCaches = executionCaches,
        )

    override fun createWindowSurface(binding: GPUNativeSurfaceBinding): GPUBackendWindowSurface =
        WgpuWindowSurface(binding = binding)

    override fun close() {
        try {
            executionCaches.close()
        } finally {
            glfw.close()
        }
    }

    private fun nextOffscreenTargetOrdinal(): Long {
        offscreenTargetOrdinalCounter += 1L
        return offscreenTargetOrdinalCounter
    }
}

private const val MAX_TEXTURE_DIMENSION: Int = 8192

private class WgpuOffscreenTarget(
    private val sessionOrdinal: Long,
    private val offscreenTargetOrdinal: Long,
    private val deviceGeneration: GPUDeviceGeneration,
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val request: GPUOffscreenTargetRequest,
    private val executionCaches: WgpuExecutionCaches,
) : GPUBackendOffscreenTarget {
    private val safeWidth = request.width.coerceAtMost(MAX_TEXTURE_DIMENSION)
    private val safeHeight = request.height.coerceAtMost(MAX_TEXTURE_DIMENSION)
    private val format = request.colorFormat.toWgpuTextureFormat()
    private val bytesPerPixel = format.bytesPerPixel()
    private val tightBytesPerRow = safeWidth * bytesPerPixel
    private val paddedBytesPerRow = alignCopyBytesPerRow(tightBytesPerRow)
    private val stagingSize = (paddedBytesPerRow.toLong() * safeHeight.toLong()).toULong()
    private val texture = device.createTexture(
        TextureDescriptor(
            size = Extent3D(width = safeWidth.toUInt(), height = safeHeight.toUInt()),
            format = format,
            usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
            label = "GPUBackend.offscreen.color",
        ),
    )
    private val depthStencilTexture = device.createTexture(
        TextureDescriptor(
            size = Extent3D(width = safeWidth.toUInt(), height = safeHeight.toUInt()),
            format = GPUTextureFormat.Depth24PlusStencil8,
            usage = GPUTextureUsage.RenderAttachment,
            label = "GPUBackend.offscreen.depthStencil",
        ),
    )
    private val depthStencilView = depthStencilTexture.createView()
    private val stagingBuffer = device.createBuffer(
        BufferDescriptor(
            size = stagingSize,
            usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
            mappedAtCreation = false,
            label = "GPUBackend.offscreen.staging",
        ),
    )
    private val vertexBuffers = mutableMapOf<String, Pair<GPUBuffer, Int>>()
    private val offscreenTextures = mutableMapOf<String, GPUTexture>()

    override val target: GPUSurfaceTarget =
        GPUSurfaceTarget(
            targetId = offscreenTargetId(
                sessionOrdinal = sessionOrdinal,
                offscreenTargetOrdinal = offscreenTargetOrdinal,
                request = request,
            ),
            descriptor = GPUSurfaceTargetDescriptor(
                width = request.width,
                height = request.height,
                colorFormat = request.colorFormat.normalizedColorFormat(),
                surfaceBacked = false,
                targetGeneration = 0L,
                usageLabels = setOf("render_attachment", "copy_src"),
                readbackAvailable = true,
            ),
            deviceGeneration = deviceGeneration,
        )

    override fun encode(
        clearColor: GPUClearColor,
        block: GPUBackendRenderRecorder.() -> Unit,
    ) {
        GPUResourceScope().use { resources ->
            val view = resources.track(texture.createView()) { it.close() }
            val encoder = resources.trackIfAutoCloseable(device.createCommandEncoder())
            encoder.beginRenderPass(
                RenderPassDescriptor(
                    colorAttachments = listOf(
                        RenderPassColorAttachment(
                            view = view,
                            loadOp = GPULoadOp.Clear,
                            clearValue = clearColor.toWgpuColor(),
                            storeOp = GPUStoreOp.Store,
                        ),
                    ),
                    depthStencilAttachment = RenderPassDepthStencilAttachment(
                        view = depthStencilView,
                        stencilClearValue = 0u,
                        stencilLoadOp = GPULoadOp.Clear,
                        stencilStoreOp = GPUStoreOp.Store,
                        stencilReadOnly = false,
                        depthReadOnly = true,
                    ),
                ),
            ) {
                val recorder = WgpuRenderRecorder(
                    device = device,
                    queue = queue,
                    targetFormat = format,
                    resourceScope = resources,
                    executionCaches = executionCaches,
                    setPipelineAction = { pipeline -> setPipeline(pipeline) },
                    setBindGroupAction = { index, bindGroup -> setBindGroup(index, bindGroup) },
                    setScissorAction = { x: UInt, y: UInt, width: UInt, height: UInt ->
                            val cx = x.coerceAtMost(safeWidth.toUInt())
                            val cy = y.coerceAtMost(safeHeight.toUInt())
                            val cw = width.coerceAtMost((safeWidth.toUInt() - cx))
                            val ch = height.coerceAtMost((safeHeight.toUInt() - cy))
                            setScissorRect(cx, cy, cw, ch)
                        },
                    drawAction = { vertexCount -> draw(vertexCount) },
                    setStencilReferenceAction = { ref -> setStencilReference(ref) },
                    setVertexBufferAction = { slot, buffer -> setVertexBuffer(slot, buffer) },
                    setIndexBufferAction = { buffer, format -> setIndexBuffer(buffer, format) },
                    drawIndexedAction = { indexCount -> drawIndexed(indexCount) },
                    offscreenTextureStore = offscreenTextures,
                )
                recorder.block()
                end()
            }
            encoder.copyTextureToBuffer(
                source = TexelCopyTextureInfo(texture = texture),
                destination = TexelCopyBufferInfo(
                    buffer = stagingBuffer,
                    offset = 0uL,
                    bytesPerRow = paddedBytesPerRow.toUInt(),
                    rowsPerImage = safeHeight.toUInt(),
                ),
                copySize = Extent3D(width = safeWidth.toUInt(), height = safeHeight.toUInt()),
            )
            val commandBuffer = resources.trackIfAutoCloseable(encoder.finish())
            queue.submit(listOf(commandBuffer))
        }
    }

    override fun readRgba(): ByteArray {
        runBlocking {
            stagingBuffer.mapAsync(GPUMapMode.Read, 0uL, stagingSize).getOrThrow()
        }
        try {
            val mapped = stagingBuffer.getMappedRange(0uL, stagingSize).toByteArray()
            val tightlyPacked = stripRowPadding(
                bytes = mapped,
                width = request.width,
                height = request.height,
                bytesPerPixel = bytesPerPixel,
                paddedBytesPerRow = paddedBytesPerRow,
            )
            return if (format.isBgraFormat()) swizzleBgraToRgba(tightlyPacked) else tightlyPacked
        } finally {
            stagingBuffer.unmap()
        }
    }

    internal fun createVertexBuffer(data: FloatArray): String {
        val label = "vertexBuffer:${data.contentHashCode()}:${vertexBuffers.size}"
        if (label in vertexBuffers) return label
        val byteSize = (data.size * 4).toULong()
        val buffer = device.createBuffer(
            BufferDescriptor(
                size = byteSize,
                usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                label = label,
            ),
        )
        queue.writeBuffer(buffer, 0uL, ArrayBuffer.of(data))
        vertexBuffers[label] = buffer to data.size
        return label
    }

    internal fun vertexBuffer(label: String): GPUBuffer {
        val (buffer, _) = vertexBuffers[label]
            ?: error("Vertex buffer not found: $label")
        return buffer
    }

    override fun createOffscreenTexture(textureDesc: GPUBackendOffscreenTexture): String {
        val safeW = textureDesc.width.coerceAtMost(MAX_TEXTURE_DIMENSION)
        val safeH = textureDesc.height.coerceAtMost(MAX_TEXTURE_DIMENSION)
        val label = "offscreenTex:${textureDesc.width}x${textureDesc.height}:${textureDesc.format}"
        if (label in offscreenTextures) return label
        val tex = device.createTexture(
            TextureDescriptor(
                size = Extent3D(width = safeW.toUInt(), height = safeH.toUInt()),
                format = textureDesc.format.toWgpuTextureFormat(),
                usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding or GPUTextureUsage.CopySrc,
                label = label,
            ),
        )
        offscreenTextures[label] = tex
        return label
    }

    override fun encodeOffscreenTexture(
        textureLabel: String,
        clearColor: GPUClearColor?,
        block: GPUBackendRenderRecorder.() -> Unit,
    ) {
        GPUResourceScope().use { resources ->
            encodeOffscreenTextureInternal(
                textureLabel = textureLabel,
                clearColor = clearColor,
                textureFormat = format,
                resources = resources,
                block = { recorder -> recorder.block() },
            )
        }
    }

    internal fun offscreenTexture(label: String): GPUTexture {
        return offscreenTextures[label]
            ?: error("Offscreen texture not found: $label")
    }

    internal fun encodeOffscreenTextureInternal(
        textureLabel: String,
        clearColor: GPUClearColor?,
        textureFormat: GPUTextureFormat,
        resources: GPUResourceScope,
        block: (WgpuRenderRecorder) -> Unit,
    ) {
        val tex = offscreenTexture(textureLabel)
        val texView = resources.track(tex.createView()) { it.close() }
        val texWidth = tex.width
        val texHeight = tex.height
        val dsTex = resources.track(
            device.createTexture(
                TextureDescriptor(
                    size = Extent3D(width = texWidth, height = texHeight),
                    format = GPUTextureFormat.Depth24PlusStencil8,
                    usage = GPUTextureUsage.RenderAttachment,
                    label = "GPUBackend.offscreenLayer.depthStencil",
                ),
            ),
        ) { it.close() }
        val dsView = resources.track(dsTex.createView()) { it.close() }
        val encoder = resources.trackIfAutoCloseable(device.createCommandEncoder())
        encoder.beginRenderPass(
            RenderPassDescriptor(
                colorAttachments = listOf(
                    RenderPassColorAttachment(
                        view = texView,
                        loadOp = if (clearColor != null) GPULoadOp.Clear else GPULoadOp.Load,
                        clearValue = clearColor?.toWgpuColor() ?: Color(r = 0.0, g = 0.0, b = 0.0, a = 0.0),
                        storeOp = GPUStoreOp.Store,
                    ),
                ),
                depthStencilAttachment = RenderPassDepthStencilAttachment(
                    view = dsView,
                    stencilClearValue = 0u,
                    stencilLoadOp = GPULoadOp.Clear,
                    stencilStoreOp = GPUStoreOp.Store,
                    stencilReadOnly = false,
                    depthReadOnly = true,
                ),
            ),
        ) {
            val recorder = WgpuRenderRecorder(
                device = device,
                queue = queue,
                targetFormat = textureFormat,
                resourceScope = resources,
                executionCaches = executionCaches,
                setPipelineAction = { pipeline -> setPipeline(pipeline) },
                setBindGroupAction = { index, bindGroup -> setBindGroup(index, bindGroup) },
                setScissorAction = { x: UInt, y: UInt, width: UInt, height: UInt ->
                        val texW = tex.width
                        val texH = tex.height
                        val cx = x.coerceAtMost(texW)
                        val cy = y.coerceAtMost(texH)
                        val cw = width.coerceAtMost(texW - cx)
                        val ch = height.coerceAtMost(texH - cy)
                        setScissorRect(cx, cy, cw, ch)
                    },
                drawAction = { vertexCount -> draw(vertexCount) },
                setStencilReferenceAction = { ref -> setStencilReference(ref) },
                setVertexBufferAction = { slot, buffer -> setVertexBuffer(slot, buffer) },
                setIndexBufferAction = { buffer, format -> setIndexBuffer(buffer, format) },
                drawIndexedAction = { indexCount -> drawIndexed(indexCount) },
                offscreenTextureStore = mutableMapOf(),
            )
            block(recorder)
            end()
        }
        val commandBuffer = resources.trackIfAutoCloseable(encoder.finish())
        queue.submit(listOf(commandBuffer))
    }

    override fun close() {
        var firstFailure: Throwable? = null
        /** Suppresses exceptions thrown inside [block] and collects the first failure for re-throw. */
        fun closeQuietly(block: () -> Unit) {
            try { block() } catch (e: Throwable) {
                if (firstFailure == null) firstFailure = e else firstFailure.addSuppressed(e)
            }
        }
        closeQuietly { stagingBuffer.close() }
        closeQuietly { depthStencilView.close() }
        closeQuietly { depthStencilTexture.close() }
        closeQuietly { texture.close() }
        vertexBuffers.values.forEach { (buffer, _) -> closeQuietly { buffer.close() } }
        vertexBuffers.clear()
        offscreenTextures.values.forEach { tex -> closeQuietly { tex.close() } }
        offscreenTextures.clear()
        firstFailure?.let { throw it }
    }
}

private class WgpuWindowSurface(
    binding: GPUNativeSurfaceBinding,
) : GPUBackendWindowSurface {
    private val windowRuntimeOrdinal = nextWindowRuntimeOrdinal()
    private val deviceGeneration = windowSurfaceDeviceGeneration(windowRuntimeOrdinal)
    private val targetId = windowSurfaceTargetId(windowRuntimeOrdinal, binding)
    private val runtime = createNativeWindowRuntime(binding)
    private val executionCaches = WgpuExecutionCaches(deviceGeneration)
    private var width = binding.width
    private var height = binding.height
    private var targetGeneration = 0L

    override val adapterInfo: GPUBackendAdapterSummary
        get() = runtime.adapterInfo

    override val target: GPUSurfaceTarget
        get() = GPUSurfaceTarget(
            targetId = targetId,
            descriptor = GPUSurfaceTargetDescriptor(
                width = width,
                height = height,
                colorFormat = runtime.format.toBackendColorFormat(),
                surfaceBacked = true,
                targetGeneration = targetGeneration,
                usageLabels = setOf("render_attachment"),
                readbackAvailable = false,
            ),
            deviceGeneration = deviceGeneration,
        )

    override fun resize(width: Int, height: Int) {
        require(width > 0) { "width must be positive" }
        require(height > 0) { "height must be positive" }
        this.width = width
        this.height = height
        runtime.configure(width = width, height = height)
        targetGeneration += 1L
    }

    override fun encodeAndPresent(
        clearColor: GPUClearColor,
        block: GPUBackendRenderRecorder.() -> Unit,
    ): Boolean {
        val surfaceTexture = runtime.surface.getCurrentTexture()
        when (surfaceTexture.status) {
            SurfaceTextureStatus.lost,
            SurfaceTextureStatus.outdated -> {
                surfaceTexture.texture.close()
                runtime.configure(width = width, height = height)
                return false
            }
            SurfaceTextureStatus.outOfMemory,
            SurfaceTextureStatus.deviceLost -> {
                surfaceTexture.texture.close()
                error("Surface texture acquisition failed with terminal status ${surfaceTexture.status.name}")
            }
            SurfaceTextureStatus.success,
            SurfaceTextureStatus.timeout -> Unit
        }

        GPUResourceScope().use { resources ->
            val view = resources.track(surfaceTexture.texture.createView(null)) { it.close() }
            val encoder = resources.trackIfAutoCloseable(runtime.device.createCommandEncoder())
            encoder.beginRenderPass(
                RenderPassDescriptor(
                    colorAttachments = listOf(
                        RenderPassColorAttachment(
                            view = view,
                            loadOp = GPULoadOp.Clear,
                            clearValue = clearColor.toWgpuColor(),
                            storeOp = GPUStoreOp.Store,
                        ),
                    ),
                ),
            ) {
                val recorder = WgpuRenderRecorder(
                    device = runtime.device,
                    queue = runtime.device.queue,
                    targetFormat = runtime.format,
                    resourceScope = resources,
                    executionCaches = executionCaches,
                    setPipelineAction = { pipeline -> setPipeline(pipeline) },
                    setBindGroupAction = { index, bindGroup -> setBindGroup(index, bindGroup) },
                    setScissorAction = { x, y, surfaceWidth, surfaceHeight -> setScissorRect(x, y, surfaceWidth, surfaceHeight) },
                    drawAction = { vertexCount -> draw(vertexCount) },
                    setStencilReferenceAction = { ref -> setStencilReference(ref) },
                    setVertexBufferAction = { slot, buffer -> setVertexBuffer(slot, buffer) },
                    setIndexBufferAction = { buffer, format -> setIndexBuffer(buffer, format) },
                    drawIndexedAction = { indexCount -> drawIndexed(indexCount) },
                    offscreenTextureStore = mutableMapOf(),
                )
                recorder.block()
                end()
            }
            val commandBuffer = resources.trackIfAutoCloseable(encoder.finish())
            runtime.device.queue.submit(listOf(commandBuffer))
            runtime.surface.present()
        }
        return true
    }

    override fun close() {
        try {
            executionCaches.close()
        } finally {
            runtime.close()
        }
    }
}

private class WgpuRenderRecorder(
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val targetFormat: GPUTextureFormat,
    private val resourceScope: GPUResourceScope,
    private val executionCaches: WgpuExecutionCaches,
    private val setPipelineAction: (GPURenderPipeline) -> Unit,
    private val setBindGroupAction: (UInt, GPUBindGroup) -> Unit,
    private val setScissorAction: (UInt, UInt, UInt, UInt) -> Unit,
    private val drawAction: (UInt) -> Unit,
    private val setStencilReferenceAction: (UInt) -> Unit,
    private val setVertexBufferAction: (UInt, GPUBuffer) -> Unit,
    private val setIndexBufferAction: (GPUBuffer, GPUIndexFormat) -> Unit,
    private val drawIndexedAction: (UInt) -> Unit,
    private val offscreenTextureStore: MutableMap<String, GPUTexture>,
) : GPUBackendRenderRecorder {
    private val vertexBufferStore = mutableMapOf<String, Pair<GPUBuffer, Int>>()
    private val vertexColorIndexStore = mutableMapOf<String, IntArray>()
    private var texturedVertexPipelineCache = mutableMapOf<String, GPURenderPipeline>()
    private var texturedVertexBindGroupLayout: GPUBindGroupLayout? = null
    private var dualUVVertexPipelineCache = mutableMapOf<String, GPURenderPipeline>()
    private var dualUVVertexBindGroupLayout: GPUBindGroupLayout? = null

    override fun drawFullscreenPass(
        wgsl: String,
        colorFormat: String,
        draws: List<GPUBackendRectDraw>,
        blendMode: GPUBlendMode?,
    ) {
        recordFullscreenUniformPass(
            wgsl = wgsl,
            colorFormat = colorFormat,
            draws = draws.map { draw ->
                WgpuFullscreenUniformDraw(
                    uniformPayload = ArrayBuffer.of(draw.rgbaPremul),
                    uniformSizeBytes = RECT_COLOR_UNIFORM_SIZE_BYTES,
                    scissorX = draw.scissorX,
                    scissorY = draw.scissorY,
                    scissorWidth = draw.scissorWidth,
                    scissorHeight = draw.scissorHeight,
                )
            },
            blendMode = blendMode,
        )
    }

    override fun drawFullscreenUniformPayloadPass(
        wgsl: String,
        colorFormat: String,
        draws: List<GPUBackendUniformPayloadDraw>,
        blendMode: GPUBlendMode?,
    ) {
        recordFullscreenUniformPass(
            wgsl = wgsl,
            colorFormat = colorFormat,
            draws = draws.map { draw ->
                val uniformBytes = draw.uniformBytes()
                WgpuFullscreenUniformDraw(
                    uniformPayload = ArrayBuffer.of(uniformBytes),
                    uniformSizeBytes = draw.materializedUniformByteSize.toULong(),
                    scissorX = draw.scissorX,
                    scissorY = draw.scissorY,
                    scissorWidth = draw.scissorWidth,
                    scissorHeight = draw.scissorHeight,
                )
            },
            blendMode = blendMode,
        )
    }

    override fun drawFullscreenRawUniformPass(
        wgsl: String,
        colorFormat: String,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode?,
    ) {
        recordFullscreenUniformPass(
            wgsl = wgsl,
            colorFormat = colorFormat,
            draws = draws.map { draw ->
                WgpuFullscreenUniformDraw(
                    uniformPayload = ArrayBuffer.of(draw.uniformBytes),
                    uniformSizeBytes = draw.uniformBytes.size.toULong(),
                    scissorX = draw.scissorX,
                    scissorY = draw.scissorY,
                    scissorWidth = draw.scissorWidth,
                    scissorHeight = draw.scissorHeight,
                )
            },
            blendMode = blendMode,
        )
    }

    override fun drawFullscreenTextureUniformPass(
        wgsl: String,
        colorFormat: String,
        textureRgba: ByteArray,
        textureWidth: Int,
        textureHeight: Int,
        textureFormat: String,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode?,
    ) {
        recordFullscreenTextureUniformPass(
            wgsl = wgsl,
            colorFormat = colorFormat,
            textureRgba = textureRgba,
            textureWidth = textureWidth,
            textureHeight = textureHeight,
            textureFormat = textureFormat,
            draws = draws.map { draw ->
                WgpuFullscreenUniformDraw(
                    uniformPayload = ArrayBuffer.of(draw.uniformBytes),
                    uniformSizeBytes = draw.uniformBytes.size.toULong(),
                    scissorX = draw.scissorX,
                    scissorY = draw.scissorY,
                    scissorWidth = draw.scissorWidth,
                    scissorHeight = draw.scissorHeight,
                )
            },
            blendMode = blendMode,
        )
    }

    override fun drawFullscreenStencilPass(
        wgsl: String,
        colorFormat: String,
        stencilMode: GPUBackendStencilMode,
        triangleData: GPUBackendTriangleData?,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode?,
    ) {
        require(wgsl.isNotBlank()) { "wgsl must not be blank" }
        require(colorFormat.normalizedColorFormat() == targetFormat.toBackendColorFormat()) {
            "Requested color format $colorFormat does not match target format ${targetFormat.toBackendColorFormat()}"
        }

        when (stencilMode) {
            GPUBackendStencilMode.Write -> {
                require(triangleData != null) { "triangleData required for stencil write mode" }
                recordStencilWritePass(wgsl = wgsl, colorFormat = colorFormat, triangleData = triangleData)
            }
            GPUBackendStencilMode.Test -> {
                require(draws.isNotEmpty()) { "draws required for stencil test mode" }
                recordStencilTestPass(wgsl = wgsl, colorFormat = colorFormat, draws = draws.map { draw ->
                    WgpuFullscreenUniformDraw(
                        uniformPayload = ArrayBuffer.of(draw.uniformBytes),
                        uniformSizeBytes = draw.uniformBytes.size.toULong(),
                        scissorX = draw.scissorX,
                        scissorY = draw.scissorY,
                        scissorWidth = draw.scissorWidth,
                        scissorHeight = draw.scissorHeight,
                    )
                }, blendMode = blendMode)
            }
        }
    }

    override fun createVertexColorBuffer(data: GPUBackendVertexColorData): String {
        val label = "vertexColor:${data.vertexData.contentHashCode()}:${vertexBufferStore.size}"
        if (label in vertexBufferStore) return label
        val byteSize = (data.vertexData.size * 4).toULong()
        val buffer = resourceScope.track(
            device.createBuffer(
                BufferDescriptor(
                    size = byteSize,
                    usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                    label = label,
                ),
            ),
        ) { it.close() }
        queue.writeBuffer(buffer, 0uL, ArrayBuffer.of(data.vertexData))
        vertexBufferStore[label] = buffer to data.vertexCount
        vertexColorIndexStore[label] = data.indices
        return label
    }

    override fun drawVertexColorIndexed(
        vertexBufferLabel: String,
        indexCount: Int,
        uniformDraw: GPUBackendRawUniformDraw,
        blendMode: GPUBlendMode?,
    ) {
        val (vertexBuffer, vertexCount) = vertexBufferStore[vertexBufferLabel]
            ?: error("Vertex buffer not found: $vertexBufferLabel")

        // Use the caller-provided triangulation indices. A fabricated sequential
        // 0..indexCount list would reference out-of-range vertices for fan/indexed
        // geometry (e.g. path-fill star, convex octagon) and fill only garbage
        // slivers; the real indices map each triangle to actual vertices.
        val indices = vertexColorIndexStore[vertexBufferLabel]
            ?: error("Vertex color index data not found: $vertexBufferLabel")
        require(indices.size == indexCount) {
            "indexCount=$indexCount does not match stored index data ${indices.size} for $vertexBufferLabel"
        }
        val indexByteSize = (indices.size * 4).toULong()
        val indexBuffer = resourceScope.track(
            device.createBuffer(
                BufferDescriptor(
                    size = indexByteSize,
                    usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
                    label = "GPUBackend.index.$vertexBufferLabel",
                ),
            ),
        ) { it.close() }
        queue.writeBuffer(indexBuffer, 0uL, ArrayBuffer.of(indices))

        val vertexWgsl = VERTEX_COLOR_WGSL
        val keys = stencilExecutionCacheKeys(wgsl = vertexWgsl, targetFormat = targetFormat, vertexStage = true, blendMode = blendMode)
        executionCaches.recordPreimages(keys)
        val bindGroupLayout = executionCaches.bindGroupLayout(device = device, keys = keys)
        val shader = executionCaches.shaderModule(device = device, wgsl = vertexWgsl, keys = keys)
        val pipelineLayout = executionCaches.pipelineLayout(device = device, bindGroupLayout = bindGroupLayout, keys = keys)
        val pipeline = executionCaches.vertexColorRenderPipeline(
            device = device,
            shader = shader,
            pipelineLayout = pipelineLayout,
            targetFormat = targetFormat,
            keys = keys,
            blendMode = blendMode,
        )

        val uniform = resourceScope.track(
            device.createBuffer(
                BufferDescriptor(
                    size = uniformDraw.uniformBytes.size.toULong(),
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                    label = "GPUBackend.vertexColor.uniform",
                ),
            ),
        ) { it.close() }
        queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(uniformDraw.uniformBytes))
        val bindGroup = resourceScope.track(
            device.createBindGroup(
                BindGroupDescriptor(
                    layout = bindGroupLayout,
                    entries = listOf(
                        BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                    ),
                ),
            ),
        ) { it.close() }

        setPipelineAction(pipeline)
        setBindGroupAction(0u, bindGroup)
        setScissorAction(
            uniformDraw.scissorX.toUInt(),
            uniformDraw.scissorY.toUInt(),
            uniformDraw.scissorWidth.toUInt(),
            uniformDraw.scissorHeight.toUInt(),
        )
        setVertexBufferAction(0u, vertexBuffer)
        setIndexBufferAction(indexBuffer, GPUIndexFormat.Uint32)
        drawIndexedAction(indexCount.toUInt())
    }

    override fun createVertexPositionUVBuffer(data: GPUBackendVertexPositionUVData): String {
        val label = "vertexUV:${data.vertexData.contentHashCode()}:${vertexBufferStore.size}"
        if (label in vertexBufferStore) return label
        val byteSize = (data.vertexData.size * 4).toULong()
        val buffer = resourceScope.track(
            device.createBuffer(
                BufferDescriptor(
                    size = byteSize,
                    usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                    label = label,
                ),
            ),
        ) { it.close() }
        queue.writeBuffer(buffer, 0uL, ArrayBuffer.of(data.vertexData))
        vertexBufferStore[label] = buffer to data.vertexCount
        vertexColorIndexStore[label] = data.indices
        return label
    }

    override fun drawVertexPositionUVIndexed(
        vertexBufferLabel: String,
        indexCount: Int,
        uniformDraw: GPUBackendRawUniformDraw,
        textureRgba: ByteArray,
        textureWidth: Int,
        textureHeight: Int,
        textureFormat: String,
        blendMode: GPUBlendMode?,
    ) {
        val (vertexBuffer, vertexCount) = vertexBufferStore[vertexBufferLabel]
            ?: error("Vertex buffer not found: $vertexBufferLabel")
        val indices = vertexColorIndexStore[vertexBufferLabel]
            ?: error("Vertex UV index data not found: $vertexBufferLabel")
        require(indices.size == indexCount) {
            "indexCount=$indexCount does not match stored index data ${indices.size} for $vertexBufferLabel"
        }
        val indexByteSize = (indices.size * 4).toULong()
        val indexBuffer = resourceScope.track(
            device.createBuffer(
                BufferDescriptor(
                    size = indexByteSize,
                    usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
                    label = "GPUBackend.uvIndex.$vertexBufferLabel",
                ),
            ),
        ) { it.close() }
        queue.writeBuffer(indexBuffer, 0uL, ArrayBuffer.of(indices))

        val texture = createTexture(textureRgba, textureWidth, textureHeight, textureFormat)
        val sampler = resourceScope.track(
            device.createSampler(
                SamplerDescriptor(
                    addressModeU = GPUAddressMode.ClampToEdge,
                    addressModeV = GPUAddressMode.ClampToEdge,
                    magFilter = GPUFilterMode.Linear,
                    minFilter = GPUFilterMode.Linear,
                ),
            ),
        ) { it.close() }
        val textureView = resourceScope.track(texture.createView()) { it.close() }

        val uniformBuffer = createUniformBuffer(uniformDraw.uniformBytes)

        val bindGroupLayout = getOrCreateTexturedVertexBindGroupLayout()
        val bindGroup = resourceScope.track(
            device.createBindGroup(
                BindGroupDescriptor(
                    label = "texturedVertex:$vertexBufferLabel",
                    layout = bindGroupLayout,
                    entries = listOf(
                        BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniformBuffer)),
                        BindGroupEntry(binding = 1u, resource = textureView),
                        BindGroupEntry(binding = 2u, resource = sampler),
                    ),
                ),
            ),
        ) { it.close() }

        val pipeline = getOrCreateTexturedVertexPipeline(textureFormat, blendMode)

        setPipelineAction(pipeline)
        setBindGroupAction(0u, bindGroup)
        setVertexBufferAction(0u, vertexBuffer)
        setIndexBufferAction(indexBuffer, GPUIndexFormat.Uint32)
        setScissorAction(
            uniformDraw.scissorX.toUInt(),
            uniformDraw.scissorY.toUInt(),
            uniformDraw.scissorWidth.toUInt(),
            uniformDraw.scissorHeight.toUInt(),
        )
        drawIndexedAction(indexCount.toUInt())
    }

    override fun drawVertexPositionDualUVIndexed(
        vertexBufferLabel: String,
        indexCount: Int,
        uniformDraw: GPUBackendRawUniformDraw,
        texture1Rgba: ByteArray,
        texture1Width: Int, texture1Height: Int,
        texture2Rgba: ByteArray,
        texture2Width: Int, texture2Height: Int,
        textureFormat: String,
        blendMode: GPUBlendMode?,
    ) {
        val (vertexBuffer, vertexCount) = vertexBufferStore[vertexBufferLabel]
            ?: error("Vertex buffer not found: $vertexBufferLabel")
        val indices = vertexColorIndexStore[vertexBufferLabel]
            ?: error("Dual UV index data not found: $vertexBufferLabel")
        require(indices.size == indexCount) {
            "indexCount=$indexCount does not match stored index data ${indices.size} for $vertexBufferLabel"
        }
        val indexByteSize = (indices.size * 4).toULong()
        val indexBuffer = resourceScope.track(
            device.createBuffer(
                BufferDescriptor(
                    size = indexByteSize,
                    usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
                    label = "GPUBackend.dualUVIndex.$vertexBufferLabel",
                ),
            ),
        ) { it.close() }
        queue.writeBuffer(indexBuffer, 0uL, ArrayBuffer.of(indices))

        val tex1 = createTexture(texture1Rgba, texture1Width, texture1Height, textureFormat)
        val tex2 = createTexture(texture2Rgba, texture2Width, texture2Height, textureFormat)
        val sampler = resourceScope.track(
            device.createSampler(
                SamplerDescriptor(
                    addressModeU = GPUAddressMode.ClampToEdge,
                    addressModeV = GPUAddressMode.ClampToEdge,
                    magFilter = GPUFilterMode.Linear,
                    minFilter = GPUFilterMode.Linear,
                ),
            ),
        ) { it.close() }
        val tex1View = resourceScope.track(tex1.createView()) { it.close() }
        val tex2View = resourceScope.track(tex2.createView()) { it.close() }

        val uniformBuffer = createUniformBuffer(uniformDraw.uniformBytes)

        val bindGroupLayout = getOrCreateDualUVVertexBindGroupLayout()
        val bindGroup = resourceScope.track(
            device.createBindGroup(
                BindGroupDescriptor(
                    label = "dualVertex:$vertexBufferLabel",
                    layout = bindGroupLayout,
                    entries = listOf(
                        BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniformBuffer)),
                        BindGroupEntry(binding = 1u, resource = tex1View),
                        BindGroupEntry(binding = 2u, resource = sampler),
                        BindGroupEntry(binding = 3u, resource = tex2View),
                        BindGroupEntry(binding = 4u, resource = sampler),
                    ),
                ),
            ),
        ) { it.close() }

        val pipeline = getOrCreateDualUVVertexPipeline(textureFormat, blendMode)

        setPipelineAction(pipeline)
        setBindGroupAction(0u, bindGroup)
        setVertexBufferAction(0u, vertexBuffer)
        setIndexBufferAction(indexBuffer, GPUIndexFormat.Uint32)
        setScissorAction(
            uniformDraw.scissorX.toUInt(),
            uniformDraw.scissorY.toUInt(),
            uniformDraw.scissorWidth.toUInt(),
            uniformDraw.scissorHeight.toUInt(),
        )
        drawIndexedAction(indexCount.toUInt())
    }

    override fun createOffscreenTexture(texture: GPUBackendOffscreenTexture): String {
        val safeW = texture.width.coerceAtMost(MAX_TEXTURE_DIMENSION)
        val safeH = texture.height.coerceAtMost(MAX_TEXTURE_DIMENSION)
        val label = "offscreenTex:${texture.width}x${texture.height}:${texture.format}"
        if (label in offscreenTextureStore) return label
        val tex = resourceScope.track(
            device.createTexture(
                TextureDescriptor(
                    size = Extent3D(width = safeW.toUInt(), height = safeH.toUInt()),
                    format = texture.format.toWgpuTextureFormat(),
                    usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.TextureBinding or GPUTextureUsage.CopySrc,
                    label = label,
                ),
            ),
        ) { it.close() }
        offscreenTextureStore[label] = tex
        return label
    }

    override fun encodeOffscreenTexture(
        textureLabel: String,
        clearColor: GPUClearColor?,
        block: GPUBackendRenderRecorder.() -> Unit,
    ) {
        error("encodeOffscreenTexture must be handled by WgpuOffscreenTarget via internal encodeOffscreenTexture")
    }

    override fun drawTextAtlasPass(
        atlasRgba: ByteArray,
        atlasWidth: Int,
        atlasHeight: Int,
        atlasFormat: String,
        vertexData: FloatArray,
        indexData: IntArray,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode?,
    ) {
        require(atlasRgba.isNotEmpty()) { "atlasRgba must not be empty" }
        require(atlasWidth > 0) { "atlasWidth must be positive" }
        require(atlasHeight > 0) { "atlasHeight must be positive" }
        require(vertexData.isNotEmpty()) { "vertexData must not be empty" }
        require(indexData.isNotEmpty()) { "indexData must not be empty" }
        if (draws.isEmpty()) return

        val wgsl = TEXT_ATLAS_A8_WGSL
        val textureFormat = atlasFormat.toWgpuTextureFormat()
        val keys = textAtlasExecutionCacheKeys(wgsl = wgsl, targetFormat = targetFormat, textureFormat = textureFormat, blendMode = blendMode)
        executionCaches.recordPreimages(keys)

        val bindGroupLayout = executionCaches.bindGroupLayout(device = device, keys = keys)
        val textureBindGroupLayout = executionCaches.textureBindGroupLayout(device = device, keys = keys)
        val shader = executionCaches.shaderModule(device = device, wgsl = wgsl, keys = keys)
        val pipelineLayout = executionCaches.texturePipelineLayout(
            device = device,
            bindGroupLayouts = listOf(bindGroupLayout, textureBindGroupLayout),
            keys = keys,
        )
        val pipeline = executionCaches.textAtlasRenderPipeline(
            device = device,
            shader = shader,
            pipelineLayout = pipelineLayout,
            targetFormat = targetFormat,
            keys = keys,
            blendMode = blendMode,
        )

        val atlasTexture = resourceScope.track(
            device.createTexture(
                TextureDescriptor(
                    size = Extent3D(width = atlasWidth.toUInt(), height = atlasHeight.toUInt()),
                    format = textureFormat,
                    usage = GPUTextureUsage.TextureBinding or GPUTextureUsage.CopyDst,
                    label = "GPUBackend.text.atlas",
                ),
            ),
        ) { it.close() }
        val paddedAtlasBytesPerRow = alignCopyBytesPerRow(atlasWidth)
        val paddedAtlasData = if (paddedAtlasBytesPerRow == atlasWidth) {
            atlasRgba
        } else {
            val padded = ByteArray(paddedAtlasBytesPerRow * atlasHeight)
            for (row in 0 until atlasHeight) {
                System.arraycopy(atlasRgba, row * atlasWidth, padded, row * paddedAtlasBytesPerRow, atlasWidth)
            }
            padded
        }
        queue.writeTexture(
            destination = TexelCopyTextureInfo(texture = atlasTexture),
            data = ArrayBuffer.of(paddedAtlasData),
            dataLayout = TexelCopyBufferLayout(
                offset = 0uL,
                bytesPerRow = paddedAtlasBytesPerRow.toUInt(),
                rowsPerImage = atlasHeight.toUInt(),
            ),
            size = Extent3D(width = atlasWidth.toUInt(), height = atlasHeight.toUInt()),
        )
        val atlasView = resourceScope.track(atlasTexture.createView()) { it.close() }
        val sampler = resourceScope.track(
            device.createSampler(
                SamplerDescriptor(
                    addressModeU = GPUAddressMode.ClampToEdge,
                    addressModeV = GPUAddressMode.ClampToEdge,
                    magFilter = GPUFilterMode.Nearest,
                    minFilter = GPUFilterMode.Nearest,
                ),
            ),
        ) { it.close() }

        val vertexBuffer = resourceScope.track(
            device.createBuffer(
                BufferDescriptor(
                    size = (vertexData.size * 4).toULong(),
                    usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                    label = "GPUBackend.text.vertex",
                ),
            ),
        ) { it.close() }
        queue.writeBuffer(vertexBuffer, 0uL, ArrayBuffer.of(vertexData))

        val indexBuffer = resourceScope.track(
            device.createBuffer(
                BufferDescriptor(
                    size = (indexData.size * 4).toULong(),
                    usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
                    label = "GPUBackend.text.index",
                ),
            ),
        ) { it.close() }
        queue.writeBuffer(indexBuffer, 0uL, ArrayBuffer.of(indexData))

        setPipelineAction(pipeline)
        draws.forEach { draw ->
            val uniform = resourceScope.track(
                device.createBuffer(
                    BufferDescriptor(
                        size = draw.uniformBytes.size.toULong(),
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                        label = "GPUBackend.text.uniform",
                    ),
                ),
            ) { it.close() }
            queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(draw.uniformBytes))
            val bindGroup = resourceScope.track(
                device.createBindGroup(
                    BindGroupDescriptor(
                        layout = bindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                        ),
                    ),
                ),
            ) { it.close() }
            val textureBindGroup = resourceScope.track(
                device.createBindGroup(
                    BindGroupDescriptor(
                        layout = textureBindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(binding = 1u, resource = atlasView),
                            BindGroupEntry(binding = 2u, resource = sampler),
                        ),
                    ),
                ),
            ) { it.close() }

            setBindGroupAction(0u, bindGroup)
            setBindGroupAction(1u, textureBindGroup)
            setScissorAction(
                draw.scissorX.toUInt(),
                draw.scissorY.toUInt(),
                draw.scissorWidth.toUInt(),
                draw.scissorHeight.toUInt(),
            )
            setVertexBufferAction(0u, vertexBuffer)
            setIndexBufferAction(indexBuffer, GPUIndexFormat.Uint32)
            drawIndexedAction(indexData.size.toUInt())
        }
    }

    override fun drawColorGlyphPass(
        atlasRgba: ByteArray,
        atlasWidth: Int,
        atlasHeight: Int,
        atlasFormat: String,
        vertexData: FloatArray,
        indexData: IntArray,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode?,
    ) {
        require(atlasRgba.isNotEmpty()) { "atlasRgba must not be empty" }
        require(atlasWidth > 0) { "atlasWidth must be positive" }
        require(atlasHeight > 0) { "atlasHeight must be positive" }
        require(vertexData.isNotEmpty()) { "vertexData must not be empty" }
        require(indexData.isNotEmpty()) { "indexData must not be empty" }
        if (draws.isEmpty()) return

        val wgsl = colorGlyphCompositeWgsl()
        val textureFormat = atlasFormat.toWgpuTextureFormat()
        val keys = colorGlyphExecutionCacheKeys(wgsl = wgsl, targetFormat = targetFormat, textureFormat = textureFormat, blendMode = blendMode)
        executionCaches.recordPreimages(keys)

        val bindGroupLayout = executionCaches.bindGroupLayout(device = device, keys = keys)
        val textureBindGroupLayout = executionCaches.textureBindGroupLayout(device = device, keys = keys)
        val shader = executionCaches.shaderModule(device = device, wgsl = wgsl, keys = keys)
        val pipelineLayout = executionCaches.texturePipelineLayout(
            device = device,
            bindGroupLayouts = listOf(bindGroupLayout, textureBindGroupLayout),
            keys = keys,
        )
        val pipeline = executionCaches.textAtlasRenderPipeline(
            device = device,
            shader = shader,
            pipelineLayout = pipelineLayout,
            targetFormat = targetFormat,
            keys = keys,
            blendMode = blendMode,
        )

        val atlasTexture = resourceScope.track(
            device.createTexture(
                TextureDescriptor(
                    size = Extent3D(width = atlasWidth.toUInt(), height = atlasHeight.toUInt()),
                    format = textureFormat,
                    usage = GPUTextureUsage.TextureBinding or GPUTextureUsage.CopyDst,
                    label = "GPUBackend.color.atlas",
                ),
            ),
        ) { it.close() }
        val paddedAtlasBytesPerRow = alignCopyBytesPerRow(atlasWidth)
        val paddedAtlasData = if (paddedAtlasBytesPerRow == atlasWidth) {
            atlasRgba
        } else {
            val padded = ByteArray(paddedAtlasBytesPerRow * atlasHeight)
            for (row in 0 until atlasHeight) {
                System.arraycopy(atlasRgba, row * atlasWidth, padded, row * paddedAtlasBytesPerRow, atlasWidth)
            }
            padded
        }
        queue.writeTexture(
            destination = TexelCopyTextureInfo(texture = atlasTexture),
            data = ArrayBuffer.of(paddedAtlasData),
            dataLayout = TexelCopyBufferLayout(
                offset = 0uL,
                bytesPerRow = paddedAtlasBytesPerRow.toUInt(),
                rowsPerImage = atlasHeight.toUInt(),
            ),
            size = Extent3D(width = atlasWidth.toUInt(), height = atlasHeight.toUInt()),
        )
        val atlasView = resourceScope.track(atlasTexture.createView()) { it.close() }
        val sampler = resourceScope.track(
            device.createSampler(
                SamplerDescriptor(
                    addressModeU = GPUAddressMode.ClampToEdge,
                    addressModeV = GPUAddressMode.ClampToEdge,
                    magFilter = GPUFilterMode.Nearest,
                    minFilter = GPUFilterMode.Nearest,
                ),
            ),
        ) { it.close() }

        val vertexBuffer = resourceScope.track(
            device.createBuffer(
                BufferDescriptor(
                    size = (vertexData.size * 4).toULong(),
                    usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                    label = "GPUBackend.color.vertex",
                ),
            ),
        ) { it.close() }
        queue.writeBuffer(vertexBuffer, 0uL, ArrayBuffer.of(vertexData))

        val indexBuffer = resourceScope.track(
            device.createBuffer(
                BufferDescriptor(
                    size = (indexData.size * 4).toULong(),
                    usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
                    label = "GPUBackend.color.index",
                ),
            ),
        ) { it.close() }
        queue.writeBuffer(indexBuffer, 0uL, ArrayBuffer.of(indexData))

        setPipelineAction(pipeline)
        draws.forEach { draw ->
            val uniform = resourceScope.track(
                device.createBuffer(
                    BufferDescriptor(
                        size = draw.uniformBytes.size.toULong(),
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                        label = "GPUBackend.color.uniform",
                    ),
                ),
            ) { it.close() }
            queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(draw.uniformBytes))
            val bindGroup = resourceScope.track(
                device.createBindGroup(
                    BindGroupDescriptor(
                        layout = bindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                        ),
                    ),
                ),
            ) { it.close() }
            val textureBindGroup = resourceScope.track(
                device.createBindGroup(
                    BindGroupDescriptor(
                        layout = textureBindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(binding = 1u, resource = atlasView),
                            BindGroupEntry(binding = 2u, resource = sampler),
                        ),
                    ),
                ),
            ) { it.close() }

            setBindGroupAction(0u, bindGroup)
            setBindGroupAction(1u, textureBindGroup)
            setScissorAction(
                draw.scissorX.toUInt(),
                draw.scissorY.toUInt(),
                draw.scissorWidth.toUInt(),
                draw.scissorHeight.toUInt(),
            )
            setVertexBufferAction(0u, vertexBuffer)
            setIndexBufferAction(indexBuffer, GPUIndexFormat.Uint32)
            drawIndexedAction(indexData.size.toUInt())
        }
    }

    override fun drawCompositePass(
        wgsl: String,
        colorFormat: String,
        textureLabel: String,
        draws: List<GPUBackendRawUniformDraw>,
        blendMode: GPUBlendMode?,
    ) {
        require(wgsl.isNotBlank()) { "wgsl must not be blank" }
        require(colorFormat.normalizedColorFormat() == targetFormat.toBackendColorFormat()) {
            "Requested color format $colorFormat does not match target format ${targetFormat.toBackendColorFormat()}"
        }
        require(draws.isNotEmpty()) { "draws required for composite pass" }

        val tex = offscreenTextureStore[textureLabel]
            ?: error("Offscreen texture not found: $textureLabel")
        val textureFormat = GPUTextureFormat.RGBA8Unorm

        val textureKeys = fullscreenTextureExecutionCacheKeys(wgsl = wgsl, targetFormat = targetFormat, textureFormat = textureFormat, blendMode = blendMode)
        val fullKeys = fullscreenExecutionCacheKeys(wgsl = wgsl, targetFormat = targetFormat, blendMode = blendMode)

        val textureBindGroupLayout = executionCaches.textureBindGroupLayout(
            device = device,
            keys = textureKeys,
        )
        val bindGroupLayout = executionCaches.bindGroupLayout(
            device = device,
            keys = fullKeys,
        )
        val shader = executionCaches.shaderModule(device = device, wgsl = wgsl, keys = fullKeys)
        val pipelineLayout = executionCaches.texturePipelineLayout(
            device = device,
            bindGroupLayouts = listOf(bindGroupLayout, textureBindGroupLayout),
            keys = textureKeys,
        )
        val pipeline = executionCaches.renderPipeline(
            device = device,
            shader = shader,
            pipelineLayout = pipelineLayout,
            targetFormat = targetFormat,
            keys = textureKeys,
            blendMode = blendMode,
        )

        val textureView = resourceScope.track(tex.createView()) { it.close() }
        val sampler = resourceScope.track(
            device.createSampler(
                SamplerDescriptor(
                    addressModeU = GPUAddressMode.ClampToEdge,
                    addressModeV = GPUAddressMode.ClampToEdge,
                    magFilter = GPUFilterMode.Linear,
                    minFilter = GPUFilterMode.Linear,
                ),
            ),
        ) { it.close() }

        setPipelineAction(pipeline)
        draws.forEach { draw ->
            val uniform = resourceScope.track(
                device.createBuffer(
                    BufferDescriptor(
                        size = draw.uniformBytes.size.toULong(),
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                        label = "GPUBackend.composite.uniform",
                    ),
                ),
            ) { it.close() }
            queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(draw.uniformBytes))
            val bindGroup = resourceScope.track(
                device.createBindGroup(
                    BindGroupDescriptor(
                        layout = bindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                        ),
                    ),
                ),
            ) { it.close() }
            val textureBindGroup = resourceScope.track(
                device.createBindGroup(
                    BindGroupDescriptor(
                        layout = textureBindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(binding = 1u, resource = textureView),
                            BindGroupEntry(binding = 2u, resource = sampler),
                        ),
                    ),
                ),
            ) { it.close() }

            setBindGroupAction(0u, bindGroup)
            setBindGroupAction(1u, textureBindGroup)
            setScissorAction(
                draw.scissorX.toUInt(),
                draw.scissorY.toUInt(),
                draw.scissorWidth.toUInt(),
                draw.scissorHeight.toUInt(),
            )
            drawAction(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
        }
    }

    override fun drawBlendPass(
        wgsl: String,
        colorFormat: String,
        srcTextureLabel: String,
        dstTextureLabel: String,
        draws: List<GPUBackendRawUniformDraw>,
    ) {
        require(wgsl.isNotBlank()) { "wgsl must not be blank" }
        require(colorFormat.normalizedColorFormat() == targetFormat.toBackendColorFormat()) {
            "Requested color format $colorFormat does not match target format ${targetFormat.toBackendColorFormat()}"
        }
        require(draws.isNotEmpty()) { "draws required for blend pass" }

        val srcTex = offscreenTextureStore[srcTextureLabel]
            ?: error("Source texture not found: $srcTextureLabel")
        val dstTex = offscreenTextureStore[dstTextureLabel]
            ?: error("Destination texture not found: $dstTextureLabel")
        val textureFormat = GPUTextureFormat.RGBA8Unorm

        val textureKeys = blendTextureExecutionCacheKeys(wgsl = wgsl, targetFormat = targetFormat, textureFormat = textureFormat)
        val fullKeys = fullscreenExecutionCacheKeys(wgsl = wgsl, targetFormat = targetFormat)

        executionCaches.recordPreimages(textureKeys)
        val textureBindGroupLayout = executionCaches.blendTextureBindGroupLayout(
            device = device,
            keys = textureKeys,
        )
        val bindGroupLayout = executionCaches.bindGroupLayout(
            device = device,
            keys = fullKeys,
        )
        val shader = executionCaches.shaderModule(device = device, wgsl = wgsl, keys = fullKeys)
        val pipelineLayout = executionCaches.texturePipelineLayout(
            device = device,
            bindGroupLayouts = listOf(bindGroupLayout, textureBindGroupLayout),
            keys = textureKeys,
        )
        val pipeline = executionCaches.renderPipeline(
            device = device,
            shader = shader,
            pipelineLayout = pipelineLayout,
            targetFormat = targetFormat,
            keys = textureKeys,
        )

        val srcView = resourceScope.track(srcTex.createView()) { it.close() }
        val dstView = resourceScope.track(dstTex.createView()) { it.close() }
        val sampler = resourceScope.track(
            device.createSampler(
                SamplerDescriptor(
                    addressModeU = GPUAddressMode.ClampToEdge,
                    addressModeV = GPUAddressMode.ClampToEdge,
                    magFilter = GPUFilterMode.Linear,
                    minFilter = GPUFilterMode.Linear,
                ),
            ),
        ) { it.close() }

        setPipelineAction(pipeline)
        draws.forEach { draw ->
            val uniform = resourceScope.track(
                device.createBuffer(
                    BufferDescriptor(
                        size = draw.uniformBytes.size.toULong(),
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                        label = "GPUBackend.blend.uniform",
                    ),
                ),
            ) { it.close() }
            queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(draw.uniformBytes))
            val bindGroup = resourceScope.track(
                device.createBindGroup(
                    BindGroupDescriptor(
                        layout = bindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                        ),
                    ),
                ),
            ) { it.close() }
            val textureBindGroup = resourceScope.track(
                device.createBindGroup(
                    BindGroupDescriptor(
                        layout = textureBindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(binding = 1u, resource = srcView),
                            BindGroupEntry(binding = 2u, resource = sampler),
                            BindGroupEntry(binding = 3u, resource = dstView),
                            BindGroupEntry(binding = 4u, resource = sampler),
                        ),
                    ),
                ),
            ) { it.close() }
            setBindGroupAction(0u, bindGroup)
            setBindGroupAction(1u, textureBindGroup)
            setScissorAction(
                draw.scissorX.toUInt(),
                draw.scissorY.toUInt(),
                draw.scissorWidth.toUInt(),
                draw.scissorHeight.toUInt(),
            )
            drawAction(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
        }
    }

    private fun recordStencilWritePass(
        wgsl: String,
        colorFormat: String,
        triangleData: GPUBackendTriangleData,
    ) {
        val vertexBuffer = resourceScope.track(
            device.createBuffer(
                BufferDescriptor(
                    size = (triangleData.vertices.size * 4).toULong(),
                    usage = GPUBufferUsage.Vertex or GPUBufferUsage.CopyDst,
                    label = "GPUBackend.stencil.write.vertex",
                ),
            ),
        ) { it.close() }
        queue.writeBuffer(vertexBuffer, 0uL, ArrayBuffer.of(triangleData.vertices))

        val indexBuffer = resourceScope.track(
            device.createBuffer(
                BufferDescriptor(
                    size = (triangleData.indices.size * 4).toULong(),
                    usage = GPUBufferUsage.Index or GPUBufferUsage.CopyDst,
                    label = "GPUBackend.stencil.write.index",
                ),
            ),
        ) { it.close() }
        queue.writeBuffer(indexBuffer, 0uL, ArrayBuffer.of(triangleData.indices))

        val keys = stencilExecutionCacheKeys(wgsl = wgsl, targetFormat = targetFormat, vertexStage = true)
        executionCaches.recordPreimages(keys)
        val pipeline = executionCaches.stencilWriteRenderPipeline(
            device = device,
            wgsl = wgsl,
            targetFormat = targetFormat,
            keys = keys,
        )

        setPipelineAction(pipeline)
        setStencilReferenceAction(0u)
        setVertexBufferAction(0u, vertexBuffer)
        setIndexBufferAction(indexBuffer, GPUIndexFormat.Uint32)
        drawIndexedAction(triangleData.indices.size.toUInt())
    }

    private fun recordStencilTestPass(
        wgsl: String,
        colorFormat: String,
        draws: List<WgpuFullscreenUniformDraw>,
        blendMode: GPUBlendMode? = null,
    ) {
        val keys = stencilExecutionCacheKeys(wgsl = wgsl, targetFormat = targetFormat, vertexStage = false, blendMode = blendMode)
        executionCaches.recordPreimages(keys)
        val bindGroupLayout = executionCaches.bindGroupLayout(device = device, keys = keys)
        val shader = executionCaches.shaderModule(device = device, wgsl = wgsl, keys = keys)
        val pipelineLayout = executionCaches.pipelineLayout(device = device, bindGroupLayout = bindGroupLayout, keys = keys)
        val pipeline = executionCaches.stencilTestRenderPipeline(
            device = device,
            shader = shader,
            pipelineLayout = pipelineLayout,
            targetFormat = targetFormat,
            keys = keys,
            blendMode = blendMode,
        )

        setPipelineAction(pipeline)
        setStencilReferenceAction(0u)
        draws.forEach { draw ->
            val uniform = resourceScope.track(
                device.createBuffer(
                    BufferDescriptor(
                        size = draw.uniformSizeBytes,
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                        label = "GPUBackend.stencil.test.color",
                    ),
                ),
            ) { it.close() }
            queue.writeBuffer(uniform, 0uL, draw.uniformPayload)
            val bindGroup = resourceScope.track(
                device.createBindGroup(
                    BindGroupDescriptor(
                        layout = bindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                        ),
                    ),
                ),
            ) { it.close() }
            setBindGroupAction(0u, bindGroup)
            setScissorAction(
                draw.scissorX.toUInt(),
                draw.scissorY.toUInt(),
                draw.scissorWidth.toUInt(),
                draw.scissorHeight.toUInt(),
            )
            drawAction(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
        }
    }

    private fun recordFullscreenTextureUniformPass(
        wgsl: String,
        colorFormat: String,
        textureRgba: ByteArray,
        textureWidth: Int,
        textureHeight: Int,
        textureFormat: String,
        draws: List<WgpuFullscreenUniformDraw>,
        blendMode: GPUBlendMode? = null,
    ) {
        require(wgsl.isNotBlank()) { "wgsl must not be blank" }
        require(colorFormat.normalizedColorFormat() == targetFormat.toBackendColorFormat()) {
            "Requested color format $colorFormat does not match target format ${targetFormat.toBackendColorFormat()}"
        }
        require(textureRgba.isNotEmpty()) { "textureRgba must not be empty" }
        require(textureWidth > 0) { "textureWidth must be positive" }
        require(textureHeight > 0) { "textureHeight must be positive" }
        if (draws.isEmpty()) return

        val gpuTextureFormat = textureFormat.toWgpuTextureFormat()
        val textureBytesPerPixel = gpuTextureFormat.bytesPerPixel()
        val tightTextureBytesPerRow = textureWidth * textureBytesPerPixel
        val paddedTextureBytesPerRow = alignCopyBytesPerRow(tightTextureBytesPerRow)
        val paddedTextureData = if (paddedTextureBytesPerRow == tightTextureBytesPerRow) {
            textureRgba
        } else {
            val padded = ByteArray(paddedTextureBytesPerRow * textureHeight)
            for (row in 0 until textureHeight) {
                System.arraycopy(
                    textureRgba, row * tightTextureBytesPerRow,
                    padded, row * paddedTextureBytesPerRow,
                    tightTextureBytesPerRow,
                )
            }
            padded
        }

        val keys = fullscreenTextureExecutionCacheKeys(
            wgsl = wgsl,
            targetFormat = targetFormat,
            textureFormat = gpuTextureFormat,
            blendMode = blendMode,
        )
        executionCaches.recordPreimages(keys)
        val bindGroupLayout = executionCaches.bindGroupLayout(device = device, keys = keys)
        val textureBindGroupLayout = executionCaches.textureBindGroupLayout(device = device, keys = keys)
        val shader = executionCaches.shaderModule(device = device, wgsl = wgsl, keys = keys)
        val pipelineLayout = executionCaches.texturePipelineLayout(
            device = device,
            bindGroupLayouts = listOf(bindGroupLayout, textureBindGroupLayout),
            keys = keys,
        )
        val pipeline = executionCaches.renderPipeline(
            device = device,
            shader = shader,
            pipelineLayout = pipelineLayout,
            targetFormat = targetFormat,
            keys = keys,
            blendMode = blendMode,
        )

        val texture = resourceScope.track(
            device.createTexture(
                TextureDescriptor(
                    size = Extent3D(width = textureWidth.toUInt(), height = textureHeight.toUInt()),
                    format = gpuTextureFormat,
                    usage = GPUTextureUsage.TextureBinding or GPUTextureUsage.CopyDst,
                    label = "GPUBackend.texture.uniform",
                ),
            ),
        ) { it.close() }
        queue.writeTexture(
            destination = TexelCopyTextureInfo(texture = texture),
            data = ArrayBuffer.of(paddedTextureData),
            dataLayout = TexelCopyBufferLayout(
                offset = 0uL,
                bytesPerRow = paddedTextureBytesPerRow.toUInt(),
                rowsPerImage = textureHeight.toUInt(),
            ),
            size = Extent3D(width = textureWidth.toUInt(), height = textureHeight.toUInt()),
        )
        val textureView = resourceScope.track(texture.createView()) { it.close() }
        val sampler = resourceScope.track(
            device.createSampler(
                SamplerDescriptor(
                    addressModeU = GPUAddressMode.ClampToEdge,
                    addressModeV = GPUAddressMode.ClampToEdge,
                    magFilter = GPUFilterMode.Nearest,
                    minFilter = GPUFilterMode.Nearest,
                ),
            ),
        ) { it.close() }

        setPipelineAction(pipeline)
        draws.forEach { draw ->
            val uniform = resourceScope.track(
                device.createBuffer(
                    BufferDescriptor(
                        size = draw.uniformSizeBytes,
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                        label = "GPUBackend.texture.color",
                    ),
                ),
            ) { it.close() }
            queue.writeBuffer(uniform, 0uL, draw.uniformPayload)
            val bindGroup = resourceScope.track(
                device.createBindGroup(
                    BindGroupDescriptor(
                        layout = bindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                        ),
                    ),
                ),
            ) { it.close() }
            val textureBindGroup = resourceScope.track(
                device.createBindGroup(
                    BindGroupDescriptor(
                        layout = textureBindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(binding = 1u, resource = textureView),
                            BindGroupEntry(binding = 2u, resource = sampler),
                        ),
                    ),
                ),
            ) { it.close() }

            setBindGroupAction(0u, bindGroup)
            setBindGroupAction(1u, textureBindGroup)
            setScissorAction(
                draw.scissorX.toUInt(),
                draw.scissorY.toUInt(),
                draw.scissorWidth.toUInt(),
                draw.scissorHeight.toUInt(),
            )
            drawAction(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
        }
    }

    private fun recordFullscreenUniformPass(
        wgsl: String,
        colorFormat: String,
        draws: List<WgpuFullscreenUniformDraw>,
        blendMode: GPUBlendMode? = null,
    ) {
        require(wgsl.isNotBlank()) { "wgsl must not be blank" }
        require(colorFormat.normalizedColorFormat() == targetFormat.toBackendColorFormat()) {
            "Requested color format $colorFormat does not match target format ${targetFormat.toBackendColorFormat()}"
        }
        if (draws.isEmpty()) return

        val keys = fullscreenExecutionCacheKeys(wgsl = wgsl, targetFormat = targetFormat, blendMode = blendMode)
        executionCaches.recordPreimages(keys)
        val bindGroupLayout = executionCaches.bindGroupLayout(device = device, keys = keys)
        val shader = executionCaches.shaderModule(device = device, wgsl = wgsl, keys = keys)
        val pipelineLayout = executionCaches.pipelineLayout(
            device = device,
            bindGroupLayout = bindGroupLayout,
            keys = keys,
        )
        val pipeline = executionCaches.renderPipeline(
            device = device,
            shader = shader,
            pipelineLayout = pipelineLayout,
            targetFormat = targetFormat,
            keys = keys,
            blendMode = blendMode,
        )

        setPipelineAction(pipeline)
        draws.forEach { draw ->
            val uniform = resourceScope.track(
                device.createBuffer(
                    BufferDescriptor(
                        size = draw.uniformSizeBytes,
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                        label = "GPUBackend.rect.color",
                    ),
                ),
            ) { it.close() }
            queue.writeBuffer(uniform, 0uL, draw.uniformPayload)
            val bindGroup = resourceScope.track(
                device.createBindGroup(
                    BindGroupDescriptor(
                        layout = bindGroupLayout,
                        entries = listOf(
                            BindGroupEntry(binding = 0u, resource = BufferBinding(buffer = uniform)),
                        ),
                    ),
                ),
            ) { it.close() }

            setBindGroupAction(0u, bindGroup)
            setScissorAction(
                draw.scissorX.toUInt(),
                draw.scissorY.toUInt(),
                draw.scissorWidth.toUInt(),
                draw.scissorHeight.toUInt(),
            )
            drawAction(FULL_SCREEN_TRIANGLE_VERTEX_COUNT)
        }
    }

    private data class WgpuFullscreenUniformDraw(
        val uniformPayload: ArrayBuffer,
        val uniformSizeBytes: ULong,
        val scissorX: Int,
        val scissorY: Int,
        val scissorWidth: Int,
        val scissorHeight: Int,
    )

    private fun createTexture(rgba: ByteArray, width: Int, height: Int, format: String): GPUTexture {
        val gpuFormat = format.toWgpuTextureFormat()
        val texture = resourceScope.track(
            device.createTexture(
                TextureDescriptor(
                    size = Extent3D(width = width.toUInt(), height = height.toUInt()),
                    format = gpuFormat,
                    usage = GPUTextureUsage.TextureBinding or GPUTextureUsage.CopyDst,
                    label = "GPUBackend.texturedVertex.tex",
                ),
            ),
        ) { it.close() }
        val textureBytesPerPixel = gpuFormat.bytesPerPixel()
        val tightBytesPerRow = width * textureBytesPerPixel
        val paddedBytesPerRow = alignCopyBytesPerRow(tightBytesPerRow)
        val paddedData = if (paddedBytesPerRow == tightBytesPerRow) {
            rgba
        } else {
            val padded = ByteArray(paddedBytesPerRow * height)
            for (row in 0 until height) {
                System.arraycopy(rgba, row * tightBytesPerRow, padded, row * paddedBytesPerRow, tightBytesPerRow)
            }
            padded
        }
        queue.writeTexture(
            destination = TexelCopyTextureInfo(texture = texture),
            data = ArrayBuffer.of(paddedData),
            dataLayout = TexelCopyBufferLayout(
                offset = 0uL,
                bytesPerRow = paddedBytesPerRow.toUInt(),
                rowsPerImage = height.toUInt(),
            ),
            size = Extent3D(width = width.toUInt(), height = height.toUInt()),
        )
        return texture
    }

    private fun createUniformBuffer(uniformBytes: ByteArray): GPUBuffer {
        val buffer = resourceScope.track(
            device.createBuffer(
                BufferDescriptor(
                    size = uniformBytes.size.toULong(),
                    usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                    label = "GPUBackend.texturedVertex.uniform",
                ),
            ),
        ) { it.close() }
        queue.writeBuffer(buffer, 0uL, ArrayBuffer.of(uniformBytes))
        return buffer
    }

    private fun getOrCreateTexturedVertexBindGroupLayout(): GPUBindGroupLayout {
        if (texturedVertexBindGroupLayout == null) {
            texturedVertexBindGroupLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "texturedVertexLayout",
                    entries = listOf(
                        BindGroupLayoutEntry(
                            binding = 0u,
                            visibility = GPUShaderStage.Vertex or GPUShaderStage.Fragment,
                            buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                        ),
                        BindGroupLayoutEntry(
                            binding = 1u,
                            visibility = GPUShaderStage.Fragment,
                            texture = TextureBindingLayout(
                                sampleType = GPUTextureSampleType.Float,
                                viewDimension = GPUTextureViewDimension.TwoD,
                                multisampled = false,
                            ),
                        ),
                        BindGroupLayoutEntry(
                            binding = 2u,
                            visibility = GPUShaderStage.Fragment,
                            sampler = SamplerBindingLayout(type = GPUSamplerBindingType.Filtering),
                        ),
                    ),
                ),
            )
        }
        return texturedVertexBindGroupLayout!!
    }

    private fun getOrCreateTexturedVertexPipeline(colorFormat: String, blendMode: GPUBlendMode?): GPURenderPipeline {
        val key = "$colorFormat:${blendMode?.name ?: "none"}"
        return texturedVertexPipelineCache.getOrPut(key) {
            createTexturedVertexPipeline(colorFormat, blendMode)
        }
    }

    private fun createTexturedVertexPipeline(colorFormat: String, blendMode: GPUBlendMode?): GPURenderPipeline {
        val shaderModule = device.createShaderModule(
            ShaderModuleDescriptor(label = "texturedVertex:$colorFormat", code = TexturedVerticesWgsl),
        )
        val bindGroupLayout = getOrCreateTexturedVertexBindGroupLayout()
        val pipelineLayout = device.createPipelineLayout(
            PipelineLayoutDescriptor(
                label = "texturedVertexLayout",
                bindGroupLayouts = listOf(bindGroupLayout),
            ),
        )
        try {
            return device.createRenderPipelineWithValidationScope(
                RenderPipelineDescriptor(
                    label = "texturedVertex:$colorFormat:${blendMode?.name ?: "none"}",
                    layout = pipelineLayout,
                    vertex = VertexState(
                        module = shaderModule,
                        entryPoint = "vs_main",
                        buffers = listOf(
                            VertexBufferLayout(
                                arrayStride = 16uL,
                                attributes = listOf(
                                    VertexAttribute(
                                        shaderLocation = 0u,
                                        offset = 0uL,
                                        format = GPUVertexFormat.Float32x2,
                                    ),
                                    VertexAttribute(
                                        shaderLocation = 1u,
                                        offset = 8uL,
                                        format = GPUVertexFormat.Float32x2,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    primitive = PrimitiveState(),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = GPUCompareFunction.Always,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = GPUCompareFunction.Always,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = 0u,
                        stencilWriteMask = 0u,
                    ),
                    fragment = FragmentState(
                        module = shaderModule,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = targetFormat,
                                blend = blendStateFor(blendMode),
                            ),
                        ),
                    ),
                ),
            )
        } finally {
            shaderModule.close()
            pipelineLayout.close()
        }
    }

    private fun getOrCreateDualUVVertexBindGroupLayout(): GPUBindGroupLayout {
        if (dualUVVertexBindGroupLayout == null) {
            dualUVVertexBindGroupLayout = device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    label = "dualUVVertexLayout",
                    entries = listOf(
                        BindGroupLayoutEntry(
                            binding = 0u,
                            visibility = GPUShaderStage.Vertex or GPUShaderStage.Fragment,
                            buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                        ),
                        BindGroupLayoutEntry(
                            binding = 1u,
                            visibility = GPUShaderStage.Fragment,
                            texture = TextureBindingLayout(
                                sampleType = GPUTextureSampleType.Float,
                                viewDimension = GPUTextureViewDimension.TwoD,
                                multisampled = false,
                            ),
                        ),
                        BindGroupLayoutEntry(
                            binding = 2u,
                            visibility = GPUShaderStage.Fragment,
                            sampler = SamplerBindingLayout(type = GPUSamplerBindingType.Filtering),
                        ),
                        BindGroupLayoutEntry(
                            binding = 3u,
                            visibility = GPUShaderStage.Fragment,
                            texture = TextureBindingLayout(
                                sampleType = GPUTextureSampleType.Float,
                                viewDimension = GPUTextureViewDimension.TwoD,
                                multisampled = false,
                            ),
                        ),
                        BindGroupLayoutEntry(
                            binding = 4u,
                            visibility = GPUShaderStage.Fragment,
                            sampler = SamplerBindingLayout(type = GPUSamplerBindingType.Filtering),
                        ),
                    ),
                ),
            )
        }
        return dualUVVertexBindGroupLayout!!
    }

    private fun getOrCreateDualUVVertexPipeline(colorFormat: String, blendMode: GPUBlendMode?): GPURenderPipeline {
        val key = "$colorFormat:${blendMode?.name ?: "none"}"
        return dualUVVertexPipelineCache.getOrPut(key) {
            createDualUVVertexPipeline(colorFormat, blendMode)
        }
    }

    private fun createDualUVVertexPipeline(colorFormat: String, blendMode: GPUBlendMode?): GPURenderPipeline {
        val shaderModule = device.createShaderModule(
            ShaderModuleDescriptor(label = "dualUVVertex:$colorFormat", code = TexturedVerticesDualBlendWgsl),
        )
        val bindGroupLayout = getOrCreateDualUVVertexBindGroupLayout()
        val pipelineLayout = device.createPipelineLayout(
            PipelineLayoutDescriptor(
                label = "dualUVVertexLayout",
                bindGroupLayouts = listOf(bindGroupLayout),
            ),
        )
        try {
            return device.createRenderPipelineWithValidationScope(
                RenderPipelineDescriptor(
                    label = "dualUVVertex:$colorFormat:${blendMode?.name ?: "none"}",
                    layout = pipelineLayout,
                    vertex = VertexState(
                        module = shaderModule,
                        entryPoint = "vs_main",
                        buffers = listOf(
                            VertexBufferLayout(
                                arrayStride = 24uL,
                                attributes = listOf(
                                    VertexAttribute(
                                        shaderLocation = 0u,
                                        offset = 0uL,
                                        format = GPUVertexFormat.Float32x2,
                                    ),
                                    VertexAttribute(
                                        shaderLocation = 1u,
                                        offset = 8uL,
                                        format = GPUVertexFormat.Float32x2,
                                    ),
                                    VertexAttribute(
                                        shaderLocation = 2u,
                                        offset = 16uL,
                                        format = GPUVertexFormat.Float32x2,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    primitive = PrimitiveState(),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = GPUCompareFunction.Always,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = GPUCompareFunction.Always,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = 0u,
                        stencilWriteMask = 0u,
                    ),
                    fragment = FragmentState(
                        module = shaderModule,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = targetFormat,
                                blend = blendStateFor(blendMode),
                            ),
                        ),
                    ),
                ),
            )
        } finally {
            shaderModule.close()
            pipelineLayout.close()
        }
    }

    companion object {
        val VERTEX_COLOR_WGSL: String = """
struct Uniforms { color: vec4f };

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

struct VertexInput {
    @location(0) position: vec2f,
    @location(1) color: vec4f,
};

struct VertexOutput {
    @builtin(position) position: vec4f,
    @location(0) color: vec4f,
};

@vertex
fn vs_main(in: VertexInput) -> VertexOutput {
    let nx = in.position.x / 160.0 - 1.0;
    let ny = 1.0 - in.position.y / 100.0;
    var out: VertexOutput;
    out.position = vec4f(nx, ny, 0.0, 1.0);
    out.color = in.color;
    return out;
}

@fragment
fn fs_main(in: VertexOutput) -> @location(0) vec4f {
    return in.color * uniforms.color;
}
""".trimIndent()

        val STENCIL_WRITE_VERTEX_WGSL: String = """
struct VertexInput {
    @location(0) position: vec2f,
};

@vertex
fn vs_main(in: VertexInput) -> @builtin(position) vec4f {
    return vec4f(in.position.x / 160.0 - 1.0, 1.0 - in.position.y / 100.0, 0.0, 1.0);
}

@fragment
fn fs_main() -> @location(0) vec4f {
    return vec4f(0.0, 0.0, 0.0, 0.0);
}
""".trimIndent()

        val STENCIL_TEST_FULLSCREEN_WGSL: String = """
struct Uniforms { color: vec4f };

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

@vertex
fn vs_main(@builtin(vertex_index) idx: u32) -> @builtin(position) vec4f {
    let x = f32((idx << 1u) & 2u) * 2.0 - 1.0;
    let y = f32(idx & 2u) * 2.0 - 1.0;
    return vec4f(x, y, 0.0, 1.0);
}

@fragment
fn fs_main() -> @location(0) vec4f {
    return uniforms.color;
}
""".trimIndent()

        val TEXT_ATLAS_A8_WGSL: String = """
struct Uniforms {
    targetWidth: f32,
    targetHeight: f32,
    color: vec4f,
};

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

struct VertexInput {
    @location(0) position: vec2<f32>,
    @location(1) texcoord: vec2<f32>,
};

struct VertexOutput {
    @builtin(position) position: vec4<f32>,
    @location(0) texcoord: vec2<f32>,
};

@vertex
fn vs_main(in: VertexInput) -> VertexOutput {
    var out: VertexOutput;
    out.position = vec4<f32>(
        in.position.x / uniforms.targetWidth * 2.0 - 1.0,
        1.0 - in.position.y / uniforms.targetHeight * 2.0,
        0.0,
        1.0
    );
    out.texcoord = in.texcoord;
    return out;
}

@group(1) @binding(1) var a8_atlas_texture: texture_2d<f32>;
@group(1) @binding(2) var a8_atlas_sampler: sampler;

@fragment
fn fs_main(in: VertexOutput) -> @location(0) vec4<f32> {
    let a8 = textureSample(a8_atlas_texture, a8_atlas_sampler, in.texcoord).r;
    return vec4<f32>(uniforms.color.rgb, a8 * uniforms.color.a);
}
""".trimIndent()
    }
}

private class WgpuExecutionCaches(
    private val deviceGeneration: GPUDeviceGeneration,
) : AutoCloseable {
    private val moduleCache = GPUExecutionObjectCache(
        domain = GPUExecutionCacheDomain.Module,
        dispose = GPUShaderModule::close,
    )
    private val bindGroupLayoutCache =
        GPUExecutionObjectCache(
            domain = GPUExecutionCacheDomain.BindGroupLayout,
            dispose = GPUBindGroupLayout::close,
        )
    private val pipelineLayoutCache =
        GPUExecutionObjectCache(
            domain = GPUExecutionCacheDomain.PipelineLayout,
            dispose = GPUPipelineLayout::close,
        )
    private val renderPipelineCache =
        GPUExecutionObjectCache(
            domain = GPUExecutionCacheDomain.RenderPipeline,
            dispose = GPURenderPipeline::close,
        )
    private var ledger = GPUTelemetryLedger.empty()
    private val recordedDumpLines = mutableListOf<String>()
    private val recordedPreimageKeys = linkedSetOf<String>()

    val cacheTelemetry: List<GPUCacheTelemetry>
        get() = ledger.cacheTelemetry

    val dumpLines: List<String>
        get() = recordedDumpLines.toList()

    /** Records stable cache-key preimage dumps once per cache key. */
    fun recordPreimages(keys: FullscreenExecutionCacheKeys) {
        keys.preimageDumpLines().forEach { line ->
            if (recordedPreimageKeys.add(line)) {
                recordedDumpLines += line
            }
        }
    }

    /** Returns a cached shader module for the stable fullscreen WGSL identity. */
    fun shaderModule(
        device: GPUDevice,
        wgsl: String,
        keys: FullscreenExecutionCacheKeys,
    ): GPUShaderModule {
        val decision = moduleCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.Module,
                keyHash = keys.moduleKeyHash,
                subjectHash = keys.moduleSubjectHash,
            ),
        ) {
            device.createShaderModule(ShaderModuleDescriptor(code = wgsl))
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached bind-group layout accepted by the fullscreen uniform lane. */
    fun bindGroupLayout(
        device: GPUDevice,
        keys: FullscreenExecutionCacheKeys,
    ): GPUBindGroupLayout {
        val decision = bindGroupLayoutCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.BindGroupLayout,
                keyHash = keys.bindGroupLayoutKeyHash,
                subjectHash = keys.bindGroupLayoutSubjectHash,
            ),
        ) {
            device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    entries = listOf(
                    BindGroupLayoutEntry(
                        binding = 0u,
                        visibility = GPUShaderStage.Vertex or GPUShaderStage.Fragment,
                        buffer = BufferBindingLayout(type = GPUBufferBindingType.Uniform),
                    ),
                    ),
                ),
            )
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached pipeline layout derived from the stable bind-group layout key. */
    fun pipelineLayout(
        device: GPUDevice,
        bindGroupLayout: GPUBindGroupLayout,
        keys: FullscreenExecutionCacheKeys,
    ): GPUPipelineLayout {
        val decision = pipelineLayoutCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.PipelineLayout,
                keyHash = keys.pipelineLayoutKeyHash,
                subjectHash = keys.pipelineLayoutSubjectHash,
            ),
        ) {
            device.createPipelineLayout(
                PipelineLayoutDescriptor(bindGroupLayouts = listOf(bindGroupLayout)),
            )
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached texture bind-group layout (binding 1=texture, binding 2=sampler) at @group(1). */
    fun textureBindGroupLayout(
        device: GPUDevice,
        keys: FullscreenExecutionCacheKeys,
    ): GPUBindGroupLayout {
        val decision = bindGroupLayoutCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.BindGroupLayout,
                keyHash = keys.textureBindGroupLayoutKeyHash,
                subjectHash = keys.textureBindGroupLayoutSubjectHash,
            ),
        ) {
            device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    entries = listOf(
                        BindGroupLayoutEntry(
                            binding = 1u,
                            visibility = GPUShaderStage.Fragment,
                            texture = TextureBindingLayout(
                                sampleType = GPUTextureSampleType.Float,
                                viewDimension = GPUTextureViewDimension.TwoD,
                                multisampled = false,
                            ),
                        ),
                        BindGroupLayoutEntry(
                            binding = 2u,
                            visibility = GPUShaderStage.Fragment,
                            sampler = SamplerBindingLayout(type = GPUSamplerBindingType.Filtering),
                        ),
                    ),
                ),
            )
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns cached dual-texture bind-group layout (bindings 1-4: src tex+sampler, dst tex+sampler) at @group(1). */
    fun blendTextureBindGroupLayout(
        device: GPUDevice,
        keys: FullscreenExecutionCacheKeys,
    ): GPUBindGroupLayout {
        val decision = bindGroupLayoutCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.BindGroupLayout,
                keyHash = keys.textureBindGroupLayoutKeyHash,
                subjectHash = keys.textureBindGroupLayoutSubjectHash,
            ),
        ) {
            device.createBindGroupLayout(
                BindGroupLayoutDescriptor(
                    entries = listOf(
                        BindGroupLayoutEntry(
                            binding = 1u,
                            visibility = GPUShaderStage.Fragment,
                            texture = TextureBindingLayout(
                                sampleType = GPUTextureSampleType.Float,
                                viewDimension = GPUTextureViewDimension.TwoD,
                                multisampled = false,
                            ),
                        ),
                        BindGroupLayoutEntry(
                            binding = 2u,
                            visibility = GPUShaderStage.Fragment,
                            sampler = SamplerBindingLayout(type = GPUSamplerBindingType.Filtering),
                        ),
                        BindGroupLayoutEntry(
                            binding = 3u,
                            visibility = GPUShaderStage.Fragment,
                            texture = TextureBindingLayout(
                                sampleType = GPUTextureSampleType.Float,
                                viewDimension = GPUTextureViewDimension.TwoD,
                                multisampled = false,
                            ),
                        ),
                        BindGroupLayoutEntry(
                            binding = 4u,
                            visibility = GPUShaderStage.Fragment,
                            sampler = SamplerBindingLayout(type = GPUSamplerBindingType.Filtering),
                        ),
                    ),
                ),
            )
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached pipeline layout for texture passes with two bind-group layouts. */
    fun texturePipelineLayout(
        device: GPUDevice,
        bindGroupLayouts: List<GPUBindGroupLayout>,
        keys: FullscreenExecutionCacheKeys,
    ): GPUPipelineLayout {
        val decision = pipelineLayoutCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.PipelineLayout,
                keyHash = keys.pipelineLayoutKeyHash,
                subjectHash = keys.pipelineLayoutSubjectHash,
            ),
        ) {
            device.createPipelineLayout(
                PipelineLayoutDescriptor(bindGroupLayouts = bindGroupLayouts),
            )
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached render pipeline for the module, layout, target, and blend-state facts. */
    fun renderPipeline(
        device: GPUDevice,
        shader: GPUShaderModule,
        pipelineLayout: GPUPipelineLayout,
        targetFormat: GPUTextureFormat,
        keys: FullscreenExecutionCacheKeys,
        blendMode: GPUBlendMode? = null,
    ): GPURenderPipeline {
        val decision = renderPipelineCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.RenderPipeline,
                keyHash = keys.renderPipelineKeyHash,
                subjectHash = keys.renderPipelineSubjectHash,
            ),
        ) {
            device.createRenderPipelineWithValidationScope(
                RenderPipelineDescriptor(
                    layout = pipelineLayout,
                    vertex = VertexState(module = shader, entryPoint = "vs_main"),
                    primitive = PrimitiveState(),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = GPUCompareFunction.Always,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = GPUCompareFunction.Always,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = 0u,
                        stencilWriteMask = 0u,
                    ),
                    fragment = FragmentState(
                        module = shader,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = targetFormat,
                                blend = blendStateFor(blendMode),
                            ),
                        ),
                    ),
                ),
            )
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached stencil-write render pipeline with vertex buffer input, no color writes, and winding stencil ops. */
    fun stencilWriteRenderPipeline(
        device: GPUDevice,
        wgsl: String,
        targetFormat: GPUTextureFormat,
        keys: FullscreenExecutionCacheKeys,
    ): GPURenderPipeline {
        val decision = renderPipelineCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.RenderPipeline,
                keyHash = keys.renderPipelineKeyHash,
                subjectHash = keys.renderPipelineSubjectHash,
            ),
        ) {
            val shader = device.createShaderModule(ShaderModuleDescriptor(code = wgsl))
            try {
                val pipelineLayout = device.createPipelineLayout(
                    PipelineLayoutDescriptor(bindGroupLayouts = emptyList()),
                )
                device.createRenderPipelineWithValidationScope(
                    RenderPipelineDescriptor(
                        layout = pipelineLayout,
                        vertex = VertexState(
                            module = shader,
                            entryPoint = "vs_main",
                            buffers = listOf(
                                VertexBufferLayout(
                                    arrayStride = 8uL,
                                    attributes = listOf(
                                        VertexAttribute(
                                            shaderLocation = 0u,
                                            offset = 0uL,
                                            format = GPUVertexFormat.Float32x2,
                                        ),
                                    ),
                                ),
                            ),
                        ),
                        primitive = PrimitiveState(),
                        depthStencil = DepthStencilState(
                            format = GPUTextureFormat.Depth24PlusStencil8,
                            depthWriteEnabled = false,
                            depthCompare = GPUCompareFunction.Always,
                            stencilFront = StencilFaceState(
                                compare = GPUCompareFunction.Always,
                                failOp = GPUStencilOperation.Keep,
                                depthFailOp = GPUStencilOperation.Keep,
                                passOp = GPUStencilOperation.IncrementWrap,
                            ),
                            stencilBack = StencilFaceState(
                                compare = GPUCompareFunction.Always,
                                failOp = GPUStencilOperation.Keep,
                                depthFailOp = GPUStencilOperation.Keep,
                                passOp = GPUStencilOperation.DecrementWrap,
                            ),
                            stencilReadMask = 0xFFu,
                            stencilWriteMask = 0xFFu,
                        ),
                        fragment = FragmentState(
                            module = shader,
                            entryPoint = "fs_main",
                            targets = listOf(
                                ColorTargetState(
                                    format = targetFormat,
                                    blend = BlendState(
                                        color = BlendComponent(
                                            operation = GPUBlendOperation.Add,
                                            srcFactor = io.ygdrasil.webgpu.GPUBlendFactor.One,
                                            dstFactor = io.ygdrasil.webgpu.GPUBlendFactor.Zero,
                                        ),
                                        alpha = BlendComponent(
                                            operation = GPUBlendOperation.Add,
                                            srcFactor = io.ygdrasil.webgpu.GPUBlendFactor.One,
                                            dstFactor = io.ygdrasil.webgpu.GPUBlendFactor.Zero,
                                        ),
                                    ),
                                    writeMask = GPUColorWrite.None,
                                ),
                            ),
                        ),
                    ),
                )
            } finally {
                shader.close()
            }
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached stencil-test render pipeline that fills pixels where stencil != 0 with the fragment color. */
    fun stencilTestRenderPipeline(
        device: GPUDevice,
        shader: GPUShaderModule,
        pipelineLayout: GPUPipelineLayout,
        targetFormat: GPUTextureFormat,
        keys: FullscreenExecutionCacheKeys,
        blendMode: GPUBlendMode? = null,
    ): GPURenderPipeline {
        val decision = renderPipelineCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.RenderPipeline,
                keyHash = keys.renderPipelineKeyHash,
                subjectHash = keys.renderPipelineSubjectHash,
            ),
        ) {
            device.createRenderPipelineWithValidationScope(
                RenderPipelineDescriptor(
                    layout = pipelineLayout,
                    vertex = VertexState(module = shader, entryPoint = "vs_main"),
                    primitive = PrimitiveState(),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = GPUCompareFunction.NotEqual,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = GPUCompareFunction.NotEqual,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = 0xFFu,
                        stencilWriteMask = 0xFFu,
                    ),
                    fragment = FragmentState(
                        module = shader,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = targetFormat,
                                blend = blendStateFor(blendMode),
                            ),
                        ),
                    ),
                ),
            )
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached vertex-color render pipeline with interleaved position+color vertex buffer input. */
    fun vertexColorRenderPipeline(
        device: GPUDevice,
        shader: GPUShaderModule,
        pipelineLayout: GPUPipelineLayout,
        targetFormat: GPUTextureFormat,
        keys: FullscreenExecutionCacheKeys,
        blendMode: GPUBlendMode? = null,
    ): GPURenderPipeline {
        val decision = renderPipelineCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.RenderPipeline,
                keyHash = keys.renderPipelineKeyHash,
                subjectHash = keys.renderPipelineSubjectHash,
            ),
        ) {
            device.createRenderPipelineWithValidationScope(
                RenderPipelineDescriptor(
                    layout = pipelineLayout,
                    vertex = VertexState(
                        module = shader,
                        entryPoint = "vs_main",
                        buffers = listOf(
                            VertexBufferLayout(
                                arrayStride = VERTEX_COLOR_STRIDE_BYTES.toULong(),
                                attributes = listOf(
                                    VertexAttribute(
                                        shaderLocation = 0u,
                                        offset = 0uL,
                                        format = GPUVertexFormat.Float32x2,
                                    ),
                            VertexAttribute(
                                shaderLocation = 1u,
                                offset = 16uL,
                                format = GPUVertexFormat.Float32x4,
                            ),
                                ),
                            ),
                        ),
                    ),
                    primitive = PrimitiveState(),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = GPUCompareFunction.Always,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = GPUCompareFunction.Always,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = 0u,
                        stencilWriteMask = 0u,
                    ),
                    fragment = FragmentState(
                        module = shader,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = targetFormat,
                                blend = blendStateFor(blendMode),
                            ),
                        ),
                    ),
                ),
            )
        }
        record(decision)
        return decision.readyHandle()
    }

    /** Returns the cached text atlas A8 render pipeline with position+texcoord vertex buffers and indexed draw. */
    fun textAtlasRenderPipeline(
        device: GPUDevice,
        shader: GPUShaderModule,
        pipelineLayout: GPUPipelineLayout,
        targetFormat: GPUTextureFormat,
        keys: FullscreenExecutionCacheKeys,
        blendMode: GPUBlendMode? = null,
    ): GPURenderPipeline {
        val decision = renderPipelineCache.getOrCreate(
            request = request(
                domain = GPUExecutionCacheDomain.RenderPipeline,
                keyHash = keys.renderPipelineKeyHash,
                subjectHash = keys.renderPipelineSubjectHash,
            ),
        ) {
            device.createRenderPipelineWithValidationScope(
                RenderPipelineDescriptor(
                    layout = pipelineLayout,
                    vertex = VertexState(
                        module = shader,
                        entryPoint = "vs_main",
                        buffers = listOf(
                            VertexBufferLayout(
                                arrayStride = TEXT_ATLAS_VERTEX_STRIDE_BYTES.toULong(),
                                attributes = listOf(
                                    VertexAttribute(
                                        shaderLocation = 0u,
                                        offset = 0uL,
                                        format = GPUVertexFormat.Float32x2,
                                    ),
                                    VertexAttribute(
                                        shaderLocation = 1u,
                                        offset = 8uL,
                                        format = GPUVertexFormat.Float32x2,
                                    ),
                                ),
                            ),
                        ),
                    ),
                    primitive = PrimitiveState(),
                    depthStencil = DepthStencilState(
                        format = GPUTextureFormat.Depth24PlusStencil8,
                        depthWriteEnabled = false,
                        depthCompare = GPUCompareFunction.Always,
                        stencilFront = StencilFaceState(
                            compare = GPUCompareFunction.Always,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilBack = StencilFaceState(
                            compare = GPUCompareFunction.Always,
                            failOp = GPUStencilOperation.Keep,
                            depthFailOp = GPUStencilOperation.Keep,
                            passOp = GPUStencilOperation.Keep,
                        ),
                        stencilReadMask = 0u,
                        stencilWriteMask = 0u,
                    ),
                    fragment = FragmentState(
                        module = shader,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = targetFormat,
                                blend = blendStateFor(blendMode),
                            ),
                        ),
                    ),
                ),
            )
        }
        record(decision)
        return decision.readyHandle()
    }

    private fun request(
        domain: GPUExecutionCacheDomain,
        keyHash: String,
        subjectHash: String,
    ): GPUExecutionCacheRequest =
        GPUExecutionCacheRequest(
            domain = domain,
            keyHash = keyHash,
            subjectHash = subjectHash,
            deviceGeneration = deviceGeneration,
            expectedDeviceGeneration = deviceGeneration,
            ownerScope = "GPUResourceProvider",
        )

    private fun record(decision: GPUExecutionCacheDecision<*>) {
        decision.cacheEvents.forEach { event ->
            ledger = ledger.recordCacheEvent(event)
        }
        recordedDumpLines += decision.dumpLines()
    }

    override fun close() {
        var firstFailure: Throwable? = null
        listOf(
            renderPipelineCache,
            pipelineLayoutCache,
            bindGroupLayoutCache,
            moduleCache,
        ).forEach { cache ->
            try {
                cache.close()
            } catch (failure: Throwable) {
                if (firstFailure == null) {
                    firstFailure = failure
                } else {
                    firstFailure.addSuppressed(failure)
                }
            }
        }
        firstFailure?.let { throw it }
    }
}

private data class FullscreenExecutionCacheKeys(
    val moduleKeyHash: String,
    val moduleSubjectHash: String,
    val modulePreimage: String,
    val bindGroupLayoutKeyHash: String,
    val bindGroupLayoutSubjectHash: String,
    val bindGroupLayoutPreimage: String,
    val textureBindGroupLayoutKeyHash: String = "",
    val textureBindGroupLayoutSubjectHash: String = "",
    val textureBindGroupLayoutPreimage: String = "",
    val pipelineLayoutKeyHash: String,
    val pipelineLayoutSubjectHash: String,
    val pipelineLayoutPreimage: String,
    val renderPipelineKeyHash: String,
    val renderPipelineSubjectHash: String,
    val renderPipelinePreimage: String,
) {
    /** Emits backend-neutral cache-key preimage dumps without WGPU handles. */
    fun preimageDumpLines(): List<String> {
        val lines = mutableListOf(
            preimageDumpLine(
                domain = GPUExecutionCacheDomain.Module.telemetryDomain,
                keyHash = moduleKeyHash,
                subjectHash = moduleSubjectHash,
                preimage = modulePreimage,
            ),
            preimageDumpLine(
                domain = GPUExecutionCacheDomain.BindGroupLayout.telemetryDomain,
                keyHash = bindGroupLayoutKeyHash,
                subjectHash = bindGroupLayoutSubjectHash,
                preimage = bindGroupLayoutPreimage,
            ),
        )
        if (textureBindGroupLayoutKeyHash.isNotEmpty()) {
            lines += preimageDumpLine(
                domain = GPUExecutionCacheDomain.BindGroupLayout.telemetryDomain,
                keyHash = textureBindGroupLayoutKeyHash,
                subjectHash = textureBindGroupLayoutSubjectHash,
                preimage = textureBindGroupLayoutPreimage,
            )
        }
        lines += listOf(
            preimageDumpLine(
                domain = GPUExecutionCacheDomain.PipelineLayout.telemetryDomain,
                keyHash = pipelineLayoutKeyHash,
                subjectHash = pipelineLayoutSubjectHash,
                preimage = pipelineLayoutPreimage,
            ),
            preimageDumpLine(
                domain = GPUExecutionCacheDomain.RenderPipeline.telemetryDomain,
                keyHash = renderPipelineKeyHash,
                subjectHash = renderPipelineSubjectHash,
                preimage = renderPipelinePreimage,
            ),
        )
        return lines
    }

    private fun preimageDumpLine(
        domain: String,
        keyHash: String,
        subjectHash: String,
        preimage: String,
    ): String =
        "execution.cache.preimage domain=$domain key=$keyHash subject=$subjectHash " +
            "deviceScope=runtime-helper preimage=${preimage.dumpPreimage()}"
}

private fun fullscreenTextureExecutionCacheKeys(
    wgsl: String,
    targetFormat: GPUTextureFormat,
    textureFormat: GPUTextureFormat,
    blendMode: GPUBlendMode? = null,
): FullscreenExecutionCacheKeys {
    val blendLabel = blendMode?.wgpuLabel ?: "src_over"
    val targetFormatClass = targetFormat.toBackendColorFormat()
    val wgslHash = stableSha256(wgsl)
    val bindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=fullscreen-uniform",
        "version=1",
        "binding=0",
        "visibility=fragment",
        "bufferType=uniform",
        "dynamicOffsets=false",
    ).joinToString("\n")
    val bindGroupLayoutHash = stableSha256(bindGroupLayoutPreimage)
    val textureBindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=fullscreen-texture-sampler",
        "version=1",
        "binding=1:type=texture:format=${textureFormat.name}",
        "binding=2:type=sampler:filtering",
        "visibility=fragment",
    ).joinToString("\n")
    val textureBindGroupLayoutHash = stableSha256(textureBindGroupLayoutPreimage)
    val modulePreimage = listOf(
        "kind=wgsl-module",
        "role=fullscreen-texture-pass",
        "entryPoints=vs_main,fs_main",
        "wgsl=$wgslHash",
    ).joinToString("\n")
    val moduleHash = stableSha256(modulePreimage)
    val pipelineLayoutPreimage = listOf(
        "kind=pipeline-layout",
        "role=fullscreen-texture-pass",
        "version=1",
        "bindGroupLayouts=$bindGroupLayoutHash,$textureBindGroupLayoutHash",
    ).joinToString("\n")
    val pipelineLayoutHash = stableSha256(pipelineLayoutPreimage)
    val renderPreimage = GPUPipelineKeyPreimage.Render(
        renderStepIdentity = "gpu-backend.fullscreen-texture-pass",
        renderStepVersion = "1",
        primitiveTopology = "triangle-list",
        materialKeyHash = stableSha256("material:fullscreen-texture-color-uniform:v1"),
        materialProgramId = "wgsl.fullscreen-texture-color",
        materialDictionaryVersion = "runtime-helper-v1",
        materialLayoutHash = "$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        snippetIdentityHash = stableSha256("snippet:fullscreen-texture:v1"),
        moduleHash = moduleHash,
        vertexLayoutHash = stableSha256("vertex-layout:fullscreen-triangle:vertex-index-only"),
        targetFormatClass = targetFormatClass,
        blendStateHash = stableSha256("blend:$blendLabel-premul:v1"),
        sampleStateHash = stableSha256("sample-state:count=1:mask=all"),
        bindGroupLayoutHash = "$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        capabilityClass = "webgpu-wgsl-fullscreen-texture-pass",
        capabilityFacts = listOf("adapter-backed-helper", "targetFormat=$targetFormatClass", "textureFormat=${textureFormat.name}"),
        rendererSalt = "kgpu-m26-001",
    )
    val canonicalRenderPreimage = GPUPipelineKeys.canonicalRenderPreimage(renderPreimage)
    val renderPipelineKey = GPUPipelineKeys.renderPipelineKey(renderPreimage).value

    return FullscreenExecutionCacheKeys(
        moduleKeyHash = "module:$moduleHash",
        moduleSubjectHash = "wgsl:$wgslHash",
        modulePreimage = modulePreimage,
        bindGroupLayoutKeyHash = "bind-group-layout:$bindGroupLayoutHash",
        bindGroupLayoutSubjectHash = "layout-shape:$bindGroupLayoutHash",
        bindGroupLayoutPreimage = bindGroupLayoutPreimage,
        textureBindGroupLayoutKeyHash = "bind-group-layout:$textureBindGroupLayoutHash",
        textureBindGroupLayoutSubjectHash = "layout-shape:$textureBindGroupLayoutHash",
        textureBindGroupLayoutPreimage = textureBindGroupLayoutPreimage,
        pipelineLayoutKeyHash = "pipeline-layout:$pipelineLayoutHash",
        pipelineLayoutSubjectHash = "bind-groups:$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        pipelineLayoutPreimage = pipelineLayoutPreimage,
        renderPipelineKeyHash = renderPipelineKey,
        renderPipelineSubjectHash = stableSha256(canonicalRenderPreimage),
        renderPipelinePreimage = canonicalRenderPreimage,
    )
}

private fun blendTextureExecutionCacheKeys(
    wgsl: String,
    targetFormat: GPUTextureFormat,
    textureFormat: GPUTextureFormat,
): FullscreenExecutionCacheKeys {
    val targetFormatClass = targetFormat.toBackendColorFormat()
    val wgslHash = stableSha256(wgsl)
    val bindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=blend-uniform",
        "version=1",
        "binding=0",
        "visibility=fragment",
        "bufferType=uniform",
        "dynamicOffsets=false",
    ).joinToString("\n")
    val bindGroupLayoutHash = stableSha256(bindGroupLayoutPreimage)
    val textureBindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=blend-dual-texture-sampler",
        "version=1",
        "binding=1:type=texture:format=${textureFormat.name}",
        "binding=2:type=sampler:filtering",
        "binding=3:type=texture:format=${textureFormat.name}",
        "binding=4:type=sampler:filtering",
        "visibility=fragment",
    ).joinToString("\n")
    val textureBindGroupLayoutHash = stableSha256(textureBindGroupLayoutPreimage)
    val modulePreimage = listOf(
        "kind=wgsl-module",
        "role=blend-pass",
        "entryPoints=vs_main,fs_main",
        "wgsl=$wgslHash",
    ).joinToString("\n")
    val moduleHash = stableSha256(modulePreimage)
    val pipelineLayoutPreimage = listOf(
        "kind=pipeline-layout",
        "role=blend-pass",
        "version=1",
        "bindGroupLayouts=$bindGroupLayoutHash,$textureBindGroupLayoutHash",
    ).joinToString("\n")
    val pipelineLayoutHash = stableSha256(pipelineLayoutPreimage)
    val renderPreimage = GPUPipelineKeyPreimage.Render(
        renderStepIdentity = "gpu-backend.blend-pass",
        renderStepVersion = "1",
        primitiveTopology = "triangle-list",
        materialKeyHash = stableSha256("material:blend-uniform:v1"),
        materialProgramId = "wgsl.blend-pass",
        materialDictionaryVersion = "runtime-helper-v1",
        materialLayoutHash = "$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        snippetIdentityHash = stableSha256("snippet:blend-pass:v1"),
        moduleHash = moduleHash,
        vertexLayoutHash = stableSha256("vertex-layout:fullscreen-triangle:vertex-index-only"),
        targetFormatClass = targetFormatClass,
        blendStateHash = stableSha256("blend:src_over-premul:v1"),
        sampleStateHash = stableSha256("sample-state:count=1:mask=all"),
        bindGroupLayoutHash = "$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        capabilityClass = "webgpu-wgsl-blend-pass",
        capabilityFacts = listOf("adapter-backed-helper", "targetFormat=$targetFormatClass", "textureFormat=${textureFormat.name}"),
        rendererSalt = "kgpu-m26-001",
    )
    val canonicalRenderPreimage = GPUPipelineKeys.canonicalRenderPreimage(renderPreimage)
    val renderPipelineKey = GPUPipelineKeys.renderPipelineKey(renderPreimage).value

    return FullscreenExecutionCacheKeys(
        moduleKeyHash = "module:$moduleHash",
        moduleSubjectHash = "wgsl:$wgslHash",
        modulePreimage = modulePreimage,
        bindGroupLayoutKeyHash = "bind-group-layout:$bindGroupLayoutHash",
        bindGroupLayoutSubjectHash = "layout-shape:$bindGroupLayoutHash",
        bindGroupLayoutPreimage = bindGroupLayoutPreimage,
        textureBindGroupLayoutKeyHash = "bind-group-layout:$textureBindGroupLayoutHash",
        textureBindGroupLayoutSubjectHash = "layout-shape:$textureBindGroupLayoutHash",
        textureBindGroupLayoutPreimage = textureBindGroupLayoutPreimage,
        pipelineLayoutKeyHash = "pipeline-layout:$pipelineLayoutHash",
        pipelineLayoutSubjectHash = "bind-groups:$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        pipelineLayoutPreimage = pipelineLayoutPreimage,
        renderPipelineKeyHash = renderPipelineKey,
        renderPipelineSubjectHash = stableSha256(canonicalRenderPreimage),
        renderPipelinePreimage = canonicalRenderPreimage,
    )
}

private fun fullscreenExecutionCacheKeys(
    wgsl: String,
    targetFormat: GPUTextureFormat,
    blendMode: GPUBlendMode? = null,
): FullscreenExecutionCacheKeys {
    val blendLabel = blendMode?.wgpuLabel ?: "src_over"
    val targetFormatClass = targetFormat.toBackendColorFormat()
    val wgslHash = stableSha256(wgsl)
    val bindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=fullscreen-uniform",
        "version=1",
        "binding=0",
        "visibility=fragment",
        "bufferType=uniform",
        "dynamicOffsets=false",
    ).joinToString("\n")
    val bindGroupLayoutHash = stableSha256(bindGroupLayoutPreimage)
    val modulePreimage = listOf(
        "kind=wgsl-module",
        "role=fullscreen-pass",
        "entryPoints=vs_main,fs_main",
        "wgsl=$wgslHash",
    ).joinToString("\n")
    val moduleHash = stableSha256(modulePreimage)
    val pipelineLayoutPreimage = listOf(
        "kind=pipeline-layout",
        "role=fullscreen-pass",
        "version=1",
        "bindGroupLayouts=$bindGroupLayoutHash",
    ).joinToString("\n")
    val pipelineLayoutHash = stableSha256(pipelineLayoutPreimage)
    val renderPreimage = GPUPipelineKeyPreimage.Render(
        renderStepIdentity = "gpu-backend.fullscreen-pass",
        renderStepVersion = "1",
        primitiveTopology = "triangle-list",
        materialKeyHash = stableSha256("material:fullscreen-solid-color-uniform:v1"),
        materialProgramId = "wgsl.fullscreen-solid-color",
        materialDictionaryVersion = "runtime-helper-v1",
        materialLayoutHash = bindGroupLayoutHash,
        snippetIdentityHash = stableSha256("snippet:fullscreen-solid-color:v1"),
        moduleHash = moduleHash,
        vertexLayoutHash = stableSha256("vertex-layout:fullscreen-triangle:vertex-index-only"),
        targetFormatClass = targetFormatClass,
        blendStateHash = stableSha256("blend:$blendLabel-premul:v1"),
        sampleStateHash = stableSha256("sample-state:count=1:mask=all"),
        bindGroupLayoutHash = bindGroupLayoutHash,
        capabilityClass = "webgpu-wgsl-fullscreen-pass",
        capabilityFacts = listOf("adapter-backed-helper", "targetFormat=$targetFormatClass"),
        rendererSalt = "kgpu-m11-001",
    )
    val canonicalRenderPreimage = GPUPipelineKeys.canonicalRenderPreimage(renderPreimage)
    val renderPipelineKey = GPUPipelineKeys.renderPipelineKey(renderPreimage).value

    return FullscreenExecutionCacheKeys(
        moduleKeyHash = "module:$moduleHash",
        moduleSubjectHash = "wgsl:$wgslHash",
        modulePreimage = modulePreimage,
        bindGroupLayoutKeyHash = "bind-group-layout:$bindGroupLayoutHash",
        bindGroupLayoutSubjectHash = "layout-shape:$bindGroupLayoutHash",
        bindGroupLayoutPreimage = bindGroupLayoutPreimage,
        pipelineLayoutKeyHash = "pipeline-layout:$pipelineLayoutHash",
        pipelineLayoutSubjectHash = "bind-groups:$bindGroupLayoutHash",
        pipelineLayoutPreimage = pipelineLayoutPreimage,
        renderPipelineKeyHash = renderPipelineKey,
        renderPipelineSubjectHash = stableSha256(canonicalRenderPreimage),
        renderPipelinePreimage = canonicalRenderPreimage,
    )
}

private fun textAtlasExecutionCacheKeys(
    wgsl: String,
    targetFormat: GPUTextureFormat,
    textureFormat: GPUTextureFormat,
    blendMode: GPUBlendMode? = null,
): FullscreenExecutionCacheKeys {
    val blendLabel = blendMode?.wgpuLabel ?: "src_over"
    val targetFormatClass = targetFormat.toBackendColorFormat()
    val wgslHash = stableSha256(wgsl)
    val bindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=text-atlas-uniform",
        "version=1",
        "binding=0",
        "visibility=vertex|fragment",
        "bufferType=uniform",
        "dynamicOffsets=false",
    ).joinToString("\n")
    val bindGroupLayoutHash = stableSha256(bindGroupLayoutPreimage)
    val textureBindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=text-atlas-sampler",
        "version=1",
        "binding=1:type=texture:format=${textureFormat.name}",
        "binding=2:type=sampler:filtering",
        "visibility=fragment",
    ).joinToString("\n")
    val textureBindGroupLayoutHash = stableSha256(textureBindGroupLayoutPreimage)
    val modulePreimage = listOf(
        "kind=wgsl-module",
        "role=text-atlas-pass",
        "entryPoints=vs_main,fs_main",
        "wgsl=$wgslHash",
    ).joinToString("\n")
    val moduleHash = stableSha256(modulePreimage)
    val pipelineLayoutPreimage = listOf(
        "kind=pipeline-layout",
        "role=text-atlas-pass",
        "version=1",
        "bindGroupLayouts=$bindGroupLayoutHash,$textureBindGroupLayoutHash",
    ).joinToString("\n")
    val pipelineLayoutHash = stableSha256(pipelineLayoutPreimage)
    val renderPreimage = GPUPipelineKeyPreimage.Render(
        renderStepIdentity = "gpu-backend.text-atlas-pass",
        renderStepVersion = "1",
        primitiveTopology = "triangle-list",
        materialKeyHash = stableSha256("material:text-atlas-uniform:v1"),
        materialProgramId = "wgsl.text-atlas-a8",
        materialDictionaryVersion = "runtime-helper-v1",
        materialLayoutHash = "$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        snippetIdentityHash = stableSha256("snippet:text-atlas-a8:v1"),
        moduleHash = moduleHash,
        vertexLayoutHash = stableSha256("vertex-layout:text-atlas:float32x2+float32x2"),
        targetFormatClass = targetFormatClass,
        blendStateHash = stableSha256("blend:$blendLabel-premul:v1"),
        sampleStateHash = stableSha256("sample-state:count=1:mask=all"),
        bindGroupLayoutHash = "$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        capabilityClass = "webgpu-wgsl-text-atlas-pass",
        capabilityFacts = listOf("adapter-backed-helper", "targetFormat=$targetFormatClass", "textureFormat=${textureFormat.name}"),
        rendererSalt = "kgpu-m26-001",
    )
    val canonicalRenderPreimage = GPUPipelineKeys.canonicalRenderPreimage(renderPreimage)
    val renderPipelineKey = GPUPipelineKeys.renderPipelineKey(renderPreimage).value

    return FullscreenExecutionCacheKeys(
        moduleKeyHash = "module:$moduleHash",
        moduleSubjectHash = "wgsl:$wgslHash",
        modulePreimage = modulePreimage,
        bindGroupLayoutKeyHash = "bind-group-layout:$bindGroupLayoutHash",
        bindGroupLayoutSubjectHash = "layout-shape:$bindGroupLayoutHash",
        bindGroupLayoutPreimage = bindGroupLayoutPreimage,
        textureBindGroupLayoutKeyHash = "bind-group-layout:$textureBindGroupLayoutHash",
        textureBindGroupLayoutSubjectHash = "layout-shape:$textureBindGroupLayoutHash",
        textureBindGroupLayoutPreimage = textureBindGroupLayoutPreimage,
        pipelineLayoutKeyHash = "pipeline-layout:$pipelineLayoutHash",
        pipelineLayoutSubjectHash = "bind-groups:$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        pipelineLayoutPreimage = pipelineLayoutPreimage,
        renderPipelineKeyHash = renderPipelineKey,
        renderPipelineSubjectHash = stableSha256(canonicalRenderPreimage),
        renderPipelinePreimage = canonicalRenderPreimage,
    )
}

private fun colorGlyphExecutionCacheKeys(
    wgsl: String,
    targetFormat: GPUTextureFormat,
    textureFormat: GPUTextureFormat,
    blendMode: GPUBlendMode? = null,
): FullscreenExecutionCacheKeys {
    val blendLabel = blendMode?.wgpuLabel ?: "src_over"
    val targetFormatClass = targetFormat.toBackendColorFormat()
    val wgslHash = stableSha256(wgsl)
    val bindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=color-glyph-uniform",
        "version=1",
        "binding=0",
        "visibility=vertex|fragment",
        "bufferType=uniform",
        "dynamicOffsets=false",
    ).joinToString("\n")
    val bindGroupLayoutHash = stableSha256(bindGroupLayoutPreimage)
    val textureBindGroupLayoutPreimage = listOf(
        "kind=bind-group-layout",
        "role=color-glyph-sampler",
        "version=1",
        "binding=1:type=texture:format=${textureFormat.name}",
        "binding=2:type=sampler:filtering",
        "visibility=fragment",
    ).joinToString("\n")
    val textureBindGroupLayoutHash = stableSha256(textureBindGroupLayoutPreimage)
    val modulePreimage = listOf(
        "kind=wgsl-module",
        "role=color-glyph-pass",
        "entryPoints=vs_main,fs_main",
        "wgsl=$wgslHash",
    ).joinToString("\n")
    val moduleHash = stableSha256(modulePreimage)
    val pipelineLayoutPreimage = listOf(
        "kind=pipeline-layout",
        "role=color-glyph-pass",
        "version=1",
        "bindGroupLayouts=$bindGroupLayoutHash,$textureBindGroupLayoutHash",
    ).joinToString("\n")
    val pipelineLayoutHash = stableSha256(pipelineLayoutPreimage)
    val renderPreimage = GPUPipelineKeyPreimage.Render(
        renderStepIdentity = "gpu-backend.color-glyph-pass",
        renderStepVersion = "1",
        primitiveTopology = "triangle-list",
        materialKeyHash = stableSha256("material:color-glyph-uniform:v1"),
        materialProgramId = "wgsl.color-glyph",
        materialDictionaryVersion = "runtime-helper-v1",
        materialLayoutHash = "$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        snippetIdentityHash = stableSha256("snippet:color-glyph:v1"),
        moduleHash = moduleHash,
        vertexLayoutHash = stableSha256("vertex-layout:color-glyph:float32x2+float32x2"),
        targetFormatClass = targetFormatClass,
        blendStateHash = stableSha256("blend:$blendLabel-premul:v1"),
        sampleStateHash = stableSha256("sample-state:count=1:mask=all"),
        bindGroupLayoutHash = "$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        capabilityClass = "webgpu-wgsl-color-glyph-pass",
        capabilityFacts = listOf("adapter-backed-helper", "targetFormat=$targetFormatClass", "textureFormat=${textureFormat.name}"),
        rendererSalt = "kgpu-m34-001",
    )
    val canonicalRenderPreimage = GPUPipelineKeys.canonicalRenderPreimage(renderPreimage)
    val renderPipelineKey = GPUPipelineKeys.renderPipelineKey(renderPreimage).value

    return FullscreenExecutionCacheKeys(
        moduleKeyHash = "module:$moduleHash",
        moduleSubjectHash = "wgsl:$wgslHash",
        modulePreimage = modulePreimage,
        bindGroupLayoutKeyHash = "bind-group-layout:$bindGroupLayoutHash",
        bindGroupLayoutSubjectHash = "layout-shape:$bindGroupLayoutHash",
        bindGroupLayoutPreimage = bindGroupLayoutPreimage,
        textureBindGroupLayoutKeyHash = "bind-group-layout:$textureBindGroupLayoutHash",
        textureBindGroupLayoutSubjectHash = "layout-shape:$textureBindGroupLayoutHash",
        textureBindGroupLayoutPreimage = textureBindGroupLayoutPreimage,
        pipelineLayoutKeyHash = "pipeline-layout:$pipelineLayoutHash",
        pipelineLayoutSubjectHash = "bind-groups:$bindGroupLayoutHash,$textureBindGroupLayoutHash",
        pipelineLayoutPreimage = pipelineLayoutPreimage,
        renderPipelineKeyHash = renderPipelineKey,
        renderPipelineSubjectHash = stableSha256(canonicalRenderPreimage),
        renderPipelinePreimage = canonicalRenderPreimage,
    )
}

private fun stencilExecutionCacheKeys(
    wgsl: String,
    targetFormat: GPUTextureFormat,
    vertexStage: Boolean,
    blendMode: GPUBlendMode? = null,
): FullscreenExecutionCacheKeys {
    val blendLabel = blendMode?.wgpuLabel ?: "src_over"
    val targetFormatClass = targetFormat.toBackendColorFormat()
    val wgslHash = stableSha256(wgsl)
        val role = if (vertexStage) "stencil-write-vertex" else "stencil-test-fullscreen"
        val bindGroupLayoutPreimage = listOf(
            "kind=bind-group-layout",
            "role=$role",
            "version=1",
            "binding=0",
            "visibility=vertex|fragment",
            "bufferType=uniform",
            "dynamicOffsets=false",
        ).joinToString("\n")
    val bindGroupLayoutHash = stableSha256(bindGroupLayoutPreimage)
    val modulePreimage = listOf(
        "kind=wgsl-module",
        "role=$role",
        "entryPoints=vs_main,fs_main",
        "wgsl=$wgslHash",
    ).joinToString("\n")
    val moduleHash = stableSha256(modulePreimage)
    val pipelineLayoutPreimage = listOf(
        "kind=pipeline-layout",
        "role=$role",
        "version=1",
        "bindGroupLayouts=$bindGroupLayoutHash",
    ).joinToString("\n")
    val pipelineLayoutHash = stableSha256(pipelineLayoutPreimage)
    val renderPreimage = GPUPipelineKeyPreimage.Render(
        renderStepIdentity = "gpu-backend.$role",
        renderStepVersion = "1",
        primitiveTopology = "triangle-list",
        materialKeyHash = stableSha256("material:$role:v1"),
        materialProgramId = "wgsl.$role",
        materialDictionaryVersion = "runtime-helper-v1",
        materialLayoutHash = bindGroupLayoutHash,
        snippetIdentityHash = stableSha256("snippet:$role:v1"),
        moduleHash = moduleHash,
        vertexLayoutHash = if (vertexStage) stableSha256("vertex-layout:float32x2:stencil") else stableSha256("vertex-layout:fullscreen-triangle:vertex-index-only"),
        targetFormatClass = targetFormatClass,
        blendStateHash = stableSha256("blend:$blendLabel-premul:v1"),
        sampleStateHash = stableSha256("sample-state:count=1:mask=all"),
        bindGroupLayoutHash = bindGroupLayoutHash,
        capabilityClass = "webgpu-wgsl-$role",
        capabilityFacts = listOf("adapter-backed-helper", "targetFormat=$targetFormatClass"),
        rendererSalt = "kgpu-m28-001",
    )
    val canonicalRenderPreimage = GPUPipelineKeys.canonicalRenderPreimage(renderPreimage)
    val renderPipelineKey = GPUPipelineKeys.renderPipelineKey(renderPreimage).value

    return FullscreenExecutionCacheKeys(
        moduleKeyHash = "module:$moduleHash",
        moduleSubjectHash = "wgsl:$wgslHash",
        modulePreimage = modulePreimage,
        bindGroupLayoutKeyHash = "bind-group-layout:$bindGroupLayoutHash",
        bindGroupLayoutSubjectHash = "layout-shape:$bindGroupLayoutHash",
        bindGroupLayoutPreimage = bindGroupLayoutPreimage,
        pipelineLayoutKeyHash = "pipeline-layout:$pipelineLayoutHash",
        pipelineLayoutSubjectHash = "bind-groups:$bindGroupLayoutHash",
        pipelineLayoutPreimage = pipelineLayoutPreimage,
        renderPipelineKeyHash = renderPipelineKey,
        renderPipelineSubjectHash = stableSha256(canonicalRenderPreimage),
        renderPipelinePreimage = canonicalRenderPreimage,
    )
}

private fun String.dumpPreimage(): String =
    lineSequence()
        .map { line -> line.trim() }
        .filter { line -> line.isNotEmpty() }
        .joinToString(";")

private fun <T : Any> GPUExecutionCacheDecision<T>.readyHandle(): T =
    when (this) {
        is GPUExecutionCacheDecision.Ready -> handle
        is GPUExecutionCacheDecision.Refused ->
            error("WGPU execution cache refused materialization with $diagnosticCode")
        is GPUExecutionCacheDecision.Evicted ->
            error("WGPU execution cache entry was evicted before materialization")
    }

private fun GPUDevice.createRenderPipelineWithValidationScope(
    descriptor: RenderPipelineDescriptor,
): GPURenderPipeline {
    pushErrorScope(GPUErrorFilter.Validation)
    val pipeline = createRenderPipeline(descriptor)
    val validationError = runBlocking { popErrorScope().getOrThrow() }
    if (validationError != null) {
        pipeline.close()
        error("WGPU render pipeline validation failed: ${validationError.message}")
    }
    return pipeline
}

private fun stableSha256(input: String): String {
    val digest = MessageDigest.getInstance("SHA-256").digest(input.toByteArray(Charsets.UTF_8))
    return "sha256:" + digest.joinToString(separator = "") { byte -> "%02x".format(byte) }
}

private data class NativeWindowRuntime(
    val instance: WGPU,
    val surface: NativeSurface,
    val device: GPUDevice,
    val format: GPUTextureFormat,
    val alphaMode: CompositeAlphaMode,
    val adapterInfo: GPUBackendAdapterSummary,
) : AutoCloseable {
    /** Reconfigures the native surface to the latest non-zero size before presentation. */
    fun configure(width: Int, height: Int) {
        if (width <= 0 || height <= 0) return
        surface.configure(
            SurfaceConfiguration(
                device = device,
                format = format,
                usage = GPUTextureUsage.RenderAttachment,
                alphaMode = alphaMode,
            ),
            width.toUInt(),
            height.toUInt(),
        )
    }

    override fun close() {
        device.close()
        surface.close()
        instance.close()
    }
}

private fun createNativeWindowRuntime(binding: GPUNativeSurfaceBinding): NativeWindowRuntime {
    require(binding.platform == GPUNativePlatform.AppKitMetalLayer) {
        "Unsupported native platform ${binding.platform}"
    }

    LibraryLoader.load()
    val instance = WGPU.createInstance(WGPUInstanceBackend.Metal)
        ?: error("WGPU Metal instance creation returned null")
    try {
        val layerAddress = binding.pointerLabels.firstNonZeroPointer("layerHandle", "nsLayer", "metalLayer")
            ?: error("GPUNativeSurfaceBinding.pointerLabels must provide a non-zero AppKit Metal layer pointer")
        val surface = instance.getSurfaceFromMetalLayer(JvmNativeAddress(MemorySegment.ofAddress(layerAddress)))
            ?: error("WGPU surface creation from Metal layer returned null")
        try {
            val adapter = instance.requestAdapter(surface)
                ?: error("WGPU adapter request failed for native surface")
            try {
                surface.computeSurfaceCapabilities(adapter)
                val adapterInfo = GPUBackendAdapterSummary(adapterSummary(adapter.info))
                val device = runBlocking { adapter.requestDevice() }
                    .getOrElse { error -> error(error.message ?: error.toString()) }
                try {
                    val format = surface.supportedFormats.firstOrNull { it == GPUTextureFormat.BGRA8Unorm }
                        ?: surface.supportedFormats.firstOrNull()
                        ?: GPUTextureFormat.BGRA8Unorm
                    val alphaMode = surface.supportedAlphaMode.firstOrNull { it == CompositeAlphaMode.Opaque }
                        ?: CompositeAlphaMode.Auto
                    return NativeWindowRuntime(
                        instance = instance,
                        surface = surface,
                        device = device,
                        format = format,
                        alphaMode = alphaMode,
                        adapterInfo = adapterInfo,
                    ).also { runtime ->
                        runtime.configure(width = binding.width, height = binding.height)
                    }
                } catch (failure: Throwable) {
                    device.close()
                    throw failure
                }
            } finally {
                adapter.close()
            }
        } catch (failure: Throwable) {
            surface.close()
            throw failure
        }
    } catch (failure: Throwable) {
        instance.close()
        throw failure
    }
}

private fun adapterSummary(glfw: GLFWContext): String = adapterSummary(glfw.wgpuContext.adapter.info)

private fun adapterSummary(info: GPUAdapterInfo): String {
    val parts = buildList {
        if (info.vendor.isNotBlank()) add(info.vendor)
        if (info.device.isNotBlank()) add(info.device)
    }
    val head = if (parts.isEmpty()) "unknown-adapter" else parts.joinToString("/")
    val detail = buildList {
        if (info.architecture.isNotBlank()) add("arch=${info.architecture}")
        if (info.description.isNotBlank()) add("desc=${info.description}")
    }
    return if (detail.isEmpty()) head else "$head ${detail.joinToString(" ")}"
}

private fun Map<String, Long>.firstNonZeroPointer(vararg keys: String): Long? =
    keys.asSequence()
        .mapNotNull { key -> this[key] }
        .firstOrNull { pointer -> pointer != 0L }

private fun GPUClearColor.toWgpuColor(): Color =
    Color(r = red, g = green, b = blue, a = alpha)

private fun toWgpuFactor(f: GPUBlendFactor): io.ygdrasil.webgpu.GPUBlendFactor = when (f) {
    GPUBlendFactor.Zero -> io.ygdrasil.webgpu.GPUBlendFactor.Zero
    GPUBlendFactor.One -> io.ygdrasil.webgpu.GPUBlendFactor.One
    GPUBlendFactor.Src -> io.ygdrasil.webgpu.GPUBlendFactor.Src
    GPUBlendFactor.OneMinusSrc -> io.ygdrasil.webgpu.GPUBlendFactor.OneMinusSrc
    GPUBlendFactor.Dst -> io.ygdrasil.webgpu.GPUBlendFactor.Dst
    GPUBlendFactor.OneMinusDst -> io.ygdrasil.webgpu.GPUBlendFactor.OneMinusDst
    GPUBlendFactor.SrcAlpha -> io.ygdrasil.webgpu.GPUBlendFactor.SrcAlpha
    GPUBlendFactor.OneMinusSrcAlpha -> io.ygdrasil.webgpu.GPUBlendFactor.OneMinusSrcAlpha
    GPUBlendFactor.DstAlpha -> io.ygdrasil.webgpu.GPUBlendFactor.DstAlpha
    GPUBlendFactor.OneMinusDstAlpha -> io.ygdrasil.webgpu.GPUBlendFactor.OneMinusDstAlpha
    GPUBlendFactor.SrcAlphaSaturated -> io.ygdrasil.webgpu.GPUBlendFactor.SrcAlphaSaturated
    GPUBlendFactor.Constant -> io.ygdrasil.webgpu.GPUBlendFactor.Constant
    GPUBlendFactor.OneMinusConstant -> io.ygdrasil.webgpu.GPUBlendFactor.OneMinusConstant
}

private fun blendStateFor(blendMode: GPUBlendMode?): BlendState {
    val mode = blendMode
    if (mode == null || mode == GPUBlendMode.SRC_OVER || mode.requiresDestinationRead) {
        return BlendState(
            color = BlendComponent(GPUBlendOperation.Add, io.ygdrasil.webgpu.GPUBlendFactor.One, io.ygdrasil.webgpu.GPUBlendFactor.OneMinusSrcAlpha),
            alpha = BlendComponent(GPUBlendOperation.Add, io.ygdrasil.webgpu.GPUBlendFactor.One, io.ygdrasil.webgpu.GPUBlendFactor.OneMinusSrcAlpha),
        )
    }
    return BlendState(
        color = BlendComponent(
            operation = GPUBlendOperation.Add,
            srcFactor = toWgpuFactor(mode.colorSrcFactor),
            dstFactor = toWgpuFactor(mode.colorDstFactor),
        ),
        alpha = BlendComponent(
            operation = GPUBlendOperation.Add,
            srcFactor = toWgpuFactor(mode.alphaSrcFactor),
            dstFactor = toWgpuFactor(mode.alphaDstFactor),
        ),
    )
}

private fun GPUTextureFormat.bytesPerPixel(): Int =
    when (this) {
        GPUTextureFormat.RGBA8Unorm,
        GPUTextureFormat.RGBA8UnormSrgb,
        GPUTextureFormat.BGRA8Unorm,
        GPUTextureFormat.BGRA8UnormSrgb -> RGBA_BYTES_PER_PIXEL
        GPUTextureFormat.R8Unorm -> 1
        else -> error("Unsupported bytes-per-pixel mapping for texture format $this")
    }

private fun String.toWgpuTextureFormat(): GPUTextureFormat =
    when (normalizedColorFormat()) {
        "rgba8unorm" -> GPUTextureFormat.RGBA8Unorm
        "rgba8unorm-srgb" -> GPUTextureFormat.RGBA8UnormSrgb
        "bgra8unorm" -> GPUTextureFormat.BGRA8Unorm
        "bgra8unorm-srgb" -> GPUTextureFormat.BGRA8UnormSrgb
        "r8unorm" -> GPUTextureFormat.R8Unorm
        "a8unorm" -> GPUTextureFormat.R8Unorm
        else -> error("Unsupported GPU color format $this")
    }

private fun GPUTextureFormat.toBackendColorFormat(): String =
    when (this) {
        GPUTextureFormat.RGBA8Unorm -> "rgba8unorm"
        GPUTextureFormat.RGBA8UnormSrgb -> "rgba8unorm-srgb"
        GPUTextureFormat.BGRA8Unorm -> "bgra8unorm"
        GPUTextureFormat.BGRA8UnormSrgb -> "bgra8unorm-srgb"
        else -> name.lowercase()
    }

private fun GPUTextureFormat.isBgraFormat(): Boolean =
    this == GPUTextureFormat.BGRA8Unorm || this == GPUTextureFormat.BGRA8UnormSrgb

private fun String.normalizedColorFormat(): String = lowercase()

private class GPUResourceScope : AutoCloseable {
    private val closeActions = ArrayDeque<() -> Unit>()

    /** Tracks a resource and registers the matching cleanup callback for reverse-order teardown. */
    fun <T> track(resource: T, close: (T) -> Unit): T {
        closeActions.addFirst { close(resource) }
        return resource
    }

    /** Tracks the resource only when it already exposes `AutoCloseable`. */
    fun <T> trackIfAutoCloseable(resource: T): T {
        if (resource is AutoCloseable) {
            track(resource) { it.close() }
        }
        return resource
    }

    override fun close() {
        var firstFailure: Throwable? = null
        while (closeActions.isNotEmpty()) {
            try {
                closeActions.removeFirst().invoke()
            } catch (failure: Throwable) {
                if (firstFailure == null) {
                    firstFailure = failure
                } else {
                    firstFailure.addSuppressed(failure)
                }
            }
        }
        firstFailure?.let { throw it }
    }
}
