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
import io.ygdrasil.webgpu.Extent3D
import io.ygdrasil.webgpu.FragmentState
import io.ygdrasil.webgpu.GLFWContext
import io.ygdrasil.webgpu.GPUBlendFactor
import io.ygdrasil.webgpu.GPUBlendOperation
import io.ygdrasil.webgpu.GPUAdapterInfo
import io.ygdrasil.webgpu.GPUBindGroup
import io.ygdrasil.webgpu.GPUBindGroupLayout
import io.ygdrasil.webgpu.GPUBuffer
import io.ygdrasil.webgpu.GPUBufferBindingType
import io.ygdrasil.webgpu.GPUBufferUsage
import io.ygdrasil.webgpu.GPUDevice
import io.ygdrasil.webgpu.GPUErrorFilter
import io.ygdrasil.webgpu.GPULoadOp
import io.ygdrasil.webgpu.GPUMapMode
import io.ygdrasil.webgpu.GPUPipelineLayout
import io.ygdrasil.webgpu.GPUQueue
import io.ygdrasil.webgpu.GPURenderPipeline
import io.ygdrasil.webgpu.GPUShaderModule
import io.ygdrasil.webgpu.GPUShaderStage
import io.ygdrasil.webgpu.GPUStoreOp
import io.ygdrasil.webgpu.GPUTexture
import io.ygdrasil.webgpu.GPUTextureFormat
import io.ygdrasil.webgpu.GPUTextureUsage
import io.ygdrasil.webgpu.NativeSurface
import io.ygdrasil.webgpu.PipelineLayoutDescriptor
import io.ygdrasil.webgpu.PrimitiveState
import io.ygdrasil.webgpu.RenderPassColorAttachment
import io.ygdrasil.webgpu.RenderPassDescriptor
import io.ygdrasil.webgpu.RenderPipelineDescriptor
import io.ygdrasil.webgpu.ShaderModuleDescriptor
import io.ygdrasil.webgpu.SurfaceConfiguration
import io.ygdrasil.webgpu.SurfaceTextureStatus
import io.ygdrasil.webgpu.TexelCopyBufferInfo
import io.ygdrasil.webgpu.TexelCopyTextureInfo
import io.ygdrasil.webgpu.TextureDescriptor
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

