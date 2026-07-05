package org.graphiks.kanvas.gpu.renderer.execution

import ffi.JvmNativeAddress
import ffi.LibraryLoader
import io.ygdrasil.webgpu.ArrayBuffer
import io.ygdrasil.webgpu.BindGroupDescriptor
import io.ygdrasil.webgpu.BindGroupEntry
import io.ygdrasil.webgpu.BufferBinding
import io.ygdrasil.webgpu.BufferDescriptor
import io.ygdrasil.webgpu.Color
import io.ygdrasil.webgpu.CompositeAlphaMode
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.GLFWContext
import io.ygdrasil.webgpu.GPUAddressMode
import io.ygdrasil.webgpu.GPUAdapterInfo
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUFilterMode
import io.ygdrasil.webgpu.GPUIndexFormat
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUSampler
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.GPUTextureView
import io.ygdrasil.webgpu.NativeSurface
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDepthStencilAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.SamplerDescriptor
import io.ygdrasil.webgpu.SurfaceConfiguration
import io.ygdrasil.webgpu.SurfaceTextureStatus
import io.ygdrasil.webgpu.TexelCopyBufferInfo
import io.ygdrasil.webgpu.TexelCopyBufferLayout
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.TextureDescriptor
import io.ygdrasil.webgpu.WGPU
import io.ygdrasil.webgpu.WGPUInstanceBackend
import io.ygdrasil.webgpu.beginRenderPass
import io.ygdrasil.webgpu.glfwContextRenderer
import java.lang.foreign.MemorySegment
import java.util.concurrent.atomic.AtomicLong
import kotlinx.coroutines.runBlocking
import org.graphiks.kanvas.gpu.renderer.telemetry.GPUCacheTelemetry

import org.graphiks.kanvas.gpu.renderer.text.colorGlyphCompositeWgsl
import org.graphiks.kanvas.gpu.renderer.wgsl.TexturedVerticesColorFilterWgsl
import org.graphiks.kanvas.gpu.renderer.passes.GPUBlendMode
import org.graphiks.kanvas.gpu.renderer.pipelines.GPUFullscreenPipelineRequest
import org.graphiks.kanvas.gpu.renderer.state.GPUBlendMode as ContractGPUBlendMode

private const val COPY_BYTES_PER_ROW_ALIGNMENT: Int = 256
private const val FULL_SCREEN_TRIANGLE_VERTEX_COUNT: UInt = 3u
private const val RGBA_BYTES_PER_PIXEL: Int = 4
private const val RECT_COLOR_UNIFORM_SIZE_BYTES: ULong = 16uL
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

internal fun GPUBlendMode.toContractBlendModeOrNull(): ContractGPUBlendMode? = when (this) {
    GPUBlendMode.SRC -> ContractGPUBlendMode.Src
    GPUBlendMode.SRC_OVER -> ContractGPUBlendMode.SrcOver
    GPUBlendMode.MULTIPLY -> ContractGPUBlendMode.Multiply
    GPUBlendMode.SCREEN -> ContractGPUBlendMode.Screen
    else -> null
}

private fun nextSessionOrdinal(): Long = sessionOrdinalCounter.incrementAndGet()
private fun nextWindowRuntimeOrdinal(): Long = windowRuntimeOrdinalCounter.incrementAndGet()

object WgpuBackendRuntimeFactory {
    private var sharedInner: GPUBackendSession? = null