private class WgpuOffscreenTarget(
    private val sessionOrdinal: Long,
    private val offscreenTargetOrdinal: Long,
    private val deviceGeneration: GPUDeviceGeneration,
    private val device: GPUDevice,
    private val queue: GPUQueue,
    private val request: GPUOffscreenTargetRequest,
    private val executionCaches: WgpuExecutionCaches,
) : GPUBackendOffscreenTarget {
    private val format = request.colorFormat.toWgpuTextureFormat()
    private val bytesPerPixel = format.bytesPerPixel()
    private val tightBytesPerRow = request.width * bytesPerPixel
    private val paddedBytesPerRow = alignCopyBytesPerRow(tightBytesPerRow)
    private val stagingSize = (paddedBytesPerRow.toLong() * request.height.toLong()).toULong()
    private val texture = device.createTexture(
        TextureDescriptor(
            size = Extent3D(width = request.width.toUInt(), height = request.height.toUInt()),
            format = format,
            usage = GPUTextureUsage.RenderAttachment or GPUTextureUsage.CopySrc,
            label = "GPUBackend.offscreen.color",
        ),
    )
    private val stagingBuffer = device.createBuffer(
        BufferDescriptor(
            size = stagingSize,
            usage = GPUBufferUsage.MapRead or GPUBufferUsage.CopyDst,
            mappedAtCreation = false,
            label = "GPUBackend.offscreen.staging",
        ),
    )

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
        GpuResourceScope().use { resources ->
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
                    setScissorAction = { x, y, width, height -> setScissorRect(x, y, width, height) },
                    drawAction = { vertexCount -> draw(vertexCount) },
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
                    rowsPerImage = request.height.toUInt(),
                ),
                copySize = Extent3D(width = request.width.toUInt(), height = request.height.toUInt()),
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

    override fun close() {
        stagingBuffer.close()
        texture.close()
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

        GpuResourceScope().use { resources ->
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
    private val resourceScope: GpuResourceScope,
    private val executionCaches: WgpuExecutionCaches,
    private val setPipelineAction: (GPURenderPipeline) -> Unit,
    private val setBindGroupAction: (UInt, GPUBindGroup) -> Unit,
    private val setScissorAction: (UInt, UInt, UInt, UInt) -> Unit,
    private val drawAction: (UInt) -> Unit,
) : GPUBackendRenderRecorder {
    override fun drawFullscreenPass(
        wgsl: String,
        colorFormat: String,
        draws: List<GPUBackendRectDraw>,
    ) {
        require(wgsl.isNotBlank()) { "wgsl must not be blank" }
        require(colorFormat.normalizedColorFormat() == targetFormat.toBackendColorFormat()) {
            "Requested color format $colorFormat does not match target format ${targetFormat.toBackendColorFormat()}"
        }
        if (draws.isEmpty()) return

        val keys = fullscreenExecutionCacheKeys(wgsl = wgsl, targetFormat = targetFormat)
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
        )

        setPipelineAction(pipeline)
        draws.forEach { draw ->
            val uniform = resourceScope.track(
                device.createBuffer(
                    BufferDescriptor(
                        size = RECT_COLOR_UNIFORM_SIZE_BYTES,
                        usage = GPUBufferUsage.Uniform or GPUBufferUsage.CopyDst,
                        label = "GPUBackend.rect.color",
                    ),
                ),
            ) { it.close() }
            queue.writeBuffer(uniform, 0uL, ArrayBuffer.of(draw.rgbaPremul))
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
                            visibility = GPUShaderStage.Fragment,
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

    /** Returns the cached render pipeline for the module, layout, target, and blend-state facts. */
    fun renderPipeline(
        device: GPUDevice,
        shader: GPUShaderModule,
        pipelineLayout: GPUPipelineLayout,
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
            device.createRenderPipelineWithValidationScope(
                RenderPipelineDescriptor(
                    layout = pipelineLayout,
                    vertex = VertexState(module = shader, entryPoint = "vs_main"),
                    primitive = PrimitiveState(),
                    fragment = FragmentState(
                        module = shader,
                        entryPoint = "fs_main",
                        targets = listOf(
                            ColorTargetState(
                                format = targetFormat,
                                blend = srcOverBlendState(),
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
    val pipelineLayoutKeyHash: String,
    val pipelineLayoutSubjectHash: String,
    val pipelineLayoutPreimage: String,
    val renderPipelineKeyHash: String,
    val renderPipelineSubjectHash: String,
    val renderPipelinePreimage: String,
) {
    /** Emits backend-neutral cache-key preimage dumps without WGPU handles. */
    fun preimageDumpLines(): List<String> =
        listOf(
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

    private fun preimageDumpLine(
        domain: String,
        keyHash: String,
        subjectHash: String,
        preimage: String,
    ): String =
        "execution.cache.preimage domain=$domain key=$keyHash subject=$subjectHash " +
            "deviceScope=runtime-helper preimage=${preimage.dumpPreimage()}"
}

private fun fullscreenExecutionCacheKeys(
    wgsl: String,
    targetFormat: GPUTextureFormat,
): FullscreenExecutionCacheKeys {
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
        blendStateHash = stableSha256("blend:src-over-premul:v1"),
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
        error("WGPU render pipeline validation failed")
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

private fun srcOverBlendState(): BlendState =
    BlendState(
        color = BlendComponent(
            operation = GPUBlendOperation.Add,
            srcFactor = GPUBlendFactor.One,
            dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
        ),
        alpha = BlendComponent(
            operation = GPUBlendOperation.Add,
            srcFactor = GPUBlendFactor.One,
            dstFactor = GPUBlendFactor.OneMinusSrcAlpha,
        ),
    )

private fun GPUTextureFormat.bytesPerPixel(): Int =
    when (this) {
        GPUTextureFormat.RGBA8Unorm,
        GPUTextureFormat.RGBA8UnormSrgb,
        GPUTextureFormat.BGRA8Unorm,
        GPUTextureFormat.BGRA8UnormSrgb -> RGBA_BYTES_PER_PIXEL
        else -> error("Unsupported bytes-per-pixel mapping for texture format $this")
    }

private fun String.toWgpuTextureFormat(): GPUTextureFormat =
    when (normalizedColorFormat()) {
        "rgba8unorm" -> GPUTextureFormat.RGBA8Unorm
        "rgba8unorm-srgb" -> GPUTextureFormat.RGBA8UnormSrgb
        "bgra8unorm" -> GPUTextureFormat.BGRA8Unorm
        "bgra8unorm-srgb" -> GPUTextureFormat.BGRA8UnormSrgb
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

private class GpuResourceScope : AutoCloseable {
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