    /** Creates a WebGPU-backed runtime session when the host can initialize the backend.
     *  The session is **reused** across renders — creating a new WGPU device per render
     *  leaks native Metal/Dawn memory and causes "Context leak detected" after ~250 renders.
     *  The returned wrapper ignores close() so that callers using .use {} do not destroy
     *  the shared device. Call [dispose] before JVM shutdown to release native resources. */
    fun createOrNull(): GPUBackendSession? {
        if (sharedInner == null) {
            sharedInner = try {
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
        return sharedInner?.let { NonClosingSession(it) }
    }

    /** Release the shared session and its native resources. Must be called
     *  before JVM shutdown to avoid wgpu-native panics during cleanup. */
    fun dispose() {
        sharedInner?.close()
        sharedInner = null
    }

    private class NonClosingSession(
        private val inner: GPUBackendSession,
    ) : GPUBackendSession by inner {
        private val initialCacheTelemetry = inner.executionCacheTelemetry.associateBy(GPUCacheTelemetry::cacheName)

        override val executionCacheTelemetry: List<GPUCacheTelemetry>
            get() = inner.executionCacheTelemetry
                .map { telemetry -> telemetry.minusBaseline(initialCacheTelemetry[telemetry.cacheName]) }
                .filter(GPUCacheTelemetry::hasObservedEvents)

        override fun close() {
            (inner as? WgpuBackendSession)?.resetLogicalSessionState()
        }
    }
}

private fun GPUCacheTelemetry.minusBaseline(baseline: GPUCacheTelemetry?): GPUCacheTelemetry =
    if (baseline == null) {
        this
    } else {
        copy(
            hits = (hits - baseline.hits).coerceAtLeast(0L),
            misses = (misses - baseline.misses).coerceAtLeast(0L),
            evictions = (evictions - baseline.evictions).coerceAtLeast(0L),
            residentBytes = (residentBytes - baseline.residentBytes).coerceAtLeast(0L),
            pressureBytes = (pressureBytes - baseline.pressureBytes).coerceAtLeast(0L),
            creations = (creations - baseline.creations).coerceAtLeast(0L),
            failures = (failures - baseline.failures).coerceAtLeast(0L),
            staleGenerations = (staleGenerations - baseline.staleGenerations).coerceAtLeast(0L),
        )
    }

private fun GPUCacheTelemetry.hasObservedEvents(): Boolean =
    hits > 0L ||
        misses > 0L ||
        evictions > 0L ||
        creations > 0L ||
        failures > 0L ||
        staleGenerations > 0L

private class WgpuBackendSession(
    private val glfw: GLFWContext,
) : GPUBackendSession {
    private val sessionOrdinal = nextSessionOrdinal()
    private val deviceGeneration = sessionDeviceGeneration(sessionOrdinal)
    private val pipelineProvider = GPUBackendPipelineProvider(
        device = glfw.wgpuContext.device,
        deviceGeneration = deviceGeneration,
    )
    private var offscreenTargetOrdinalCounter = 0L

    override val adapterInfo: GPUBackendAdapterSummary? =
        GPUBackendAdapterSummary(adapterSummary(glfw))

    override val executionCacheTelemetry: List<GPUCacheTelemetry>
        get() = pipelineProvider.cacheTelemetry

    override val executionCacheDumpLines: List<String>
        get() = pipelineProvider.dumpLines

    override fun createOffscreenTarget(request: GPUOffscreenTargetRequest): GPUBackendOffscreenTarget =
        WgpuOffscreenTarget(
            sessionOrdinal = sessionOrdinal,
            offscreenTargetOrdinal = nextOffscreenTargetOrdinal(),
            deviceGeneration = deviceGeneration,
            device = glfw.wgpuContext.device,
            queue = glfw.wgpuContext.device.queue,
            request = request,
            pipelineProvider = pipelineProvider,
        )

    override fun createWindowSurface(binding: GPUNativeSurfaceBinding): GPUBackendWindowSurface =
        WgpuWindowSurface(binding = binding)

    override fun close() {
        try {
            pipelineProvider.close()
        } finally {
            glfw.close()
        }
    }

    private fun nextOffscreenTargetOrdinal(): Long {
        offscreenTargetOrdinalCounter += 1L
        return offscreenTargetOrdinalCounter
    }

    fun resetLogicalSessionState() {
        pipelineProvider.resetLogicalSessionState()
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
    private val pipelineProvider: GPUBackendPipelineProvider,
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
                    pipelineProvider = pipelineProvider,
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
                try {
                    recorder.block()
                } finally {
                    recorder.closeCachedResources()
                }
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
                pipelineProvider = pipelineProvider,
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
            try {
                block(recorder)
            } finally {
                recorder.closeCachedResources()
            }
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
    private val pipelineProvider = GPUBackendPipelineProvider(
        device = runtime.device,
        deviceGeneration = deviceGeneration,
    )
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
                    pipelineProvider = pipelineProvider,
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
                try {
                    recorder.block()
                } finally {
                    recorder.closeCachedResources()
                }
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
            pipelineProvider.close()
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
    private val pipelineProvider: GPUBackendPipelineProvider,
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
    private val recorderPipelineProvider = GPURecorderPipelineProvider(
        device = device,
        targetFormat = targetFormat,
    )

    fun closeCachedResources() {
        recorderPipelineProvider.close()
    }

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
        pipelineProvider.recordPreimages(keys)
        val bindGroupLayout = pipelineProvider.bindGroupLayout(keys = keys)
        val shader = pipelineProvider.shaderModule(wgsl = vertexWgsl, keys = keys)
        val pipelineLayout = pipelineProvider.pipelineLayout(bindGroupLayout = bindGroupLayout, keys = keys)
        val pipeline = pipelineProvider.vertexColorRenderPipeline(
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

        val bindGroupLayout = recorderPipelineProvider.texturedVertexBindGroupLayout()
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

        val gpuTextureFormat = textureFormat.toWgpuTextureFormat()
        val pipeline = recorderPipelineProvider.texturedVertexPipeline(
            colorFormat = gpuTextureFormat,
            blendMode = blendMode,
        )

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

        val bindGroupLayout = recorderPipelineProvider.dualUVVertexBindGroupLayout()
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

        val gpuTextureFormat = textureFormat.toWgpuTextureFormat()
        val pipeline = recorderPipelineProvider.dualUVVertexPipeline(
            colorFormat = gpuTextureFormat,
            blendMode = blendMode,
        )

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
        val resolution = pipelineProvider.resolveTextAtlasPipeline(
            wgsl = wgsl,
            targetFormat = targetFormat,
            textureFormat = textureFormat,
            blendMode = blendMode,
        )
        val bindGroupLayout = resolution.bindGroupLayouts[0]
        val textureBindGroupLayout = resolution.bindGroupLayouts[1]
        val pipeline = resolution.pipeline

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
        val resolution = pipelineProvider.resolveColorGlyphPipeline(
            wgsl = wgsl,
            targetFormat = targetFormat,
            textureFormat = textureFormat,
            blendMode = blendMode,
        )
        val bindGroupLayout = resolution.bindGroupLayouts[0]
        val textureBindGroupLayout = resolution.bindGroupLayouts[1]
        val pipeline = resolution.pipeline

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

        val resolution = pipelineProvider.resolveFullscreenTexturePipeline(
            wgsl = wgsl,
            targetFormat = targetFormat,
            textureFormat = textureFormat,
            blendMode = blendMode,
        )
        val bindGroupLayout = resolution.bindGroupLayouts[0]
        val textureBindGroupLayout = resolution.bindGroupLayouts[1]
        val pipeline = resolution.pipeline

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

        val resolution = pipelineProvider.resolveBlendTexturePipeline(
            wgsl = wgsl,
            targetFormat = targetFormat,
            textureFormat = textureFormat,
        )
        val bindGroupLayout = resolution.bindGroupLayouts[0]
        val textureBindGroupLayout = resolution.bindGroupLayouts[1]
        val pipeline = resolution.pipeline

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
        pipelineProvider.recordPreimages(keys)
        val pipeline = pipelineProvider.stencilWriteRenderPipeline(
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
        pipelineProvider.recordPreimages(keys)
        val bindGroupLayout = pipelineProvider.bindGroupLayout(keys = keys)
        val shader = pipelineProvider.shaderModule(wgsl = wgsl, keys = keys)
        val pipelineLayout = pipelineProvider.pipelineLayout(bindGroupLayout = bindGroupLayout, keys = keys)
        val pipeline = pipelineProvider.stencilTestRenderPipeline(
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

        val resolution = pipelineProvider.resolveFullscreenTexturePipeline(
            wgsl = wgsl,
            targetFormat = targetFormat,
            textureFormat = gpuTextureFormat,
            blendMode = blendMode,
        )
        val bindGroupLayout = resolution.bindGroupLayouts[0]
        val textureBindGroupLayout = resolution.bindGroupLayouts[1]
        val pipeline = resolution.pipeline

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

        val resolution = when (val contractBlendMode = blendMode?.toContractBlendModeOrNull()) {
            null -> if (blendMode == null) {
                pipelineProvider.resolveFullscreenPipeline(
                    GPUFullscreenPipelineRequest(
                        shaderSource = wgsl,
                        colorFormat = targetFormat,
                        blendMode = null,
                    ),
                )
            } else {
                pipelineProvider.resolveFullscreenPipelineForBackendBlend(
                    wgsl = wgsl,
                    targetFormat = targetFormat,
                    blendMode = blendMode,
                )
            }
            else -> pipelineProvider.resolveFullscreenPipeline(
                GPUFullscreenPipelineRequest(
                    shaderSource = wgsl,
                    colorFormat = targetFormat,
                    blendMode = contractBlendMode,
                ),
            )
        }
        val bindGroupLayout = resolution.bindGroupLayouts.single()
        val pipeline = resolution.pipeline

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

internal fun GPUTextureFormat.toBackendColorFormat(): String =
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
